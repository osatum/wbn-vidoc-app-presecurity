package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Stability;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticleLanguage;
import com.osatum.wbn.domain.model.survey.model.Language;
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
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class Languages2024ImportService {
    private static final int SURVEY_YEAR = 2024;
    private static final String LANGUAGE_PREFIX = "surveyData.survey.stability.articleLanguages[].";

    private final QuestionnaireSpringDataRepository questionnaireRepository;
    private final DictionaryLanguageService dictionaryLanguageService;

    public List<Languages2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find languages 2024 Excel file: " + sourcePath);
        }

        Map<Long, JournalLanguages> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                if (row.values().values().stream().anyMatch(value -> "journal.id".equals(value.trim()))) {
                    columns[0] = headerColumns(row, file);
                }
                return;
            }
            if (row.values().isEmpty()) {
                return;
            }

            try {
                Long journalId = readJournalId(row.value(columns[0].journalId()));
                String code = readLanguageCode(row.value(columns[0].code()));
                ArticleLanguageValues values = new ArticleLanguageValues(
                        code,
                        readCount(row.value(columns[0].previous()), LANGUAGE_PREFIX + "previousValue"),
                        readCount(row.value(columns[0].current()), LANGUAGE_PREFIX + "currentValue")
                );
                JournalLanguages record = records.computeIfAbsent(journalId,
                        identifier -> new JournalLanguages(row.rowIndex() + 1, new LinkedHashMap<>()));
                if (record.languages().putIfAbsent(code, values) != null) {
                    throw new IllegalArgumentException("Duplicate language code " + code + " for journal.id " + journalId);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(file.getName() + ":" + (row.rowIndex() + 1) + ": " + e.getMessage(), e);
            }
        });
        if (columns[0] == null) {
            throw new IllegalStateException("Missing languages 2024 header with journal.id: " + file.getName());
        }
        return records.entrySet().stream().map(entry -> new Languages2024Record(
                entry.getKey(), List.copyOf(entry.getValue().languages().values()), file.getName(), entry.getValue().rowNumber()
        )).toList();
    }

    @Transactional
    public void updateLanguages(Languages2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.size() != 1) {
            throw new IllegalStateException("Expected one 2024 questionnaire for journal " + record.journalId()
                    + ", found " + questionnaires.size());
        }

        List<ArticleLanguage> languages = record.languages().stream().map(this::articleLanguage).toList();
        Questionnaire questionnaire = questionnaires.getFirst();
        stability(questionnaire).setArticleLanguages(new ArrayList<>(languages));
        questionnaireRepository.save(questionnaire);
    }

    private ArticleLanguage articleLanguage(ArticleLanguageValues values) {
        var dictionaryLanguage = dictionaryLanguageService.getByCode(values.code());
        if (dictionaryLanguage == null) {
            throw new IllegalStateException("Cannot find language dictionary entry: " + values.code());
        }
        if (missing(dictionaryLanguage.getNamePl()) || missing(dictionaryLanguage.getNameEn()) || missing(dictionaryLanguage.getNameRu())) {
            throw new IllegalStateException("Incomplete language dictionary entry: " + values.code());
        }
        Language language = new Language();
        language.setCode(dictionaryLanguage.getCode());
        language.setNamePl(dictionaryLanguage.getNamePl());
        language.setNameEn(dictionaryLanguage.getNameEn());
        language.setNameRu(dictionaryLanguage.getNameRu());
        ArticleLanguage article = new ArticleLanguage();
        article.setLanguage(language);
        article.setPreviousYear(SURVEY_YEAR - 1);
        article.setPreviousValue(values.previousValue());
        article.setCurrentYear(SURVEY_YEAR);
        article.setCurrentValue(values.currentValue());
        return article;
    }

    private Stability stability(Questionnaire questionnaire) {
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
        Stability stability = survey.getStability();
        if (stability == null) {
            stability = new Stability();
            survey.setStability(stability);
        }
        return stability;
    }

    private HeaderColumns headerColumns(MigrationExcelReader.RowData header, File file) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            String name = cell.getValue().trim();
            if (columns.putIfAbsent(name, cell.getKey()) != null) {
                throw new IllegalStateException("Duplicate languages 2024 header in " + file.getName() + ": " + name);
            }
        }
        return new HeaderColumns(
                requiredColumn(columns, "journal.id", file),
                requiredColumn(columns, LANGUAGE_PREFIX + "language.code", file),
                requiredColumn(columns, LANGUAGE_PREFIX + "previousValue", file),
                requiredColumn(columns, LANGUAGE_PREFIX + "currentValue", file)
        );
    }

    private int requiredColumn(Map<String, Integer> columns, String name, File file) {
        Integer column = columns.get(name);
        if (column == null) {
            throw new IllegalStateException("Missing languages 2024 column in " + file.getName() + ": " + name);
        }
        return column;
    }

    private Long readJournalId(String value) {
        if (missing(value)) {
            throw new IllegalArgumentException("Missing journal.id");
        }
        try {
            long journalId = readNumber(value).longValueExact();
            if (journalId <= 0) {
                throw new ArithmeticException("Journal identifier must be positive");
            }
            return journalId;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid journal.id: " + value, e);
        }
    }

    private String readLanguageCode(String value) {
        if (missing(value)) {
            throw new IllegalArgumentException("Missing " + LANGUAGE_PREFIX + "language.code");
        }
        String code = normalize(value).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("Invalid language code: " + value);
        }
        return code;
    }

    private Integer readCount(String value, String field) {
        if (missing(value)) {
            return null;
        }
        try {
            int count = readNumber(value).intValueExact();
            if (count < 0) {
                throw new ArithmeticException("Count must be nonnegative");
            }
            return count;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid nonnegative integer for " + field + ": " + value, e);
        }
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

    private BigDecimal readNumber(String value) {
        return new BigDecimal(normalize(value).replace(" ", "").replace(',', '.'));
    }

    public record Languages2024Record(Long journalId, List<ArticleLanguageValues> languages, String sourceFile, int rowNumber) {
        public Languages2024Record {
            languages = List.copyOf(languages);
        }

        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    public record ArticleLanguageValues(String code, Integer previousValue, Integer currentValue) {
    }

    private record JournalLanguages(int rowNumber, Map<String, ArticleLanguageValues> languages) {
    }

    private record HeaderColumns(int journalId, int code, int previous, int current) {
    }
}
