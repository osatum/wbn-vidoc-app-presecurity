package com.osatum.wbn.migration.service;

import com.osatum.wbn.migration.dto.MigrationImportErrorDto;
import com.osatum.wbn.migration.dto.MigrationImportStage;
import com.osatum.wbn.migration.dto.MigrationImportState;
import com.osatum.wbn.migration.dto.MigrationImportStatusDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Przechowuje bieżący postęp pełnego importu w pamięci tej instancji aplikacji.
 * Synchronizuje aktualizacje wielu wykonawców i zwraca kopie statusu, aby klient nie modyfikował liczników.
 * Stan ten służy prezentacji w Narzędziach systemowych; trwałą gotowość generacji P2 przechowuje repozytorium analityki.
 */
@Service
public class MigrationImportStatusService {
    private static final int MAX_ERRORS = 50;

    private MigrationImportStatusDto status = idleStatus();

    public synchronized MigrationImportStatusDto current() {
        return copy(status);
    }

    public synchronized void started(int dictionaryTotal, int journalTotal) {
        Instant now = Instant.now();
        status = MigrationImportStatusDto.builder()
                .state(MigrationImportState.RUNNING)
                .stage(MigrationImportStage.PREPARING)
                .dictionaryTotal(dictionaryTotal)
                .journalTotal(journalTotal)
                .stageTotal(0)
                .stageProcessed(0)
                .message("Przygotowanie importu.")
                .startedAt(now)
                .updatedAt(now)
                .errors(List.of())
                .build();
    }

    public synchronized void prepared(int dictionaryTotal, int journalTotal) {
        status.setStage(MigrationImportStage.PREPARING);
        status.setDictionaryTotal(Math.max(dictionaryTotal, 0));
        status.setJournalTotal(Math.max(journalTotal, 0));
        status.setStageTotal(0);
        status.setStageProcessed(0);
        status.setCurrentFile(null);
        status.setMessage("Przygotowanie importu.");
        touch();
    }

    public synchronized void stage(MigrationImportStage stage, String message) {
        stage(stage, message, 0);
    }

    public synchronized void stage(MigrationImportStage stage, String message, int stageTotal) {
        status.setStage(stage);
        status.setMessage(message);
        status.setStageTotal(Math.max(stageTotal, 0));
        status.setStageProcessed(0);
        status.setCurrentFile(null);
        touch();
    }

    public synchronized void fileStarted(MigrationImportStage stage, String fileName) {
        status.setStage(stage);
        status.setCurrentFile(fileName);
        touch();
    }

    public synchronized void stageItemSucceeded(String itemName) {
        status.setCurrentFile(itemName);
        status.setStageProcessed(status.getStageProcessed() + 1);
        touch();
    }

    public synchronized void stageItemFailed(String type, String itemName, Throwable error) {
        status.setCurrentFile(itemName);
        status.setStageProcessed(status.getStageProcessed() + 1);
        addError(type, itemName, error);
        touch();
    }

    public synchronized void stageCompleted(String message) {
        status.setStageProcessed(status.getStageTotal());
        status.setCurrentFile(null);
        status.setMessage(message);
        touch();
    }

    /** Postęp liczony dla bieżącego rocznika bez rozszerzania publicznego kontraktu statusu. */
    public synchronized void stageProgress(int total, int processed, String year, String message) {
        status.setStageTotal(total);
        status.setStageProcessed(processed);
        status.setCurrentFile(year);
        status.setMessage(message);
        touch();
    }

    public synchronized void dictionarySucceeded(String fileName) {
        status.setCurrentFile(fileName);
        status.setDictionaryProcessed(status.getDictionaryProcessed() + 1);
        status.setDictionarySucceeded(status.getDictionarySucceeded() + 1);
        status.setStageProcessed(status.getStageProcessed() + 1);
        status.setMessage("Import slownikow w toku.");
        touch();
    }

