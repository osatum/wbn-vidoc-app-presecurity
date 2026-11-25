package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.BooleanDouble;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InfoReview2024ImportServiceTest {
    private final JournalRepository journalRepository = mock();
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final InfoReview2024ImportService service = new InfoReview2024ImportService(
            journalRepository,
            questionnaireRepository
    );

    @TempDir
    Path tempDir;

    @Test
    void readsTakNieAndNumericValuesAndKeepsLastRecordForJournal() throws Exception {
        Path file = tempDir.resolve("info-review-2024.xlsx");
        createInfoReviewFile(file);

        List<InfoReview2024ImportService.InfoReview2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.get(0).journalId()).isEqualTo(123L);
        assertThat(records.get(0).confirm()).isFalse();
        assertThat(records.get(0).value()).isEqualTo(50.0);
        assertThat(records.get(1).journalId()).isEqualTo(456L);
        assertThat(records.get(1).confirm()).isFalse();
        assertThat(records.get(1).value()).isEqualTo(12.5);
    }

    @Test
    void rejectsInvalidConfirmValue() throws Exception {
        Path file = tempDir.resolve("invalid-confirm-info-review-2024.xlsx");
        createInvalidConfirmFile(file);

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid confirm value: yes");
    }

    @Test
    void rejectsMissingNumericValue() throws Exception {
        Path file = tempDir.resolve("missing-value-info-review-2024.xlsx");
        createMissingValueFile(file);

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing numeric value");
    }

    @Test
    void updatesDoubleBlindReviewIn2024Questionnaire() {
        Journal journal = journal(123L);
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setYear(2024);
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateDoubleBlindReview(record(123L, true, 42.5));

        SurveyData surveyData = questionnaire.getSurveyData();
        assertThat(surveyData).isNotNull();
        assertThat(surveyData.getSurvey()).isNotNull();
        assertThat(surveyData.getSurvey().getStandards().getInfoReviewProcedure().getDoubleBlindReview().getConfirm())
                .isTrue();
        assertThat(surveyData.getSurvey().getStandards().getInfoReviewProcedure().getDoubleBlindReview().getValue())
                .isEqualTo(42.5);
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void preservesOtherReviewProcedureValues() {
        Journal journal = journal(123L);
        Questionnaire questionnaire = new Questionnaire();
        SurveyData surveyData = new SurveyData();
        Survey survey = new Survey();
        survey.getStandards().getInfoReviewProcedure().setSingleBlindReview(new BooleanDouble(true, 20.0));
        surveyData.setSurvey(survey);
        questionnaire.setSurveyData(surveyData);
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateDoubleBlindReview(record(123L, false, 0.0));

        assertThat(survey.getStandards().getInfoReviewProcedure().getSingleBlindReview())
                .extracting(BooleanDouble::getConfirm, BooleanDouble::getValue)
                .containsExactly(true, 20.0);
        assertThat(survey.getStandards().getInfoReviewProcedure().getDoubleBlindReview())
                .extracting(BooleanDouble::getConfirm, BooleanDouble::getValue)
                .containsExactly(false, 0.0);
    }

    @Test
    void skipsRecordForMissingJournal() {
        when(journalRepository.findById(123L)).thenReturn(Optional.empty());

        service.updateDoubleBlindReview(record(123L, true, 10.0));

        verify(questionnaireRepository, never()).findByYearAndJournalId(2024, 123L);
        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsRecordWithout2024Questionnaire() {
        Journal journal = journal(123L);
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of());

        service.updateDoubleBlindReview(record(123L, true, 10.0));

        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createInfoReviewFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("info-review");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("confirm");
            header.createCell(2).setCellValue("value");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue("Tak");
            first.createCell(2).setCellValue(40);

            var second = sheet.createRow(2);
            second.createCell(0).setCellValue(456);
            second.createCell(1).setCellValue(" NIE ");
            second.createCell(2).setCellValue("12,5");

            var duplicate = sheet.createRow(3);
            duplicate.createCell(0).setCellValue(123);
            duplicate.createCell(1).setCellValue("Nie");
            duplicate.createCell(2).setCellValue(50);

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private void createInvalidConfirmFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("info-review");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("confirm");
            header.createCell(2).setCellValue("value");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(123);
            row.createCell(1).setCellValue("yes");
            row.createCell(2).setCellValue(50);

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private void createMissingValueFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("info-review");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("confirm");
            header.createCell(2).setCellValue("value");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(123);
            row.createCell(1).setCellValue("Tak");

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

    private InfoReview2024ImportService.InfoReview2024Record record(Long journalId, boolean confirm, Double value) {
        return new InfoReview2024ImportService.InfoReview2024Record(
                journalId,
                confirm,
                value,
                "info-review-2024.xlsx",
                2
        );
    }
}
