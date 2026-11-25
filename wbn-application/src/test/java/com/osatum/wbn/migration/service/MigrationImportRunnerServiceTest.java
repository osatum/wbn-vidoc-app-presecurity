package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService.SurveyReportExportSummary;
import com.osatum.wbn.domain.model.journal.JournalKind;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import com.osatum.wbn.infrastructure.elasticsearch.repository.springdata.JournalDocumentRepository;
import com.osatum.wbn.infrastructure.time.JournalEventTaskCoordinator;
import com.osatum.wbn.migration.dto.MigrationImportStage;
import com.osatum.wbn.migration.dto.MigrationImportState;
import com.osatum.wbn.migration.dto.MigrationImportStatusDto;
import com.osatum.wbn.migration.service.AbstractCategory2024ImportService.Category2024Item;
import com.osatum.wbn.migration.service.AbstractCategory2024ImportService.Category2024Record;
import com.osatum.wbn.migration.service.Cooperation2024ImportService.Cooperation2024Record;
import com.osatum.wbn.migration.service.Cooperation2024ImportService.MetricValues;
import com.osatum.wbn.migration.service.Directors2024ImportService.DirectorValues;
import com.osatum.wbn.migration.service.Directors2024ImportService.Directors2024Record;
import com.osatum.wbn.migration.service.Editorial2024ImportService.Editorial2024Record;
import com.osatum.wbn.migration.service.Publisher2024ImportService.Publisher2024Record;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.EditorValues;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.EditorialStaff2024Record;
import com.osatum.wbn.migration.service.Languages2024ImportService.ArticleLanguageValues;
import com.osatum.wbn.migration.service.Languages2024ImportService.Languages2024Record;
import com.osatum.wbn.migration.service.Reviewers2024ImportService.ReviewerValues;
import com.osatum.wbn.migration.service.Reviewers2024ImportService.Reviewers2024Record;
import com.osatum.wbn.migration.service.Stability2024ImportService.ArticlePublishedValues;
import com.osatum.wbn.migration.service.Stability2024ImportService.Stability2024Record;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MigrationImportRunnerServiceTest {

    @TempDir
    private Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {
            "SUCCESS", "BUILD_FAILURE", "BEGIN_FAILURE", "NOT_INDEXED_2024_FAILURE", "NOT_INDEXED_2025_FAILURE",
            "ASYNC_FAILURE", "REPORT_FAILURE", "PUBLISH_FAILURE"
    })
    void startImportProcessesFilesWithConfiguredParallelism(String scenario) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        Path wos2022Dir = Files.createDirectory(tempDir.resolve("wos2022"));
        Path wos2023Dir = Files.createDirectory(tempDir.resolve("wos2023"));
        Path scopus2022Dir = Files.createDirectory(tempDir.resolve("scopus2022"));
        Path scopus2023Dir = Files.createDirectory(tempDir.resolve("scopus2023"));
        Path notIndexed2024File = tempDir.resolve("not-indexed-2024.xlsx");
        Path notIndexed2025File = tempDir.resolve("not-indexed-2025.xlsx");
        Path databasesIndexedFile = tempDir.resolve("databases-indexed-2024.xlsx");
        Path mnisw2024File = tempDir.resolve("mnisw-2024.xlsx");
        Path linkToReview2024File = tempDir.resolve("link-to-review-2024.xlsx");
        Path kind2024File = tempDir.resolve("kind-2024.xlsx");
        Path infoReview2024File = tempDir.resolve("info-review-2024.xlsx");
        Path cooperation2024File = tempDir.resolve("cooperation-2024.xlsx");
        Path stability2024File = tempDir.resolve("stability-2024.xlsx");
        Path languages2024File = tempDir.resolve("languages-2024.xlsx");
        Path reviewers2024File = tempDir.resolve("reviewers-2024.xlsx");
        Path directors2024File = tempDir.resolve("directors-2024.xlsx");
        Path editorialStaff2024File = tempDir.resolve("editorial-staff-2024.xlsx");
        Path editorial2024File = tempDir.resolve("editorial-2024.xlsx");
        Path publisher2024File = tempDir.resolve("publisher-2024.xlsx");
        Path wosCategories2024File = tempDir.resolve("wos-categories-2024.xlsx");
        Path scopusCategories2024File = tempDir.resolve("scopus-categories-2024.xlsx");
        Path questionnaireReportPl = tempDir.resolve("questionnaires-2024-pl.xlsx");
        Path questionnaireReportEn = tempDir.resolve("questionnaires-2024-en.xlsx");
        createFiles(dictionaryDir, 4);
        createFiles(journalDir, 6);
        Map<Long, ReasonNotIndexing> notIndexed2024Reasons = Map.of(1L, ReasonNotIndexing.dec06);
        Map<Long, ReasonNotIndexing> notIndexed2025Reasons = new LinkedHashMap<>();
        notIndexed2025Reasons.put(1L, ReasonNotIndexing.dec07);
        notIndexed2025Reasons.put(2L, null);

        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        WosIndicatorImportService wosIndicatorImportService = mock(WosIndicatorImportService.class);
        ScopusIndicatorImportService scopusIndicatorImportService = mock(ScopusIndicatorImportService.class);
        NotIndexed2024ImportService notIndexed2024ImportService = mock(NotIndexed2024ImportService.class);
        DatabasesIndexed2024ImportService databasesIndexed2024ImportService = mock(DatabasesIndexed2024ImportService.class);
        Mnisw2024ImportService mnisw2024ImportService = mock(Mnisw2024ImportService.class);
        LinkToReview2024ImportService linkToReview2024ImportService = mock(LinkToReview2024ImportService.class);
        JournalKind2024ImportService journalKind2024ImportService = mock(JournalKind2024ImportService.class);
        InfoReview2024ImportService infoReview2024ImportService = mock(InfoReview2024ImportService.class);
        Cooperation2024ImportService cooperation2024ImportService = mock(Cooperation2024ImportService.class);
        Stability2024ImportService stability2024ImportService = mock(Stability2024ImportService.class);
        Languages2024ImportService languages2024ImportService = mock(Languages2024ImportService.class);
        Reviewers2024ImportService reviewers2024ImportService = mock(Reviewers2024ImportService.class);
        Directors2024ImportService directors2024ImportService = mock(Directors2024ImportService.class);
        EditorialStaff2024ImportService editorialStaff2024ImportService = mock(EditorialStaff2024ImportService.class);
        Editorial2024ImportService editorial2024ImportService = mock(Editorial2024ImportService.class);
        Publisher2024ImportService publisher2024ImportService = mock(Publisher2024ImportService.class);
        WosCategory2024ImportService wosCategory2024ImportService = mock(WosCategory2024ImportService.class);
        ScopusCategory2024ImportService scopusCategory2024ImportService = mock(ScopusCategory2024ImportService.class);
        SurveyReportExportService surveyReportExportService = mock(SurveyReportExportService.class);
        JournalDocumentRepository journalDocumentRepository = mock(JournalDocumentRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        JournalEventTaskCoordinator journalEventTaskCoordinator = mock(JournalEventTaskCoordinator.class);

        AtomicInteger activeDictionaries = new AtomicInteger();
        AtomicInteger maxActiveDictionaries = new AtomicInteger();
        AtomicInteger activeJournalDeletes = new AtomicInteger();
        AtomicInteger maxActiveJournalDeletes = new AtomicInteger();
        AtomicInteger activeJournals = new AtomicInteger();
        AtomicInteger maxActiveJournals = new AtomicInteger();
        AtomicInteger activeJournalUpdates = new AtomicInteger();
        AtomicInteger maxActiveJournalUpdates = new AtomicInteger();
        AtomicInteger completedJournalUpdates = new AtomicInteger();
        AtomicInteger activeWosPrevious = new AtomicInteger();
        AtomicInteger maxActiveWosPrevious = new AtomicInteger();
        AtomicInteger activeDatabasesIndexed = new AtomicInteger();
        AtomicInteger maxActiveDatabasesIndexed = new AtomicInteger();
        AtomicInteger activeMnisw2024 = new AtomicInteger();
        AtomicInteger maxActiveMnisw2024 = new AtomicInteger();
        AtomicInteger activeLinkToReview2024 = new AtomicInteger();
        AtomicInteger maxActiveLinkToReview2024 = new AtomicInteger();
        AtomicInteger activeJournalKind2024 = new AtomicInteger();
        AtomicInteger maxActiveJournalKind2024 = new AtomicInteger();
        AtomicInteger activeInfoReview2024 = new AtomicInteger();
        AtomicInteger maxActiveInfoReview2024 = new AtomicInteger();
        AtomicInteger completedInfoReview2024 = new AtomicInteger();
        AtomicInteger activeCooperation2024 = new AtomicInteger();
        AtomicInteger maxActiveCooperation2024 = new AtomicInteger();
        AtomicInteger completedCooperation2024 = new AtomicInteger();
        AtomicInteger activeStability2024 = new AtomicInteger();
        AtomicInteger maxActiveStability2024 = new AtomicInteger();
        AtomicInteger completedStability2024 = new AtomicInteger();
        AtomicInteger activeLanguages2024 = new AtomicInteger();
        AtomicInteger maxActiveLanguages2024 = new AtomicInteger();
        AtomicInteger completedLanguages2024 = new AtomicInteger();
        AtomicInteger activeReviewers2024 = new AtomicInteger();
        AtomicInteger maxActiveReviewers2024 = new AtomicInteger();
        AtomicInteger completedReviewers2024 = new AtomicInteger();
        AtomicInteger activeDirectors2024 = new AtomicInteger();
        AtomicInteger maxActiveDirectors2024 = new AtomicInteger();
        AtomicInteger completedDirectors2024 = new AtomicInteger();
        AtomicInteger activeEditorialStaff2024 = new AtomicInteger();
        AtomicInteger maxActiveEditorialStaff2024 = new AtomicInteger();
        AtomicInteger completedEditorialStaff2024 = new AtomicInteger();
        AtomicInteger activeEditorial2024 = new AtomicInteger();
        AtomicInteger maxActiveEditorial2024 = new AtomicInteger();
        AtomicInteger completedEditorial2024 = new AtomicInteger();
        AtomicInteger activePublisher2024 = new AtomicInteger();
        AtomicInteger maxActivePublisher2024 = new AtomicInteger();
        AtomicInteger completedPublisher2024 = new AtomicInteger();
        AtomicInteger activeWosCategories2024 = new AtomicInteger();
        AtomicInteger maxActiveWosCategories2024 = new AtomicInteger();
        AtomicInteger activeScopusCategories2024 = new AtomicInteger();
        AtomicInteger maxActiveScopusCategories2024 = new AtomicInteger();
        AtomicInteger activeWosCurrent = new AtomicInteger();
        AtomicInteger maxActiveWosCurrent = new AtomicInteger();
        AtomicInteger activeScopusPrevious = new AtomicInteger();
        AtomicInteger maxActiveScopusPrevious = new AtomicInteger();
        AtomicInteger activeScopusCurrent = new AtomicInteger();
        AtomicInteger maxActiveScopusCurrent = new AtomicInteger();
        AtomicInteger completedScopusCurrent = new AtomicInteger();
        AtomicInteger completedLinkToReview2024 = new AtomicInteger();
        AtomicInteger completedJournalKind2024 = new AtomicInteger();
        CountDownLatch infoReviewWorkersStarted = new CountDownLatch(4);
        CountDownLatch cooperationWorkersStarted = new CountDownLatch(4);
        CountDownLatch stabilityWorkersStarted = new CountDownLatch(4);
        CountDownLatch languageWorkersStarted = new CountDownLatch(4);
        CountDownLatch reviewerWorkersStarted = new CountDownLatch(4);
        CountDownLatch directorWorkersStarted = new CountDownLatch(4);
        CountDownLatch editorialStaffWorkersStarted = new CountDownLatch(4);
        CountDownLatch editorialWorkersStarted = new CountDownLatch(4);
        CountDownLatch publisherWorkersStarted = new CountDownLatch(4);

        when(importService.findJournalIds()).thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        when(importService.countJournals()).thenReturn(6L);
        when(importService.findJournalIdsAfter(Long.MIN_VALUE, 32))
                .thenReturn(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        when(wosIndicatorImportService.readRecords(wos2022Dir.toString())).thenReturn(createWosRecords(12));
        when(wosIndicatorImportService.readRecords(wos2023Dir.toString())).thenReturn(createWosRecords(12));
        when(scopusIndicatorImportService.readRecords(scopus2022Dir.toString())).thenReturn(createScopusRecords(12));
        when(scopusIndicatorImportService.readRecords(scopus2023Dir.toString())).thenReturn(createScopusRecords(12));
        when(notIndexed2024ImportService.readReasons(notIndexed2024File.toString())).thenReturn(notIndexed2024Reasons);
        when(notIndexed2024ImportService.readReasons(notIndexed2025File.toString())).thenReturn(notIndexed2025Reasons);
        when(databasesIndexed2024ImportService.readRecords(databasesIndexedFile.toString())).thenAnswer(invocation -> {
            MigrationImportStatusDto status = statusService.current();
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.NOT_INDEXED_2025);
            assertThat(status.getStageProcessed()).isEqualTo(2);
            assertThat(status.getStageTotal()).isEqualTo(2);
            return createDatabasesIndexedRecords(12);
        });
        when(mnisw2024ImportService.readRecords(mnisw2024File.toString())).thenReturn(createMnisw2024Records(12));
        when(linkToReview2024ImportService.readRecords(linkToReview2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedScopusCurrent.get()).isEqualTo(12);
            return createLinkToReview2024Records(12);
        });
        when(journalKind2024ImportService.readRecords(kind2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedLinkToReview2024.get()).isEqualTo(12);
            return createJournalKind2024Records(12);
        });
        when(infoReview2024ImportService.readRecords(infoReview2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedJournalKind2024.get()).isEqualTo(12);
            return createInfoReview2024Records(12);
        });
        when(cooperation2024ImportService.readRecords(cooperation2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedInfoReview2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.COOPERATION_2024);
            return createCooperation2024Records(12);
        });
        when(stability2024ImportService.readRecords(stability2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedCooperation2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.STABILITY_2024);
            return createStability2024Records(12);
        });
        when(wosCategory2024ImportService.readRecords(wosCategories2024File.toString()))
                .thenReturn(createCategory2024Records(12, "wos-categories-2024.xlsx"));
        when(languages2024ImportService.readRecords(languages2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedStability2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.LANGUAGES_2024);
            return createLanguages2024Records(12);
        });
        when(reviewers2024ImportService.readRecords(reviewers2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedLanguages2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.REVIEWERS_2024);
            return createReviewers2024Records(12);
        });
        when(scopusCategory2024ImportService.readRecords(scopusCategories2024File.toString()))
                .thenReturn(createCategory2024Records(12, "scopus-categories-2024.xlsx"));
        when(directors2024ImportService.readRecords(directors2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedReviewers2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.DIRECTORS_2024);
            return createDirectors2024Records(12);
        });
        when(editorialStaff2024ImportService.readRecords(editorialStaff2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedDirectors2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.EDITORIAL_STAFF_2024);
            return createEditorialStaff2024Records(12);
        });
        when(editorial2024ImportService.readRecords(editorial2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedEditorialStaff2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.EDITORIAL_2024);
            return createEditorial2024Records(12);
        });
        when(publisher2024ImportService.readRecords(publisher2024File.toString())).thenAnswer(invocation -> {
            assertThat(completedEditorial2024.get()).isEqualTo(12);
            assertThat(statusService.current().getStage()).isEqualTo(MigrationImportStage.PUBLISHER_2024);
            return createPublisher2024Records(12);
        });
        doAnswer(invocation -> {
            assertThat(completedJournalUpdates.get()).isEqualTo(6);
            return new SurveyReportExportSummary(6, 6);
        }).when(surveyReportExportService).export(
                any(List.class),
                org.mockito.ArgumentMatchers.eq(2024),
                org.mockito.ArgumentMatchers.eq(questionnaireReportPl.toString()),
                org.mockito.ArgumentMatchers.eq(questionnaireReportEn.toString())
        );
        doAnswer(invocation -> {
            trackConcurrency(activeDictionaries, maxActiveDictionaries);
            return null;
        }).when(importService).importDictionaryJson(any(File.class));
        doAnswer(invocation -> {
            trackConcurrency(activeJournalDeletes, maxActiveJournalDeletes);
            return null;
        }).when(importService).deleteJournalById(any(Long.class));
        doAnswer(invocation -> {
            trackConcurrency(activeJournals, maxActiveJournals);
            return null;
        }).when(importService).importJournalJson(any(File.class));
        doAnswer(invocation -> {
            int year = invocation.getArgument(1);
            MigrationImportStage stage = year == 2024
                    ? MigrationImportStage.NOT_INDEXED_2024 : MigrationImportStage.NOT_INDEXED_2025;
            MigrationImportStatusDto status = statusService.current();
            assertThat(status.getStage()).isEqualTo(stage);
            assertThat(status.getStageTotal()).isEqualTo(year == 2024 ? notIndexed2024Reasons.size() : notIndexed2025Reasons.size());
            assertThat(status.getJournalProcessed()).isEqualTo(6);
            if (scenario.equals(stage.name() + "_FAILURE") && invocation.<Long>getArgument(0) == 1L) {
                throw new IllegalStateException("Updating the indexing reason failed");
            }
            return null;
        }).when(importService).updateReasonNotIndexing(any(), anyInt(), any());
        doAnswer(invocation -> {
            trackConcurrency(activeJournalUpdates, maxActiveJournalUpdates);
            assertThat(completedPublisher2024.get()).isEqualTo(12);
            completedJournalUpdates.incrementAndGet();
            return null;
        }).when(importService).updateJournalFromCurrentQuestionnaire(any(Long.class));
        doAnswer(invocation -> {
            trackConcurrency(activeDatabasesIndexed, maxActiveDatabasesIndexed);
            return null;
        }).when(databasesIndexed2024ImportService)
                .updateRepositories(any(DatabasesIndexed2024ImportService.DatabasesIndexed2024Record.class));
        doAnswer(invocation -> {
            trackConcurrency(activeMnisw2024, maxActiveMnisw2024);
            return null;
        }).when(mnisw2024ImportService).addRating(any(Mnisw2024ImportService.Mnisw2024Record.class));
        doAnswer(invocation -> {
            trackConcurrency(activeLinkToReview2024, maxActiveLinkToReview2024);
            completedLinkToReview2024.incrementAndGet();
            return null;
        }).when(linkToReview2024ImportService)
                .updateLinkToReviewProcedure(any(LinkToReview2024ImportService.LinkToReview2024Record.class));
        doAnswer(invocation -> {
            trackConcurrency(activeJournalKind2024, maxActiveJournalKind2024);
            completedJournalKind2024.incrementAndGet();
            return null;
        }).when(journalKind2024ImportService)
                .updateKind(any(JournalKind2024ImportService.JournalKind2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeInfoReview2024, maxActiveInfoReview2024, infoReviewWorkersStarted);
            completedInfoReview2024.incrementAndGet();
            return null;
        }).when(infoReview2024ImportService)
                .updateDoubleBlindReview(any(InfoReview2024ImportService.InfoReview2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeCooperation2024, maxActiveCooperation2024, cooperationWorkersStarted);
            completedCooperation2024.incrementAndGet();
            return null;
        }).when(cooperation2024ImportService).updateCooperation(any(Cooperation2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeStability2024, maxActiveStability2024, stabilityWorkersStarted);
            completedStability2024.incrementAndGet();
            return null;
        }).when(stability2024ImportService).updateStability(any(Stability2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeLanguages2024, maxActiveLanguages2024, languageWorkersStarted);
            completedLanguages2024.incrementAndGet();
            return null;
        }).when(languages2024ImportService).updateLanguages(any(Languages2024Record.class));
        doAnswer(invocation -> {
            trackConcurrency(activeWosCategories2024, maxActiveWosCategories2024);
            return null;
        }).when(wosCategory2024ImportService).updateCategories(any(Category2024Record.class));
        doAnswer(invocation -> {
            trackConcurrency(activeScopusCategories2024, maxActiveScopusCategories2024);
            return null;
        }).when(scopusCategory2024ImportService).updateCategories(any(Category2024Record.class));
        doAnswer(invocation -> {
            trackConcurrency(activeWosPrevious, maxActiveWosPrevious);
            return null;
        }).when(wosIndicatorImportService).updatePrevious(any(WosIndicatorImportService.WosIndicatorRecord.class));
        doAnswer(invocation -> {
            trackConcurrency(activeWosCurrent, maxActiveWosCurrent);
            return null;
        }).when(wosIndicatorImportService).updateCurrent(any(WosIndicatorImportService.WosIndicatorRecord.class));
        doAnswer(invocation -> {
            trackConcurrency(activeScopusPrevious, maxActiveScopusPrevious);
            return null;
        }).when(scopusIndicatorImportService).updatePrevious(any(ScopusIndicatorImportService.ScopusIndicatorRecord.class));
        doAnswer(invocation -> {
            trackConcurrency(activeScopusCurrent, maxActiveScopusCurrent);
            completedScopusCurrent.incrementAndGet();
            return null;
        }).when(scopusIndicatorImportService).updateCurrent(any(ScopusIndicatorImportService.ScopusIndicatorRecord.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeReviewers2024, maxActiveReviewers2024, reviewerWorkersStarted);
            completedReviewers2024.incrementAndGet();
            return null;
        }).when(reviewers2024ImportService).updateReviewers(any(Reviewers2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeDirectors2024, maxActiveDirectors2024, directorWorkersStarted);
            completedDirectors2024.incrementAndGet();
            return null;
        }).when(directors2024ImportService).updateDirectors(any(Directors2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeEditorialStaff2024, maxActiveEditorialStaff2024, editorialStaffWorkersStarted);
            completedEditorialStaff2024.incrementAndGet();
            return null;
        }).when(editorialStaff2024ImportService).updateEditorialStaff(any(EditorialStaff2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activeEditorial2024, maxActiveEditorial2024, editorialWorkersStarted);
            completedEditorial2024.incrementAndGet();
            return null;
        }).when(editorial2024ImportService).updateEditorial(any(Editorial2024Record.class));
        doAnswer(invocation -> {
            trackConcurrentWorkers(activePublisher2024, maxActivePublisher2024, publisherWorkersStarted);
            completedPublisher2024.incrementAndGet();
            return null;
        }).when(publisher2024ImportService).updatePublisher(any(Publisher2024Record.class));

        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                wosIndicatorImportService,
                scopusIndicatorImportService,
                notIndexed2024ImportService,
                databasesIndexed2024ImportService,
                mnisw2024ImportService,
                linkToReview2024ImportService,
                journalKind2024ImportService,
                infoReview2024ImportService,
                cooperation2024ImportService,
                stability2024ImportService,
                languages2024ImportService,
                reviewers2024ImportService,
                directors2024ImportService,
                editorialStaff2024ImportService,
                editorial2024ImportService,
                publisher2024ImportService,
                wosCategory2024ImportService,
                scopusCategory2024ImportService,
                surveyReportExportService,
                journalDocumentRepository,
                publisher,
                statusService,
                new MigrationJobCoordinator(),
                journalEventTaskCoordinator,
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "wos2022", wos2022Dir.toString());
        ReflectionTestUtils.setField(runner, "wos2023", wos2023Dir.toString());
        ReflectionTestUtils.setField(runner, "scopus2022", scopus2022Dir.toString());
        ReflectionTestUtils.setField(runner, "scopus2023", scopus2023Dir.toString());
        ReflectionTestUtils.setField(runner, "notIndexed2024", notIndexed2024File.toString());
        ReflectionTestUtils.setField(runner, "notIndexed2025", notIndexed2025File.toString());
        ReflectionTestUtils.setField(runner, "databasesIndexed2024", databasesIndexedFile.toString());
        ReflectionTestUtils.setField(runner, "mnisw2024", mnisw2024File.toString());
        ReflectionTestUtils.setField(runner, "linkToReview2024", linkToReview2024File.toString());
        ReflectionTestUtils.setField(runner, "kind2024", kind2024File.toString());
        ReflectionTestUtils.setField(runner, "infoReview2024", infoReview2024File.toString());
        ReflectionTestUtils.setField(runner, "cooperation2024", cooperation2024File.toString());
        ReflectionTestUtils.setField(runner, "stability2024", stability2024File.toString());
        ReflectionTestUtils.setField(runner, "languages2024", languages2024File.toString());
        ReflectionTestUtils.setField(runner, "reviewers2024", reviewers2024File.toString());
        ReflectionTestUtils.setField(runner, "directors2024", directors2024File.toString());
        ReflectionTestUtils.setField(runner, "editorialStaff2024", editorialStaff2024File.toString());
        ReflectionTestUtils.setField(runner, "editorial2024", editorial2024File.toString());
        ReflectionTestUtils.setField(runner, "publisher2024", publisher2024File.toString());
        ReflectionTestUtils.setField(runner, "wosCategory2024", wosCategories2024File.toString());
        ReflectionTestUtils.setField(runner, "scopusCategory2024", scopusCategories2024File.toString());
        ReflectionTestUtils.setField(runner, "outputQuestionaries2024xls", questionnaireReportPl.toString());
        ReflectionTestUtils.setField(runner, "outputQuestionaries2024xlsEn", questionnaireReportEn.toString());
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));

        var analytics = (com.osatum.wbn.application.journal.service.DecisionAnalyticsService) ReflectionTestUtils.getField(runner, "decisionAnalytics");
        var run = com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.RUN;
        doAnswer(invocation -> {
            assertThat(runner.status().getStage()).isEqualTo(MigrationImportStage.DELETE_P2_ANALYTICS);
            assertThat(runner.status().getStageTotal()).isEqualTo(1);
            assertThat(runner.status().getStageProcessed()).isZero();
            if (scenario.equals("BEGIN_FAILURE")) throw new IllegalStateException("cleanup failed");
            return run;
        }).when(analytics).begin();
        doAnswer(invocation -> {
            assertThat(runner.status().getStage()).isEqualTo(MigrationImportStage.BUILD_P2_ANALYTICS);
            java.util.function.Consumer<com.osatum.wbn.application.journal.service.DecisionAnalyticsService.Progress> progress = invocation.getArgument(1);
            progress.accept(new com.osatum.wbn.application.journal.service.DecisionAnalyticsService.Progress(2024, 10, 4, "Obliczanie benchmarków P2 za 2024."));
            assertThat(runner.status().getStageTotal()).isEqualTo(10);
            assertThat(runner.status().getStageProcessed()).isEqualTo(4);
            assertThat(runner.status().getCurrentFile()).isEqualTo("2024");
            if (scenario.equals("BUILD_FAILURE")) throw new IllegalStateException("build failed");
            return null;
        }).when(analytics).prepare(org.mockito.ArgumentMatchers.eq(run), any());
        if (scenario.equals("ASYNC_FAILURE")) doThrow(new IllegalStateException("index failed")).when(journalEventTaskCoordinator).awaitIdle(any());
        if (scenario.equals("REPORT_FAILURE")) doThrow(new IllegalStateException("report failed")).when(surveyReportExportService).export(any(), any(), any(), any());
        if (scenario.equals("PUBLISH_FAILURE")) doThrow(new IllegalStateException("revision changed")).when(analytics).publish(run);
        MigrationImportStatusDto status = runner.startImport();

        assertThat(status.getState()).isEqualTo(MigrationImportState.RUNNING);

        status = waitUntilFinished(runner);

        if (!scenario.equals("SUCCESS")) {
            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            if (!scenario.equals("PUBLISH_FAILURE")) verify(analytics, never()).publish(any());
            if (scenario.equals("BEGIN_FAILURE")) {
                assertThat(status.getStage()).isEqualTo(MigrationImportStage.DELETE_P2_ANALYTICS);
                assertThat(status.getStageProcessed()).isZero();
                verify(importService, never()).deleteJournalById(any());
                verify(analytics, never()).prepare(any(), any());
            } else {
                verify(analytics, org.mockito.Mockito.timeout(1000)).fail(run);
                if (scenario.equals("ASYNC_FAILURE")) verify(analytics, never()).prepare(any(), any());
            }
            if (scenario.equals("BUILD_FAILURE")) {
                assertThat(status.getStage()).isEqualTo(MigrationImportStage.BUILD_P2_ANALYTICS);
                assertThat(status.getStageProcessed()).isEqualTo(4);
            }
            if (scenario.startsWith("NOT_INDEXED_")) {
                boolean failedIn2024 = scenario.equals("NOT_INDEXED_2024_FAILURE");
                MigrationImportStage expectedStage = failedIn2024
                        ? MigrationImportStage.NOT_INDEXED_2024 : MigrationImportStage.NOT_INDEXED_2025;
                assertThat(status.getStage()).isEqualTo(expectedStage);
                assertThat(status.getStageProcessed()).isEqualTo(failedIn2024 ? 1 : 2);
                assertThat(status.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getType()).isEqualTo(expectedStage.name());
                    assertThat(error.getFileName()).isEqualTo("journal-1");
                });
                if (failedIn2024) {
                    verify(importService, never()).updateReasonNotIndexing(any(), eq(2025), any());
                }
                verify(databasesIndexed2024ImportService, never()).readRecords(any());
                verify(importService, never()).updateJournalFromCurrentQuestionnaire(any());
            }
            runner.shutdown();
            return;
        }
        var lifecycle = inOrder(analytics, importService, journalEventTaskCoordinator, surveyReportExportService);
        lifecycle.verify(analytics).begin();
        lifecycle.verify(journalEventTaskCoordinator).beginBatch(any());
        lifecycle.verify(importService, atLeastOnce()).deleteJournalById(any());
        lifecycle.verify(journalEventTaskCoordinator).awaitIdle(any());
        lifecycle.verify(analytics).prepare(org.mockito.ArgumentMatchers.eq(run), any());
        lifecycle.verify(surveyReportExportService).export(any(), org.mockito.ArgumentMatchers.eq(2024), any(), any());
        lifecycle.verify(analytics).publish(run);
        verify(analytics, never()).fail(any());
        assertThat(status.getState()).isEqualTo(MigrationImportState.COMPLETED);
        assertThat(status.getDictionaryProcessed()).isEqualTo(4);
        assertThat(status.getJournalProcessed()).isEqualTo(6);
        assertThat(maxActiveDictionaries.get()).isEqualTo(4);
        assertThat(maxActiveJournalDeletes.get()).isEqualTo(4);
        assertThat(maxActiveJournals.get()).isEqualTo(4);
        assertThat(maxActiveJournalUpdates.get()).isEqualTo(4);
        assertThat(maxActiveDatabasesIndexed.get()).isEqualTo(6);
        assertThat(maxActiveMnisw2024.get()).isEqualTo(4);
        assertThat(maxActiveLinkToReview2024.get()).isEqualTo(4);
        assertThat(maxActiveJournalKind2024.get()).isEqualTo(4);
        assertThat(maxActiveInfoReview2024.get()).isEqualTo(4);
        assertThat(maxActiveCooperation2024.get()).isEqualTo(4);
        assertThat(maxActiveStability2024.get()).isEqualTo(4);
        assertThat(maxActiveLanguages2024.get()).isEqualTo(4);
        assertThat(maxActiveReviewers2024.get()).isEqualTo(4);
        assertThat(maxActiveDirectors2024.get()).isEqualTo(4);
        assertThat(maxActiveEditorialStaff2024.get()).isEqualTo(4);
        assertThat(maxActiveEditorial2024.get()).isEqualTo(4);
        assertThat(maxActivePublisher2024.get()).isEqualTo(4);
        assertThat(maxActiveWosCategories2024.get()).isEqualTo(6);
        assertThat(maxActiveScopusCategories2024.get()).isEqualTo(6);
        assertThat(maxActiveWosPrevious.get()).isEqualTo(6);
        assertThat(maxActiveWosCurrent.get()).isEqualTo(6);
        assertThat(maxActiveScopusPrevious.get()).isEqualTo(6);
        assertThat(maxActiveScopusCurrent.get()).isEqualTo(6);
        var importOrder = inOrder(importService);
        importOrder.verify(importService, atLeastOnce()).deleteJournalById(any(Long.class));
        importOrder.verify(importService).deleteAllDictionaries();
        verify(importService).deleteAllDictionaries();
        verify(importService).reloadDictionaryCaches();
        verify(wosIndicatorImportService).readRecords(wos2022Dir.toString());
        verify(wosIndicatorImportService).readRecords(wos2023Dir.toString());
        verify(wosIndicatorImportService, times(12)).updatePrevious(any(WosIndicatorImportService.WosIndicatorRecord.class));
        verify(wosIndicatorImportService, times(12)).updateCurrent(any(WosIndicatorImportService.WosIndicatorRecord.class));
        verify(notIndexed2024ImportService).readReasons(notIndexed2024File.toString());
        verify(notIndexed2024ImportService).readReasons(notIndexed2025File.toString());
        verify(databasesIndexed2024ImportService).readRecords(databasesIndexedFile.toString());
        verify(databasesIndexed2024ImportService, times(12))
                .updateRepositories(any(DatabasesIndexed2024ImportService.DatabasesIndexed2024Record.class));
        verify(mnisw2024ImportService).readRecords(mnisw2024File.toString());
        verify(mnisw2024ImportService, times(12)).addRating(any(Mnisw2024ImportService.Mnisw2024Record.class));
        verify(linkToReview2024ImportService).readRecords(linkToReview2024File.toString());
        verify(linkToReview2024ImportService, times(12))
                .updateLinkToReviewProcedure(any(LinkToReview2024ImportService.LinkToReview2024Record.class));
        verify(journalKind2024ImportService).readRecords(kind2024File.toString());
        verify(journalKind2024ImportService, times(12))
                .updateKind(any(JournalKind2024ImportService.JournalKind2024Record.class));
        verify(infoReview2024ImportService).readRecords(infoReview2024File.toString());
        verify(infoReview2024ImportService, times(12))
                .updateDoubleBlindReview(any(InfoReview2024ImportService.InfoReview2024Record.class));
        verify(cooperation2024ImportService).readRecords(cooperation2024File.toString());
        verify(cooperation2024ImportService, times(12)).updateCooperation(any(Cooperation2024Record.class));
        verify(stability2024ImportService).readRecords(stability2024File.toString());
        verify(stability2024ImportService, times(12)).updateStability(any(Stability2024Record.class));
        verify(languages2024ImportService).readRecords(languages2024File.toString());
        verify(languages2024ImportService, times(12)).updateLanguages(any(Languages2024Record.class));
        verify(reviewers2024ImportService).readRecords(reviewers2024File.toString());
        verify(reviewers2024ImportService, times(12)).updateReviewers(any(Reviewers2024Record.class));
        verify(directors2024ImportService).readRecords(directors2024File.toString());
        verify(directors2024ImportService, times(12)).updateDirectors(any(Directors2024Record.class));
        verify(editorialStaff2024ImportService).readRecords(editorialStaff2024File.toString());
        verify(editorialStaff2024ImportService, times(12)).updateEditorialStaff(any(EditorialStaff2024Record.class));
        verify(editorial2024ImportService).readRecords(editorial2024File.toString());
        verify(editorial2024ImportService, times(12)).updateEditorial(any(Editorial2024Record.class));
        verify(publisher2024ImportService).readRecords(publisher2024File.toString());
        verify(publisher2024ImportService, times(12)).updatePublisher(any(Publisher2024Record.class));
        verify(wosCategory2024ImportService).readRecords(wosCategories2024File.toString());
        verify(wosCategory2024ImportService, times(12)).updateCategories(any(Category2024Record.class));
        verify(scopusCategory2024ImportService).readRecords(scopusCategories2024File.toString());
        verify(scopusCategory2024ImportService, times(12)).updateCategories(any(Category2024Record.class));
        importOrder.verify(importService, times(6)).importJournalJson(any(File.class));
        importOrder.verify(importService).updateReasonNotIndexing(1L, 2024, ReasonNotIndexing.dec06);
        importOrder.verify(importService, times(2)).updateReasonNotIndexing(any(), eq(2025), any());
        verify(importService).updateReasonNotIndexing(1L, 2025, ReasonNotIndexing.dec07);
        verify(importService).updateReasonNotIndexing(2L, 2025, null);
        verify(importService).countJournals();
        verify(importService).findJournalIdsAfter(Long.MIN_VALUE, 32);
        verify(importService, times(6)).updateJournalFromCurrentQuestionnaire(any(Long.class));
        verify(scopusIndicatorImportService).readRecords(scopus2022Dir.toString());
        verify(scopusIndicatorImportService).readRecords(scopus2023Dir.toString());
        verify(scopusIndicatorImportService, times(12)).updatePrevious(any(ScopusIndicatorImportService.ScopusIndicatorRecord.class));
        verify(scopusIndicatorImportService, times(12)).updateCurrent(any(ScopusIndicatorImportService.ScopusIndicatorRecord.class));
        verify(surveyReportExportService).export(
                org.mockito.ArgumentMatchers.eq(List.of(1L, 2L, 3L, 4L, 5L, 6L)),
                org.mockito.ArgumentMatchers.eq(2024),
                org.mockito.ArgumentMatchers.eq(questionnaireReportPl.toString()),
                org.mockito.ArgumentMatchers.eq(questionnaireReportEn.toString())
        );
        verify(journalDocumentRepository).deleteAll();
        verify(journalEventTaskCoordinator, times(3)).awaitIdle(Duration.ofSeconds(5));
        runner.shutdown();
    }

    @Test
    void updatesJournalsInBoundedParallelBatches() throws Exception {
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        JournalEventTaskCoordinator journalEventTaskCoordinator = mock(JournalEventTaskCoordinator.class);
        List<Long> firstPage = createJournalIds(1, 32);
        List<Long> secondPage = createJournalIds(33, 35);
        AtomicInteger activeJournalUpdates = new AtomicInteger();
        AtomicInteger maxActiveJournalUpdates = new AtomicInteger();

        when(importService.countJournals()).thenReturn(35L);
        when(importService.findJournalIdsAfter(Long.MIN_VALUE, 32)).thenReturn(firstPage);
        when(importService.findJournalIdsAfter(32L, 32)).thenReturn(secondPage);
        doAnswer(invocation -> {
            trackConcurrency(activeJournalUpdates, maxActiveJournalUpdates);
            return null;
        }).when(importService).updateJournalFromCurrentQuestionnaire(any(Long.class));

        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                mock(SurveyReportExportService.class),
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                journalEventTaskCoordinator,
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));

        try {
            Boolean succeeded = ReflectionTestUtils.invokeMethod(runner, "updateJournalsFromCurrentQuestionnaire");

            assertThat(succeeded).isTrue();
            assertThat(maxActiveJournalUpdates.get()).isEqualTo(4);
            MigrationImportStatusDto status = statusService.current();
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.UPDATE_JOURNALS_FROM_CURRENT_QUESTIONNAIRE);
            assertThat(status.getStageTotal()).isEqualTo(35);
            assertThat(status.getStageProcessed()).isEqualTo(35);
            verify(importService).findJournalIdsAfter(Long.MIN_VALUE, 32);
            verify(importService).findJournalIdsAfter(32L, 32);
            verify(importService, times(35)).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(journalEventTaskCoordinator, times(2)).awaitIdle(Duration.ofSeconds(5));
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025})
    void marksImportAsFailedAfterOutOfMemoryErrorBeforeDeletingData(int year) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("oom-dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("oom-journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        WosIndicatorImportService wosIndicatorImportService = mock(WosIndicatorImportService.class);
        ScopusIndicatorImportService scopusIndicatorImportService = mock(ScopusIndicatorImportService.class);
        NotIndexed2024ImportService notIndexed2024ImportService = mock(NotIndexed2024ImportService.class);
        DatabasesIndexed2024ImportService databasesIndexed2024ImportService = mock(DatabasesIndexed2024ImportService.class);
        Mnisw2024ImportService mnisw2024ImportService = mock(Mnisw2024ImportService.class);
        when(notIndexed2024ImportService.readReasons("not-indexed-" + year + ".xlsx"))
                .thenThrow(new OutOfMemoryError("Java heap space"));
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                wosIndicatorImportService,
                scopusIndicatorImportService,
                notIndexed2024ImportService,
                databasesIndexed2024ImportService,
                mnisw2024ImportService,
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                mock(SurveyReportExportService.class),
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                new MigrationImportStatusService(),
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "notIndexed2024", "not-indexed-2024.xlsx");
        ReflectionTestUtils.setField(runner, "notIndexed2025", "not-indexed-2025.xlsx");

        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.PREPARING);
            assertThat(status.getErrors()).singleElement()
                    .satisfies(error -> assertThat(error.getMessage()).isEqualTo("Java heap space"));
            verify(importService, never()).deleteJournalById(any());
            verify(importService, never()).deleteAllDictionaries();
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsBeforeJournalSynchronizationAndReportsWhenCooperationImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        Cooperation2024ImportService cooperationService = mock(Cooperation2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(cooperationService.readRecords("cooperation.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid cooperation spreadsheet"));
        } else {
            when(cooperationService.readRecords("cooperation.xlsx")).thenReturn(createCooperation2024Records(1));
            doThrow(new IllegalStateException("Missing questionnaire"))
                    .when(cooperationService).updateCooperation(any(Cooperation2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                cooperationService,
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "cooperation2024", "cooperation.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.COOPERATION_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error ->
                    assertThat(error.getMessage()).contains(readFailure ? "Invalid cooperation spreadsheet" : "Missing questionnaire")
            );
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsBeforeJournalSynchronizationAndReportsWhenStabilityImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        Stability2024ImportService stabilityService = mock(Stability2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(stabilityService.readRecords("stability.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid stability spreadsheet"));
        } else {
            when(stabilityService.readRecords("stability.xlsx")).thenReturn(createStability2024Records(1));
            doThrow(new IllegalStateException("Missing questionnaire"))
                    .when(stabilityService).updateStability(any(Stability2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                stabilityService,
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "stability2024", "stability.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.STABILITY_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getMessage()).contains(readFailure ? "Invalid stability spreadsheet" : "Missing questionnaire");
                if (!readFailure) {
                    assertThat(error.getFileName()).isEqualTo("stability-2024.xlsx:3:journal-1");
                }
            });
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsBeforeJournalSynchronizationAndReportsWhenLanguagesImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        Languages2024ImportService languagesService = mock(Languages2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(languagesService.readRecords("languages.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid languages spreadsheet"));
        } else {
            when(languagesService.readRecords("languages.xlsx")).thenReturn(createLanguages2024Records(1));
            doThrow(new IllegalStateException("Unknown language code"))
                    .when(languagesService).updateLanguages(any(Languages2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                languagesService,
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "languages2024", "languages.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.LANGUAGES_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getMessage()).contains(readFailure ? "Invalid languages spreadsheet" : "Unknown language code");
                if (!readFailure) {
                    assertThat(error.getFileName()).isEqualTo("languages-2024.xlsx:2:journal-1");
                }
            });
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsBeforeJournalSynchronizationAndReportsWhenReviewersImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        Reviewers2024ImportService reviewersService = mock(Reviewers2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(reviewersService.readRecords("reviewers.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid reviewers spreadsheet"));
        } else {
            when(reviewersService.readRecords("reviewers.xlsx")).thenReturn(createReviewers2024Records(1));
            doThrow(new IllegalStateException("Unknown country"))
                    .when(reviewersService).updateReviewers(any(Reviewers2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                reviewersService,
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "reviewers2024", "reviewers.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.REVIEWERS_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getMessage()).contains(readFailure ? "Invalid reviewers spreadsheet" : "Unknown country");
                if (!readFailure) {
                    assertThat(error.getFileName()).isEqualTo("reviewers-2024.xlsx:2:journal-1");
                }
            });
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsBeforeJournalSynchronizationAndReportsWhenDirectorsImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        Directors2024ImportService directorsService = mock(Directors2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(directorsService.readRecords("directors.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid directors spreadsheet"));
        } else {
            when(directorsService.readRecords("directors.xlsx")).thenReturn(createDirectors2024Records(1));
            doThrow(new IllegalStateException("Unknown country"))
                    .when(directorsService).updateDirectors(any(Directors2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                directorsService,
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "directors2024", "directors.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.DIRECTORS_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getMessage()).contains(readFailure ? "Invalid directors spreadsheet" : "Unknown country");
                if (!readFailure) {
                    assertThat(error.getFileName()).isEqualTo("directors-2024.xlsx:2:journal-1");
                }
            });
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stopsBeforeJournalSynchronizationAndReportsWhenEditorialStaffImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        EditorialStaff2024ImportService editorialStaffService = mock(EditorialStaff2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(editorialStaffService.readRecords("editors.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid editorial staff spreadsheet"));
        } else {
            when(editorialStaffService.readRecords("editors.xlsx")).thenReturn(createEditorialStaff2024Records(1));
            doThrow(new IllegalStateException("Missing questionnaire"))
                    .when(editorialStaffService).updateEditorialStaff(any(EditorialStaff2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                editorialStaffService,
                mock(Editorial2024ImportService.class),
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "editorialStaff2024", "editors.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.EDITORIAL_STAFF_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getMessage()).contains(readFailure ? "Invalid editorial staff spreadsheet" : "Missing questionnaire");
                if (!readFailure) {
                    assertThat(error.getFileName()).isEqualTo("editorial-staff-2024.xlsx:2:journal-1");
                }
            });
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stopsBeforeJournalSynchronizationAndReportsWhenEditorialImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        Editorial2024ImportService editorialService = mock(Editorial2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(editorialService.readRecords("editorial.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid editorial spreadsheet"));
        } else {
            when(editorialService.readRecords("editorial.xlsx")).thenReturn(createEditorial2024Records(1));
            doThrow(new IllegalStateException("Missing questionnaire"))
                    .when(editorialService).updateEditorial(any(Editorial2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                editorialService,
                mock(Publisher2024ImportService.class),
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "editorial2024", "editorial.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.EDITORIAL_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getMessage()).contains(readFailure ? "Invalid editorial spreadsheet" : "Missing questionnaire");
                if (!readFailure) {
                    assertThat(error.getFileName()).isEqualTo("editorial-2024.xlsx:2:journal-1");
                }
            });
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stopsBeforeJournalSynchronizationAndReportsWhenPublisherImportFails(boolean readFailure) throws Exception {
        Path dictionaryDir = Files.createDirectory(tempDir.resolve("dictionaries"));
        Path journalDir = Files.createDirectory(tempDir.resolve("journals"));
        JournalMigrationImportService importService = mock(JournalMigrationImportService.class);
        Publisher2024ImportService publisherService = mock(Publisher2024ImportService.class);
        SurveyReportExportService reportService = mock(SurveyReportExportService.class);
        MigrationImportStatusService statusService = new MigrationImportStatusService();
        if (readFailure) {
            when(publisherService.readRecords("publisher.xlsx"))
                    .thenThrow(new IllegalArgumentException("Invalid publisher spreadsheet"));
        } else {
            when(publisherService.readRecords("publisher.xlsx")).thenReturn(createPublisher2024Records(1));
            doThrow(new IllegalStateException("Missing questionnaire"))
                    .when(publisherService).updatePublisher(any(Publisher2024Record.class));
        }
        MigrationImportRunnerService runner = new MigrationImportRunnerService(
                importService,
                mock(WosIndicatorImportService.class),
                mock(ScopusIndicatorImportService.class),
                mock(NotIndexed2024ImportService.class),
                mock(DatabasesIndexed2024ImportService.class),
                mock(Mnisw2024ImportService.class),
                mock(LinkToReview2024ImportService.class),
                mock(JournalKind2024ImportService.class),
                mock(InfoReview2024ImportService.class),
                mock(Cooperation2024ImportService.class),
                mock(Stability2024ImportService.class),
                mock(Languages2024ImportService.class),
                mock(Reviewers2024ImportService.class),
                mock(Directors2024ImportService.class),
                mock(EditorialStaff2024ImportService.class),
                mock(Editorial2024ImportService.class),
                publisherService,
                mock(WosCategory2024ImportService.class),
                mock(ScopusCategory2024ImportService.class),
                reportService,
                mock(JournalDocumentRepository.class),
                mock(ApplicationEventPublisher.class),
                statusService,
                new MigrationJobCoordinator(),
                mock(JournalEventTaskCoordinator.class),
                com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.importAnalytics()
        );
        ReflectionTestUtils.setField(runner, "jsonDictionary", dictionaryDir.toString());
        ReflectionTestUtils.setField(runner, "jsonSource", journalDir.toString());
        ReflectionTestUtils.setField(runner, "publisher2024", "publisher.xlsx");
        ReflectionTestUtils.setField(runner, "journalEventAwaitTimeout", Duration.ofSeconds(5));
        try {
            runner.startImport();

            MigrationImportStatusDto status = waitUntilFinished(runner);

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getStage()).isEqualTo(MigrationImportStage.PUBLISHER_2024);
            assertThat(status.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getMessage()).contains(readFailure ? "Invalid publisher spreadsheet" : "Missing questionnaire");
                if (!readFailure) {
                    assertThat(error.getFileName()).isEqualTo("publisher-2024.xlsx:2:journal-1");
                }
            });
            verify(importService, never()).countJournals();
            verify(importService, never()).updateJournalFromCurrentQuestionnaire(any(Long.class));
            verify(reportService, never()).export(any(), any(), any(), any());
        } finally {
            runner.shutdown();
        }
    }

    private MigrationImportStatusDto waitUntilFinished(MigrationImportRunnerService runner) throws InterruptedException {
        long deadline = System.nanoTime() + 15_000_000_000L;
        MigrationImportStatusDto status;
        do {
            status = runner.status();
            if (status.getState() != MigrationImportState.RUNNING) return status;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        return status;
    }
    private List<WosIndicatorImportService.WosIndicatorRecord> createWosRecords(int count) {
        List<WosIndicatorImportService.WosIndicatorRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new WosIndicatorImportService.WosIndicatorRecord((long) i + 1, 1.0, 2.0, "wos.xlsx", i + 2));
        }
        return records;
    }

    private List<ScopusIndicatorImportService.ScopusIndicatorRecord> createScopusRecords(int count) {
        List<ScopusIndicatorImportService.ScopusIndicatorRecord> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new ScopusIndicatorImportService.ScopusIndicatorRecord((long) i + 1, 1.0, 2.0, 3.0, 4.0, "scopus.xlsx", i + 2));
        }
        return records;
    }

    private List<DatabasesIndexed2024ImportService.DatabasesIndexed2024Record> createDatabasesIndexedRecords(int count) {
        List<DatabasesIndexed2024ImportService.DatabasesIndexed2024Record> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new DatabasesIndexed2024ImportService.DatabasesIndexed2024Record(
                    (long) i + 1,
                    List.of(1L),
                    "databases-indexed-2024.xlsx",
                    i + 2
            ));
        }
        return records;
    }

    private List<Mnisw2024ImportService.Mnisw2024Record> createMnisw2024Records(int count) {
        List<Mnisw2024ImportService.Mnisw2024Record> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new Mnisw2024ImportService.Mnisw2024Record(
                    (long) i + 1,
                    100.0,
                    Set.of("101"),
                    "mnisw-2024.xlsx",
                    i + 2
            ));
        }
        return records;
    }

    private List<LinkToReview2024ImportService.LinkToReview2024Record> createLinkToReview2024Records(int count) {
        List<LinkToReview2024ImportService.LinkToReview2024Record> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new LinkToReview2024ImportService.LinkToReview2024Record(
                    (long) i + 1,
                    "https://example.org/review/" + (i + 1),
                    "link-to-review-2024.xlsx",
                    i + 2
            ));
        }
        return records;
    }

    private List<JournalKind2024ImportService.JournalKind2024Record> createJournalKind2024Records(int count) {
        List<JournalKind2024ImportService.JournalKind2024Record> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new JournalKind2024ImportService.JournalKind2024Record(
                    (long) i + 1,
                    JournalKind.SCIENTIFIC,
                    "kind-2024.xlsx",
                    i + 2
            ));
        }
        return records;
    }

    private List<InfoReview2024ImportService.InfoReview2024Record> createInfoReview2024Records(int count) {
        List<InfoReview2024ImportService.InfoReview2024Record> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new InfoReview2024ImportService.InfoReview2024Record(
                    (long) i + 1,
                    true,
                    100.0,
                    "info-review-2024.xlsx",
                    i + 2
            ));
        }
        return records;
    }

    private List<Cooperation2024Record> createCooperation2024Records(int count) {
        List<Cooperation2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            MetricValues metric = new MetricValues(recordIndex, 100.0, 25.0);
            records.add(new Cooperation2024Record(
                    (long) recordIndex + 1, metric, metric, metric, "cooperation-2024.xlsx", recordIndex + 5
            ));
        }
        return records;
    }

    private List<Reviewers2024Record> createReviewers2024Records(int count) {
        List<Reviewers2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            records.add(new Reviewers2024Record((long) recordIndex + 1,
                    List.of(new ReviewerValues("Reviewer", "University", "PL", null)),
                    "reviewers-2024.xlsx", recordIndex + 2));
        }
        return records;
    }

    private List<Directors2024Record> createDirectors2024Records(int count) {
        List<Directors2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            records.add(new Directors2024Record((long) recordIndex + 1,
                    List.of(new DirectorValues("Director", "University", "PL", null)),
                    "directors-2024.xlsx", recordIndex + 2));
        }
        return records;
    }

    private List<EditorialStaff2024Record> createEditorialStaff2024Records(int count) {
        List<EditorialStaff2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            records.add(new EditorialStaff2024Record((long) recordIndex + 1,
                    List.of(new EditorValues("Anna", null, "Nowak", null, null, null, null, null, null)),
                    "editorial-staff-2024.xlsx", recordIndex + 2));
        }
        return records;
    }

    private List<Editorial2024Record> createEditorial2024Records(int count) {
        List<Editorial2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            records.add(new Editorial2024Record((long) recordIndex + 1, List.of(), "editorial-2024.xlsx", recordIndex + 2));
        }
        return records;
    }

    private List<Publisher2024Record> createPublisher2024Records(int count) {
        List<Publisher2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            records.add(new Publisher2024Record((long) recordIndex + 1, List.of(), "publisher-2024.xlsx", recordIndex + 2));
        }
        return records;
    }

    private List<Languages2024Record> createLanguages2024Records(int count) {
        List<Languages2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            records.add(new Languages2024Record((long) recordIndex + 1,
                    List.of(new ArticleLanguageValues("EN", 10, 8), new ArticleLanguageValues("UK", null, 0)),
                    "languages-2024.xlsx", recordIndex + 2));
        }
        return records;
    }

    private List<Stability2024Record> createStability2024Records(int count) {
        List<Stability2024Record> records = new ArrayList<>();
        for (int recordIndex = 0; recordIndex < count; recordIndex++) {
            List<ArticlePublishedValues> articles = List.of(2021, 2022, 2023, 2024).stream()
                    .map(year -> new ArticlePublishedValues(year, 10, 9, 20, 5, 4, null)).toList();
            records.add(new Stability2024Record((long) recordIndex + 1, articles, "stability-2024.xlsx", recordIndex + 3));
        }
        return records;
    }

    private List<Category2024Record> createCategory2024Records(int count, String fileName) {
        List<Category2024Record> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new Category2024Record(
                    (long) i + 1,
                    List.of(new Category2024Item(1L, true)),
                    fileName,
                    i + 2
            ));
        }
        return records;
    }

    private void createFiles(Path directory, int count) throws Exception {
        for (int i = 0; i < count; i++) {
            Files.writeString(directory.resolve("file-" + i + ".json"), "{}");
        }
    }

    private List<Long> createJournalIds(int first, int last) {
        List<Long> journalIds = new ArrayList<>();
        for (int journalId = first; journalId <= last; journalId++) {
            journalIds.add((long) journalId);
        }
        return journalIds;
    }

    private void trackConcurrency(AtomicInteger active, AtomicInteger maxActive) throws InterruptedException {
        int running = active.incrementAndGet();
        maxActive.accumulateAndGet(running, Math::max);
        try {
            Thread.sleep(75);
        } finally {
            active.decrementAndGet();
        }
    }

    private void trackConcurrentWorkers(
            AtomicInteger active,
            AtomicInteger maxActive,
            CountDownLatch workersStarted
    ) throws InterruptedException {
        int running = active.incrementAndGet();
        maxActive.accumulateAndGet(running, Math::max);
        try {
            workersStarted.countDown();
            if (!workersStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while waiting for concurrent migration workers");
            }
        } finally {
            active.decrementAndGet();
        }
    }
}
