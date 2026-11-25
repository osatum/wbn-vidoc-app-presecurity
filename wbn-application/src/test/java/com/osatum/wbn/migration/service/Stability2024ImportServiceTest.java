package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Stability2024ImportService.ArticlePublishedValues;
import com.osatum.wbn.migration.service.Stability2024ImportService.Stability2024Record;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

class Stability2024ImportServiceTest {
    @TempDir
    private Path tempDir;

    private final QuestionnaireSpringDataRepository questionnaireRepository = mock(QuestionnaireSpringDataRepository.class);
    private final Stability2024ImportService service = new Stability2024ImportService(questionnaireRepository);

    @ParameterizedTest
    @CsvSource({"xlsx,0", "xlsx,1", "xlsx,4", "xls,0", "xls,1", "xls,4"})
    void detectsHeaderAndReadsAllPublicationYears(String extension, int headerIndex) throws Exception {
        Path file = tempDir.resolve("stability." + extension);
        Object[] values = values(123);
        for (int yearIndex = 0; yearIndex < 4; yearIndex++) {
            int firstColumn = 1 + yearIndex * 6;
            values[firstColumn] = 20 + yearIndex;
            values[firstColumn + 1] = 10 + yearIndex;
            values[firstColumn + 2] = 100 + yearIndex;
            values[firstColumn + 3] = 40 + yearIndex;
            values[firstColumn + 4] = 4;
            values[firstColumn + 5] = "Kwartalnik";
        }
        writeWorkbook(file, headerIndex, headers(), Collections.singletonList(values));

        Stability2024Record record = service.readRecords(file.toString()).getFirst();

        assertThat(record.journalId()).isEqualTo(123L);
        assertThat(record.rowNumber()).isEqualTo(headerIndex + 2);
        assertThat(record.itemName()).isEqualTo("stability." + extension + ":" + (headerIndex + 2) + ":journal-123");
        assertThat(record.articles()).containsExactly(
                new ArticlePublishedValues(2021, 20, 10, 100, 40, 4, JournalFrequency.q),
                new ArticlePublishedValues(2022, 21, 11, 101, 41, 4, JournalFrequency.q),
                new ArticlePublishedValues(2023, 22, 12, 102, 42, 4, JournalFrequency.q),
                new ArticlePublishedValues(2024, 23, 13, 103, 43, 4, JournalFrequency.q)
        );
    }

    @Test
    void mapsReorderedColumnsByTargetPaths() throws Exception {
        Path file = tempDir.resolve("reordered.xlsx");
        List<String> headers = new ArrayList<>(headers());
        Object[] values = values(123);
        values[19] = 50;
        values[24] = "Rocznik";
        List<Object> reversedValues = new ArrayList<>(java.util.Arrays.asList(values));
        Collections.reverse(headers);
        Collections.reverse(reversedValues);
        writeWorkbook(file, 0, headers, Collections.singletonList(reversedValues.toArray()));

        Stability2024Record record = service.readRecords(file.toString()).getFirst();

        assertThat(record.journalId()).isEqualTo(123L);
        assertThat(record.articles().getLast()).isEqualTo(new ArticlePublishedValues(2024, 50, null, null, null, null, JournalFrequency.a));
    }

