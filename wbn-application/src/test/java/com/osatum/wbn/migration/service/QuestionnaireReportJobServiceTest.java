package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService.SurveyReportExportSummary;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.migration.dto.MigrationImportState;
import com.osatum.wbn.migration.dto.MigrationImportStatusDto;
import com.osatum.wbn.migration.service.MigrationJobCoordinator.Job;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuestionnaireReportJobServiceTest {

    @Test
    void generatesQuestionnaireReportsAsSeparateJob() throws Exception {
        JournalRepository journalRepository = mock(JournalRepository.class);
        SurveyReportExportService exportService = mock(SurveyReportExportService.class);
        QuestionnaireReportJobStatusService statusService = new QuestionnaireReportJobStatusService();
        MigrationJobCoordinator jobCoordinator = new MigrationJobCoordinator();
        QuestionnaireReportJobService service = new QuestionnaireReportJobService(
                journalRepository,
                exportService,
                statusService,
                jobCoordinator
        );
        ReflectionTestUtils.setField(service, "outputQuestionaries2024xls", "questionnaires-2024-pl.xlsx");
        ReflectionTestUtils.setField(service, "outputQuestionaries2024xlsEn", "questionnaires-2024-en.xlsx");

        CountDownLatch exportStarted = new CountDownLatch(1);
        CountDownLatch finishExport = new CountDownLatch(1);
        when(journalRepository.findAllIds()).thenReturn(List.of(3L, 1L, 2L));
        doAnswer(invocation -> {
            exportStarted.countDown();
            assertThat(finishExport.await(5, TimeUnit.SECONDS)).isTrue();
            return new SurveyReportExportSummary(3, 2);
        }).when(exportService).export(
                List.of(3L, 1L, 2L),
                2024,
                "questionnaires-2024-pl.xlsx",
                "questionnaires-2024-en.xlsx"
        );

        try {
            MigrationImportStatusDto started = service.startQuestionnaireReport2024();

            assertThat(started.getState()).isEqualTo(MigrationImportState.RUNNING);
            assertThat(exportStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.startQuestionnaireReport2024().getState()).isEqualTo(MigrationImportState.RUNNING);

            finishExport.countDown();
            MigrationImportStatusDto completed = waitUntilFinished(service);

            assertThat(completed.getState()).isEqualTo(MigrationImportState.COMPLETED);
            assertThat(completed.getStageTotal()).isEqualTo(3);
            assertThat(completed.getStageProcessed()).isEqualTo(3);
            assertThat(completed.getMessage()).contains("Uwzgledniono czasopism: 2");
            verify(exportService).export(
                    List.of(3L, 1L, 2L),
                    2024,
                    "questionnaires-2024-pl.xlsx",
                    "questionnaires-2024-en.xlsx"
            );
        } finally {
            finishExport.countDown();
            service.shutdown();
        }
    }

    @Test
    void rejectsReportJobWhileImportIsRunning() {
        JournalRepository journalRepository = mock(JournalRepository.class);
        SurveyReportExportService exportService = mock(SurveyReportExportService.class);
        MigrationJobCoordinator jobCoordinator = new MigrationJobCoordinator();
        assertThat(jobCoordinator.tryStart(Job.IMPORT)).isTrue();
        QuestionnaireReportJobService service = new QuestionnaireReportJobService(
                journalRepository,
                exportService,
                new QuestionnaireReportJobStatusService(),
                jobCoordinator
        );

        try {
            MigrationImportStatusDto status = service.startQuestionnaireReport2024();

            assertThat(status.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(status.getMessage()).contains("trwa inny job systemowy");
            verifyNoInteractions(journalRepository, exportService);
        } finally {
            jobCoordinator.finish(Job.IMPORT);
            service.shutdown();
        }
    }

    @Test
    void marksReportAsFailedAndReleasesJobAfterOutOfMemoryError() throws Exception {
        JournalRepository journalRepository = mock(JournalRepository.class);
        SurveyReportExportService exportService = mock(SurveyReportExportService.class);
        QuestionnaireReportJobStatusService statusService = new QuestionnaireReportJobStatusService();
        MigrationJobCoordinator jobCoordinator = new MigrationJobCoordinator();
        QuestionnaireReportJobService service = new QuestionnaireReportJobService(
                journalRepository,
                exportService,
                statusService,
                jobCoordinator
        );
        ReflectionTestUtils.setField(service, "outputQuestionaries2024xls", "questionnaires-2024-pl.xlsx");
        ReflectionTestUtils.setField(service, "outputQuestionaries2024xlsEn", "questionnaires-2024-en.xlsx");
        when(journalRepository.findAllIds()).thenReturn(List.of(1L));
        when(exportService.export(
                List.of(1L),
                2024,
                "questionnaires-2024-pl.xlsx",
                "questionnaires-2024-en.xlsx"
        )).thenThrow(new OutOfMemoryError("Java heap space"));

        try {
            service.startQuestionnaireReport2024();

            MigrationImportStatusDto failed = waitUntilFinished(service);

            assertThat(failed.getState()).isEqualTo(MigrationImportState.FAILED);
            assertThat(failed.getErrors()).singleElement()
                    .satisfies(error -> assertThat(error.getMessage()).isEqualTo("Java heap space"));
            assertThat(jobCoordinator.tryStart(Job.IMPORT)).isTrue();
        } finally {
            jobCoordinator.finish(Job.IMPORT);
            service.shutdown();
        }
    }

    private MigrationImportStatusDto waitUntilFinished(QuestionnaireReportJobService service) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        MigrationImportStatusDto status;
        do {
            status = service.status();
            if (status.getState() != MigrationImportState.RUNNING) {
                return status;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        return status;
    }
}
