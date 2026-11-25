package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.PersonPosition;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Editorial;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Country;
import com.osatum.wbn.domain.model.survey.model.Person;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class EditorialStaff2024ImportService {
    private static final int SURVEY_YEAR = 2024;
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "journal_id", "questionnaire_year", "editor_index_docelowy",
            "firstName", "middleName", "lastName", "personPosition", "title", "department", "email", "gicid",
            "country.code", "country.codeAlpha3", "country.nameEn", "country.namePl", "country.nameRu"
    );

    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<EditorialStaff2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find editorial staff 2024 Excel file: " + sourcePath);
        }

        Map<Long, JournalEditors> records = new LinkedHashMap<>();
        Map<String, Integer> columns = new LinkedHashMap<>();
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns.isEmpty()) {
                if (row.values().values().stream().anyMatch(value -> "journal_id".equals(normalize(value)))) {
                    columns.putAll(headerColumns(row, file));
                }
                return;
            }
            if (row.values().isEmpty()) {
                return;
            }

            try {
                Long journalId = readJournalId(value(row, columns, "journal_id"));
                int year = readInteger(value(row, columns, "questionnaire_year"), "questionnaire_year", 1);
                if (year != SURVEY_YEAR) {
                    throw new IllegalArgumentException("Expected questionnaire_year 2024, found " + year);
                }
                int editorIndex = readInteger(value(row, columns, "editor_index_docelowy"), "editor_index_docelowy", 0);
                EditorValues editor = new EditorValues(
                        value(row, columns, "firstName"),
                        value(row, columns, "middleName"),
                        value(row, columns, "lastName"),
                        readPosition(value(row, columns, "personPosition")),
                        value(row, columns, "title"),
                        value(row, columns, "department"),
                        value(row, columns, "email"),
                        value(row, columns, "gicid"),
                        readCountry(row, columns)
                );
                JournalEditors record = records.computeIfAbsent(journalId,
                        identifier -> new JournalEditors(row.rowIndex() + 1, new TreeMap<>()));
                if (record.editors().putIfAbsent(editorIndex, editor) != null) {
                    throw new IllegalArgumentException("Duplicate editor_index_docelowy " + editorIndex + " for journal_id " + journalId);
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(file.getName() + ":" + (row.rowIndex() + 1) + ": " + exception.getMessage(), exception);
            }
        });
        if (columns.isEmpty()) {
            throw new IllegalStateException("Missing editorial staff 2024 header with journal_id: " + file.getName());
        }

        List<EditorialStaff2024Record> result = new ArrayList<>();
        for (Map.Entry<Long, JournalEditors> entry : records.entrySet()) {
            int expectedIndex = 0;
            List<EditorValues> editors = new ArrayList<>();
            for (Map.Entry<Integer, EditorValues> editor : entry.getValue().editors().entrySet()) {
                if (editor.getKey() != expectedIndex) {
                    throw new IllegalArgumentException(file.getName() + ":" + entry.getValue().rowNumber()
                            + ": Expected consecutive editor_index_docelowy from 0 for journal_id " + entry.getKey());
                }
                expectedIndex++;
                if (!editor.getValue().empty()) {
                    editors.add(editor.getValue());
                }
            }
            result.add(new EditorialStaff2024Record(entry.getKey(), editors, file.getName(), entry.getValue().rowNumber()));
        }
        return List.copyOf(result);
    }

    @Transactional
    public void updateEditorialStaff(EditorialStaff2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.size() != 1) {
            throw new IllegalStateException("Expected one 2024 questionnaire for journal " + record.journalId()
                    + ", found " + questionnaires.size());
        }

        List<Person> editors = record.editors().stream().map(this::toPerson).toList();
        Questionnaire questionnaire = questionnaires.getFirst();
        editorial(questionnaire).setEditors(new ArrayList<>(editors));
        questionnaireRepository.save(questionnaire);
    }

    private Person toPerson(EditorValues values) {
        Person person = new Person();
        person.setFirstName(values.firstName());
        person.setMiddleName(values.middleName());
        person.setLastName(values.lastName());
        person.setPersonPosition(values.personPosition());
        person.setTitle(values.title());
        person.setDepartment(values.department());
        person.setEmail(values.email());
        person.setGicid(values.gicid());
        if (values.country() != null) {
            Country country = new Country();
            country.setCode(values.country().code());
            country.setCodeAlpha3(values.country().codeAlpha3());
            country.setNameEn(values.country().nameEn());
            country.setNamePl(values.country().namePl());
            country.setNameRu(values.country().nameRu());
            person.setCountry(country);
        }
        return person;
    }

    private Editorial editorial(Questionnaire questionnaire) {
        SurveyData surveyData = questionnaire.getSurveyData();
        if (surveyData == null) {
            surveyData = new SurveyData();
            questionnaire.setSurveyData(surveyData);
        }
        Survey survey = surveyData.getSurvey();
        if (survey == null) {
            survey = new Survey();
            surveyData.setSurvey(survey);
        }
        Editorial editorial = survey.getEditorial();
        if (editorial == null) {
            editorial = new Editorial();
            survey.setEditorial(editorial);
        }
        return editorial;
    }

    private Map<String, Integer> headerColumns(MigrationExcelReader.RowData header, File file) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            String name = normalize(cell.getValue());
            if (columns.putIfAbsent(name, cell.getKey()) != null) {
                throw new IllegalStateException("Duplicate editorial staff 2024 header in " + file.getName() + ": " + name);
            }
        }
        for (String name : REQUIRED_COLUMNS) {
            if (!columns.containsKey(name)) {
                throw new IllegalStateException("Missing editorial staff 2024 column in " + file.getName() + ": " + name);
            }
        }
        return columns;
    }

    private String value(MigrationExcelReader.RowData row, Map<String, Integer> columns, String name) {
        String value = row.value(columns.get(name));
        return missing(value) ? null : value.replace('\u00A0', ' ').trim();
    }

    private CountryValues readCountry(MigrationExcelReader.RowData row, Map<String, Integer> columns) {
        String code = value(row, columns, "country.code");
        String codeAlpha3 = value(row, columns, "country.codeAlpha3");
        String nameEn = value(row, columns, "country.nameEn");
        String namePl = value(row, columns, "country.namePl");
        String nameRu = value(row, columns, "country.nameRu");
        if (code == null && codeAlpha3 == null && nameEn == null && namePl == null && nameRu == null) {
            return null;
        }
        return new CountryValues(code, codeAlpha3, nameEn, namePl, nameRu);
    }

    private PersonPosition readPosition(String value) {
        if (value == null) {
            return null;
        }
        try {
            return PersonPosition.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid personPosition: " + value, exception);
        }
    }

    private Long readJournalId(String value) {
        try {
            long journalId = readNumber(value).longValueExact();
            if (journalId <= 0) {
                throw new ArithmeticException("Identifier must be positive");
            }
            return journalId;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid journal_id: " + value, exception);
        }
    }

    private int readInteger(String value, String name, int minimum) {
        try {
            int number = readNumber(value).intValueExact();
            if (number < minimum) {
                throw new ArithmeticException("Number is below " + minimum);
            }
            return number;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value, exception);
        }
    }

    private BigDecimal readNumber(String value) {
        if (value == null) {
            throw new NumberFormatException("Missing number");
        }
        return new BigDecimal(normalize(value).replace(" ", "").replace(',', '.'));
    }

    private boolean missing(String value) {
        if (value == null) {
            return true;
        }
        String normalized = normalize(value);
        return normalized.isBlank() || "-".equals(normalized) || "brak danych".equalsIgnoreCase(normalized);
    }

    private String normalize(String value) {
        return value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    public record EditorialStaff2024Record(Long journalId, List<EditorValues> editors, String sourceFile, int rowNumber) {
        public EditorialStaff2024Record {
            editors = List.copyOf(editors);
        }

        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    public record EditorValues(String firstName, String middleName, String lastName, PersonPosition personPosition,
                               String title, String department, String email, String gicid, CountryValues country) {
        private boolean empty() {
            return firstName == null && middleName == null && lastName == null && personPosition == null
                    && title == null && department == null && email == null && gicid == null && country == null;
        }
    }

    public record CountryValues(String code, String codeAlpha3, String nameEn, String namePl, String nameRu) {
    }

    private record JournalEditors(int rowNumber, Map<Integer, EditorValues> editors) {
    }
}
