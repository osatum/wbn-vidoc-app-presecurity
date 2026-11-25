package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Reviewers2024ImportService.ReviewerValues;
import com.osatum.wbn.migration.service.Reviewers2024ImportService.Reviewers2024Record;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class Reviewers2024ImportServiceTest {
    @TempDir
    private Path tempDir;

    private final QuestionnaireSpringDataRepository questionnaireRepository = mock(QuestionnaireSpringDataRepository.class);
    private final DictionaryCountryService dictionaryCountryService = mock(DictionaryCountryService.class);
    private final Reviewers2024ImportService service = new Reviewers2024ImportService(questionnaireRepository, dictionaryCountryService);

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void readsPolishHeadersGroupsNonadjacentRowsAndRemovesDuplicates(String extension) throws Exception {
        Path file = tempDir.resolve("reviewers." + extension);
        writeWorkbook(file, List.of("E-mail", "\tAfiliacja", "Identyfikator czasopisma", "Kraj", "Imię i nazwisko"), List.of(
                List.of("-", " University ", 123, "Polska", " Anna Nowak "),
                List.of("brak danych", "brak danych", 456, "brak danych", "brak danych"),
                List.of("-", "University", "123", "Polska", "Anna Nowak"),
                List.of("other@example.org", "Other", 123, "PL", "Jan Kowalski")
        ));

        List<Reviewers2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123);
        assertThat(records.getFirst().reviewers()).containsExactly(
                new ReviewerValues("Anna Nowak", "University", "Polska", null),
                new ReviewerValues("Jan Kowalski", "Other", "PL", "other@example.org")
        );
        assertThat(records.getFirst().itemName()).isEqualTo("reviewers." + extension + ":3:journal-123");
        assertThat(records.getLast().journalId()).isEqualTo(456);
        assertThat(records.getLast().reviewers()).isEmpty();
        verifyNoInteractions(questionnaireRepository, dictionaryCountryService);
    }

    @Test
    void readsTechnicalHeadersAndSkipsUnnamedPeopleWithoutCreatingClearRecords() throws Exception {
        Path file = tempDir.resolve("reviewers.xlsx");
        writeWorkbook(file, headers(), List.of(
                List.of(123, "Anna Nowak", "-", "Polska", "-"),
                List.of(123, "brak danych", "Other", "Polska", "contact@example.org"),
                List.of(456, "", "Other", "Polska", "-"),
                List.of(789, " BRAK\u00a0DANYCH ", "brak danych", "brak danych", "brak danych"),
                List.of()
        ));

        List<Reviewers2024Record> records = service.readRecords(file.toString());

        assertThat(records).extracting(Reviewers2024Record::journalId).containsExactly(123L, 789L);
        assertThat(records.getFirst().reviewers()).containsExactly(new ReviewerValues("Anna Nowak", null, "Polska", null));
        assertThat(records.getLast().reviewers()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsConflictingClearAndPopulatedRowsInEitherOrder(boolean clearFirst) throws Exception {
        Path file = tempDir.resolve("reviewers.xlsx");
        List<Object> clear = List.of(123, "brak danych", "brak danych", "brak danych", "brak danych");
        List<Object> person = List.of(123, "Anna Nowak", "University", "PL", "-");
        writeWorkbook(file, headers(), clearFirst ? List.of(clear, person) : List.of(person, clear));

        assertThatThrownBy(() -> service.readRecords(file.toString())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reviewers.xlsx:4:").hasMessageContaining("Conflicting empty and populated reviewers");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "brak danych", "0", "-1", "1.5", "invalid"})
    void rejectsInvalidJournalIdentifiersWithSourceRow(String identifier) throws Exception {
        Path file = tempDir.resolve("reviewers.xlsx");
        writeWorkbook(file, headers(), List.of(List.of(identifier, "Anna Nowak", "University", "PL", "-")));

        assertThatThrownBy(() -> service.readRecords(file.toString())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reviewers.xlsx:3:").hasMessageContaining("journal.id");
    }

    @Test
    void rejectsMissingDuplicateAndAmbiguousHeaders() throws Exception {
        Path file = tempDir.resolve("reviewers.xlsx");
        List<String> columns = new ArrayList<>(headers());
        columns.removeLast();
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Missing reviewers 2024 column");
        columns = new ArrayList<>(headers());
        columns.add("reviewersList[].email");
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Duplicate reviewers 2024 header");
        columns = new ArrayList<>(headers());
        columns.add("Identyfikator czasopisma");
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Ambiguous reviewers 2024 column");
    }

    @Test
    void rejectsMissingFileAndHeader() throws Exception {
        Path file = tempDir.resolve("reviewers.xlsx");
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Cannot find reviewers 2024 Excel file");
        writeWorkbook(file, List.of("Wrong header"), List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Missing reviewers 2024 header");
    }

    @Test
    void replacesOnlyReviewersAndResolvesCountryNamesAndCodesBeforeDeduplication() {
        Questionnaire questionnaire = questionnaire();
        Survey survey = questionnaire.getSurveyData().getSurvey();
        ScientistMetric metric = new ScientistMetric();
        metric.setUnique(99);
        survey.getCooperation().setReviewerMetric(metric);
        survey.getCooperation().setAuthorMetric(metric);
        survey.getCooperation().setDirectorsMetric(metric);
        survey.getCooperation().setReviewers("legacy");
        Cooperator director = new Cooperator();
        director.setFullName("Director");
        survey.getCooperation().setDirectorsList(List.of(director));
        survey.getCooperation().setTitleAndAbstractInAllArticles(true);
        survey.getStability().setFirstIssueYear(1980);
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryCountryService.getAll()).thenReturn(List.of(country("PL", "POL", "Polska", "Poland", "Польша")));

        service.updateReviewers(record(
                new ReviewerValues("Anna Nowak", "University", " Polska ", null),
                new ReviewerValues("Anna Nowak", "University", "pl", null),
                new ReviewerValues("Jan Kowalski", null, "Poland", "jan@example.org")
        ));

        assertThat(survey.getCooperation().getReviewersList()).hasSize(2);
        assertThat(survey.getCooperation().getReviewersList().getFirst()).extracting("fullName", "affiliation", "country", "email")
                .containsExactly("Anna Nowak", "University", "PL", null);
        assertThat(survey.getCooperation().getReviewersList().getLast().getCountry()).isEqualTo("PL");
        assertThat(survey.getCooperation()).extracting("authorMetric", "reviewerMetric", "directorsMetric", "reviewers", "titleAndAbstractInAllArticles")
                .containsExactly(metric, metric, metric, "legacy", true);
        assertThat(survey.getCooperation().getDirectorsList()).containsExactly(director);
        assertThat(survey.getStability().getFirstIssueYear()).isEqualTo(1980);
        assertThat(survey.getTimestamp()).isEqualTo("original");
        verify(questionnaireRepository).save(questionnaire);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PL", "pol", "Polska", "Poland", "Польша"})
    void resolvesCountryAliases(String alias) {
        Questionnaire questionnaire = questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryCountryService.getAll()).thenReturn(List.of(country("PL", "POL", "Polska", "Poland", "Польша")));

        service.updateReviewers(record(new ReviewerValues("Anna Nowak", null, alias, null)));

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getReviewersList().getFirst().getCountry()).isEqualTo("PL");
    }

    @Test
    void clearsAllExistingReviewersWithoutRequiringCountries() {
        Questionnaire questionnaire = questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateReviewers(record());

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getReviewersList()).isEmpty();
        verify(questionnaireRepository).save(questionnaire);
        verifyNoInteractions(dictionaryCountryService);
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
            questionnaire.getSurveyData().getSurvey().setCooperation(null);
        }
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateReviewers(record(new ReviewerValues("Anna Nowak", null, null, null)));

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getReviewersList()).hasSize(1);
        verify(questionnaireRepository).save(questionnaire);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsUnknownOrAmbiguousCountriesWithoutMutatingQuestionnaire(boolean ambiguous) {
        Questionnaire questionnaire = new Questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryCountryService.getAll()).thenReturn(ambiguous
                ? List.of(country("PL", "POL", "Same", "Poland", null), country("DE", "DEU", "Same", "Germany", null))
                : List.of(country("PL", "POL", "Polska", "Poland", null)));

        assertThatThrownBy(() -> service.updateReviewers(record(new ReviewerValues("Anna Nowak", null, ambiguous ? "Same" : "Unknown", null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(ambiguous ? "Ambiguous country" : "Cannot find country");
        assertThat(questionnaire.getSurveyData()).isNull();
        verify(questionnaireRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void requiresExactlyOne2024Questionnaire(int count) {
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L))
                .thenReturn(count == 0 ? List.of() : List.of(questionnaire(), questionnaire()));

        assertThatThrownBy(() -> service.updateReviewers(record())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected one 2024 questionnaire").hasMessageContaining("found " + count);
        verify(questionnaireRepository, never()).save(any());
    }

    private Questionnaire questionnaire() {
        Questionnaire questionnaire = new Questionnaire();
        SurveyData surveyData = new SurveyData();
        Survey survey = new Survey();
        survey.setTimestamp("original");
        Cooperator existing = new Cooperator();
        existing.setFullName("Existing reviewer");
        survey.getCooperation().setReviewersList(new ArrayList<>(List.of(existing)));
        surveyData.setSurvey(survey);
        questionnaire.setSurveyData(surveyData);
        return questionnaire;
    }

    private DictionaryCountry country(String code, String alpha3, String namePl, String nameEn, String nameRu) {
        DictionaryCountry country = new DictionaryCountry();
        country.setCode(code);
        country.setCodeAlpha3(alpha3);
        country.setNamePl(namePl);
        country.setNameEn(nameEn);
        country.setNameRu(nameRu);
        return country;
    }

    private Reviewers2024Record record(ReviewerValues... reviewers) {
        return new Reviewers2024Record(123L, Arrays.asList(reviewers), "reviewers.xlsx", 2);
    }

    private List<String> headers() {
        return List.of("Questionnaire.journal.id", "reviewersList[].fullName", "reviewersList[].affiliation", "reviewersList[].country", "reviewersList[].email");
    }

    private void writeWorkbook(Path file, List<String> headers, List<? extends List<?>> rows) throws Exception {
        try (Workbook workbook = file.toString().endsWith(".xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Reviewers");
            sheet.createRow(0).createCell(0).setCellValue("Reviewers for 2024");
            var header = sheet.createRow(1);
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                var row = sheet.createRow(rowIndex + 2);
                for (int column = 0; column < rows.get(rowIndex).size(); column++) {
                    Object value = rows.get(rowIndex).get(column);
                    if (value instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(column).setCellValue(value.toString());
                    }
                }
            }
            try (var output = Files.newOutputStream(file)) {
                workbook.write(output);
            }
        }
    }
}