    public synchronized void dictionaryFailed(String fileName, Throwable error) {
        status.setCurrentFile(fileName);
        status.setDictionaryProcessed(status.getDictionaryProcessed() + 1);
        status.setDictionaryFailed(status.getDictionaryFailed() + 1);
        status.setStageProcessed(status.getStageProcessed() + 1);
        status.setMessage("Import slownikow zakonczyl sie bledem.");
        addError("DICTIONARY", fileName, error);
        touch();
    }

    public synchronized void journalSucceeded(String fileName) {
        status.setCurrentFile(fileName);
        status.setJournalProcessed(status.getJournalProcessed() + 1);
        status.setJournalSucceeded(status.getJournalSucceeded() + 1);
        status.setStageProcessed(status.getStageProcessed() + 1);
        status.setMessage("Import czasopism w toku.");
        touch();
    }

    public synchronized void journalFailed(String fileName, Throwable error) {
        status.setCurrentFile(fileName);
        status.setJournalProcessed(status.getJournalProcessed() + 1);
        status.setJournalFailed(status.getJournalFailed() + 1);
        status.setStageProcessed(status.getStageProcessed() + 1);
        status.setMessage("Import czasopism zakonczyl sie bledem.");
        addError("JOURNAL", fileName, error);
        touch();
    }

    public synchronized void failed(String message, Throwable error) {
        status.setState(MigrationImportState.FAILED);
        status.setMessage(message);
        status.setFinishedAt(Instant.now());
        addError("IMPORT", null, error);
        touch();
    }

    public synchronized void finished() {
        boolean hasErrors = status.getDictionaryFailed() > 0 || status.getJournalFailed() > 0 || !status.getErrors().isEmpty();
        status.setState(hasErrors ? MigrationImportState.FAILED : MigrationImportState.COMPLETED);
        status.setMessage(hasErrors ? "Import zakonczony z bledami." : "Import zakonczony.");
        status.setFinishedAt(Instant.now());
        status.setCurrentFile(null);
        touch();
    }

    private static MigrationImportStatusDto idleStatus() {
        Instant now = Instant.now();
        return MigrationImportStatusDto.builder()
                .state(MigrationImportState.IDLE)
                .stage(MigrationImportStage.NONE)
                .message("Import nie zostal uruchomiony.")
                .updatedAt(now)
                .errors(List.of())
                .build();
    }

    private void addError(String type, String fileName, Throwable error) {
        addError(type, fileName, errorMessage(error));
    }

    private void addError(String type, String fileName, String message) {
        List<MigrationImportErrorDto> errors = new ArrayList<>(status.getErrors() == null ? List.of() : status.getErrors());
        if (errors.size() >= MAX_ERRORS) {
            return;
        }
        errors.add(MigrationImportErrorDto.builder()
                .type(type)
                .fileName(fileName)
                .message(message)
                .build());
        status.setErrors(errors);
    }

    private String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "Nieznany blad importu.";
        }
        return error.getMessage();
    }

    private void touch() {
        status.setUpdatedAt(Instant.now());
    }

    private MigrationImportStatusDto copy(MigrationImportStatusDto source) {
        return MigrationImportStatusDto.builder()
                .state(source.getState())
                .stage(source.getStage())
                .dictionaryTotal(source.getDictionaryTotal())
                .dictionaryProcessed(source.getDictionaryProcessed())
                .dictionarySucceeded(source.getDictionarySucceeded())
                .dictionaryFailed(source.getDictionaryFailed())
                .journalTotal(source.getJournalTotal())
                .journalProcessed(source.getJournalProcessed())
                .journalSucceeded(source.getJournalSucceeded())
                .journalFailed(source.getJournalFailed())
                .stageTotal(source.getStageTotal())
                .stageProcessed(source.getStageProcessed())
                .currentFile(source.getCurrentFile())
                .message(source.getMessage())
                .startedAt(source.getStartedAt())
                .finishedAt(source.getFinishedAt())
                .updatedAt(source.getUpdatedAt())
                .errors(source.getErrors() == null ? List.of() : new ArrayList<>(source.getErrors()))
                .build();
    }
}
