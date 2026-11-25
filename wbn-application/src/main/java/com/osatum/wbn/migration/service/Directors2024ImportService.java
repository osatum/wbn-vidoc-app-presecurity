package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Cooperation;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class Directors2024ImportService {
    private static final int SURVEY_YEAR = 2024;
    private static final String DIRECTOR_PREFIX = "directorsList[].";
    private static final List<String> JOURNAL_HEADERS = List.of("Questionnaire.journal.id", "journal.id", "Identyfikator czasopisma");

    private final QuestionnaireSpringDataRepository questionnaireRepository;
    private final DictionaryCountryService dictionaryCountryService;

    public List<Directors2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find directors 2024 Excel file: " + sourcePath);
        }

        Map<Long, JournalDirectors> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                if (row.values().values().stream().anyMatch(value -> JOURNAL_HEADERS.contains(normalize(value)))) {
                    columns[0] = headerColumns(row, file);
                }
                return;
            }
            if (row.values().isEmpty()) {
                return;
            }

            try {
                Long journalId = readJournalId(row.value(columns[0].journalId()));
                DirectorValues director = new DirectorValues(
                        readText(row.value(columns[0].fullName())),
                        readText(row.value(columns[0].affiliation())),
                        readText(row.value(columns[0].country())),
                        readText(row.value(columns[0].email()))
                );
                boolean clear = director.fullName() == null && director.affiliation() == null
                        && director.country() == null && director.email() == null;
                if (!clear && director.fullName() == null) {
                    return;
                }
                JournalDirectors record = records.computeIfAbsent(journalId,
                        identifier -> new JournalDirectors(row.rowIndex() + 1, new LinkedHashSet<>()));
                if (!clear) {
                    record.directors().add(director);
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(file.getName() + ":" + (row.rowIndex() + 1) + ": " + exception.getMessage(), exception);
            }
        });
        if (columns[0] == null) {
            throw new IllegalStateException("Missing directors 2024 header with journal identifier: " + file.getName());
        }
        return records.entrySet().stream().map(entry -> new Directors2024Record(
                entry.getKey(), List.copyOf(entry.getValue().directors()), file.getName(), entry.getValue().rowNumber()
        )).toList();
    }

    @Transactional
    public void updateDirectors(Directors2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.size() != 1) {
            throw new IllegalStateException("Expected one 2024 questionnaire for journal " + record.journalId()
                    + ", found " + questionnaires.size());
        }

        Map<String, Set<String>> countries = record.directors().stream().anyMatch(director -> !missing(director.country()))
                ? countryCodes() : Map.of();
        Set<Cooperator> directors = new LinkedHashSet<>();
        for (DirectorValues values : record.directors()) {
            Cooperator director = new Cooperator();
            director.setFullName(values.fullName());
            director.setAffiliation(values.affiliation());
            director.setCountry(countryCode(values.country(), countries));
            director.setEmail(values.email());
            directors.add(director);
        }
        Questionnaire questionnaire = questionnaires.getFirst();
        cooperation(questionnaire).setDirectorsList(new ArrayList<>(directors));
        questionnaireRepository.save(questionnaire);
    }

    private Map<String, Set<String>> countryCodes() {
        Map<String, Set<String>> countries = new LinkedHashMap<>();
        for (var country : dictionaryCountryService.getAll()) {
            if (missing(country.getCode())) {
                continue;
            }
            for (String alias : Arrays.asList(country.getCode(), country.getCodeAlpha3(), country.getNamePl(), country.getNameEn(), country.getNameRu())) {
                if (!missing(alias)) {
                    countries.computeIfAbsent(normalize(alias).toLowerCase(Locale.ROOT), name -> new LinkedHashSet<>())
                            .add(country.getCode());
                }
            }
        }
        return countries;
    }

    private String countryCode(String value, Map<String, Set<String>> countries) {
        if (missing(value)) {
            return null;
        }
        Set<String> codes = countries.get(normalize(value).toLowerCase(Locale.ROOT));
        if (codes == null || codes.isEmpty()) {
            throw new IllegalStateException("Cannot find country dictionary entry: " + value);
        }
        if (codes.size() != 1) {
            throw new IllegalStateException("Ambiguous country dictionary entry: " + value);
        }
        return codes.iterator().next();
    }

    private Cooperation cooperation(Questionnaire questionnaire) {
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
        Cooperation cooperation = survey.getCooperation();
        if (cooperation == null) {
            cooperation = new Cooperation();
            survey.setCooperation(cooperation);
        }
        return cooperation;
    }

    private HeaderColumns headerColumns(MigrationExcelReader.RowData header, File file) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            String name = normalize(cell.getValue());
            if (columns.putIfAbsent(name, cell.getKey()) != null) {
                throw new IllegalStateException("Duplicate directors 2024 header in " + file.getName() + ": " + name);
            }
        }
        return new HeaderColumns(
                requiredColumn(columns, file, JOURNAL_HEADERS),
                requiredColumn(columns, file, List.of(DIRECTOR_PREFIX + "fullName", "Imię i nazwisko")),
                requiredColumn(columns, file, List.of(DIRECTOR_PREFIX + "affiliation", "Afiliacja")),
                requiredColumn(columns, file, List.of(DIRECTOR_PREFIX + "country", "Kraj")),
                requiredColumn(columns, file, List.of(DIRECTOR_PREFIX + "email", "E-mail"))
        );
    }

    private int requiredColumn(Map<String, Integer> columns, File file, List<String> names) {
        List<Integer> matches = names.stream().filter(columns::containsKey).map(columns::get).toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("Missing directors 2024 column in " + file.getName() + ": " + names.getFirst());
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Ambiguous directors 2024 column in " + file.getName() + ": " + names.getFirst());
        }
        return matches.getFirst();
    }

    private Long readJournalId(String value) {
        if (missing(value)) {
            throw new IllegalArgumentException("Missing journal.id");
        }
        try {
            long journalId = new BigDecimal(normalize(value).replace(" ", "").replace(',', '.')).longValueExact();
            if (journalId <= 0) {
                throw new ArithmeticException("Journal identifier must be positive");
            }
            return journalId;
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid journal.id: " + value, exception);
        }
    }

    private String readText(String value) {
        return missing(value) ? null : value.replace('\u00A0', ' ').trim();
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

    public record Directors2024Record(Long journalId, List<DirectorValues> directors, String sourceFile, int rowNumber) {
        public Directors2024Record {
            directors = List.copyOf(directors);
        }

        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    public record DirectorValues(String fullName, String affiliation, String country, String email) {
    }

    private record JournalDirectors(int rowNumber, Set<DirectorValues> directors) {
    }

    private record HeaderColumns(int journalId, int fullName, int affiliation, int country, int email) {
    }
}

