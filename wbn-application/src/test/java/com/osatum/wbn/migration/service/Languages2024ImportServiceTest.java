package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryLanguageService;
import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticleLanguage;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Languages2024ImportService.ArticleLanguageValues;
import com.osatum.wbn.migration.service.Languages2024ImportService.Languages2024Record;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Languages2024ImportServiceTest {
    private static final String PREFIX = "surveyData.survey.stability.articleLanguages[].";

    @TempDir
    private Path tempDir;

    private final QuestionnaireSpringDataRepository questionnaireRepository = mock(QuestionnaireSpringDataRepository.class);
    private final DictionaryLanguageService dictionaryLanguageService = mock(DictionaryLanguageService.class);
    private final Languages2024ImportService service = new Languages2024ImportService(questionnaireRepository, dictionaryLanguageService);

    @ParameterizedTest
    @CsvSource({"xlsx,0", "xlsx,1", "xlsx,4", "xls,0", "xls,1", "xls,4"})
    void detectsHeaderAndGroupsNonadjacentRowsByJournal(String extension, int headerIndex) throws Exception {
        Path file = tempDir.resolve("languages." + extension);
        writeWorkbook(file, headerIndex, headers(), List.of(
                new Object[]{123, "en", 16, 15},
                new Object[]{456, "UK", 10, 8},
                new Object[]{null, null, null, null},
                new Object[]{123, " PL ", 5, 0}
        ));

        List<Languages2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().rowNumber()).isEqualTo(headerIndex + 2);
        assertThat(records.getFirst().itemName()).isEqualTo("languages." + extension + ":" + (headerIndex + 2) + ":journal-123");
        assertThat(records.getFirst().languages()).containsExactly(
                new ArticleLanguageValues("EN", 16, 15), new ArticleLanguageValues("PL", 5, 0)
        );
        assertThat(records.getLast().languages()).containsExactly(new ArticleLanguageValues("UK", 10, 8));
    }

    @Test
    void mapsReorderedColumnsByTargetPaths() throws Exception {
        Path file = tempDir.resolve("reordered.xlsx");
        List<String> headers = new ArrayList<>(headers());
        Collections.reverse(headers);
        writeWorkbook(file, 0, headers, Collections.singletonList(new Object[]{8, 10, "UK", 123}));

        Languages2024Record record = service.readRecords(file.toString()).getFirst();

        assertThat(record.journalId()).isEqualTo(123L);
        assertThat(record.languages()).containsExactly(new ArticleLanguageValues("UK", 10, 8));
    }

    @ParameterizedTest
    @ValueSource(strings = {"brak danych", " BRAK DANYCH ", "brak\u00A0danych", "-", " - ", "", " "})
    void readsMissingCountsAsNull(String marker) throws Exception {
        Path file = tempDir.resolve("missing.xlsx");
        writeWorkbook(file, 0, headers(), Collections.singletonList(new Object[]{123, "EN", marker, marker}));

        assertThat(service.readRecords(file.toString()).getFirst().languages())
                .containsExactly(new ArticleLanguageValues("EN", null, null));
    }

    @Test
    void preservesZerosAndParsesLocalizedIntegers() throws Exception {
        Path file = tempDir.resolve("numbers.xlsx");
        writeWorkbook(file, 0, headers(), List.of(
                new Object[]{"1 234", "EN", 0, "0,0"}, new Object[]{"1\u00A0234", "PL", "1\u00A0234", "125,0"}
        ));

        Languages2024Record record = service.readRecords(file.toString()).getFirst();

        assertThat(record.journalId()).isEqualTo(1234L);
        assertThat(record.languages()).containsExactly(new ArticleLanguageValues("EN", 0, 0), new ArticleLanguageValues("PL", 1234, 125));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "2.5", "2147483648", "NaN", "Infinity"})
    void rejectsInvalidCountsInBothYearsWithSourceRowAndTargetField(String value) throws Exception {
        for (int column : List.of(2, 3)) {
            Path file = tempDir.resolve("invalid-count.xlsx");
            Object[] row = {123, "EN", 10, 8};
            row[column] = value;
            writeWorkbook(file, 0, headers(), Collections.singletonList(row));

            assertThatThrownBy(() -> service.readRecords(file.toString()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid-count.xlsx:2:")
                    .hasMessageContaining(headers().get(column));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "9223372036854775808", "abc", "", "-", "brak danych"})
    void rejectsInvalidJournalIdentifiers(String value) throws Exception {
        Path file = tempDir.resolve("invalid-id.xlsx");
        writeWorkbook(file, 0, headers(), Collections.singletonList(new Object[]{value, "EN", 10, 8}));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid-id.xlsx:2:").hasMessageContaining("journal.id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-", "brak danych", "Angielski", "ENG", "E1"})
    void requiresLanguageCodesInsteadOfNamesOrMissingMarkers(String value) throws Exception {
        Path file = tempDir.resolve("invalid-language.xlsx");
        writeWorkbook(file, 0, headers(), Collections.singletonList(new Object[]{123, value, 10, 8}));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid-language.xlsx:2:");
    }

    @Test
    void rejectsDuplicateJournalLanguagePairsIgnoringCodeCase() throws Exception {
        Path file = tempDir.resolve("duplicate.xlsx");
        writeWorkbook(file, 0, headers(), List.of(new Object[]{123, "en", 10, 8}, new Object[]{123, "EN", 5, 4}));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate.xlsx:3:").hasMessageContaining("Duplicate language code EN");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void requiresEveryTargetHeader(int missingColumn) throws Exception {
        Path file = tempDir.resolve("missing-header.xlsx");
        List<String> headers = new ArrayList<>(headers());
        headers.set(missingColumn, "unrecognized");
        writeWorkbook(file, 0, headers, Collections.singletonList(new Object[]{123, "EN", 10, 8}));

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing-header.xlsx");
    }

    @Test
    void rejectsDuplicateHeaders() throws Exception {
        Path file = tempDir.resolve("duplicate-header.xlsx");
        List<String> headers = new ArrayList<>(headers());
        headers.set(3, headers.get(2));
        writeWorkbook(file, 0, headers, List.of());

        assertThatThrownBy(() -> service.readRecords(file.toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate languages 2024 header");
    }

    @Test
    void rejectsMissingFile() {
        assertThatThrownBy(() -> service.readRecords(tempDir.resolve("absent.xlsx").toString()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot find languages 2024 Excel file");
    }

    @Test
    void replacesWholeLanguageListAndHydratesAllNamesWithoutChangingOtherFields() {
        Questionnaire questionnaire = questionnaire();
        Survey survey = questionnaire.getSurveyData().getSurvey();
        ArticleLanguage existing = new ArticleLanguage();
        existing.setCurrentValue(999);
        List<ArticleLanguage> originalLanguages = List.of(existing);
        survey.getStability().setArticleLanguages(originalLanguages);
        survey.getStability().setFirstIssueYear(1980);
        ArticlePublished publication = new ArticlePublished();
        publication.setAmount(42);
        survey.getStability().setArticlePublished(List.of(publication));
        survey.setTimestamp("original");
        var cooperation = survey.getCooperation();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryLanguageService.getByCode("UK")).thenReturn(dictionary("UK", "Ukraiński", "Ukrainian", "Украинский"));
        Languages2024Record record = record(new ArticleLanguageValues("UK", null, 0));

        service.updateLanguages(record);
        service.updateLanguages(record);

        assertThat(survey.getStability().getArticleLanguages()).singleElement().satisfies(article -> {
            assertThat(article.getLanguage()).extracting("code", "namePl", "nameEn", "nameRu")
                    .containsExactly("UK", "Ukraiński", "Ukrainian", "Украинский");
            assertThat(article).extracting("previousYear", "previousValue", "currentYear", "currentValue")
                    .containsExactly(2023, null, 2024, 0);
        });
        assertThat(originalLanguages).containsExactly(existing);
        assertThat(existing.getCurrentValue()).isEqualTo(999);
        assertThat(survey.getStability().getFirstIssueYear()).isEqualTo(1980);
        assertThat(survey.getStability().getArticlePublished()).containsExactly(publication);
        assertThat(survey.getCooperation()).isSameAs(cooperation);
        assertThat(survey.getTimestamp()).isEqualTo("original");
        verify(questionnaireRepository, times(2)).save(questionnaire);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void initializesMissingSurveyObjects(int missingLevel) {
        Questionnaire questionnaire = questionnaire();
        if (missingLevel == 0) {
            questionnaire.setSurveyData(null);
        } else if (missingLevel == 1) {
            questionnaire.getSurveyData().setSurvey(null);
        } else {
            questionnaire.getSurveyData().getSurvey().setStability(null);
        }
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryLanguageService.getByCode("EN")).thenReturn(dictionary("EN", "Angielski", "English", "Английский"));

        service.updateLanguages(record(new ArticleLanguageValues("EN", 10, 8)));

        assertThat(questionnaire.getSurveyData().getSurvey().getStability().getArticleLanguages()).hasSize(1);
        verify(questionnaireRepository).save(questionnaire);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void rejectsMissingOrAmbiguousQuestionnairesWithoutSaving(int matches) {
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L))
                .thenReturn(matches == 0 ? List.of() : List.of(questionnaire(), questionnaire()));

        assertThatThrownBy(() -> service.updateLanguages(record(new ArticleLanguageValues("EN", 10, 8))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("found " + matches);
        verify(questionnaireRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownDictionaryCodesBeforeReplacingExistingLanguages() {
        Questionnaire questionnaire = questionnaire();
        List<ArticleLanguage> original = List.of(new ArticleLanguage());
        questionnaire.getSurveyData().getSurvey().getStability().setArticleLanguages(original);
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryLanguageService.getByCode("EN")).thenReturn(dictionary("EN", "Angielski", "English", "Английский"));

        assertThatThrownBy(() -> service.updateLanguages(record(new ArticleLanguageValues("EN", 10, 8), new ArticleLanguageValues("ZZ", 5, 4))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot find language dictionary entry: ZZ");
        assertThat(questionnaire.getSurveyData().getSurvey().getStability().getArticleLanguages()).isSameAs(original);
        verify(questionnaireRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void rejectsIncompleteDictionaryNamesWithoutMutatingQuestionnaire(int missingName) {
        Questionnaire questionnaire = new Questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        DictionaryLanguage language = dictionary("EN", "Angielski", "English", "Английский");
        if (missingName == 0) {
            language.setNamePl(null);
        } else if (missingName == 1) {
            language.setNameEn("");
        } else {
            language.setNameRu(" ");
        }
        when(dictionaryLanguageService.getByCode("EN")).thenReturn(language);

        assertThatThrownBy(() -> service.updateLanguages(record(new ArticleLanguageValues("EN", 10, 8))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Incomplete language dictionary entry: EN");
        assertThat(questionnaire.getSurveyData()).isNull();
        verify(questionnaireRepository, never()).save(any());
    }

    private Languages2024Record record(ArticleLanguageValues... languages) {
        return new Languages2024Record(123L, Arrays.asList(languages), "languages.xlsx", 2);
    }

    private Questionnaire questionnaire() {
        Questionnaire questionnaire = new Questionnaire();
        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(new Survey());
        questionnaire.setSurveyData(surveyData);
        return questionnaire;
    }

    private DictionaryLanguage dictionary(String code, String namePl, String nameEn, String nameRu) {
        DictionaryLanguage language = new DictionaryLanguage();
        language.setCode(code);
        language.setNamePl(namePl);
        language.setNameEn(nameEn);
        language.setNameRu(nameRu);
        return language;
    }

    private List<String> headers() {
        return List.of("journal.id", PREFIX + "language.code", PREFIX + "previousValue", PREFIX + "currentValue");
    }

    private void writeWorkbook(Path file, int headerIndex, List<String> headers, List<Object[]> rows) throws Exception {
        try (Workbook workbook = file.toString().endsWith(".xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Languages");
            if (headerIndex > 0) {
                sheet.createRow(0).createCell(0).setCellValue("Języki publikacji");
            }
            var header = sheet.createRow(headerIndex);
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(headerIndex + rowIndex + 1);
                Object[] values = rows.get(rowIndex);
                for (int column = 0; column < values.length; column++) {
                    if (values[column] instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else if (values[column] != null) {
                        row.createCell(column).setCellValue(values[column].toString());
                    }
                }
            }
            try (OutputStream output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
    }
}
