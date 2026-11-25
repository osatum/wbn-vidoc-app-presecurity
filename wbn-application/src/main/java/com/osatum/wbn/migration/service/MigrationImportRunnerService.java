package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.journal.event.JournalRecreateEvent;
import com.osatum.wbn.application.journal.service.DecisionAnalyticsService;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService.SurveyReportExportSummary;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import com.osatum.wbn.infrastructure.elasticsearch.repository.springdata.JournalDocumentRepository;
import com.osatum.wbn.infrastructure.time.JournalEventTaskCoordinator;
import com.osatum.wbn.migration.dto.MigrationImportStage;
import com.osatum.wbn.migration.dto.MigrationImportStatusDto;
import com.osatum.wbn.migration.service.AbstractCategory2024ImportService.Category2024Record;
import com.osatum.wbn.migration.service.Cooperation2024ImportService.Cooperation2024Record;
import com.osatum.wbn.migration.service.DatabasesIndexed2024ImportService.DatabasesIndexed2024Record;
import com.osatum.wbn.migration.service.Directors2024ImportService.Directors2024Record;
import com.osatum.wbn.migration.service.Editorial2024ImportService.Editorial2024Record;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.EditorialStaff2024Record;
import com.osatum.wbn.migration.service.InfoReview2024ImportService.InfoReview2024Record;
import com.osatum.wbn.migration.service.JournalKind2024ImportService.JournalKind2024Record;
import com.osatum.wbn.migration.service.Languages2024ImportService.Languages2024Record;
import com.osatum.wbn.migration.service.LinkToReview2024ImportService.LinkToReview2024Record;
import com.osatum.wbn.migration.service.MigrationJobCoordinator.Job;
import com.osatum.wbn.migration.service.Mnisw2024ImportService.Mnisw2024Record;
import com.osatum.wbn.migration.service.Publisher2024ImportService.Publisher2024Record;
import com.osatum.wbn.migration.service.Reviewers2024ImportService.Reviewers2024Record;
import com.osatum.wbn.migration.service.ScopusIndicatorImportService.ScopusIndicatorRecord;
import com.osatum.wbn.migration.service.Stability2024ImportService.Stability2024Record;
import com.osatum.wbn.migration.service.WosIndicatorImportService.WosIndicatorRecord;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Koordynuje pełne zasilenie WBN: usuwa dotychczasowe dane, importuje słowniki i JSON-y czasopism,
 * uzupełnia dane z plików dodatkowych oraz odbudowuje indeks. Nie służy do obsługi żądania wyszukiwania.
 * Poszczególne zadania mają własne transakcje; cały przebieg nie jest jedną atomową operacją.
 * Usuwa poprzednią analitykę przed źródłami, a nowe benchmarki przygotowuje po aktualizacji czasopism.
 * Publikacja generacji następuje po powodzeniu raportu, dzięki czemu gotowość nie wyprzedza końca importu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MigrationImportRunnerService {
    private static final int SURVEY_REPORT_YEAR = 2024;
    private static final int DICTIONARY_PARALLELISM = 4;
    private static final int JOURNAL_PARALLELISM = 4;
    private static final int NOT_INDEXED_PARALLELISM = 4;
    private static final int JOURNAL_UPDATE_PARALLELISM = 4;
    private static final int JOURNAL_UPDATE_BATCH_SIZE = 32;
    private static final int DATABASES_INDEXED_PARALLELISM = 6;
    private static final int MNISW_PARALLELISM = 4;
    private static final int CATEGORY_2024_PARALLELISM = 6;
    private static final int WOS_PARALLELISM = 6;
    private static final int SCOPUS_PARALLELISM = 6;
    private static final int LINK_TO_REVIEW_PARALLELISM = 4;
    private static final int JOURNAL_KIND_PARALLELISM = 4;
    private static final int INFO_REVIEW_PARALLELISM = 4;
    private static final int COOPERATION_PARALLELISM = 4;
    private static final int STABILITY_PARALLELISM = 4;
    private static final int LANGUAGES_PARALLELISM = 4;
    private static final int REVIEWERS_PARALLELISM = 4;
    private static final int DIRECTORS_PARALLELISM = 4;
    private static final int EDITORIAL_STAFF_PARALLELISM = 4;
    private static final int EDITORIAL_PARALLELISM = 4;
    private static final int PUBLISHER_PARALLELISM = 4;

    private final JournalMigrationImportService journalMigrationImportService;
    private final WosIndicatorImportService wosIndicatorImportService;
    private final ScopusIndicatorImportService scopusIndicatorImportService;
    private final NotIndexed2024ImportService notIndexedImportService;
    private final DatabasesIndexed2024ImportService databasesIndexed2024ImportService;
    private final Mnisw2024ImportService mnisw2024ImportService;
    private final LinkToReview2024ImportService linkToReview2024ImportService;
    private final JournalKind2024ImportService journalKind2024ImportService;
    private final InfoReview2024ImportService infoReview2024ImportService;
    private final Cooperation2024ImportService cooperation2024ImportService;
    private final Stability2024ImportService stability2024ImportService;
    private final Languages2024ImportService languages2024ImportService;
    private final Reviewers2024ImportService reviewers2024ImportService;
    private final Directors2024ImportService directors2024ImportService;
    private final EditorialStaff2024ImportService editorialStaff2024ImportService;
    private final Editorial2024ImportService editorial2024ImportService;
    private final Publisher2024ImportService publisher2024ImportService;
    private final WosCategory2024ImportService wosCategory2024ImportService;
    private final ScopusCategory2024ImportService scopusCategory2024ImportService;
    private final SurveyReportExportService surveyReportExportService;
    private final JournalDocumentRepository journalDocumentRepository;
    private final ApplicationEventPublisher publisher;
    private final MigrationImportStatusService statusService;
    private final MigrationJobCoordinator jobCoordinator;
    private final JournalEventTaskCoordinator journalEventTaskCoordinator;
    private final DecisionAnalyticsService decisionAnalytics;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "migration-import-runner");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${app.json-source}")
    private String jsonSource;

    @Value("${app.json-dictionary}")
    private String jsonDictionary;

    @Value("${app.wos-2022-path}")
    private String wos2022;

    @Value("${app.wos-2023-path}")
    private String wos2023;


    @Value("${app.scopus-2022-path}")
    private String scopus2022;

    @Value("${app.scopus-2023-path}")
    private String scopus2023;

    @Value("${app.not-indexed-2024}")
    private String notIndexed2024;

    @Value("${app.not-indexed-2025}")
    private String notIndexed2025;

    @Value("${app.database-indexed-2024}")
    private String databasesIndexed2024;

    @Value("${app.mnisw-2024}")
    private String mnisw2024;

    @Value("${app.wos-category-2024-path}")
    private String wosCategory2024;

    @Value("${app.scopus-category-2024-path}")
    private String scopusCategory2024;


    @Value("${app.link-to-review-2024}")
    private String linkToReview2024;

    @Value("${app.kind-2024}")
    private String kind2024;

    @Value("${app.cooperation-2024}")
    private String cooperation2024;

    @Value("${app.stability-2024}")
    private String stability2024;

    @Value("${app.languages-2024}")
    private String languages2024;

    @Value("${app.reviewers-2024}")
    private String reviewers2024;

    @Value("${app.directors-2024}")
    private String directors2024;

    @Value("${app.editorial-staff-2024}")
    private String editorialStaff2024;

    @Value("${app.editorial-2024}")
    private String editorial2024;

    @Value("${app.publisher-2024}")
    private String publisher2024;


    @Value("${app.info-review-2024}")
    private String infoReview2024;


    @Value("${app.outpux-questionaris-xls}")
    private String outputQuestionaries2024xls;

    @Value("${app.outpux-questionaris-xls-en}")
    private String outputQuestionaries2024xlsEn;

    @Value("${app.migration.journal-events.await-timeout:10m}")
    private Duration journalEventAwaitTimeout;

    /**
     * Uruchamia import w tle po uzyskaniu blokady koordynatora; zwrócony status nie oznacza zakończenia importu.
     * Gdy inne zadanie zajmuje koordynator, nie rozpoczyna kolejnego przebiegu.
     */
    public MigrationImportStatusDto startImport() {
        if (!jobCoordinator.tryStart(Job.IMPORT)) {
            return statusService.current();
        }

        try {
            statusService.started(0, 0);
            importExecutor.submit(() -> {
                try {
                    runImport();
                } finally {
                    jobCoordinator.finish(Job.IMPORT);
                }
            });
            return statusService.current();
        } catch (RuntimeException e) {
            jobCoordinator.finish(Job.IMPORT);
            statusService.failed("Import danych nie mogl zostac uruchomiony.", e);
            return statusService.current();
        }
    }

    /**
     * Odczytuje postęp istniejącego zadania bez ponownego odczytywania plików ani uruchamiania importu.
     */
    public MigrationImportStatusDto status() {
        return statusService.current();
    }

    @PreDestroy
    void shutdown() {
        importExecutor.shutdownNow();
    }

    /**
     * Zachowuje kolejność zależności: słowniki przed czasopismami, a uzupełnienia i indeksowanie po danych bazowych.
     * Błąd etapu przerywa dalsze kroki, ale nie cofa wcześniej zatwierdzonych transakcji.
     */
    private void runImport() {
        UUID analyticsRun = null;
        boolean analyticsReady = false;
        try {
            List<File> dictionaryFiles = listImportFiles(jsonDictionary);
            List<File> journalFiles = listImportFiles(jsonSource);
            Map<Long, ReasonNotIndexing> notIndexed2024Reasons = notIndexedImportService.readReasons(notIndexed2024);
            Map<Long, ReasonNotIndexing> notIndexed2025Reasons = notIndexedImportService.readReasons(notIndexed2025);
            List<Long> journalIds = journalMigrationImportService.findJournalIds();

            statusService.prepared(dictionaryFiles.size(), journalFiles.size());

            // Poprzednie wyniki i osie muszą zostać usunięte przed pierwszym usunięciem źródeł.
            statusService.stage(MigrationImportStage.DELETE_P2_ANALYTICS, stageMessage(MigrationImportStage.DELETE_P2_ANALYTICS), 1);
            analyticsRun = decisionAnalytics.begin();
            statusService.stageCompleted("Poprzednie benchmarki, wejścia kontrolne i osie P2 zostały usunięte.");
            journalEventTaskCoordinator.beginBatch(journalEventAwaitTimeout);

            boolean journalsDeleted = deleteJournals(journalIds);
            if (!journalsDeleted) {
                statusService.finished();
                return;
            }

            boolean dictionariesDeleted = deleteDictionaries();
            if (!dictionariesDeleted) {
                statusService.finished();
                return;
            }

            boolean dictionariesSucceeded = importFiles(
                    dictionaryFiles,
                    DICTIONARY_PARALLELISM,
                    MigrationImportStage.DICTIONARIES,
                    journalMigrationImportService::importDictionaryJson
            );
            if (!dictionariesSucceeded) {
                statusService.finished();
                return;
            }
            journalMigrationImportService.reloadDictionaryCaches();

            statusService.stage(MigrationImportStage.JOURNALS, "Przygotowanie importu czasopism.", journalFiles.size());
            recreateJournalIndex();

            boolean journalsSucceeded = importFiles(
                    journalFiles,
                    JOURNAL_PARALLELISM,
                    MigrationImportStage.JOURNALS,
                    journalMigrationImportService::importJournalJson
            );
            if (!journalsSucceeded) {
                statusService.finished();
                return;
            }

            if (!importNotIndexedReasons(notIndexed2024Reasons, 2024, MigrationImportStage.NOT_INDEXED_2024)) {
                statusService.finished();
                return;
            }

            if (!importNotIndexedReasons(notIndexed2025Reasons, 2025, MigrationImportStage.NOT_INDEXED_2025)) {
                statusService.finished();
                return;
            }

            boolean databasesIndexedSucceeded = importDatabasesIndexed2024();
            if (!databasesIndexedSucceeded) {
                statusService.finished();
                return;
            }

            boolean mnisw2024Succeeded = importMnisw2024();
            journalEventTaskCoordinator.awaitIdle(journalEventAwaitTimeout);
            if (!mnisw2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean wosCategories2024Succeeded = importCategories2024(
                    wosCategory2024,
                    MigrationImportStage.WOS_CATEGORIES_2024,
                    wosCategory2024ImportService,
                    "WOS_CATEGORIES_2024"
            );
            if (!wosCategories2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean wos2022Succeeded = importWosIndicators(
                    wos2022,
                    MigrationImportStage.WOS_2022,
                    wosIndicatorImportService::updatePrevious,
                    "WOS_2022"
            );
            if (!wos2022Succeeded) {
                statusService.finished();
                return;
            }

            boolean wos2023Succeeded = importWosIndicators(
                    wos2023,
                    MigrationImportStage.WOS_2023,
                    wosIndicatorImportService::updateCurrent,
                    "WOS_2023"
            );
            if (!wos2023Succeeded) {
                statusService.finished();
                return;
            }

            boolean scopusCategories2024Succeeded = importCategories2024(
                    scopusCategory2024,
                    MigrationImportStage.SCOPUS_CATEGORIES_2024,
                    scopusCategory2024ImportService,
                    "SCOPUS_CATEGORIES_2024"
            );
            if (!scopusCategories2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean scopus2022Succeeded = importScopusIndicators(
                    scopus2022,
                    MigrationImportStage.SCOPUS_2022,
                    scopusIndicatorImportService::updatePrevious,
                    "SCOPUS_2022"
            );
            if (!scopus2022Succeeded) {
                statusService.finished();
                return;
            }

            boolean scopus2023Succeeded = importScopusIndicators(
                    scopus2023,
                    MigrationImportStage.SCOPUS_2023,
                    scopusIndicatorImportService::updateCurrent,
                    "SCOPUS_2023"
            );
            if (!scopus2023Succeeded) {
                statusService.finished();
                return;
            }

            boolean linkToReview2024Succeeded = importLinkToReview2024();
            if (!linkToReview2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean kind2024Succeeded = importKind2024();
            if (!kind2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean infoReview2024Succeeded = importInfoReview2024();
            if (!infoReview2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean cooperation2024Succeeded = importCooperation2024();
            if (!cooperation2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean stability2024Succeeded = importStability2024();
            if (!stability2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean languages2024Succeeded = importLanguages2024();
            if (!languages2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean reviewers2024Succeeded = importReviewers2024();
            if (!reviewers2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean directors2024Succeeded = importDirectors2024();
            if (!directors2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean editorialStaff2024Succeeded = importEditorialStaff2024();
            if (!editorialStaff2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean editorial2024Succeeded = importEditorial2024();
            if (!editorial2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean publisher2024Succeeded = importPublisher2024();
            if (!publisher2024Succeeded) {
                statusService.finished();
                return;
            }

            boolean journalUpdatesSucceeded = updateJournalsFromCurrentQuestionnaire();
            if (!journalUpdatesSucceeded) {
                statusService.finished();
                return;
            }

            journalEventTaskCoordinator.awaitIdle(journalEventAwaitTimeout);
            statusService.stage(MigrationImportStage.BUILD_P2_ANALYTICS, stageMessage(MigrationImportStage.BUILD_P2_ANALYTICS));
            decisionAnalytics.prepare(analyticsRun, progress -> statusService.stageProgress(
                    progress.total(), progress.processed(), Integer.toString(progress.year()), progress.message()));
            statusService.stageCompleted("Benchmarki i osie P2 zostały zapisane; oczekiwanie na publikację generacji.");

            boolean questionnaireReportSucceeded = exportQuestionnaireReport2024();
            if (!questionnaireReportSucceeded) {
                statusService.finished();
                return;
            }
            decisionAnalytics.publish(analyticsRun);
            statusService.finished();
            analyticsReady = true;
        } catch (Exception | OutOfMemoryError e) {
            log.error("Error importing migration files", e);
            statusService.failed("Import danych nie powiodl sie.", e);
        } finally {
            if (analyticsRun != null && !analyticsReady) {
                try { decisionAnalytics.fail(analyticsRun); }
                catch (RuntimeException e) {
                    log.error("Cannot record failed P2 preparation; the committed BUILDING state remains unavailable", e);
                    statusService.failed("Nie mozna zapisac bledu przygotowania P2.", e);
                }
            }
        }
    }

    private List<File> listImportFiles(String sourcePath) {
        File directory = Path.of(sourcePath).toFile();
        File[] files = directory.listFiles(File::isFile);
        if (files == null) {
            throw new IllegalStateException("Cannot read import directory: " + sourcePath);
        }

        return Arrays.stream(files)
                .sorted(Comparator.comparing(File::getName))
                .toList();
    }

    private boolean importFiles(
            List<File> files,
            int parallelism,
            MigrationImportStage stage,
            MigrationFileImporter importer
    ) throws InterruptedException {
        statusService.stage(stage, stageMessage(stage), files.size());
        if (files.isEmpty()) {
            return true;
        }

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CompletionService<MigrationFileResult> completionService = new ExecutorCompletionService<>(executor);

        boolean success = true;
        try {
            Iterator<File> filesIterator = files.iterator();
            int inFlight = submitFiles(filesIterator, parallelism, completionService, stage, importer);
            while (inFlight > 0) {
                MigrationFileResult result = completionService.take().get();
                inFlight--;
                if (result.success()) {
                    markSucceeded(stage, result.fileName());
                } else {
                    success = false;
                    markFailed(stage, result.fileName(), result.error());
                }
                if (filesIterator.hasNext()) {
                    completionService.submit(importTask(stage, filesIterator.next(), importer));
                    inFlight++;
                }
            }
            return success;
        } catch (ExecutionException e) {
            log.error("Unexpected migration worker failure", e);
            statusService.failed("Import danych nie powiodl sie.", e.getCause() == null ? e : e.getCause());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<MigrationFileResult> importTask(MigrationImportStage stage, File file, MigrationFileImporter importer) {
        return () -> {
            String fileName = file.getName();
            statusService.fileStarted(stage, fileName);
            try {
                importer.importFile(file);
                return MigrationFileResult.success(fileName);
            } catch (Exception e) {
                log.warn("Error importing {} file {}", stage, fileName, e);
                return MigrationFileResult.failure(fileName, e);
            }
        };
    }

    private boolean deleteDictionaries() {
        statusService.stage(MigrationImportStage.DELETE_DICTIONARIES, stageMessage(MigrationImportStage.DELETE_DICTIONARIES), 1);
        try {
            journalMigrationImportService.deleteAllDictionaries();
            statusService.stageCompleted("Slowniki zostaly usuniete.");
            return true;
        } catch (Exception e) {
            log.warn("Error deleting dictionaries", e);
            statusService.stageItemFailed("DICTIONARY_DELETE", "slowniki", e);
            return false;
        }
    }

    private boolean deleteJournals(List<Long> journalIds) throws InterruptedException {
        return processItems(
                journalIds,
                JOURNAL_PARALLELISM,
                MigrationImportStage.DELETE_JOURNALS,
                stageMessage(MigrationImportStage.DELETE_JOURNALS),
                journalId -> "journal-" + journalId,
                journalMigrationImportService::deleteJournalById,
                "JOURNAL_DELETE"
        );
    }

    private boolean importWosIndicators(
            String sourcePath,
            MigrationImportStage stage,
            MigrationItemProcessor<WosIndicatorRecord> processor,
            String errorType
    ) throws Exception {
        List<WosIndicatorRecord> records = wosIndicatorImportService.readRecords(sourcePath);
        return processItems(
                records,
                WOS_PARALLELISM,
                stage,
                stageMessage(stage),
                WosIndicatorRecord::itemName,
                processor,
                errorType
        );
    }

    private boolean importNotIndexedReasons(
            Map<Long, ReasonNotIndexing> reasons,
            int year,
            MigrationImportStage stage
    ) throws InterruptedException {
        return processItems(
                List.copyOf(reasons.entrySet()),
                NOT_INDEXED_PARALLELISM,
                stage,
                stageMessage(stage),
                entry -> "journal-" + entry.getKey(),
                entry -> journalMigrationImportService.updateReasonNotIndexing(entry.getKey(), year, entry.getValue()),
                stage.name()
        );
    }

    private boolean importDatabasesIndexed2024() throws Exception {
        List<DatabasesIndexed2024Record> records = databasesIndexed2024ImportService.readRecords(databasesIndexed2024);
        return processItems(
                records,
                DATABASES_INDEXED_PARALLELISM,
                MigrationImportStage.DATABASES_INDEXED_2024,
                stageMessage(MigrationImportStage.DATABASES_INDEXED_2024),
                DatabasesIndexed2024Record::itemName,
                databasesIndexed2024ImportService::updateRepositories,
                "DATABASES_INDEXED_2024"
        );
    }

    private boolean importScopusIndicators(
            String sourcePath,
            MigrationImportStage stage,
            MigrationItemProcessor<ScopusIndicatorRecord> processor,
            String errorType
    ) throws Exception {
        List<ScopusIndicatorRecord> records = scopusIndicatorImportService.readRecords(sourcePath);
        return processItems(
                records,
                SCOPUS_PARALLELISM,
                stage,
                stageMessage(stage),
                ScopusIndicatorRecord::itemName,
                processor,
                errorType
        );
    }

    private boolean importCategories2024(
            String sourcePath,
            MigrationImportStage stage,
            AbstractCategory2024ImportService<?> importService,
            String errorType
    ) throws Exception {
        List<Category2024Record> records = importService.readRecords(sourcePath);
        return processItems(
                records,
                CATEGORY_2024_PARALLELISM,
                stage,
                stageMessage(stage),
                Category2024Record::itemName,
                importService::updateCategories,
                errorType
        );
    }

    private boolean importMnisw2024() throws Exception {
        List<Mnisw2024Record> records = mnisw2024ImportService.readRecords(mnisw2024);
        return processItems(
                records,
                MNISW_PARALLELISM,
                MigrationImportStage.MNISW_2024,
                stageMessage(MigrationImportStage.MNISW_2024),
                Mnisw2024Record::itemName,
                mnisw2024ImportService::addRating,
                "MNISW_2024"
        );
    }

    private boolean importLinkToReview2024() throws Exception {
        List<LinkToReview2024Record> records = linkToReview2024ImportService.readRecords(linkToReview2024);
        return processItems(
                records,
                LINK_TO_REVIEW_PARALLELISM,
                MigrationImportStage.LINK_TO_REVIEW_2024,
                stageMessage(MigrationImportStage.LINK_TO_REVIEW_2024),
                LinkToReview2024Record::itemName,
                linkToReview2024ImportService::updateLinkToReviewProcedure,
                "LINK_TO_REVIEW_2024"
        );
    }

    private boolean importKind2024() throws Exception {
        List<JournalKind2024Record> records = journalKind2024ImportService.readRecords(kind2024);
        return processItems(
                records,
                JOURNAL_KIND_PARALLELISM,
                MigrationImportStage.KIND_2024,
                stageMessage(MigrationImportStage.KIND_2024),
                JournalKind2024Record::itemName,
                journalKind2024ImportService::updateKind,
                "KIND_2024"
        );
    }

    private boolean importInfoReview2024() throws Exception {
        List<InfoReview2024Record> records = infoReview2024ImportService.readRecords(infoReview2024);
        return processItems(
                records,
                INFO_REVIEW_PARALLELISM,
                MigrationImportStage.INFO_REVIEW_2024,
                stageMessage(MigrationImportStage.INFO_REVIEW_2024),
                InfoReview2024Record::itemName,
                infoReview2024ImportService::updateDoubleBlindReview,
                "INFO_REVIEW_2024"
        );
    }

    private boolean importCooperation2024() throws Exception {
        statusService.stage(MigrationImportStage.COOPERATION_2024, stageMessage(MigrationImportStage.COOPERATION_2024));
        List<Cooperation2024Record> records = cooperation2024ImportService.readRecords(cooperation2024);
        return processItems(
                records,
                COOPERATION_PARALLELISM,
                MigrationImportStage.COOPERATION_2024,
                stageMessage(MigrationImportStage.COOPERATION_2024),
                Cooperation2024Record::itemName,
                cooperation2024ImportService::updateCooperation,
                "COOPERATION_2024"
        );
    }

    private boolean importStability2024() throws Exception {
        statusService.stage(MigrationImportStage.STABILITY_2024, stageMessage(MigrationImportStage.STABILITY_2024));
        List<Stability2024Record> records = stability2024ImportService.readRecords(stability2024);
        return processItems(
                records,
                STABILITY_PARALLELISM,
                MigrationImportStage.STABILITY_2024,
                stageMessage(MigrationImportStage.STABILITY_2024),
                Stability2024Record::itemName,
                stability2024ImportService::updateStability,
                "STABILITY_2024"
        );
    }

    private boolean importLanguages2024() throws Exception {
        statusService.stage(MigrationImportStage.LANGUAGES_2024, stageMessage(MigrationImportStage.LANGUAGES_2024));
        List<Languages2024Record> records = languages2024ImportService.readRecords(languages2024);
        return processItems(
                records,
                LANGUAGES_PARALLELISM,
                MigrationImportStage.LANGUAGES_2024,
                stageMessage(MigrationImportStage.LANGUAGES_2024),
                Languages2024Record::itemName,
                languages2024ImportService::updateLanguages,
                "LANGUAGES_2024"
        );
    }

    private boolean importReviewers2024() throws Exception {
        statusService.stage(MigrationImportStage.REVIEWERS_2024, stageMessage(MigrationImportStage.REVIEWERS_2024));
        List<Reviewers2024Record> records = reviewers2024ImportService.readRecords(reviewers2024);
        return processItems(
                records,
                REVIEWERS_PARALLELISM,
                MigrationImportStage.REVIEWERS_2024,
                stageMessage(MigrationImportStage.REVIEWERS_2024),
                Reviewers2024Record::itemName,
                reviewers2024ImportService::updateReviewers,
                "REVIEWERS_2024"
        );
    }

    private boolean importDirectors2024() throws Exception {
        statusService.stage(MigrationImportStage.DIRECTORS_2024, stageMessage(MigrationImportStage.DIRECTORS_2024));
        List<Directors2024Record> records = directors2024ImportService.readRecords(directors2024);
        return processItems(
                records,
                DIRECTORS_PARALLELISM,
                MigrationImportStage.DIRECTORS_2024,
                stageMessage(MigrationImportStage.DIRECTORS_2024),
                Directors2024Record::itemName,
                directors2024ImportService::updateDirectors,
                "DIRECTORS_2024"
        );
    }

    private boolean importEditorialStaff2024() throws Exception {
        statusService.stage(MigrationImportStage.EDITORIAL_STAFF_2024, stageMessage(MigrationImportStage.EDITORIAL_STAFF_2024));
        List<EditorialStaff2024Record> records = editorialStaff2024ImportService.readRecords(editorialStaff2024);
        return processItems(
                records,
                EDITORIAL_STAFF_PARALLELISM,
                MigrationImportStage.EDITORIAL_STAFF_2024,
                stageMessage(MigrationImportStage.EDITORIAL_STAFF_2024),
                EditorialStaff2024Record::itemName,
                editorialStaff2024ImportService::updateEditorialStaff,
                "EDITORIAL_STAFF_2024"
        );
    }

    private boolean importEditorial2024() throws Exception {
        statusService.stage(MigrationImportStage.EDITORIAL_2024, stageMessage(MigrationImportStage.EDITORIAL_2024));
        List<Editorial2024Record> records = editorial2024ImportService.readRecords(editorial2024);
        return processItems(
                records,
                EDITORIAL_PARALLELISM,
                MigrationImportStage.EDITORIAL_2024,
                stageMessage(MigrationImportStage.EDITORIAL_2024),
                Editorial2024Record::itemName,
                editorial2024ImportService::updateEditorial,
                "EDITORIAL_2024"
        );
    }

    private boolean importPublisher2024() throws Exception {
        statusService.stage(MigrationImportStage.PUBLISHER_2024, stageMessage(MigrationImportStage.PUBLISHER_2024));
        List<Publisher2024Record> records = publisher2024ImportService.readRecords(publisher2024);
        return processItems(
                records,
                PUBLISHER_PARALLELISM,
                MigrationImportStage.PUBLISHER_2024,
                stageMessage(MigrationImportStage.PUBLISHER_2024),
                Publisher2024Record::itemName,
                publisher2024ImportService::updatePublisher,
                "PUBLISHER_2024"
        );
    }

    private boolean updateJournalsFromCurrentQuestionnaire() throws InterruptedException {
        long journalCount = journalMigrationImportService.countJournals();
        statusService.stage(
                MigrationImportStage.UPDATE_JOURNALS_FROM_CURRENT_QUESTIONNAIRE,
                stageMessage(MigrationImportStage.UPDATE_JOURNALS_FROM_CURRENT_QUESTIONNAIRE),
                (int) Math.min(journalCount, Integer.MAX_VALUE)
        );

        boolean success = true;
        long lastJournalId = Long.MIN_VALUE;
        while (true) {
            List<Long> journalIds = journalMigrationImportService.findJournalIdsAfter(
                    lastJournalId,
                    JOURNAL_UPDATE_BATCH_SIZE
            );
            if (journalIds.isEmpty()) {
                return success;
            }

            boolean batchSucceeded = processItemsInCurrentStage(
                    journalIds,
                    JOURNAL_UPDATE_PARALLELISM,
                    journalId -> "journal-" + journalId,
                    journalMigrationImportService::updateJournalFromCurrentQuestionnaire,
                    "UPDATE_JOURNALS_FROM_CURRENT_QUESTIONNAIRE"
            );
            success = success && batchSucceeded;
            journalEventTaskCoordinator.awaitIdle(journalEventAwaitTimeout);

            lastJournalId = journalIds.getLast();
            if (journalIds.size() < JOURNAL_UPDATE_BATCH_SIZE) {
                return success;
            }
        }
    }

    private boolean exportQuestionnaireReport2024() {
        List<Long> journalIds = journalMigrationImportService.findJournalIds();
        statusService.stage(
                MigrationImportStage.QUESTIONNAIRE_REPORT_2024,
                stageMessage(MigrationImportStage.QUESTIONNAIRE_REPORT_2024),
                journalIds.size()
        );
        try {
            SurveyReportExportSummary summary = surveyReportExportService.export(
                    journalIds,
                    SURVEY_REPORT_YEAR,
                    outputQuestionaries2024xls,
                    outputQuestionaries2024xlsEn
            );
            statusService.stageCompleted(
                    "Raport ankiet 2024 zostal zapisany. Uwzgledniono czasopism: " + summary.includedJournals() + "."
            );
            return true;
        } catch (RuntimeException | OutOfMemoryError e) {
            log.warn("Error generating questionnaire report for 2024", e);
            statusService.stageItemFailed(
                    "QUESTIONNAIRE_REPORT_2024",
                    outputQuestionaries2024xls,
                    e
            );
            return false;
        }
    }

    private int submitFiles(
            Iterator<File> files,
            int parallelism,
            CompletionService<MigrationFileResult> completionService,
            MigrationImportStage stage,
            MigrationFileImporter importer
    ) {
        int submitted = 0;
        while (files.hasNext() && submitted < parallelism) {
            completionService.submit(importTask(stage, files.next(), importer));
            submitted++;
        }
        return submitted;
    }

    private <T> boolean processItems(
            List<T> items,
            int parallelism,
            MigrationImportStage stage,
            String message,
            Function<T, String> itemName,
            MigrationItemProcessor<T> processor,
            String errorType
    ) throws InterruptedException {
        statusService.stage(stage, message, items.size());
        return processItemsInCurrentStage(items, parallelism, itemName, processor, errorType);
    }

    private <T> boolean processItemsInCurrentStage(
            List<T> items,
            int parallelism,
            Function<T, String> itemName,
            MigrationItemProcessor<T> processor,
            String errorType
    ) throws InterruptedException {
        if (items.isEmpty()) {
            return true;
        }

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CompletionService<MigrationItemResult> completionService = new ExecutorCompletionService<>(executor);

        boolean success = true;
        try {
            Iterator<T> itemsIterator = items.iterator();
            int inFlight = submitItems(itemsIterator, parallelism, completionService, itemName, processor);
            while (inFlight > 0) {
                MigrationItemResult result = completionService.take().get();
                inFlight--;
                if (result.success()) {
                    statusService.stageItemSucceeded(result.itemName());
                } else {
                    success = false;
                    statusService.stageItemFailed(errorType, result.itemName(), result.error());
                }
                if (itemsIterator.hasNext()) {
                    completionService.submit(processItemTask(itemsIterator.next(), itemName, processor));
                    inFlight++;
                }
            }
            return success;
        } catch (ExecutionException e) {
            log.error("Unexpected migration worker failure", e);
            statusService.failed("Import danych nie powiodl sie.", e.getCause() == null ? e : e.getCause());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> int submitItems(
            Iterator<T> items,
            int parallelism,
            CompletionService<MigrationItemResult> completionService,
            Function<T, String> itemName,
            MigrationItemProcessor<T> processor
    ) {
        int submitted = 0;
        while (items.hasNext() && submitted < parallelism) {
            completionService.submit(processItemTask(items.next(), itemName, processor));
            submitted++;
        }
        return submitted;
    }

    private <T> Callable<MigrationItemResult> processItemTask(
            T item,
            Function<T, String> itemName,
            MigrationItemProcessor<T> processor
    ) {
        return () -> {
            String name = itemName.apply(item);
            try {
                processor.process(item);
                return MigrationItemResult.success(name);
            } catch (Exception e) {
                log.warn("Error processing migration item {}", name, e);
                return MigrationItemResult.failure(name, e);
            }
        };
    }

    private void recreateJournalIndex() {
        journalDocumentRepository.deleteAll();
        publisher.publishEvent(new JournalRecreateEvent());
    }

    private String stageMessage(MigrationImportStage stage) {
        return switch (stage) {
            case DELETE_P2_ANALYTICS -> "Usuwanie poprzednich benchmarków i osi P2.";
            case DELETE_DICTIONARIES -> "Usuwanie slownikow.";
            case DELETE_JOURNALS -> "Usuwanie czasopism.";
            case DICTIONARIES -> "Zaczytywanie slownikow.";
            case JOURNALS -> "Import czasopism w toku.";
            case NOT_INDEXED_2024 -> "Aktualizacja przyczyn nieindeksowania dla ankiet 2024.";
            case NOT_INDEXED_2025 -> "Aktualizacja przyczyn nieindeksowania dla ankiet 2025.";
            case DATABASES_INDEXED_2024 -> "Aktualizacja baz indeksacyjnych dla ankiet 2024.";
            case MNISW_2024 -> "Import punktacji MNISW 2024.";
            case WOS_CATEGORIES_2024 -> "Aktualizacja kategorii WoS dla ankiet 2024.";
            case INDEX -> "Odtwarzanie indeksu czasopism.";
            case WOS_2022 -> "Aktualizacja wskaznikow WoS 2022 dla ankiet 2024.";
            case WOS_2023 -> "Aktualizacja wskaznikow WoS 2023 dla ankiet 2024.";
            case SCOPUS_CATEGORIES_2024 -> "Aktualizacja kategorii Scopus dla ankiet 2024.";
            case SCOPUS_2022 -> "Aktualizacja wskaznikow Scopus 2022 dla ankiet 2024.";
            case SCOPUS_2023 -> "Aktualizacja wskaznikow Scopus 2023 dla ankiet 2024.";
            case LINK_TO_REVIEW_2024 -> "Aktualizacja linkow do procedur recenzowania dla ankiet 2024.";
            case KIND_2024 -> "Aktualizacja rodzaju czasopism dla ankiet 2024.";
            case INFO_REVIEW_2024 -> "Aktualizacja procedur recenzowania double-blind dla ankiet 2024.";
            case COOPERATION_2024 -> "Aktualizacja metryk kooperacji dla ankiet 2024.";
            case STABILITY_2024 -> "Aktualizacja stabilnosci wydawniczej dla ankiet 2024.";
            case LANGUAGES_2024 -> "Aktualizacja jezykow publikacji dla ankiet 2024.";
            case REVIEWERS_2024 -> "Aktualizacja recenzentow dla ankiet 2024.";
            case DIRECTORS_2024 -> "Aktualizacja czlonkow rady naukowej dla ankiet 2024.";
            case EDITORIAL_STAFF_2024 -> "Aktualizacja skladu redakcji dla ankiet 2024.";
            case EDITORIAL_2024 -> "Aktualizacja danych instytucji redakcji dla ankiet 2024.";
            case PUBLISHER_2024 -> "Aktualizacja danych wydawcow dla ankiet 2024.";
            case UPDATE_JOURNALS_FROM_CURRENT_QUESTIONNAIRE -> "Aktualizacja czasopism z aktualnych ankiet.";
            case QUESTIONNAIRE_REPORT_2024 -> "Generowanie raportow ankiet 2024.";
            case BUILD_P2_ANALYTICS -> "Przygotowanie i zapis benchmarków oraz osi P2 dla skonfigurowanych roczników.";
            case PREPARING -> "Przygotowanie importu.";
            case NONE -> "Import nie zostal uruchomiony.";
        };
    }

    private void markSucceeded(MigrationImportStage stage, String fileName) {
        if (stage == MigrationImportStage.DICTIONARIES) {
            statusService.dictionarySucceeded(fileName);
            return;
        }
        statusService.journalSucceeded(fileName);
    }

    private void markFailed(MigrationImportStage stage, String fileName, Throwable error) {
        if (stage == MigrationImportStage.DICTIONARIES) {
            statusService.dictionaryFailed(fileName, error);
            return;
        }
        statusService.journalFailed(fileName, error);
    }

    @FunctionalInterface
    private interface MigrationFileImporter {
        void importFile(File file) throws Exception;
    }

    @FunctionalInterface
    private interface MigrationItemProcessor<T> {
        void process(T item) throws Exception;
    }

    private record MigrationFileResult(String fileName, boolean success, Throwable error) {
        static MigrationFileResult success(String fileName) {
            return new MigrationFileResult(fileName, true, null);
        }

        static MigrationFileResult failure(String fileName, Throwable error) {
            return new MigrationFileResult(fileName, false, error);
        }
    }

    private record MigrationItemResult(String itemName, boolean success, Throwable error) {
        static MigrationItemResult success(String itemName) {
            return new MigrationItemResult(itemName, true, null);
        }

        static MigrationItemResult failure(String itemName, Throwable error) {
            return new MigrationItemResult(itemName, false, error);
        }
    }
}
