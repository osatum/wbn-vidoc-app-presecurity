package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Publisher;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Country;
import com.osatum.wbn.domain.model.survey.model.Institution;
import com.osatum.wbn.domain.model.survey.model.JournalInstitution;
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
public class Publisher2024ImportService {
    private static final int SURVEY_YEAR = 2024;
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "journal_id", "questionnaire_year", "json_index", "name", "department", "street", "postCode", "city", "state",
            "country.code", "country.codeAlpha3", "country.nameEn", "country.namePl", "country.nameRu",
            "phone", "email", "www", "gicid", "type"
    );

    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<Publisher2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find publisher 2024 Excel file: " + sourcePath);
        }

        Map<Long, JournalInstitutions> records = new LinkedHashMap<>();
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
                String year = value(row, columns, "questionnaire_year");
                if (year != null && readInteger(year, "questionnaire_year") != SURVEY_YEAR) {
                    throw new IllegalArgumentException("Expected questionnaire_year 2024, found " + year);
                }
                InstitutionValues institution = new InstitutionValues(
                        value(row, columns, "name"),
                        value(row, columns, "department"),
                        value(row, columns, "street"),
                        value(row, columns, "postCode"),
                        value(row, columns, "city"),
                        value(row, columns, "state"),
                        readCountry(row, columns),
                        value(row, columns, "phone"),
                        value(row, columns, "email"),
                        value(row, columns, "www"),
                        value(row, columns, "gicid"),
                        value(row, columns, "type")
                );
                JournalInstitutions record = records.computeIfAbsent(journalId,
                        identifier -> new JournalInstitutions(row.rowIndex() + 1, new TreeMap<>()));
                if (!institution.empty()) {
                    int institutionIndex = readInteger(value(row, columns, "json_index"), "json_index");
                    if (record.institutions().putIfAbsent(institutionIndex, institution) != null) {
                        throw new IllegalArgumentException("Duplicate json_index " + institutionIndex + " for journal_id " + journalId);
                    }
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(file.getName() + ":" + (row.rowIndex() + 1) + ": " + exception.getMessage(), exception);
            }
        });
        if (columns.isEmpty()) {
            throw new IllegalStateException("Missing publisher 2024 header with journal_id: " + file.getName());
        }

        return records.entrySet().stream()
                .map(entry -> new Publisher2024Record(entry.getKey(), new ArrayList<>(entry.getValue().institutions().values()),
                        file.getName(), entry.getValue().rowNumber()))
                .toList();
    }

    @Transactional
    public void updatePublisher(Publisher2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.size() != 1) {
            throw new IllegalStateException("Expected one 2024 questionnaire for journal " + record.journalId()
                    + ", found " + questionnaires.size());
        }

        Questionnaire questionnaire = questionnaires.getFirst();
        List<JournalInstitution> institutions = record.institutions().stream().map(this::toJournalInstitution).toList();
        publisher(questionnaire).setPublisherInstitutions(new ArrayList<>(institutions));
        questionnaireRepository.save(questionnaire);
    }

    private JournalInstitution toJournalInstitution(InstitutionValues values) {
        Institution institution = new Institution();
        institution.setName(values.name());
        institution.setDepartment(values.department());
        institution.setStreet(values.street());
        institution.setPostCode(values.postCode());
        institution.setCity(values.city());
        institution.setState(values.state());
        institution.setPhone(values.phone());
        institution.setEmail(values.email());
        institution.setWww(values.www());
        institution.setGicid(values.gicid());
        institution.setType(values.type());
        if (values.country() != null) {
            Country country = new Country();
            country.setCode(values.country().code());
            country.setCodeAlpha3(values.country().codeAlpha3());
            country.setNameEn(values.country().nameEn());
            country.setNamePl(values.country().namePl());
            country.setNameRu(values.country().nameRu());
            institution.setCountry(country);
        }
        JournalInstitution journalInstitution = new JournalInstitution();
        journalInstitution.setInstitutionRole('P');
        journalInstitution.setInstitution(institution);
        return journalInstitution;
    }

    private Publisher publisher(Questionnaire questionnaire) {
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
        Publisher publisher = survey.getPublisher();
        if (publisher == null) {
            publisher = new Publisher();
            survey.setPublisher(publisher);
        }
        return publisher;
    }

    private Map<String, Integer> headerColumns(MigrationExcelReader.RowData header, File file) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            String name = normalize(cell.getValue());
            if (columns.putIfAbsent(name, cell.getKey()) != null) {
                throw new IllegalStateException("Duplicate publisher 2024 header in " + file.getName() + ": " + name);
            }
        }
        for (String name : REQUIRED_COLUMNS) {
            if (!columns.containsKey(name)) {
                throw new IllegalStateException("Missing publisher 2024 column in " + file.getName() + ": " + name);
            }
        }
        return columns;
    }

    private String value(MigrationExcelReader.RowData row, Map<String, Integer> columns, String name) {
        String value = row.value(columns.get(name));
        if (value == null || normalize(value).isBlank() || "-".equals(normalize(value))
                || "brak danych".equalsIgnoreCase(normalize(value))) {
            return null;
        }
        return value.replace('\u00A0', ' ').trim();
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

    private int readInteger(String value, String name) {
        try {
            int number = readNumber(value).intValueExact();
            if (number < 0) {
                throw new ArithmeticException("Number must not be negative");
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

    private String normalize(String value) {
        return value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
    }

    public record Publisher2024Record(Long journalId, List<InstitutionValues> institutions, String sourceFile, int rowNumber) {
        public Publisher2024Record {
            institutions = List.copyOf(institutions);
        }

        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    public record InstitutionValues(String name, String department, String street, String postCode, String city,
                                    String state, CountryValues country, String phone, String email, String www,
                                    String gicid, String type) {
        private boolean empty() {
            return name == null && department == null && street == null && postCode == null && city == null
                    && state == null && country == null && phone == null && email == null && www == null
                    && gicid == null && type == null;
        }
    }

    public record CountryValues(String code, String codeAlpha3, String nameEn, String namePl, String nameRu) {
    }

    private record JournalInstitutions(int rowNumber, Map<Integer, InstitutionValues> institutions) {
    }
}
