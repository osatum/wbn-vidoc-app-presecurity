package com.osatum.wbn.application.report.survey.sevice;

import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoAssembler;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class SurveyReportExportService {
    private static final int REPORT_PARALLELISM = 10;
    private static final String POLISH_LANGUAGE = "pl";
    private static final String ENGLISH_LANGUAGE = "en";
    private static final Locale POLISH_LOCALE = Locale.forLanguageTag(POLISH_LANGUAGE);

    private final SurveyReportJournalService surveyReportJournalService;
    private final SurveyReportDtoAssembler surveyReportDtoAssembler;
    private final SurveyReportGenerator surveyReportGenerator;

    public SurveyReportExportSummary export(
            List<Long> journalIds,
            Integer year,
            String polishOutputPath,
            String englishOutputPath
    ) {
        List<Long> sortedJournalIds = journalIds.stream()
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();
        Path polishTarget = outputPath(polishOutputPath);
        Path englishTarget = outputPath(englishOutputPath);
        if (polishTarget.equals(englishTarget)) {
            throw new IllegalStateException("Polish and English survey report output paths must be different");
        }

        ExecutorService executor = reportExecutor();
        Path polishTemporary = null;
        Path englishTemporary = null;
        try {
            LanguageExportResult polishResult = exportLanguage(
                    sortedJournalIds,
                    year,
                    POLISH_LOCALE,
                    POLISH_LANGUAGE,
                    polishTarget,
                    executor
            );
            polishTemporary = polishResult.temporaryFile();
            LanguageExportResult englishResult = exportLanguage(
                    sortedJournalIds,
                    year,
                    Locale.ENGLISH,
                    ENGLISH_LANGUAGE,
                    englishTarget,
                    executor
            );
            englishTemporary = englishResult.temporaryFile();

            if (polishResult.includedJournals() != englishResult.includedJournals()) {
                log.warn(
                        "Survey report language variants include a different number of journals: pl={}, en={}",
                        polishResult.includedJournals(),
                        englishResult.includedJournals()
                );
            }

            replace(polishTemporary, polishTarget);
            polishTemporary = null;
            replace(englishTemporary, englishTarget);
            englishTemporary = null;
            return new SurveyReportExportSummary(sortedJournalIds.size(), polishResult.includedJournals());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write survey XLSX reports", e);
        } finally {
            executor.shutdownNow();
            deleteTemporary(polishTemporary);
            deleteTemporary(englishTemporary);
        }
    }

    private LanguageExportResult exportLanguage(
            List<Long> journalIds,
            Integer year,
            Locale locale,
            String language,
            Path target,
            ExecutorService executor
    ) throws IOException {
        LanguageReport languageReport = assembleLanguageReport(journalIds, year, locale, executor);
        Path temporary = writeTemporary(target, languageReport.report(), year, language);
        return new LanguageExportResult(temporary, languageReport.includedJournals());
    }

    private LanguageReport assembleLanguageReport(
            List<Long> journalIds,
            Integer year,
            Locale locale,
            ExecutorService executor
    ) {
        SurveyReportDto report = surveyReportDtoAssembler.emptyDto();
        Iterator<Long> journalIterator = journalIds.iterator();
        Deque<JournalReportTask> tasks = new ArrayDeque<>(REPORT_PARALLELISM);
        while (journalIterator.hasNext() && tasks.size() < REPORT_PARALLELISM) {
            tasks.addLast(submit(journalIterator.next(), year, locale, executor));
        }

        int includedJournals = 0;
        while (!tasks.isEmpty()) {
            JournalReportTask task = tasks.removeFirst();
            Optional<SurveyReportDto> journalReport = await(task);
            if (journalReport.isPresent()) {
                surveyReportDtoAssembler.merge(report, journalReport.get());
                includedJournals++;
            }
            if (journalIterator.hasNext()) {
                tasks.addLast(submit(journalIterator.next(), year, locale, executor));
            }
        }
        return new LanguageReport(report, includedJournals);
    }

    private JournalReportTask submit(
            Long journalId,
            Integer year,
            Locale locale,
            ExecutorService executor
    ) {
        return new JournalReportTask(
                journalId,
                executor.submit(() -> surveyReportJournalService.assemble(journalId, year, locale))
        );
    }

    private ExecutorService reportExecutor() {
        AtomicInteger threadNumber = new AtomicInteger();
        return Executors.newFixedThreadPool(REPORT_PARALLELISM, task ->
                new Thread(task, "survey-report-" + threadNumber.incrementAndGet())
        );
    }

    private Optional<SurveyReportDto> await(JournalReportTask task) {
        try {
            return task.future().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Survey report generation was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException(
                    "Cannot assemble survey report for journal " + task.journalId(),
                    cause
            );
        }
    }

    private Path writeTemporary(
            Path target,
            SurveyReportDto report,
            Integer year,
            String language
    ) throws IOException {
        Path temporary = createTemporary(target);
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                surveyReportGenerator.generate(report, year, language, output);
            }
            return temporary;
        } catch (IOException | RuntimeException | Error e) {
            deleteTemporary(temporary);
            throw e;
        }
    }

    private Path outputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalStateException("Survey report output path must not be empty");
        }
        return Path.of(outputPath).toAbsolutePath().normalize();
    }

    private Path createTemporary(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.createTempFile(parent, target.getFileName().toString(), ".tmp");
    }

    private void replace(Path temporary, Path target) throws IOException {
        try {
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException e) {
            log.warn("Cannot delete temporary survey report file {}", temporary, e);
        }
    }

    public record SurveyReportExportSummary(int processedJournals, int includedJournals) {
    }

    private record JournalReportTask(
            Long journalId,
            Future<Optional<SurveyReportDto>> future
    ) {
    }

    private record LanguageReport(SurveyReportDto report, int includedJournals) {
    }

    private record LanguageExportResult(Path temporaryFile, int includedJournals) {
    }
}
