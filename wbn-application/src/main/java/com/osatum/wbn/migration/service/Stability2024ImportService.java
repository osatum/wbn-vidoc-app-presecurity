package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Stability;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
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
public class Stability2024ImportService {
    private static final int SURVEY_YEAR = 2024;
    private static final List<Integer> PUBLICATION_YEARS = List.of(2021, 2022, 2023, 2024);
    private static final String ARTICLE_PREFIX = "surveyData.survey.stability.articlePublished";

    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<Stability2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find stability 2024 Excel file: " + sourcePath);
        }

        Map<Long, Stability2024Record> records = new LinkedHashMap<>();
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
                List<ArticlePublishedValues> articles = columns[0].articles().stream()
                        .map(article -> readArticle(row, article))
                        .toList();
                Stability2024Record record = new Stability2024Record(journalId, articles, file.getName(), row.rowIndex() + 1);
                if (records.putIfAbsent(journalId, record) != null) {
                    throw new IllegalArgumentException("Duplicate journal.id: " + journalId);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(file.getName() + ":" + (row.rowIndex() + 1) + ": " + e.getMessage(), e);
            }
        });
        if (columns[0] == null) {
            throw new IllegalStateException("Missing stability 2024 header with journal.id: " + file.getName());
        }
        return List.copyOf(records.values());
    }

    @Transactional
    public void updateStability(Stability2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.size() != 1) {
            throw new IllegalStateException("Expected one 2024 questionnaire for journal " + record.journalId()
                    + ", found " + questionnaires.size());
        }

        Questionnaire questionnaire = questionnaires.getFirst();
        Stability stability = stability(questionnaire);
        List<ArticlePublished> articles = stability.getArticlePublished() == null
                ? new ArrayList<>() : new ArrayList<>(stability.getArticlePublished());
        Map<Integer, ArticlePublished> articlesByYear = new LinkedHashMap<>();
        for (ArticlePublished article : articles) {
            if (article != null && article.getYear() != null && PUBLICATION_YEARS.contains(article.getYear())
                    && articlesByYear.putIfAbsent(article.getYear(), article) != null) {
                throw new IllegalStateException("Duplicate articlePublished year " + article.getYear()
                        + " in 2024 questionnaire for journal " + record.journalId());
            }
        }

        for (ArticlePublishedValues values : record.articles()) {
            ArticlePublished article = articlesByYear.get(values.year());
            if (article == null) {
                article = new ArticlePublished();
                article.setYear(values.year());
                articles.add(article);
                articlesByYear.put(values.year(), article);
            }
            article.setAmount(values.amount());
            article.setScientificAmount(values.scientificAmount());
            article.setReceivedManuscriptCount(values.receivedManuscriptCount());
            article.setRejectedManuscriptCount(values.rejectedManuscriptCount());
            article.setPublishedIssuesAmount(values.publishedIssuesAmount());
            article.setFrequencyCode(values.frequencyCode());
        }
        stability.setArticlePublished(articles);
        questionnaireRepository.save(questionnaire);
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
                throw new IllegalStateException("Duplicate stability 2024 header in " + file.getName() + ": " + name);
            }
        }
        return new HeaderColumns(
                requiredColumn(columns, "journal.id", file),
                PUBLICATION_YEARS.stream().map(year -> articleColumns(columns, year, file)).toList()
        );
    }

    private ArticleColumns articleColumns(Map<String, Integer> columns, int year, File file) {
        String prefix = ARTICLE_PREFIX + "[year=" + year + "].";
        return new ArticleColumns(
                year,
                requiredColumn(columns, prefix + "amount", file),
                requiredColumn(columns, prefix + "scientificAmount", file),
                requiredColumn(columns, prefix + "receivedManuscriptCount", file),
                requiredColumn(columns, prefix + "rejectedManuscriptCount", file),
                requiredColumn(columns, prefix + "publishedIssuesAmount", file),
                requiredColumn(columns, prefix + "frequencyCode", file)
        );
    }

    private int requiredColumn(Map<String, Integer> columns, String name, File file) {
        Integer column = columns.get(name);
        if (column == null) {
            throw new IllegalStateException("Missing stability 2024 column in " + file.getName() + ": " + name);
        }
        return column;
    }

    private ArticlePublishedValues readArticle(MigrationExcelReader.RowData row, ArticleColumns columns) {
        String prefix = ARTICLE_PREFIX + "[year=" + columns.year() + "].";
        return new ArticlePublishedValues(
                columns.year(),
                readCount(row.value(columns.amount()), prefix + "amount"),
                readCount(row.value(columns.scientificAmount()), prefix + "scientificAmount"),
                readCount(row.value(columns.received()), prefix + "receivedManuscriptCount"),
                readCount(row.value(columns.rejected()), prefix + "rejectedManuscriptCount"),
                readCount(row.value(columns.issues()), prefix + "publishedIssuesAmount"),
                readFrequency(row.value(columns.frequency()), prefix + "frequencyCode")
        );
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

    private JournalFrequency readFrequency(String value, String field) {
        if (missing(value)) {
            return null;
        }
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "dwutygodnik", "c" -> JournalFrequency.c;
            case "dwumiesięcznik", "b" -> JournalFrequency.b;
            case "półrocznik", "f" -> JournalFrequency.f;
            case "nieregularny", "g" -> JournalFrequency.g;
            case "tygodnik", "e" -> JournalFrequency.e;
            case "kwartalnik", "q" -> JournalFrequency.q;
            case "miesięcznik", "m" -> JournalFrequency.m;
            case "rocznik", "a" -> JournalFrequency.a;
            case "ciągła aktualizacja", "ciągła (online)", "k" -> JournalFrequency.k;
            case "3 wydania w roku", "t" -> JournalFrequency.t;
            default -> throw new IllegalArgumentException("Invalid frequency for " + field + ": " + value);
        };
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

    public record Stability2024Record(Long journalId, List<ArticlePublishedValues> articles, String sourceFile, int rowNumber) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    public record ArticlePublishedValues(
            int year,
            Integer amount,
            Integer scientificAmount,
            Integer receivedManuscriptCount,
            Integer rejectedManuscriptCount,
            Integer publishedIssuesAmount,
            JournalFrequency frequencyCode
    ) {
    }

    private record HeaderColumns(int journalId, List<ArticleColumns> articles) {
    }

    private record ArticleColumns(int year, int amount, int scientificAmount, int received, int rejected, int issues, int frequency) {
    }
}
