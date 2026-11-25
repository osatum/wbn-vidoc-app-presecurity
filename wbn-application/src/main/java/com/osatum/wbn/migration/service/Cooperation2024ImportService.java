package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Cooperation;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class Cooperation2024ImportService {
    private static final int SURVEY_YEAR = 2024;
    private static final int HEADER_ROW_INDEX = 3;
    private static final String METRIC_PREFIX = "surveyData.survey.cooperation.";

    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<Cooperation2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find cooperation 2024 Excel file: " + sourcePath);
        }

        Map<Long, Cooperation2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (row.rowIndex() < HEADER_ROW_INDEX) {
                return;
            }
            if (row.rowIndex() == HEADER_ROW_INDEX) {
                columns[0] = headerColumns(row, file);
                return;
            }
            if (columns[0] == null) {
                throw new IllegalStateException("Missing cooperation 2024 header in row 4: " + file.getName());
            }
            if (row.values().isEmpty()) {
                return;
            }

            try {
                Long journalId = readJournalId(row.value(columns[0].journalId()));
                Cooperation2024Record record = new Cooperation2024Record(
                        journalId,
                        readMetric(row, columns[0].authors()),
                        readMetric(row, columns[0].reviewers()),
                        readMetric(row, columns[0].directors()),
                        file.getName(),
                        row.rowIndex() + 1
                );
                if (records.putIfAbsent(journalId, record) != null) {
                    throw new IllegalArgumentException("Duplicate journal.id: " + journalId);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(file.getName() + ":" + (row.rowIndex() + 1) + ": " + e.getMessage(), e);
            }
        });
        if (columns[0] == null) {
            throw new IllegalStateException("Missing cooperation 2024 header in row 4: " + file.getName());
        }
        return List.copyOf(records.values());
    }

    @Transactional
    public void updateCooperation(Cooperation2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.size() != 1) {
            throw new IllegalStateException("Expected one 2024 questionnaire for journal " + record.journalId()
                    + ", found " + questionnaires.size());
        }

        Questionnaire questionnaire = questionnaires.getFirst();
        Cooperation cooperation = cooperation(questionnaire);
        cooperation.setAuthorMetric(toMetric(record.authors()));
        cooperation.setReviewerMetric(toMetric(record.reviewers()));
        cooperation.setDirectorsMetric(toMetric(record.directors()));
        questionnaireRepository.save(questionnaire);
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

    private ScientistMetric toMetric(MetricValues values) {
        ScientistMetric metric = new ScientistMetric();
        metric.setUnique(values.unique());
        metric.setExternalAffiliationPercentage(values.externalAffiliationPercentage());
        metric.setForeignAffiliationPercentage(values.foreignAffiliationPercentage());
        return metric;
    }

    private HeaderColumns headerColumns(MigrationExcelReader.RowData header, File file) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            String name = cell.getValue().trim();
            if (columns.putIfAbsent(name, cell.getKey()) != null) {
                throw new IllegalStateException("Duplicate cooperation 2024 header in " + file.getName() + ": " + name);
            }
        }
        return new HeaderColumns(
                requiredColumn(columns, "journal.id", file),
                metricColumns(columns, "authorMetric", file),
                metricColumns(columns, "reviewerMetric", file),
                metricColumns(columns, "directorsMetric", file)
        );
    }

    private MetricColumns metricColumns(Map<String, Integer> columns, String metric, File file) {
        String prefix = METRIC_PREFIX + metric + ".";
        return new MetricColumns(
                requiredColumn(columns, prefix + "unique", file),
                requiredColumn(columns, prefix + "externalAffiliationPercentage", file),
                requiredColumn(columns, prefix + "foreignAffiliationPercentage", file)
        );
    }

    private int requiredColumn(Map<String, Integer> columns, String name, File file) {
        Integer column = columns.get(name);
        if (column == null) {
            throw new IllegalStateException("Missing cooperation 2024 column in " + file.getName() + ": " + name);
        }
        return column;
    }

    private MetricValues readMetric(MigrationExcelReader.RowData row, MetricColumns columns) {
        return new MetricValues(
                readUnique(row.value(columns.unique())),
                readPercentage(row.value(columns.external())),
                readPercentage(row.value(columns.foreign()))
        );
    }

    private Long readJournalId(String value) {
        if (value == null || value.isBlank()) {
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

    private Integer readUnique(String value) {
        if (missing(value)) {
            return null;
        }
        try {
            int unique = readNumber(value).intValueExact();
            if (unique < 0) {
                throw new ArithmeticException("Count must be nonnegative");
            }
            return unique;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid unique researcher count: " + value, e);
        }
    }

    private Double readPercentage(String value) {
        if (missing(value)) {
            return null;
        }
        try {
            BigDecimal percentage = readNumber(value);
            if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ArithmeticException("Percentage must be between 0 and 100");
            }
            return percentage.doubleValue();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid affiliation percentage: " + value, e);
        }
    }

    private boolean missing(String value) {
        return value == null || value.isBlank() || "brak danych".equalsIgnoreCase(value.trim());
    }

    private BigDecimal readNumber(String value) {
        return new BigDecimal(value.trim().replace("\u00A0", "").replace(" ", "").replace(',', '.'));
    }

    public record Cooperation2024Record(
            Long journalId,
            MetricValues authors,
            MetricValues reviewers,
            MetricValues directors,
            String sourceFile,
            int rowNumber
    ) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    public record MetricValues(Integer unique, Double externalAffiliationPercentage, Double foreignAffiliationPercentage) {
    }

    private record HeaderColumns(int journalId, MetricColumns authors, MetricColumns reviewers, MetricColumns directors) {
    }

    private record MetricColumns(int unique, int external, int foreign) {
    }
}
