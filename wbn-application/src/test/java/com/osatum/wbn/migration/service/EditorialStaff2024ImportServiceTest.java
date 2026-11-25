package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.PersonPosition;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.CountryValues;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.EditorValues;
import com.osatum.wbn.migration.service.EditorialStaff2024ImportService.EditorialStaff2024Record;
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
import java.util.LinkedHashMap;
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

class EditorialStaff2024ImportServiceTest {
    @TempDir
    private Path tempDir;

    private final QuestionnaireSpringDataRepository questionnaireRepository = mock(QuestionnaireSpringDataRepository.class);
    private final EditorialStaff2024ImportService service = new EditorialStaff2024ImportService(questionnaireRepository);

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void groupsNonadjacentRowsOrdersByTargetIndexAndImportsFlaggedRecords(String extension) throws Exception {
        Path file = tempDir.resolve("editors." + extension);
        Map<String, Object> last = row(123L, 1, "Jan", "Kowalski");
        last.put("sprawdz_podzial", true);
        last.put("questionnaire_id", "outdated-id");
        last.put("questionnaire_survey_id", "outdated-id");
        Map<String, Object> first = row(123L, 0, "Anna", "Nowak");
        first.put("sprawdz_podzial", true);
        first.put("personPosition", "EDITOR_IN_CHIEF");
        first.put("title", " Professor ");
        first.put("department", " University ");
        List<String> columns = new ArrayList<>(headers());
        Collections.reverse(columns);
        writeWorkbook(file, columns, List.of(last, row(456L, 0, "Other", "Editor"), first));

        List<EditorialStaff2024Record> records = service.readRecords(file.toString());

        assertThat(records).extracting(EditorialStaff2024Record::journalId).containsExactly(123L, 456L);
        assertThat(records.getFirst().editors()).containsExactly(
                new EditorValues("Anna", null, "Nowak", PersonPosition.EDITOR_IN_CHIEF, "Professor", "University", null, null, null),
                new EditorValues("Jan", null, "Kowalski", null, null, null, null, null, null)
        );
        assertThat(records.getFirst().itemName()).isEqualTo("editors." + extension + ":2:journal-123");
        verifyNoInteractions(questionnaireRepository);
    }

    @Test
    void readsAllCountryFieldsAndKeepsRepeatedPeopleAtDifferentIndices() throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        Map<String, Object> first = row(123L, 0, "Anna", "Nowak");
        first.put("middleName", " Maria ");
        first.put("email", "anna@example.org");
        first.put("gicid", "12345");
        first.put("country.code", "PL");
        first.put("country.codeAlpha3", "POL");
        first.put("country.nameEn", "Poland");
        first.put("country.namePl", "Polska");
        first.put("country.nameRu", "Польша");
        Map<String, Object> duplicate = new LinkedHashMap<>(first);
        duplicate.put("editor_index_docelowy", 1);
        writeWorkbook(file, headers(), List.of(first, duplicate));

        List<EditorValues> editors = service.readRecords(file.toString()).getFirst().editors();

