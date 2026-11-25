package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Cooperation;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Cooperation2024ImportService.Cooperation2024Record;
import com.osatum.wbn.migration.service.Cooperation2024ImportService.MetricValues;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Cooperation2024ImportServiceTest {
    @TempDir
    private Path tempDir;

    private final QuestionnaireSpringDataRepository questionnaireRepository = mock(QuestionnaireSpringDataRepository.class);
    private final Cooperation2024ImportService service = new Cooperation2024ImportService(questionnaireRepository);

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void readsMetricsFromFourthRowHeaderAndPreservesEmptyRecords(String extension) throws Exception {
        Path file = tempDir.resolve("cooperation." + extension);
        writeWorkbook(file, headers(), List.of(
                new Object[]{123, 25, 36, 16, 100, 100, "17,4", null, null, null},
                new Object[]{456, 0, 0, 0, "brak danych", "brak danych", "brak danych", 19, 95, 0},
                new Object[]{789, null, null, null, null, null, null, null, null, null}
        ));

        List<Cooperation2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(3);
        assertThat(records.get(0).journalId()).isEqualTo(123L);
        assertThat(records.get(0).authors()).isEqualTo(new MetricValues(25, 36.0, 16.0));
        assertThat(records.get(0).reviewers()).isEqualTo(new MetricValues(100, 100.0, 17.4));
        assertThat(records.get(0).directors()).isEqualTo(emptyMetric());
        assertThat(records.get(0).rowNumber()).isEqualTo(5);
        assertThat(records.get(0).itemName()).isEqualTo("cooperation." + extension + ":5:journal-123");
        assertThat(records.get(1).authors()).isEqualTo(new MetricValues(0, 0.0, 0.0));
        assertThat(records.get(1).reviewers()).isEqualTo(emptyMetric());
        assertThat(records.get(1).directors()).isEqualTo(new MetricValues(19, 95.0, 0.0));
        assertThat(records.get(2).journalId()).isEqualTo(789L);
        assertThat(records.get(2).authors()).isEqualTo(emptyMetric());
        assertThat(records.get(2).reviewers()).isEqualTo(emptyMetric());
        assertThat(records.get(2).directors()).isEqualTo(emptyMetric());
    }

    @Test
    void mapsReorderedColumnsByTargetPaths() throws Exception {
        Path file = tempDir.resolve("reordered.xlsx");
        List<String> headers = new ArrayList<>(headers());
        Collections.reverse(headers);
        writeWorkbook(file, headers, Collections.singletonList(
                new Object[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 123}
        ));

        Cooperation2024Record record = service.readRecords(file.toString()).getFirst();

        assertThat(record.journalId()).isEqualTo(123L);
        assertThat(record.authors()).isEqualTo(new MetricValues(90, 80.0, 70.0));
        assertThat(record.reviewers()).isEqualTo(new MetricValues(60, 50.0, 40.0));
        assertThat(record.directors()).isEqualTo(new MetricValues(30, 20.0, 10.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2.5", "2147483648", "NaN"})
    void rejectsInvalidResearcherCount(String value) throws Exception {
        Path file = tempDir.resolve("invalid-count.xlsx");
        writeWorkbook(file, headers(), Collections.singletonList(
                new Object[]{123, value, null, null, null, null, null, null, null, null}
        ));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid-count.xlsx:5:")
                .hasMessageContaining("Invalid unique researcher count");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "100.1", "NaN", "Infinity"})
    void rejectsInvalidPercentage(String value) throws Exception {
        Path file = tempDir.resolve("invalid-percentage.xlsx");
        writeWorkbook(file, headers(), Collections.singletonList(
                new Object[]{123, null, value, null, null, null, null, null, null, null}
        ));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid affiliation percentage");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "9223372036854775808", "brak danych", ""})
    void rejectsInvalidOrMissingJournalId(String value) throws Exception {
        Path file = tempDir.resolve("invalid-id.xlsx");
        writeWorkbook(file, headers(), Collections.singletonList(
                new Object[]{value, 1, null, null, null, null, null, null, null, null}
        ));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal.id");
    }

    @Test
    void rejectsDuplicateJournalIds() throws Exception {
        Path file = tempDir.resolve("duplicate.xlsx");
        writeWorkbook(file, headers(), List.of(
                new Object[]{123, null, null, null, null, null, null, null, null, null},
                new Object[]{123, 1, 0, 0, null, null, null, null, null, null}
        ));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate.xlsx:6: Duplicate journal.id: 123");
    }

    @Test
    void rejectsMissingColumn() throws Exception {
        Path file = tempDir.resolve("missing-column.xlsx");
        writeWorkbook(file, headers().subList(0, 9), List.of());

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing cooperation 2024 column")
                .hasMessageContaining("directorsMetric.foreignAffiliationPercentage");
    }

    @Test
    void rejectsDuplicateColumn() throws Exception {
        Path file = tempDir.resolve("duplicate-column.xlsx");
        List<String> headers = new ArrayList<>(headers());
        headers.set(9, headers.get(8));
        writeWorkbook(file, headers, List.of());

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate cooperation 2024 header");
    }

    @Test
    void rejectsMissingHeader() throws Exception {
        Path file = tempDir.resolve("missing-header.xlsx");
        try (Workbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(file)) {
            workbook.createSheet("Kooperacja");
            workbook.write(output);
        }

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing cooperation 2024 header in row 4");
    }

    @Test
    void clearsExistingFieldsAndPreservesOtherSurveyData() {
        Questionnaire questionnaire = new Questionnaire();
        SurveyData surveyData = new SurveyData();
        Survey survey = new Survey();
        survey.setTimestamp("original-timestamp");
        Cooperation cooperation = survey.getCooperation();
        ScientistMetric original = new ScientistMetric();
        original.setUnique(42);
        original.setExternalAffiliationPercentage(75.0);
        original.setForeignAffiliationPercentage(50.0);
        cooperation.setAuthorMetric(original);
        cooperation.setReviewerMetric(original);
        cooperation.setDirectorsMetric(original);
        cooperation.setTitleAndAbstractInAllArticles(true);
        cooperation.setReviewers("original-reviewers");
        Cooperator reviewer = new Cooperator();
        reviewer.setFullName("Reviewer");
        cooperation.getReviewersList().add(reviewer);
        Cooperator director = new Cooperator();
        director.setFullName("Director");
        cooperation.getDirectorsList().add(director);
        surveyData.setSurvey(survey);
        questionnaire.setSurveyData(surveyData);
        var originalStandards = survey.getStandards();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateCooperation(new Cooperation2024Record(
                123L, new MetricValues(null, 0.0, null), new MetricValues(0, 0.0, 0.0), emptyMetric(), "cooperation.xlsx", 5
        ));

        assertThat(cooperation.getAuthorMetric()).extracting(
                ScientistMetric::getUnique, ScientistMetric::getExternalAffiliationPercentage, ScientistMetric::getForeignAffiliationPercentage
        ).containsExactly(null, 0.0, null);
        assertThat(cooperation.getReviewerMetric()).extracting(
                ScientistMetric::getUnique, ScientistMetric::getExternalAffiliationPercentage, ScientistMetric::getForeignAffiliationPercentage
        ).containsExactly(0, 0.0, 0.0);
        assertThat(cooperation.getDirectorsMetric()).extracting(
                ScientistMetric::getUnique, ScientistMetric::getExternalAffiliationPercentage, ScientistMetric::getForeignAffiliationPercentage
        ).containsExactly(null, null, null);
        assertThat(cooperation.getReviewersList()).containsExactly(reviewer);
        assertThat(cooperation.getDirectorsList()).containsExactly(director);
        assertThat(cooperation.getTitleAndAbstractInAllArticles()).isTrue();
        assertThat(cooperation.getReviewers()).isEqualTo("original-reviewers");
        assertThat(survey.getTimestamp()).isEqualTo("original-timestamp");
        assertThat(survey.getStandards()).isSameAs(originalStandards);
        verify(questionnaireRepository).save(questionnaire);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void createsMissingSurveyObjects(int existingObjects) {
        Questionnaire questionnaire = new Questionnaire();
        if (existingObjects >= 1) {
            questionnaire.setSurveyData(new SurveyData());
        }
        if (existingObjects >= 2) {
            Survey survey = new Survey();
            survey.setCooperation(null);
            questionnaire.getSurveyData().setSurvey(survey);
        }
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateCooperation(record());

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getAuthorMetric().getUnique()).isNull();
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void rejectsMissing2024Questionnaire() {
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateCooperation(record()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected one 2024 questionnaire for journal 123, found 0");

        verify(questionnaireRepository, never()).save(any());
    }

    @Test
    void rejectsAmbiguous2024Questionnaire() {
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L))
                .thenReturn(List.of(new Questionnaire(), new Questionnaire()));

        assertThatThrownBy(() -> service.updateCooperation(record()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected one 2024 questionnaire for journal 123, found 2");

        verify(questionnaireRepository, never()).save(any());
    }

    private Cooperation2024Record record() {
        return new Cooperation2024Record(123L, emptyMetric(), emptyMetric(), emptyMetric(), "cooperation.xlsx", 5);
    }

    private MetricValues emptyMetric() {
        return new MetricValues(null, null, null);
    }

    private List<String> headers() {
        return List.of(
                "journal.id",
                "surveyData.survey.cooperation.authorMetric.unique",
                "surveyData.survey.cooperation.authorMetric.externalAffiliationPercentage",
                "surveyData.survey.cooperation.authorMetric.foreignAffiliationPercentage",
                "surveyData.survey.cooperation.reviewerMetric.unique",
                "surveyData.survey.cooperation.reviewerMetric.externalAffiliationPercentage",
                "surveyData.survey.cooperation.reviewerMetric.foreignAffiliationPercentage",
                "surveyData.survey.cooperation.directorsMetric.unique",
                "surveyData.survey.cooperation.directorsMetric.externalAffiliationPercentage",
                "surveyData.survey.cooperation.directorsMetric.foreignAffiliationPercentage"
        );
    }

    private void writeWorkbook(Path file, List<String> headers, List<Object[]> records) throws Exception {
        try (Workbook workbook = file.toString().endsWith(".xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Kooperacja");
            sheet.createRow(0).createCell(0).setCellValue("Questionnaire.surveyData.survey.cooperation");
            sheet.createRow(2).createCell(1).setCellValue("authorMetric");
            var header = sheet.createRow(3);
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(headers.get(columnIndex));
            }
            for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
                var row = sheet.createRow(recordIndex + 4);
                Object[] values = records.get(recordIndex);
                for (int columnIndex = 0; columnIndex < values.length; columnIndex++) {
                    Object value = values[columnIndex];
                    if (value instanceof Number number) {
                        row.createCell(columnIndex).setCellValue(number.doubleValue());
                    } else if (value instanceof String text) {
                        row.createCell(columnIndex).setCellValue(text);
                    }
                }
            }
            try (OutputStream output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
    }
}
