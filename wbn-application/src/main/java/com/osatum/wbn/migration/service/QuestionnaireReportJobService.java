package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportExportService.SurveyReportExportSummary;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.migration.dto.MigrationImportStatusDto;
import com.osatum.wbn.migration.service.MigrationJobCoordinator.Job;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionnaireReportJobService {
    private static final int SURVEY_REPORT_YEAR = 2024;

    private final JournalRepository journalRepository;
    private final SurveyReportExportService surveyReportExportService;
    private final QuestionnaireReportJobStatusService statusService;
    private final MigrationJobCoordinator jobCoordinator;
    private final ExecutorService reportExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "questionnaire-report-job");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${app.outpux-questionaris-xls}")
    private String outputQuestionaries2024xls;

    @Value("${app.outpux-questionaris-xls-en}")
    private String outputQuestionaries2024xlsEn;

    public MigrationImportStatusDto startQuestionnaireReport2024() {
        if (!jobCoordinator.tryStart(Job.QUESTIONNAIRE_REPORT_2024)) {
            if (!jobCoordinator.isActive(Job.QUESTIONNAIRE_REPORT_2024)) {
                statusService.rejected();
            }
            return statusService.current();
        }

        try {
            statusService.started();
            reportExecutor.submit(this::runQuestionnaireReport2024);
            return statusService.current();
        } catch (RuntimeException e) {
            jobCoordinator.finish(Job.QUESTIONNAIRE_REPORT_2024);
            statusService.failed(outputQuestionaries2024xls, e);
            return statusService.current();
        }
    }

    public MigrationImportStatusDto status() {
        return statusService.current();
    }

    @PreDestroy
    void shutdown() {
        reportExecutor.shutdownNow();
    }

    private void runQuestionnaireReport2024() {
        try {
            List<Long> journalIds = journalRepository.findAllIds();
            statusService.prepared(journalIds.size());
            SurveyReportExportSummary summary = surveyReportExportService.export(
                    journalIds,
                    SURVEY_REPORT_YEAR,
                    outputQuestionaries2024xls,
                    outputQuestionaries2024xlsEn
            );
            statusService.completed(summary);
        } catch (RuntimeException | OutOfMemoryError e) {
            log.error("Error generating questionnaire report for 2024", e);
            statusService.failed(outputQuestionaries2024xls, e);
        } finally {
            jobCoordinator.finish(Job.QUESTIONNAIRE_REPORT_2024);
        }
    }
}
