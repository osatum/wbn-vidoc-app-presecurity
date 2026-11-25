package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Person;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Editorial2024ImportService.CountryValues;
import com.osatum.wbn.migration.service.Editorial2024ImportService.Editorial2024Record;
import com.osatum.wbn.migration.service.Editorial2024ImportService.InstitutionValues;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Editorial2024ImportServiceTest {
    private static final List<String> HEADERS = List.of(
            "journal_id", "questionnaire_id", "questionnaire_survey_id", "questionnaire_year", "source_row", "json_index",
            "name", "department", "street", "postCode", "city", "state", "country.code", "country.codeAlpha3",
            "country.nameEn", "country.namePl", "country.nameRu", "phone", "email", "www", "gicid", "type", "institutionRole"
    );

    @TempDir
    private Path tempDir;

    private final QuestionnaireSpringDataRepository questionnaireRepository = mock(QuestionnaireSpringDataRepository.class);
    private final Editorial2024ImportService service = new Editorial2024ImportService(questionnaireRepository);

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void readsReorderedHeadersGroupsNonadjacentRowsAndKeepsHistoricalNonconsecutiveIndices(String extension) throws Exception {
        List<String> headers = new ArrayList<>(HEADERS);
        Collections.reverse(headers);
        Path file = workbook(extension, headers, List.of(
                Map.of("journal_id", "123", "questionnaire_year", "2024", "json_index", "3", "name", "Second office"),
                Map.of("journal_id", "456", "questionnaire_year", "2024", "json_index", "1", "email", "office@example.org"),
                Map.of("journal_id", "123", "questionnaire_year", "2024", "json_index", "1", "name", "First office"),
                Map.of("journal_id", "789"),
                Map.of("journal_id", "123", "questionnaire_year", "2024")
        ));

        List<Editorial2024Record> records = service.readRecords(file.toString());

        assertThat(records).extracting(Editorial2024Record::journalId).containsExactly(123L, 456L, 789L);
        assertThat(records.getFirst().institutions()).extracting(InstitutionValues::name)
                .containsExactly("First office", "Second office");
        assertThat(records.get(1).institutions()).singleElement().satisfies(values -> {
            assertThat(values.name()).isNull();
            assertThat(values.email()).isEqualTo("office@example.org");
        });
        assertThat(records.get(2).institutions()).isEmpty();
        assertThat(records.getFirst().itemName()).isEqualTo("editorial." + extension + ":3:journal-123");
        verifyNoInteractions(questionnaireRepository);
    }

    @Test
    void preservesRepeatedInstitutionsWithDistinctIndicesAndNormalizesMissingFields() throws Exception {
        Path file = workbook("xlsx", HEADERS, List.of(
                Map.of("journal_id", "123", "json_index", "0", "email", "office@example.org", "name", " - ",
                        "department", "Brak danych", "street", "\u00A0"),
                Map.of("journal_id", "123", "json_index", "5", "email", "office@example.org")
        ));

        List<Editorial2024Record> records = service.readRecords(file.toString());

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.institutions()).hasSize(2).allSatisfy(values -> {
                assertThat(values.name()).isNull();
                assertThat(values.department()).isNull();
                assertThat(values.street()).isNull();
                assertThat(values.country()).isNull();
            });
            assertThat(record.institutions().getFirst()).isEqualTo(record.institutions().getLast());
        });
    }

    @Test
    void copiesAllInstitutionAndCountryFieldsAndPreservesTextualPostCodesAndPhones() throws Exception {
        Map<String, String> values = Map.ofEntries(
                Map.entry("journal_id", "123"), Map.entry("json_index", "3"), Map.entry("name", "Office"),
                Map.entry("department", "Department"), Map.entry("street", "Street 1"), Map.entry("postCode", "00154"),
                Map.entry("city", "Roma"), Map.entry("state", "Region"), Map.entry("country.code", "IT"),
                Map.entry("country.codeAlpha3", "ITA"), Map.entry("country.nameEn", "Italy"),
                Map.entry("country.namePl", "Wlochy"), Map.entry("country.nameRu", "Italia"),
                Map.entry("phone", "+39 0123 456"), Map.entry("email", "office@example.org"),
                Map.entry("www", "https://example.org"), Map.entry("gicid", "00123"), Map.entry("type", "University"),
                Map.entry("institutionRole", "P"), Map.entry("questionnaire_id", "999"),
                Map.entry("questionnaire_survey_id", "998")
        );
        Editorial2024Record record = service.readRecords(workbook("xlsx", HEADERS, List.of(values)).toString()).getFirst();
        Questionnaire questionnaire = questionnaire();
        Person editor = new Person();
        editor.setLastName("Existing editor");
        questionnaire.getSurveyData().getSurvey().getEditorial().setEditors(List.of(editor));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateEditorial(record);

        assertThat(questionnaire.getSurveyData().getSurvey().getEditorial().getRedactionInstitutions()).singleElement()
                .satisfies(institution -> {
                    assertThat(institution.getInstitutionRole()).isEqualTo('E');
                    assertThat(institution.getInstitution()).extracting("name", "department", "street", "postCode", "city", "state",
                                    "phone", "email", "www", "gicid", "type")
                            .containsExactly("Office", "Department", "Street 1", "00154", "Roma", "Region", "+39 0123 456",
                                    "office@example.org", "https://example.org", "00123", "University");
                    assertThat(institution.getInstitution().getCountry()).extracting("code", "codeAlpha3", "nameEn", "namePl", "nameRu")
                            .containsExactly("IT", "ITA", "Italy", "Wlochy", "Italia");
                });
        assertThat(questionnaire.getSurveyData().getSurvey().getEditorial().getEditors()).containsExactly(editor);
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void importsNamelessInstitutionAndReplacesTheCompleteListWithNullsForMissingValues() throws Exception {
        Questionnaire questionnaire = questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        Editorial2024Record record = service.readRecords(workbook("xlsx", HEADERS, List.of(
                Map.of("journal_id", "123", "json_index", "3", "email", "office@example.org")
        )).toString()).getFirst();

        service.updateEditorial(record);
        service.updateEditorial(record);

        assertThat(questionnaire.getSurveyData().getSurvey().getEditorial().getRedactionInstitutions()).singleElement()
                .satisfies(institution -> {
                    assertThat(institution.getInstitutionRole()).isEqualTo('E');
                    assertThat(institution.getInstitution()).extracting("name", "department", "street", "postCode", "city", "state",
                                    "country", "phone", "www", "gicid", "type")
                            .containsOnlyNulls();
                    assertThat(institution.getInstitution().getEmail()).isEqualTo("office@example.org");
                });
    }

    @Test
    void clearsInstitutionsWhenOnlyJournalIdAndTechnicalMetadataArePresent() throws Exception {
        Questionnaire questionnaire = questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        Editorial2024Record record = service.readRecords(workbook("xlsx", HEADERS, List.of(
                Map.of("journal_id", "123", "questionnaire_id", "999", "institutionRole", "E")
        )).toString()).getFirst();

        service.updateEditorial(record);

        assertThat(questionnaire.getSurveyData().getSurvey().getEditorial().getRedactionInstitutions()).isEmpty();
    }

    @Test
    void initializesMissingSurveyObjects() {
        Questionnaire questionnaire = new Questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        Editorial2024Record record = new Editorial2024Record(123L, List.of(
                new InstitutionValues(null, null, null, null, null, null,
                        new CountryValues("PL", null, null, null, null), null, null, null, null, null)
        ), "editorial.xlsx", 2);

        service.updateEditorial(record);
        questionnaire.getSurveyData().setSurvey(null);
        service.updateEditorial(record);
        questionnaire.getSurveyData().getSurvey().setEditorial(null);
        service.updateEditorial(record);

        assertThat(questionnaire.getSurveyData().getSurvey().getEditorial().getRedactionInstitutions()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void rejectsMissingOrAmbiguousQuestionnaireBeforeChangingData(int count) {
        Questionnaire questionnaire = questionnaire();
        String timestamp = questionnaire.getSurveyData().getSurvey().getTimestamp();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L))
                .thenReturn(Collections.nCopies(count, questionnaire));

        assertThatThrownBy(() -> service.updateEditorial(new Editorial2024Record(123L, List.of(), "editorial.xlsx", 2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("found " + count);

        assertThat(questionnaire.getSurveyData().getSurvey().getTimestamp()).isEqualTo(timestamp);
        verify(questionnaireRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "abc", ""})
    void rejectsInvalidJournalIds(String journalId) throws Exception {
        Path file = workbook("xlsx", HEADERS, List.of(Map.of("journal_id", journalId, "email", "office@example.org")));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("editorial.xlsx:3: Invalid journal_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2023", "2025", "2024.5", "abc"})
    void rejectsOtherYearsAndInvalidYearEvenOnDeletionRows(String year) throws Exception {
        Path file = workbook("xlsx", HEADERS, List.of(Map.of("journal_id", "123", "questionnaire_year", year)));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("editorial.xlsx:3:");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.5", "abc", ""})
    void rejectsInvalidHistoricalIndexOnPopulatedRows(String index) throws Exception {
        Path file = workbook("xlsx", HEADERS, List.of(Map.of("journal_id", "123", "json_index", index, "email", "office@example.org")));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("editorial.xlsx:3: Invalid json_index");
    }

    @Test
    void rejectsDuplicateHistoricalIndex() throws Exception {
        Map<String, String> values = Map.of("journal_id", "123", "json_index", "3", "email", "office@example.org");
        Path file = workbook("xlsx", HEADERS, List.of(values, values));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("editorial.xlsx:4: Duplicate json_index 3");
    }

    @Test
    void rejectsMissingAndDuplicateHeadersAndMissingFile() throws Exception {
        List<String> headers = new ArrayList<>(HEADERS);
        headers.remove("email");
        Path file = workbook("xlsx", headers, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Missing editorial 2024 column");
        headers.add("name");
        workbook("xlsx", headers, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Duplicate editorial 2024 header");
        workbook("xlsx", List.of("unrelated"), List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Missing editorial 2024 header");
        assertThatThrownBy(() -> service.readRecords(tempDir.resolve("missing.xlsx").toString())).hasMessageContaining("Cannot find");
    }

    private Questionnaire questionnaire() {
        Questionnaire questionnaire = new Questionnaire();
        SurveyData surveyData = new SurveyData();
        Survey survey = new Survey();
        survey.setTimestamp("unchanged");
        surveyData.setSurvey(survey);
        questionnaire.setSurveyData(surveyData);
        return questionnaire;
    }

    private Path workbook(String extension, List<String> headers, List<Map<String, String>> records) throws Exception {
        Path file = tempDir.resolve("editorial." + extension);
        try (Workbook workbook = "xlsx".equals(extension) ? new XSSFWorkbook() : new HSSFWorkbook()) {
            var sheet = workbook.createSheet("Aktualizacja");
            sheet.createRow(0).createCell(0).setCellValue("Editorial addresses");
            var header = sheet.createRow(1);
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(headers.get(columnIndex));
            }
            for (int rowIndex = 0; rowIndex < records.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex + 2);
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(records.get(rowIndex).getOrDefault(headers.get(columnIndex), ""));
                }
            }
            workbook.createSheet("Kontrola adresow").createRow(0).createCell(0).setCellValue("Ignored control worksheet");
            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
        return file;
    }
}
