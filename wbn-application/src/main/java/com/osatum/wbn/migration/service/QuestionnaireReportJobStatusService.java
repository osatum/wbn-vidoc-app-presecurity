package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService.SurveyReportExportSummary;
import com.osatum.wbn.migration.dto.MigrationImportErrorDto;
import com.osatum.wbn.migration.dto.MigrationImportStage;
import com.osatum.wbn.migration.dto.MigrationImportState;
import com.osatum.wbn.migration.dto.MigrationImportStatusDto;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionnaireReportJobStatusService {
    private MigrationImportStatusDto status = idleStatus();

    public synchronized MigrationImportStatusDto current() {
        return copy(status);
    }

    public synchronized void started() {
        Instant now = Instant.now();
        status = MigrationImportStatusDto.builder()
                .state(MigrationImportState.RUNNING)
                .stage(MigrationImportStage.QUESTIONNAIRE_REPORT_2024)
                .message("Przygotowanie raportow ankiet 2024.")
                .startedAt(now)
                .updatedAt(now)
                .errors(List.of())
                .build();
    }

    public synchronized void prepared(int journalTotal) {
        status.setStageTotal(Math.max(journalTotal, 0));
        status.setMessage("Generowanie raportow ankiet 2024.");
        touch();
    }

    public synchronized void completed(SurveyReportExportSummary summary) {
        status.setState(MigrationImportState.COMPLETED);
        status.setStageTotal(summary.processedJournals());
        status.setStageProcessed(summary.processedJournals());
        status.setMessage(
                "Raporty ankiet 2024 zostaly zapisane. Uwzgledniono czasopism: "
                        + summary.includedJournals() + "."
        );
        status.setFinishedAt(Instant.now());
        touch();
    }

    public synchronized void failed(String outputPath, Throwable error) {
        status.setState(MigrationImportState.FAILED);
        status.setMessage("Generowanie raportow ankiet 2024 nie powiodlo sie.");
        status.setFinishedAt(Instant.now());
        status.setErrors(List.of(error("QUESTIONNAIRE_REPORT_2024", outputPath, errorMessage(error))));
        touch();
    }

    public synchronized void rejected() {
        Instant now = Instant.now();
        status = MigrationImportStatusDto.builder()
                .state(MigrationImportState.FAILED)
                .stage(MigrationImportStage.QUESTIONNAIRE_REPORT_2024)
                .message("Nie mozna uruchomic raportu, poniewaz trwa inny job systemowy.")
                .finishedAt(now)
                .updatedAt(now)
                .errors(List.of(error(
                        "QUESTIONNAIRE_REPORT_2024",
                        null,
                        "Inny job systemowy jest aktualnie wykonywany."
                )))
                .build();
    }

    private static MigrationImportStatusDto idleStatus() {
        Instant now = Instant.now();
        return MigrationImportStatusDto.builder()
                .state(MigrationImportState.IDLE)
                .stage(MigrationImportStage.QUESTIONNAIRE_REPORT_2024)
                .message("Raport ankiet 2024 nie zostal uruchomiony.")
                .updatedAt(now)
                .errors(List.of())
                .build();
    }

    private MigrationImportErrorDto error(String type, String fileName, String message) {
        return MigrationImportErrorDto.builder()
                .type(type)
                .fileName(fileName)
                .message(message)
                .build();
    }

    private String errorMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "Nieznany blad generowania raportu.";
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
