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

class WosIndicatorImportServiceTest {
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final WosIndicatorImportService service = new WosIndicatorImportService(questionnaireRepository);

    @TempDir
    Path tempDir;

    @Test
    void readsWosIndicatorsFromExcelFile() throws Exception {
        Path file = tempDir.resolve("wos.xlsx");
        createWosFile(file);

        List<WosIndicatorImportService.WosIndicatorRecord> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().impactFactor()).isEqualTo(1.25);
        assertThat(records.getFirst().articleInfluence()).isEqualTo(0.75);
        assertThat(records.getLast().journalId()).isEqualTo(456L);
        assertThat(records.getLast().impactFactor()).isEqualTo(2.5);
        assertThat(records.getLast().articleInfluence()).isEqualTo(1.5);
    }

    @Test
    void updatesPreviousIndicatorsForQuestionnaireSurvey() {
        Questionnaire questionnaire = questionnaire(123L);
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updatePrevious(new WosIndicatorImportService.WosIndicatorRecord(123L, 3.4, 1.2, "wos.xlsx", 2));

        var quality = questionnaire.getSurveyData().getSurvey().getDigitalization().getScientificQuality();
        assertThat(quality.getIndicatorIF().getPrevious()).isEqualTo(3.4);
        assertThat(quality.getIndicatorIF().getCurrent()).isNull();
        assertThat(quality.getIndicatorAI().getPrevious()).isEqualTo(1.2);
        assertThat(quality.getIndicatorAI().getCurrent()).isNull();
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void updatesCurrentIndicatorsForQuestionnaireSurvey() {
        Questionnaire questionnaire = questionnaire(123L);
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateCurrent(new WosIndicatorImportService.WosIndicatorRecord(123L, 4.1, 1.8, "wos.xlsx", 2));

        var quality = questionnaire.getSurveyData().getSurvey().getDigitalization().getScientificQuality();
        assertThat(quality.getIndicatorIF().getPrevious()).isNull();
        assertThat(quality.getIndicatorIF().getCurrent()).isEqualTo(4.1);
        assertThat(quality.getIndicatorAI().getPrevious()).isNull();
        assertThat(quality.getIndicatorAI().getCurrent()).isEqualTo(1.8);
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void skipsRecordWhenQuestionnaireDoesNotExist() {
        service.updatePrevious(new WosIndicatorImportService.WosIndicatorRecord(999L, 3.4, 1.2, "wos.xlsx", 2));

        verify(questionnaireRepository).findByYearAndJournalId(2024, 999L);
        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createWosFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("wos");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("IF");
            header.createCell(2).setCellValue("Article, Influence");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue(1.25);
            first.createCell(2).setCellValue(0.75);

            var second = sheet.createRow(2);
            second.createCell(0).setCellValue("456");
            second.createCell(1).setCellValue("2,5");
            second.createCell(2).setCellValue("1,5");

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