    @ParameterizedTest
    @ValueSource(strings = {"brak danych", " BRAK DANYCH ", "brak\u00A0danych", "-", " - ", "", " "})
    void clearsMissingMarkersAndRetainsIdentifierOnlyRecords(String marker) throws Exception {
        Path file = tempDir.resolve("missing.xlsx");
        Object[] marked = values(123);
        java.util.Arrays.fill(marked, 1, marked.length, marker);
        writeWorkbook(file, 1, headers(), List.of(marked, values(456)));

        List<Stability2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records).extracting(Stability2024Record::journalId).containsExactly(123L, 456L);
        for (Stability2024Record record : records) {
            assertThat(record.articles()).containsExactly(emptyArticle(2021), emptyArticle(2022), emptyArticle(2023), emptyArticle(2024));
        }
    }

    @Test
    void preservesZerosAndParsesLocalizedIntegerValues() throws Exception {
        Path file = tempDir.resolve("numbers.xlsx");
        Object[] values = values("1 234");
        values[19] = 0;
        values[20] = "0";
        values[21] = "1\u00A0234";
        values[22] = "125,0";
        values[23] = "0,0";
        writeWorkbook(file, 0, headers(), Collections.singletonList(values));

        Stability2024Record record = service.readRecords(file.toString()).getFirst();

        assertThat(record.journalId()).isEqualTo(1234L);
        assertThat(record.articles().getLast()).isEqualTo(new ArticlePublishedValues(2024, 0, 0, 1234, 125, 0, null));
    }

    @ParameterizedTest
    @CsvSource({
            "Dwutygodnik,c", "Dwumiesięcznik,b", "Półrocznik,f", "Nieregularny,g", "Tygodnik,e",
            "Kwartalnik,q", "Miesięcznik,m", "Rocznik,a", "Ciągła aktualizacja,k", "3 wydania w roku,t",
            "c,c", "b,b", "f,f", "g,g", "e,e", "q,q", "m,m", "a,a", "k,k", "t,t",
            " KWARTALNIK ,q", "Ciągła (online),k"
    })
    void mapsFrequencyLabelsAndEnumCodes(String label, JournalFrequency expected) throws Exception {
        Path file = tempDir.resolve("frequency.xlsx");
        Object[] values = values(123);
        values[24] = label;
        writeWorkbook(file, 0, headers(), Collections.singletonList(values));

        assertThat(service.readRecords(file.toString()).getFirst().articles().getLast().frequencyCode()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2.5", "2147483648", "NaN", "Infinity"})
    void rejectsInvalidCountsWithTargetPathAndRow(String value) throws Exception {
        Path file = tempDir.resolve("invalid-count.xlsx");
        Object[] values = values(123);
        values[23] = value;
        writeWorkbook(file, 1, headers(), Collections.singletonList(values));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid-count.xlsx:3:")
                .hasMessageContaining("articlePublished[year=2024].publishedIssuesAmount");
    }

    @ParameterizedTest
    @ValueSource(ints = {19, 20, 21, 22, 23})
    void validatesEachCountColumn(int column) throws Exception {
        Path file = tempDir.resolve("invalid-column.xlsx");
        Object[] values = values(123);
        values[column] = -1;
        writeWorkbook(file, 0, headers(), Collections.singletonList(values));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(headers().get(column));
    }

    @Test
    void rejectsUnknownFrequencyInsteadOfClearingIt() throws Exception {
        Path file = tempDir.resolve("unknown-frequency.xlsx");
        Object[] values = values(123);
        values[24] = "nieznana";
        writeWorkbook(file, 0, headers(), Collections.singletonList(values));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-frequency.xlsx:2:")
                .hasMessageContaining("articlePublished[year=2024].frequencyCode");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "9223372036854775808", "brak danych", "-", ""})
    void rejectsInvalidOrMissingJournalId(String value) throws Exception {
        Path file = tempDir.resolve("invalid-id.xlsx");
        Object[] values = values(value);
        values[1] = 5;
        writeWorkbook(file, 0, headers(), Collections.singletonList(values));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("journal.id");
    }

    @Test
    void rejectsDuplicateJournalIds() throws Exception {
        Path file = tempDir.resolve("duplicate.xlsx");
        writeWorkbook(file, 1, headers(), List.of(values(123), values(123)));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate.xlsx:4: Duplicate journal.id: 123");
    }

    @Test
    void rejectsMissingRequiredColumn() throws Exception {
        Path file = tempDir.resolve("missing-column.xlsx");
        writeWorkbook(file, 0, headers().subList(0, 24), List.of());

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing stability 2024 column")
                .hasMessageContaining("articlePublished[year=2024].frequencyCode");
    }

    @Test
    void rejectsDuplicateHeader() throws Exception {
        Path file = tempDir.resolve("duplicate-header.xlsx");
        List<String> headers = new ArrayList<>(headers());
        headers.set(24, headers.get(23));
        writeWorkbook(file, 0, headers, List.of());

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate stability 2024 header");
    }

    @Test
    void rejectsMissingHeader() throws Exception {
        Path file = tempDir.resolve("missing-header.xlsx");
        writeWorkbook(file, 0, List.of("id"), List.of());

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Missing stability 2024 header with journal.id");
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> service.readRecords(tempDir.resolve("missing.xlsx").toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot find stability 2024 Excel file");
    }

    @Test
    void mergesByYearAndPreservesReviewedAmountsOtherYearsAndSurveySections() {
        Questionnaire questionnaire = questionnaire();
        Survey survey = questionnaire.getSurveyData().getSurvey();
        ArticlePublished current = article(2024);
        ArticlePublished previous = article(2021);
        ArticlePublished untouched = article(2020);
        ArticlePublished withoutYear = article(null);
        survey.getStability().setArticlePublished(List.of(current, untouched, previous, withoutYear));
        survey.getStability().setFirstIssueYear(1980);
        survey.getStability().setFrequencyId(4L);
        survey.getStability().setPublishingBreak(true);
        survey.setTimestamp("original-timestamp");
        var languages = survey.getStability().getArticleLanguages();
        var cooperation = survey.getCooperation();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        Stability2024Record record = new Stability2024Record(123L, List.of(
                emptyArticle(2021), emptyArticle(2022), emptyArticle(2023),
                new ArticlePublishedValues(2024, 0, null, 20, 30, null, JournalFrequency.k)
        ), "stability.xlsx", 3);

        service.updateStability(record);
        service.updateStability(record);

        assertThat(survey.getStability().getArticlePublished()).hasSize(6);
        assertThat(survey.getStability().getArticlePublished().subList(0, 4)).containsExactly(current, untouched, previous, withoutYear);
        assertThat(current).extracting(ArticlePublished::getYear, ArticlePublished::getAmount, ArticlePublished::getScientificAmount,
                ArticlePublished::getReceivedManuscriptCount, ArticlePublished::getRejectedManuscriptCount,
                ArticlePublished::getPublishedIssuesAmount, ArticlePublished::getFrequencyCode, ArticlePublished::getReviewedAmount)
                .containsExactly(2024, 0, null, 20, 30, null, JournalFrequency.k, 8);
        assertThat(previous).extracting(ArticlePublished::getAmount, ArticlePublished::getScientificAmount,
                ArticlePublished::getReceivedManuscriptCount, ArticlePublished::getRejectedManuscriptCount,
                ArticlePublished::getPublishedIssuesAmount, ArticlePublished::getFrequencyCode, ArticlePublished::getReviewedAmount)
                .containsExactly(null, null, null, null, null, null, 8);
        assertThat(untouched).isEqualTo(article(2020));
        assertThat(withoutYear).isEqualTo(article(null));
        assertThat(survey.getStability().getFirstIssueYear()).isEqualTo(1980);
        assertThat(survey.getStability().getFrequencyId()).isEqualTo(4L);
        assertThat(survey.getStability().getPublishingBreak()).isTrue();
        assertThat(survey.getStability().getArticleLanguages()).isSameAs(languages);
        assertThat(survey.getCooperation()).isSameAs(cooperation);
        assertThat(survey.getTimestamp()).isEqualTo("original-timestamp");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void initializesMissingSurveyObjectsAndList(int existingObjects) {
        Questionnaire questionnaire = new Questionnaire();
        if (existingObjects >= 1) {
            questionnaire.setSurveyData(new SurveyData());
        }
        if (existingObjects >= 2) {
            Survey survey = new Survey();
            if (existingObjects == 2) {
                survey.setStability(null);
            } else if (existingObjects == 3) {
                survey.getStability().setArticlePublished(null);
            }
            questionnaire.getSurveyData().setSurvey(survey);
        }
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateStability(record());

        assertThat(questionnaire.getSurveyData().getSurvey().getStability().getArticlePublished())
                .extracting(ArticlePublished::getYear).containsExactly(2021, 2022, 2023, 2024);
        verify(questionnaireRepository).save(questionnaire);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void rejectsMissingOrAmbiguousQuestionnaire(int count) {
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L))
                .thenReturn(Collections.nCopies(count, new Questionnaire()));

        assertThatThrownBy(() -> service.updateStability(record()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expected one 2024 questionnaire for journal 123, found " + count);
        verify(questionnaireRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicatePublicationYearBeforeChangingValues() {
        Questionnaire questionnaire = questionnaire();
        ArticlePublished first = article(2024);
        questionnaire.getSurveyData().getSurvey().getStability().setArticlePublished(List.of(first, article(2024)));
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        assertThatThrownBy(() -> service.updateStability(record()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate articlePublished year 2024");
        assertThat(first).isEqualTo(article(2024));
        verify(questionnaireRepository, never()).save(any());
    }

    private Questionnaire questionnaire() {
        Questionnaire questionnaire = new Questionnaire();
        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(new Survey());
        questionnaire.setSurveyData(surveyData);
        return questionnaire;
    }

    private ArticlePublished article(Integer year) {
        ArticlePublished article = new ArticlePublished();
        article.setYear(year);
        article.setAmount(10);
        article.setScientificAmount(9);
        article.setReviewedAmount(8);
        article.setReceivedManuscriptCount(30);
        article.setRejectedManuscriptCount(5);
        article.setPublishedIssuesAmount(4);
        article.setFrequencyCode(JournalFrequency.q);
        return article;
    }

    private Stability2024Record record() {
        return new Stability2024Record(123L, List.of(emptyArticle(2021), emptyArticle(2022), emptyArticle(2023), emptyArticle(2024)), "stability.xlsx", 3);
    }

    private ArticlePublishedValues emptyArticle(int year) {
        return new ArticlePublishedValues(year, null, null, null, null, null, null);
    }

    private Object[] values(Object journalId) {
        Object[] values = new Object[25];
        values[0] = journalId;
        return values;
    }

    private List<String> headers() {
        List<String> headers = new ArrayList<>();
        headers.add("journal.id");
        for (int year = 2021; year <= 2024; year++) {
            for (String field : List.of("amount", "scientificAmount", "receivedManuscriptCount", "rejectedManuscriptCount", "publishedIssuesAmount", "frequencyCode")) {
                headers.add("surveyData.survey.stability.articlePublished[year=" + year + "]." + field);
            }
        }
        return headers;
    }

    private void writeWorkbook(Path file, int headerIndex, List<String> headers, List<Object[]> records) throws Exception {
        try (Workbook workbook = file.toString().endsWith(".xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Stabilność wydawnicza");
            if (headerIndex > 0) {
                sheet.createRow(headerIndex - 1).createCell(1).setCellValue(2021);
            }
            var header = sheet.createRow(headerIndex);
            for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                header.createCell(columnIndex).setCellValue(headers.get(columnIndex));
            }
            for (int recordIndex = 0; recordIndex < records.size(); recordIndex++) {
                var row = sheet.createRow(headerIndex + recordIndex + 1);
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
            sheet.createRow(headerIndex + records.size() + 1);
            try (OutputStream output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
    }
}