        assertThat(editors).hasSize(2);
        assertThat(editors.getFirst()).isEqualTo(editors.getLast());
        assertThat(editors.getFirst()).isEqualTo(new EditorValues("Anna", "Maria", "Nowak", null, null, null,
                "anna@example.org", "12345", new CountryValues("PL", "POL", "Poland", "Polska", "Польша")));
    }

    @Test
    void convertsMissingMarkersToNullWithoutSkippingPartiallyNamedPeople() throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        Map<String, Object> values = row(123L, 0, "-", "Nowak");
        for (String name : List.of("middleName", "personPosition", "title", "department", "email", "gicid",
                "country.code", "country.codeAlpha3", "country.nameEn", "country.namePl", "country.nameRu")) {
            values.put(name, " BRAK\u00a0DANYCH ");
        }
        writeWorkbook(file, headers(), List.of(values));

        assertThat(service.readRecords(file.toString()).getFirst().editors())
                .containsExactly(new EditorValues(null, null, "Nowak", null, null, null, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void createsClearRecordWhenAllPersonFieldsAreEmpty(String extension) throws Exception {
        Path file = tempDir.resolve("editors." + extension);
        writeWorkbook(file, headers(), List.of(row(123L, 0, "", "")));

        assertThat(service.readRecords(file.toString())).singleElement().satisfies(record -> {
            assertThat(record.journalId()).isEqualTo(123L);
            assertThat(record.editors()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.5", "invalid"})
    void rejectsInvalidJournalIdsWithSourceRow(String identifier) throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        Map<String, Object> values = row(123L, 0, "Anna", "Nowak");
        values.put("journal_id", identifier);
        writeWorkbook(file, headers(), List.of(values));

        assertThatThrownBy(() -> service.readRecords(file.toString())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("editors.xlsx:2:").hasMessageContaining("journal_id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "2023", "2025", "2024.5", "invalid"})
    void rejectsRowsOutsideThe2024SurveyYear(String year) throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        Map<String, Object> values = row(123L, 0, "Anna", "Nowak");
        values.put("questionnaire_year", year);
        writeWorkbook(file, headers(), List.of(values));

        assertThatThrownBy(() -> service.readRecords(file.toString())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("editors.xlsx:2:").hasMessageContaining("questionnaire_year");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "0.5", "invalid", "2147483648"})
    void rejectsInvalidTargetIndices(String index) throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        Map<String, Object> values = row(123L, 0, "Anna", "Nowak");
        values.put("editor_index_docelowy", index);
        writeWorkbook(file, headers(), List.of(values));

        assertThatThrownBy(() -> service.readRecords(file.toString())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("editors.xlsx:2:").hasMessageContaining("editor_index_docelowy");
    }

    @Test
    void rejectsDuplicateOrNonconsecutiveTargetIndices() throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        writeWorkbook(file, headers(), List.of(row(123L, 0, "Anna", "Nowak"), row(123L, 0, "Jan", "Kowalski")));
        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .hasMessageContaining("editors.xlsx:3:").hasMessageContaining("Duplicate editor_index_docelowy");
        writeWorkbook(file, headers(), List.of(row(123L, 0, "Anna", "Nowak"), row(123L, 2, "Jan", "Kowalski")));
        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .hasMessageContaining("consecutive editor_index_docelowy");
    }

    @Test
    void rejectsInvalidRoleAndIncompleteHeaders() throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        Map<String, Object> values = row(123L, 0, "Anna", "Nowak");
        values.put("personPosition", "Unknown");
        writeWorkbook(file, headers(), List.of(values));
        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .hasMessageContaining("editors.xlsx:2:").hasMessageContaining("Invalid personPosition");
        List<String> columns = new ArrayList<>(headers());
        columns.remove("email");
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .hasMessageContaining("Missing editorial staff 2024 column").hasMessageContaining("email");
        columns = new ArrayList<>(headers());
        columns.add("email");
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Duplicate editorial staff 2024 header");
    }

    @Test
    void rejectsMissingFileOrHeader() throws Exception {
        Path file = tempDir.resolve("editors.xlsx");
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Cannot find editorial staff 2024 Excel file");
        writeWorkbook(file, List.of("Wrong header"), List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Missing editorial staff 2024 header");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void requiresExactlyOne2024Questionnaire(int count) {
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L))
                .thenReturn(count == 0 ? List.of() : List.of(new Questionnaire(), new Questionnaire()));

        assertThatThrownBy(() -> service.updateEditorialStaff(new EditorialStaff2024Record(123L, List.of(), "editors.xlsx", 2)))
                .hasMessageContaining("Expected one 2024 questionnaire").hasMessageContaining("found " + count);
        verify(questionnaireRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void initializesMissingSurveyObjectsAndMapsAllPersonFields(int missingLevel) {
        Questionnaire questionnaire = new Questionnaire();
        if (missingLevel > 0) {
            questionnaire.setSurveyData(new SurveyData());
        }
        if (missingLevel > 1) {
            questionnaire.getSurveyData().setSurvey(new Survey());
            questionnaire.getSurveyData().getSurvey().setEditorial(null);
        }
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        EditorValues editor = new EditorValues("Anna", "Maria", "Nowak", PersonPosition.EDITOR_IN_CHIEF, "Dr", "University",
                "anna@example.org", "12345", new CountryValues("PL", "POL", "Poland", "Polska", "Польша"));

        service.updateEditorialStaff(new EditorialStaff2024Record(123L, List.of(editor), "editors.xlsx", 2));

        assertThat(questionnaire.getSurveyData().getSurvey().getEditorial().getEditors()).singleElement().satisfies(person -> {
            assertThat(person).extracting("firstName", "middleName", "lastName", "personPosition", "title", "department", "email", "gicid")
                    .containsExactly("Anna", "Maria", "Nowak", PersonPosition.EDITOR_IN_CHIEF, "Dr", "University", "anna@example.org", "12345");
            assertThat(person.getCountry()).extracting("code", "codeAlpha3", "nameEn", "namePl", "nameRu")
                    .containsExactly("PL", "POL", "Poland", "Polska", "Польша");
        });
        verify(questionnaireRepository).save(questionnaire);
    }

    private Map<String, Object> row(long journalId, int index, String firstName, String lastName) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("journal_id", journalId);
        values.put("questionnaire_year", 2024);
        values.put("editor_index_docelowy", index);
        values.put("firstName", firstName);
        values.put("lastName", lastName);
        return values;
    }

    private List<String> headers() {
        return List.of("journal_id", "questionnaire_id", "questionnaire_survey_id", "questionnaire_year", "source_row",
                "editor_index_docelowy", "firstName", "middleName", "lastName", "personPosition", "title", "department",
                "email", "gicid", "country.code", "country.codeAlpha3", "country.nameEn", "country.namePl", "country.nameRu", "sprawdz_podzial");
    }

    private void writeWorkbook(Path file, List<String> columns, List<Map<String, Object>> records) throws Exception {
        try (Workbook workbook = file.toString().endsWith(".xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Aktualizacja");
            var header = sheet.createRow(0);
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(columns.get(columnIndex));
            }
            for (int rowIndex = 0; rowIndex < records.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex + 1);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = records.get(rowIndex).get(columns.get(columnIndex));
                    if (value instanceof Number number) {
                        row.createCell(columnIndex).setCellValue(number.doubleValue());
                    } else if (value instanceof Boolean flag) {
                        row.createCell(columnIndex).setCellValue(flag);
                    } else if (value != null) {
                        row.createCell(columnIndex).setCellValue(value.toString());
                    }
                }
            }
            workbook.createSheet("Kontrola podzialu").createRow(0).createCell(0).setCellValue("Ignored auxiliary sheet");
            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
    }
}
