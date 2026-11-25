package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryCategory;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.SupplementaryData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
public abstract class AbstractCategory2024ImportService<D extends DictionaryCategory> {
    private static final int SURVEY_YEAR = 2024;

    private final QuestionnaireSpringDataRepository questionnaireRepository;
    private final String importName;

    protected AbstractCategory2024ImportService(
            QuestionnaireSpringDataRepository questionnaireRepository,
            String importName
    ) {
        this.questionnaireRepository = questionnaireRepository;
        this.importName = importName;
    }

    public List<Category2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find " + importName + " Excel file: " + sourcePath);
        }

        Map<Long, Category2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                columns[0] = headerColumns(row, file);
                return;
            }

            Long journalId = readLong(row.value(columns[0].journalId()), "journal_id");
            if (journalId == null) {
                return;
            }

            List<Category2024Item> categories = categories(
                    row.value(columns[0].main()),
                    row.value(columns[0].other())
            );
            records.put(journalId, new Category2024Record(
                    journalId,
                    categories,
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    @Transactional
    public void updateCategories(Category2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(
                SURVEY_YEAR,
                record.journalId()
        );
        if (questionnaires.isEmpty()) {
            log.debug(
                    "Skipping {} for journal {} without {} questionnaire",
                    importName,
                    record.journalId(),
                    SURVEY_YEAR
            );
            return;
        }

        List<Category> categories = record.categories().stream()
                .map(this::category)
                .toList();

        Questionnaire questionnaire = questionnaires.getFirst();
        replaceCategories(supplementaryData(questionnaire), new ArrayList<>(categories));
        questionnaireRepository.save(questionnaire);
    }

    protected abstract D dictionaryCategory(Long categoryId);

    protected abstract void replaceCategories(SupplementaryData supplementaryData, List<Category> categories);

    private HeaderColumns headerColumns(MigrationExcelReader.RowData header, File file) {
        Integer journalId = null;
        Integer main = null;
        Integer other = null;

        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            switch (normalizedHeader(cell.getValue())) {
                case "journalid" -> journalId = cellIndex;
                case "main" -> main = cellIndex;
                case "other" -> other = cellIndex;
                default -> {
                }
            }
        }

        if (journalId == null || main == null || other == null) {
            throw new IllegalStateException(importName + " file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, main, other);
    }

    private List<Category2024Item> categories(String mainValue, String otherValue) {
        Map<Long, Boolean> categoryIds = new LinkedHashMap<>();
        Long mainCategoryId = readLong(mainValue, "main category id");
        if (mainCategoryId != null) {
            categoryIds.put(mainCategoryId, true);
        }

        if (otherValue != null && !otherValue.isBlank()) {
            for (String value : otherValue.split(",", -1)) {
                Long categoryId = readLong(value, "other category id");
                if (categoryId != null) {
                    categoryIds.putIfAbsent(categoryId, false);
                }
            }
        }

        return categoryIds.entrySet().stream()
                .map(entry -> new Category2024Item(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Category category(Category2024Item item) {
        D dictionaryCategory = dictionaryCategory(item.categoryId());
        if (dictionaryCategory == null) {
            throw new IllegalStateException(
                    "Cannot find " + importName + " dictionary entry: " + item.categoryId()
            );
        }
        return new Category(
                dictionaryCategory.getId(),
                null,
                dictionaryCategory.getNamePl(),
                dictionaryCategory.getNameEn(),
                dictionaryCategory.getNameRu(),
                item.main()
        );
    }

    private SupplementaryData supplementaryData(Questionnaire questionnaire) {
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

        SupplementaryData supplementaryData = survey.getSupplementaryData();
        if (supplementaryData == null) {
            supplementaryData = new SupplementaryData();
            survey.setSupplementaryData(supplementaryData);
        }
        return supplementaryData;
    }

    private Long readLong(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace("\u00A0", "").replace(" ", "")).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + " value: " + value, e);
        }
    }

    private String normalizedHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record Category2024Record(
            Long journalId,
            List<Category2024Item> categories,
            String sourceFile,
            int rowNumber
    ) {
        public Category2024Record {
            categories = List.copyOf(categories);
        }

        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    public record Category2024Item(Long categoryId, boolean main) {
    }

    private record HeaderColumns(int journalId, int main, int other) {
    }
}
