package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopusIndicatorImportServiceTest {
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final ScopusIndicatorImportService service = new ScopusIndicatorImportService(questionnaireRepository);

    @TempDir
    Path tempDir;

    @Test
    void readsScopusIndicatorsFromExcelFile() throws Exception {
        Path file = tempDir.resolve("scopus.xlsx");
        createScopusFile(file);

        List<ScopusIndicatorImportService.ScopusIndicatorRecord> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().sjr()).isEqualTo(0.25);
        assertThat(records.getFirst().totalCites()).isEqualTo(321.0);
        assertThat(records.getFirst().citeScore()).isEqualTo(1.5);
        assertThat(records.getFirst().snip()).isEqualTo(0.75);
        assertThat(records.getLast().journalId()).isEqualTo(456L);
        assertThat(records.getLast().sjr()).isEqualTo(0.5);
        assertThat(records.getLast().totalCites()).isEqualTo(1234.0);
        assertThat(records.getLast().citeScore()).isEqualTo(2.5);
        assertThat(records.getLast().snip()).isEqualTo(1.5);
    }

    @Test
    void updatesPreviousIndicatorsForQuestionnaireSurvey() {
        Questionnaire questionnaire = questionnaire(123L);
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updatePrevious(new ScopusIndicatorImportService.ScopusIndicatorRecord(123L, 0.4, 50.0, 2.1, 0.8, "scopus.xlsx", 2));

        var quality = questionnaire.getSurveyData().getSurvey().getDigitalization().getScientificQuality();
        assertThat(quality.getIndicatorSJR().getPrevious()).isEqualTo(0.4);
        assertThat(quality.getIndicatorSJR().getCurrent()).isNull();
        assertThat(quality.getIndicatorTC().getPrevious()).isEqualTo(50.0);
        assertThat(quality.getIndicatorTC().getCurrent()).isNull();
        assertThat(quality.getIndicatorCS().getPrevious()).isEqualTo(2.1);
        assertThat(quality.getIndicatorCS().getCurrent()).isNull();
        assertThat(quality.getIndicatorSNIP().getPrevious()).isEqualTo(0.8);
        assertThat(quality.getIndicatorSNIP().getCurrent()).isNull();
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void updatesCurrentIndicatorsForQuestionnaireSurvey() {
        Questionnaire questionnaire = questionnaire(123L);
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateCurrent(new ScopusIndicatorImportService.ScopusIndicatorRecord(123L, 0.6, 70.0, 2.8, 1.2, "scopus.xlsx", 2));

        var quality = questionnaire.getSurveyData().getSurvey().getDigitalization().getScientificQuality();
        assertThat(quality.getIndicatorSJR().getPrevious()).isNull();
        assertThat(quality.getIndicatorSJR().getCurrent()).isEqualTo(0.6);
        assertThat(quality.getIndicatorTC().getPrevious()).isNull();
        assertThat(quality.getIndicatorTC().getCurrent()).isEqualTo(70.0);
        assertThat(quality.getIndicatorCS().getPrevious()).isNull();
        assertThat(quality.getIndicatorCS().getCurrent()).isEqualTo(2.8);
        assertThat(quality.getIndicatorSNIP().getPrevious()).isNull();
        assertThat(quality.getIndicatorSNIP().getCurrent()).isEqualTo(1.2);
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void skipsRecordWhenQuestionnaireDoesNotExist() {
        service.updatePrevious(new ScopusIndicatorImportService.ScopusIndicatorRecord(999L, 0.4, 50.0, 2.1, 0.8, "scopus.xlsx", 2));

        verify(questionnaireRepository).findByYearAndJournalId(2024, 999L);
        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createScopusFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("scopus");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("SJR");
            header.createCell(2).setCellValue("Total Cites");
            header.createCell(3).setCellValue("CiteScore");
            header.createCell(4).setCellValue("SNIP");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue(0.25);
            first.createCell(2).setCellValue(321);
            first.createCell(3).setCellValue(1.5);
            first.createCell(4).setCellValue(0.75);

            var second = sheet.createRow(2);
            second.createCell(0).setCellValue("456");
            second.createCell(1).setCellValue("0,5");
            second.createCell(2).setCellValue("1 234");
            second.createCell(3).setCellValue("2,5");
            second.createCell(4).setCellValue("1,5");

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private Questionnaire questionnaire(Long journalId) {
        Journal journal = new Journal();
        journal.setId(journalId);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(new Survey());

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setYear(2024);
        questionnaire.setSurveyData(surveyData);
        return questionnaire;
    }
}
