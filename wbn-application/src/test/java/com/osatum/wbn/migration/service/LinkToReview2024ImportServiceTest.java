package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkToReview2024ImportServiceTest {
    private final JournalRepository journalRepository = mock();
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final LinkToReview2024ImportService service = new LinkToReview2024ImportService(
            journalRepository,
            questionnaireRepository
    );

    @TempDir
    Path tempDir;

    @Test
    void readsLinksAndSkipsRowsWithoutLink() throws Exception {
        Path file = tempDir.resolve("link-to-review-2024.xlsx");
        createLinkToReviewFile(file);

        List<LinkToReview2024ImportService.LinkToReview2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().link()).isEqualTo("https://example.org/review-updated");
        assertThat(records.getLast().journalId()).isEqualTo(456L);
        assertThat(records.getLast().link()).isEqualTo("https://example.org/review-456");
    }

    @Test
    void updatesLinkIn2024QuestionnaireStandards() {
        Journal journal = journal(123L);
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setYear(2024);
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateLinkToReviewProcedure(record(123L, " https://example.org/review "));

        SurveyData surveyData = questionnaire.getSurveyData();
        assertThat(surveyData).isNotNull();
        assertThat(surveyData.getSurvey()).isNotNull();
        assertThat(surveyData.getSurvey().getStandards().getLinkToReviewProcedure())
                .isEqualTo("https://example.org/review");
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void skipsRecordForMissingJournal() {
        when(journalRepository.findById(123L)).thenReturn(Optional.empty());

        service.updateLinkToReviewProcedure(record(123L, "https://example.org/review"));

        verify(questionnaireRepository, never()).findByYearAndJournalId(2024, 123L);
        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsRecordWithout2024Questionnaire() {
        Journal journal = journal(123L);
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of());

        service.updateLinkToReviewProcedure(record(123L, "https://example.org/review"));

        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createLinkToReviewFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("links");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("link");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue("https://example.org/review");

            var withoutLink = sheet.createRow(2);
            withoutLink.createCell(0).setCellValue(789);

            var updated = sheet.createRow(3);
            updated.createCell(0).setCellValue("123");
            updated.createCell(1).setCellValue(" https://example.org/review-updated ");

            var last = sheet.createRow(4);
            last.createCell(0).setCellValue(456);
            last.createCell(1).setCellValue("https://example.org/review-456");

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private Journal journal(Long id) {
        Journal journal = new Journal();
        journal.setId(id);
        return journal;
    }

    private LinkToReview2024ImportService.LinkToReview2024Record record(Long journalId, String link) {
        return new LinkToReview2024ImportService.LinkToReview2024Record(
                journalId,
                link,
                "link-to-review-2024.xlsx",
                2
        );
    }
}
