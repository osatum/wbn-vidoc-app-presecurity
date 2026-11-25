package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalKind;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalKind2024ImportServiceTest {
    private final JournalRepository journalRepository = mock();
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final JournalKind2024ImportService service = new JournalKind2024ImportService(
            journalRepository,
            questionnaireRepository
    );

    @TempDir
    Path tempDir;

    @Test
    void readsEnumNamesAndCodesAndSkipsRowsWithoutKind() throws Exception {
        Path file = tempDir.resolve("kind-2024.xlsx");
        createKindFile(file);

        List<JournalKind2024ImportService.JournalKind2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(3);
        assertThat(records.get(0).journalId()).isEqualTo(123L);
        assertThat(records.get(0).kind()).isEqualTo(JournalKind.SCIENTIFIC);
        assertThat(records.get(1).journalId()).isEqualTo(456L);
        assertThat(records.get(1).kind()).isEqualTo(JournalKind.POPULAR_SCIENCE);
        assertThat(records.get(2).journalId()).isEqualTo(789L);
        assertThat(records.get(2).kind()).isEqualTo(JournalKind.POPULAR_SCIENCE);
    }

    @Test
    void rejectsInvalidKindValue() throws Exception {
        Path file = tempDir.resolve("invalid-kind-2024.xlsx");
        createInvalidKindFile(file);

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid kind value: unsupported");
    }

    @Test
    void updatesKindIn2024QuestionnaireSupplementaryData() {
        Journal journal = journal(123L);
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setYear(2024);
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateKind(record(123L, JournalKind.OTHER));

        SurveyData surveyData = questionnaire.getSurveyData();
        assertThat(surveyData).isNotNull();
        assertThat(surveyData.getSurvey()).isNotNull();
        assertThat(surveyData.getSurvey().getSupplementaryData().getKind()).isEqualTo(JournalKind.OTHER);
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void skipsRecordForMissingJournal() {
        when(journalRepository.findById(123L)).thenReturn(Optional.empty());

        service.updateKind(record(123L, JournalKind.SCIENTIFIC));

        verify(questionnaireRepository, never()).findByYearAndJournalId(2024, 123L);
        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void skipsRecordWithout2024Questionnaire() {
        Journal journal = journal(123L);
        when(journalRepository.findById(123L)).thenReturn(Optional.of(journal));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of());

        service.updateKind(record(123L, JournalKind.SCIENTIFIC));

        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createKindFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("kinds");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("kind");

            var scientific = sheet.createRow(1);
            scientific.createCell(0).setCellValue(123);
            scientific.createCell(1).setCellValue("SCIENTIFIC");

            var popularScienceCode = sheet.createRow(2);
            popularScienceCode.createCell(0).setCellValue(456);
            popularScienceCode.createCell(1).setCellValue("p");

            var popularScienceName = sheet.createRow(3);
            popularScienceName.createCell(0).setCellValue(789);
            popularScienceName.createCell(1).setCellValue("popular-science");

            var withoutKind = sheet.createRow(4);
            withoutKind.createCell(0).setCellValue(999);

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private void createInvalidKindFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("kinds");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("kind");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(123);
            row.createCell(1).setCellValue("unsupported");

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

    private JournalKind2024ImportService.JournalKind2024Record record(Long journalId, JournalKind kind) {
        return new JournalKind2024ImportService.JournalKind2024Record(
                journalId,
                kind,
                "kind-2024.xlsx",
                2
        );
    }
}
