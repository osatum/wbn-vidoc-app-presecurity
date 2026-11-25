package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryCountryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Cooperator;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.Directors2024ImportService.DirectorValues;
import com.osatum.wbn.migration.service.Directors2024ImportService.Directors2024Record;
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

class Directors2024ImportServiceTest {
    @TempDir
    private Path tempDir;

    private final QuestionnaireSpringDataRepository questionnaireRepository = mock(QuestionnaireSpringDataRepository.class);
    private final DictionaryCountryService dictionaryCountryService = mock(DictionaryCountryService.class);
    private final Directors2024ImportService service = new Directors2024ImportService(questionnaireRepository, dictionaryCountryService);

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void readsPolishHeadersGroupsNonadjacentRowsAndRemovesDuplicates(String extension) throws Exception {
        Path file = tempDir.resolve("directors." + extension);
        writeWorkbook(file, List.of("E-mail", "\tAfiliacja", "Identyfikator czasopisma", "Kraj", "Imię i nazwisko"), List.of(
                List.of("-", " University ", 123, "Polska", " Anna Nowak "),
                List.of("brak danych", "brak danych", 456, "brak danych", "brak danych"),
                List.of("-", "University", "123", "Polska", "Anna Nowak"),
                List.of("other@example.org", "Other", 123, "PL", "Jan Kowalski")
        ));

        List<Directors2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123);
        assertThat(records.getFirst().directors()).containsExactly(
                new DirectorValues("Anna Nowak", "University", "Polska", null),
                new DirectorValues("Jan Kowalski", "Other", "PL", "other@example.org")
        );
        assertThat(records.getFirst().itemName()).isEqualTo("directors." + extension + ":3:journal-123");
        assertThat(records.getLast().journalId()).isEqualTo(456);
        assertThat(records.getLast().directors()).isEmpty();
        verifyNoInteractions(questionnaireRepository, dictionaryCountryService);
    }

    @Test
    void readsTechnicalHeadersAndSkipsUnnamedPeopleWithoutCreatingClearRecords() throws Exception {
        Path file = tempDir.resolve("directors.xlsx");
        writeWorkbook(file, headers(), List.of(
                List.of(123, "Anna Nowak", "-", "Polska", "-"),
                List.of(123, "brak danych", "Other", "Polska", "contact@example.org"),
                List.of(456, "", "Other", "Polska", "-"),
                List.of(789, " BRAK\u00a0DANYCH ", "brak danych", "brak danych", "brak danych"),
                List.of()
        ));

        List<Directors2024Record> records = service.readRecords(file.toString());

        assertThat(records).extracting(Directors2024Record::journalId).containsExactly(123L, 789L);
        assertThat(records.getFirst().directors()).containsExactly(new DirectorValues("Anna Nowak", null, "Polska", null));
        assertThat(records.getLast().directors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void keepsPopulatedDirectorsWhenOtherRowsAreEmptyInEitherOrder(boolean clearFirst) throws Exception {
        Path file = tempDir.resolve("directors.xlsx");
        List<Object> clear = List.of(123, "brak danych", "brak danych", "brak danych", "brak danych");
        List<Object> person = List.of(123, "Anna Nowak", "University", "PL", "-");
        writeWorkbook(file, headers(), clearFirst ? List.of(clear, person) : List.of(person, clear));

        assertThat(service.readRecords(file.toString())).singleElement().satisfies(record ->
                assertThat(record.directors()).containsExactly(new DirectorValues("Anna Nowak", "University", "PL", null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xls"})
    void clearsDirectorsWhenEveryRowForJournalHasEmptyFields(String extension) throws Exception {
        Path file = tempDir.resolve("directors." + extension);
        writeWorkbook(file, headers(), List.of(
                List.of(123, "", "", "", ""),
                List.of(123, "brak danych", "-", "brak danych", "-"),
                List.of(123, "-", "brak danych", "-", "brak danych")
        ));
        Questionnaire questionnaire = questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        List<Directors2024Record> records = service.readRecords(file.toString());
        assertThat(records).singleElement().satisfies(record -> assertThat(record.directors()).isEmpty());
        service.updateDirectors(records.getFirst());

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getDirectorsList()).isEmpty();
        verify(questionnaireRepository).save(questionnaire);
        verifyNoInteractions(dictionaryCountryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "brak danych", "0", "-1", "1.5", "invalid"})
    void rejectsInvalidJournalIdentifiersWithSourceRow(String identifier) throws Exception {
        Path file = tempDir.resolve("directors.xlsx");
        writeWorkbook(file, headers(), List.of(List.of(identifier, "Anna Nowak", "University", "PL", "-")));

        assertThatThrownBy(() -> service.readRecords(file.toString())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("directors.xlsx:3:").hasMessageContaining("journal.id");
    }

    @Test
    void rejectsMissingDuplicateAndAmbiguousHeaders() throws Exception {
        Path file = tempDir.resolve("directors.xlsx");
        List<String> columns = new ArrayList<>(headers());
        columns.removeLast();
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Missing directors 2024 column");
        columns = new ArrayList<>(headers());
        columns.add("directorsList[].email");
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Duplicate directors 2024 header");
        columns = new ArrayList<>(headers());
        columns.add("Identyfikator czasopisma");
        writeWorkbook(file, columns, List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Ambiguous directors 2024 column");
    }

    @Test
    void rejectsMissingFileAndHeader() throws Exception {
        Path file = tempDir.resolve("directors.xlsx");
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Cannot find directors 2024 Excel file");
        writeWorkbook(file, List.of("Wrong header"), List.of());
        assertThatThrownBy(() -> service.readRecords(file.toString())).hasMessageContaining("Missing directors 2024 header");
    }

    @Test
    void replacesOnlyDirectorsAndResolvesCountryNamesAndCodesBeforeDeduplication() {
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
        survey.getCooperation().setReviewersList(List.of(director));
        survey.getCooperation().setTitleAndAbstractInAllArticles(true);
        survey.getStability().setFirstIssueYear(1980);
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryCountryService.getAll()).thenReturn(List.of(country("PL", "POL", "Polska", "Poland", "Польша")));

        service.updateDirectors(record(
                new DirectorValues("Anna Nowak", "University", " Polska ", null),
                new DirectorValues("Anna Nowak", "University", "pl", null),
                new DirectorValues("Jan Kowalski", null, "Poland", "jan@example.org")
        ));

        assertThat(survey.getCooperation().getDirectorsList()).hasSize(2);
        assertThat(survey.getCooperation().getDirectorsList().getFirst()).extracting("fullName", "affiliation", "country", "email")
                .containsExactly("Anna Nowak", "University", "PL", null);
        assertThat(survey.getCooperation().getDirectorsList().getLast().getCountry()).isEqualTo("PL");
        assertThat(survey.getCooperation()).extracting("authorMetric", "reviewerMetric", "directorsMetric", "reviewers", "titleAndAbstractInAllArticles")
                .containsExactly(metric, metric, metric, "legacy", true);
        assertThat(survey.getCooperation().getReviewersList()).containsExactly(director);
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

        service.updateDirectors(record(new DirectorValues("Anna Nowak", null, alias, null)));

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getDirectorsList().getFirst().getCountry()).isEqualTo("PL");
    }

    @Test
    void clearsAllExistingDirectorsWithoutRequiringCountries() {
        Questionnaire questionnaire = questionnaire();
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));

        service.updateDirectors(record());

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getDirectorsList()).isEmpty();
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

        service.updateDirectors(record(new DirectorValues("Anna Nowak", null, null, null)));

        assertThat(questionnaire.getSurveyData().getSurvey().getCooperation().getDirectorsList()).hasSize(1);
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

        assertThatThrownBy(() -> service.updateDirectors(record(new DirectorValues("Anna Nowak", null, ambiguous ? "Same" : "Unknown", null))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(ambiguous ? "Ambiguous country" : "Cannot find country");
        assertThat(questionnaire.getSurveyData()).isNull();
        verify(questionnaireRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void requiresExactlyOne2024Questionnaire(int count) {
        when(questionnaireRepository.findByYearAndJournalId(2024, 123L))
                .thenReturn(count == 0 ? List.of() : List.of(questionnaire(), questionnaire()));

        assertThatThrownBy(() -> service.updateDirectors(record())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected one 2024 questionnaire").hasMessageContaining("found " + count);
        verify(questionnaireRepository, never()).save(any());
    }

    private Questionnaire questionnaire() {
        Questionnaire questionnaire = new Questionnaire();
        SurveyData surveyData = new SurveyData();
        Survey survey = new Survey();
        survey.setTimestamp("original");
        Cooperator existing = new Cooperator();
        existing.setFullName("Existing director");
        survey.getCooperation().setDirectorsList(new ArrayList<>(List.of(existing)));
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

    private Directors2024Record record(DirectorValues... directors) {
        return new Directors2024Record(123L, Arrays.asList(directors), "directors.xlsx", 2);
    }

    private List<String> headers() {
        return List.of("Questionnaire.journal.id", "directorsList[].fullName", "directorsList[].affiliation", "directorsList[].country", "directorsList[].email");
    }

    private void writeWorkbook(Path file, List<String> headers, List<? extends List<?>> rows) throws Exception {
        try (Workbook workbook = file.toString().endsWith(".xls") ? new HSSFWorkbook() : new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Directors");
            sheet.createRow(0).createCell(0).setCellValue("Directors for 2024");
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

