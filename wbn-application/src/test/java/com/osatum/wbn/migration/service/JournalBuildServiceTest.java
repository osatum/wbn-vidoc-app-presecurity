package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.*;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.questionnaire.assembler.SurveyDtoMapper;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.SurveyJsonConverter;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionStatus;
import com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy;
import com.osatum.wbn.domain.service.journal.JournalSurveyPolicy;
import com.osatum.wbn.infrastructure.persistence.JournalRepositoryAdapter;
import com.osatum.wbn.migration.assembler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JournalBuildServiceTest {
    private static final List<String> EXTENSION_PATHS = List.of(
            "/publishingVersions/publishedArticlesCount", "/publishingVersions/electronicFullTextArticlesCount",
            "/publishingVersions/aopArticlesCount", "/stability/englishTitleAndAbstractArticlesCount",
            "/standards/publishedReviewersListWww", "/standards/publicFormWww", "/standards/publicCriteriaWww",
            "/standards/internationalPoliciesAdopted", "/standards/internationalPoliciesWww",
            "/digitalization/infoWebsite/directorsWww", "/digitalization/infoWebsite/editOfficeInfoWww",
            "/digitalization/infoWebsite/antiAbusePolicyInfo", "/digitalization/infoWebsite/antiAbusePolicyInfoWww",
            "/digitalization/scientificQuality/indicatorJCI",
            "/cooperation/authorMetric/uniqueExternal", "/cooperation/authorMetric/uniqueForeign",
            "/cooperation/reviewerMetric/uniqueExternal", "/cooperation/reviewerMetric/uniqueForeign",
            "/cooperation/directorsMetric/uniqueExternal", "/cooperation/directorsMetric/uniqueForeign");

    private final AuthorFeeSelectionPolicy policy = new AuthorFeeSelectionPolicy();
    private final JournalRepositoryAdapter repository = mock(JournalRepositoryAdapter.class);
    private final ToJournalAssembler assembler = new ToJournalAssembler(
            mock(IssnMapper.class), mock(JournalInstitutionMapper.class), mock(PersonMapper.class),
            mock(CardinalityMapper.class), mock(RatingMapper.class), policy,
            mock(DictionaryScientificCategoryService.class), mock(DictionaryTRCategoryService.class),
            mock(DictionaryAsjcCategoryService.class), mock(DictionaryRepositoryService.class),
            mock(DictionaryCountryService.class), mock(DictionaryLanguageService.class));
    private final JournalBuildService service = new JournalBuildService(assembler, repository,
            new JournalSurveyPolicy(), new ConfigurationService());

    @TempDir
    private Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"submissionFee", "publicationFee", "openAccessFee", "rapidReview", "apc"})
    void importsAllFeeKindsAndPreservesSelectionThroughStorageAndApi(String feeName) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Journal journal = service.importJournalJson(file("1.1", feeName, """
                {"feeType":"COUNTRY_SPECIFIC_FEE","feeDetails":[
                  {"description":"EXTERNAL_AUTHORS","minValue":1000,"maxValue":1500,"currencyCode":"PLN"},
                  {"description":"INTERNAL_AUTHORS","minValue":500,"maxValue":700,"currencyCode":"PLN"}],
                 "countrySpecificFeeSelection":{"authorCountryCode":"PL","overrideVariant":"EXTERNAL_AUTHORS",
                  "effectiveVariant":"EXTERNAL_AUTHORS","source":"JOURNAL_OVERRIDE"}}
                """));
        var survey = journal.getQuestionnaires().iterator().next().getSurveyData().getSurvey();
        SurveyJsonConverter converter = new SurveyJsonConverter();
        var restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(survey));
        var fees = List.of(restored.getStandards().getSubmissionFee(), restored.getStandards().getPublicationFee(),
                restored.getStandards().getOpenAccessFee(), restored.getStandards().getRapidReview(), restored.getStandards().getApc());
        var fee = fees.stream().filter(value -> value.getCountrySpecificFeeSelection() != null).findFirst().orElseThrow();
        assertThat(fee.getFeeSelectionStatus()).isEqualTo(FeeSelectionStatus.RESOLVED);
        assertThat(fee.getFeeDetails()).hasSize(2);
        assertThat(fee.getCountrySpecificFeeSelection().getOverrideVariant().name()).isEqualTo("EXTERNAL_AUTHORS");
        assertThat(fee.getCountrySpecificFeeSelection().getSource().name()).isEqualTo("JOURNAL_OVERRIDE");
        assertThat(fee.getCountrySpecificFeeSelection().getAuthorCountryCode()).isEqualTo("PL");
        SurveyDtoMapper apiMapper = Mappers.getMapper(SurveyDtoMapper.class);
        ReflectionTestUtils.setField(apiMapper, "authorFeeSelectionPolicy", policy);
        var dto = apiMapper.toDto(fee);
        assertThat(dto.getSelectedFeeDetail().getMinValue()).isEqualTo(1000);
        assertThat(dto.getSelectedFeeDetail().getMaxValue()).isEqualTo(1500);
        assertThat(dto.getSelectedFeeDetail().getCurrencyCode()).isEqualTo("PLN");
        assertThat(dto.getFeeDetails()).hasSize(2);
    }

    @Test
    void preservesAnnualSurveyDataAndExportedFeeSelectionInControlledP1Fixture() throws Exception {
        Path fixture = directory.resolve("p1-surveys.json");
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/p1/journal-surveys-2024-2025.json"))) {
            Files.copy(input, fixture);
        }
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Journal journal = service.importJournalJson(fixture.toFile());

        assertThat(journal.getQuestionnaires()).hasSize(2);
        var questionnaires = journal.getQuestionnaires().stream()
                .collect(Collectors.toMap(questionnaire -> questionnaire.getYear(), Function.identity()));
        assertThat(questionnaires.keySet()).containsExactlyInAnyOrder(2024, 2025);
        assertThat(questionnaires.get(2024).getRating().getIcv()).isEqualTo(80.25);
        assertThat(questionnaires.get(2025).getRating().getIcv()).isEqualTo(95.75);

        SurveyJsonConverter converter = new SurveyJsonConverter();
        var survey2024 = questionnaires.get(2024).getSurveyData().getSurvey();
        var restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(survey2024));
        var survey2025 = questionnaires.get(2025).getSurveyData().getSurvey();
        assertThat(restored.getEvaluationYear()).isEqualTo(2024);
        assertThat(restored.getSupplementaryData().getCountry().getCode()).isEqualTo("PL");
        assertThat(restored.getSupplementaryData().getPaidArticle()).isEqualTo(com.osatum.wbn.domain.model.journal.PaidArticleType.FREE_ARTICLES);
        assertThat(restored.getDigitalization().getDoiType()).isEqualTo(com.osatum.wbn.domain.model.survey.model.DoiType.PART);
        assertThat(restored.getDigitalization().getEditorialTools()).contains(com.osatum.wbn.domain.model.survey.model.EditorialTool.NEWSLETTERS);
        assertThat(restored.getDigitalization().getSocialMedias().getUsed()).isTrue();
        assertThat(restored.getDigitalization().getExternalRepositories().getGeneral()).extracting(value -> value.getId()).containsExactly(10L, 20L, 10L);
        assertThat(restored.getDigitalization().getArticlePublishedLicense()).isEqualTo(com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense.CC_BY);
        assertThat(restored.getPublishingVersions().getAop()).isTrue();
        assertThat(restored.getStability().getFirstIssueYear()).isEqualTo(1960);
        assertThat(restored.getStability().getArticlePublished().stream().filter(value -> value.getYear() == 2024).findFirst().orElseThrow().getFrequencyCode())
                .isEqualTo(com.osatum.wbn.domain.model.journal.JournalFrequency.q);
        assertThat(survey2025.getSupplementaryData().getCountry().getCode()).isEqualTo("DE");
        assertThat(restored.getStandards().getAvgManuscriptTimes().getPublication()).isEqualTo(60);
        assertThat(survey2025.getStandards().getAvgManuscriptTimes().getPublication()).isEqualTo(90);
        assertThat(restored.getCooperation().getAuthorMetric().getForeignAffiliationPercentage()).isEqualTo(20);
        assertThat(restored.getCooperation().getAuthorMetric().getExternalAffiliationPercentage()).isEqualTo(80);
        assertThat(restored.getCooperation().getReviewerMetric().getForeignAffiliationPercentage()).isEqualTo(25);
        assertThat(restored.getCooperation().getReviewerMetric().getExternalAffiliationPercentage()).isEqualTo(75);
        assertThat(restored.getStability().getArticlePublished()).anySatisfy(article -> {
            assertThat(article.getYear()).isEqualTo(2024);
            assertThat(article.getReceivedManuscriptCount()).isEqualTo(38);
            assertThat(article.getRejectedManuscriptCount()).isEqualTo(19);
        });
        var fee = restored.getStandards().getPublicationFee();
        assertThat(fee.getFeeSelectionStatus()).isEqualTo(FeeSelectionStatus.RESOLVED);
        assertThat(policy.resolve(fee).selectedFeeDetail().getDescription()).isEqualTo("EXTERNAL_AUTHORS");
        assertThat(policy.resolve(fee).selectedFeeDetail().getMaxValue()).isEqualTo(150);
        assertThat(policy.resolve(survey2025.getStandards().getPublicationFee()).selectedFeeDetail().getMaxValue()).isEqualTo(250);
        assertThat(restored.getStandards().getOpenAccessFee().getFeeDetails().getFirst().getMaxValue()).isZero();
        assertThat(restored.getStandards().getRapidReview().getFeeDetails()).hasSize(2);
        verify(repository).save(journal);
    }

    @Test
    void distinguishesLegacyDataFromIncompleteNewData() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String fee = "{\"feeType\":\"COUNTRY_SPECIFIC_FEE\",\"feeDetails\":[]}";
        var legacy = service.importJournalJson(file("1.0", "publicationFee", fee));
        var current = service.importJournalJson(file("1.1", "publicationFee", fee));
        assertThat(legacy.getQuestionnaires().iterator().next().getSurveyData().getSurvey().getStandards()
                .getPublicationFee().getFeeSelectionStatus()).isEqualTo(FeeSelectionStatus.LEGACY);
        assertThat(current.getQuestionnaires().iterator().next().getSurveyData().getSurvey().getStandards()
                .getPublicationFee().getFeeSelectionStatus()).isEqualTo(FeeSelectionStatus.INCOMPLETE);
    }

    @Test
    void doesNotSaveJournalWhenSurveyConversionFails() throws Exception {
        assertThatThrownBy(() -> service.importJournalJson(file("1.1", "null")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal 123, year 2024");
        verifyNoInteractions(repository);
    }

    @Test
    void usesExportedCountryInsteadOfTheCountryInsideSurvey() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var journal = service.importJournalJson(file("1.1", """
                {"supplementaryData":{"country":{"code":"DE"}},"standards":{"publicationFee":{
                 "feeType":"COUNTRY_SPECIFIC_FEE",
                 "feeDetails":[{"description":"INTERNAL_AUTHORS","minValue":10,"maxValue":20,"currencyCode":"EUR"}],
                 "countrySpecificFeeSelection":{"authorCountryCode":"PL","effectiveVariant":"INTERNAL_AUTHORS",
                   "source":"JOURNAL_COUNTRY"}}}}
                """));
        var fee = journal.getQuestionnaires().iterator().next().getSurveyData().getSurvey().getStandards().getPublicationFee();
        assertThat(fee.getFeeSelectionStatus()).isEqualTo(FeeSelectionStatus.RESOLVED);
        assertThat(policy.resolve(fee).selectedFeeDetail().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void preservesTariffAndMarksInvalidSelectionInsteadOfFailingTheEntireJournal() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var journal = service.importJournalJson(file("1.1", "publicationFee", """
                {"feeType":"COUNTRY_SPECIFIC_FEE",
                 "feeDetails":[{"description":"EXTERNAL_AUTHORS","minValue":100,"maxValue":200,"currencyCode":"EUR"}],
                 "countrySpecificFeeSelection":{"authorCountryCode":"PL","effectiveVariant":"INTERNAL_AUTHORS",
                   "source":"JOURNAL_COUNTRY"}}
                """));
        var survey = journal.getQuestionnaires().iterator().next().getSurveyData().getSurvey();
        SurveyJsonConverter converter = new SurveyJsonConverter();
        var fee = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(survey)).getStandards().getPublicationFee();
        assertThat(fee.getFeeSelectionStatus()).isEqualTo(FeeSelectionStatus.INVALID);
        assertThat(fee.getFeeDetails()).hasSize(1);
        assertThat(fee.getCountrySpecificFeeSelection().getEffectiveVariant().name()).isEqualTo("INTERNAL_AUTHORS");
        assertThat(policy.resolve(fee).selectedFeeDetail()).isNull();
    }

    @Test
    void rejectsUnsupportedSchemaBeforeSaving() throws Exception {
        assertThatThrownBy(() -> service.importJournalJson(file("2.0", "publicationFee", "null")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schemaVersion");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"stability\":{\"articlePublished\":[{\"year\":2024,\"receivedManuscriptCount\":0,\"rejectedManuscriptCount\":0}]}}",
            "{\"stability\":{\"articlePublished\":[{\"year\":2024,\"receivedManuscriptCount\":3,\"rejectedManuscriptCount\":4}]}}",
            "{\"stability\":{\"articlePublished\":[{\"year\":2024},{\"year\":2024}]}}",
            "{\"stability\":{\"firstIssueYear\":-1}}",
            "{\"standards\":{\"avgManuscriptTimes\":{\"review\":-1}}}",
            "{\"standards\":{\"avgManuscriptTimes\":{\"initialAssessment\":-1}}}",
            "{\"standards\":{\"avgManuscriptTimes\":{\"publication\":-1}}}",
            "{\"cooperation\":{\"authorMetric\":{\"foreignAffiliationPercentage\":101}}}",
            "{\"cooperation\":{\"reviewerMetric\":{\"foreignAffiliationPercentage\":-1}}}",
            "{\"evaluationYear\":2025}"
    })
    void rejectsInvalidSurveyBeforeCallingTheRepository(String survey) throws Exception {
        var input = file("1.1", survey);

        assertThatThrownBy(() -> service.importJournalJson(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal 123, year 2024");

        verifyNoInteractions(repository);
    }

    @Test
    void importsInvalidUnpublishedSurveyWithoutChangingItsData() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var input = fileWithQuestionnaires("1.1", """
                [{"year":2024,"survey":{}},
                 {"year":2026,"survey":{"standards":{"avgManuscriptTimes":{"publication":-1}}}}]
                """);

        var journal = service.importJournalJson(input);

        assertThat(journal.getQuestionnaires()).hasSize(2);
        var laterSurvey = journal.getQuestionnaires().stream().filter(value -> value.getYear() == 2026)
                .findFirst().orElseThrow().getSurveyData().getSurvey();
        assertThat(laterSurvey.getStandards().getAvgManuscriptTimes().getPublication()).isEqualTo(-1);
        verify(repository).save(journal);
    }

    @Test
    void rejectsInvalidPublishedSurveyEvenWhenItIsNotTheDefaultYear() throws Exception {
        var input = fileWithQuestionnaires("1.1", """
                [{"year":2024,"survey":{}},
                 {"year":2025,"survey":{"standards":{"avgManuscriptTimes":{"publication":-1}}}}]
                """);

        assertThatThrownBy(() -> service.importJournalJson(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal 123, year 2025");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsInvalidLaterSurveyWhenItIsTheConfiguredYear() throws Exception {
        var configuration = mock(ConfigurationService.class);
        when(configuration.getCurrentRatingYear()).thenReturn(2025);
        var configuredService = new JournalBuildService(assembler, repository, new JournalSurveyPolicy(), configuration);
        var input = fileWithQuestionnaires("1.1", """
                [{"year":2024,"survey":{}},
                 {"year":2025,"survey":{"standards":{"avgManuscriptTimes":{"publication":-1}}}}]
                """);

        assertThatThrownBy(() -> configuredService.importJournalJson(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journal 123, year 2025");

        verifyNoInteractions(repository);
    }

    @Test
    void importsJournalWithInvalidHistoricalCountsAndPreservesTheSource() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var input = fileWithQuestionnaires("1.0", """
                [{"year":2021,"survey":{"stability":{"articlePublished":[
                  {"year":2021,"receivedManuscriptCount":33,"rejectedManuscriptCount":35}]}}},
                 {"year":2024,"survey":{"stability":{"articlePublished":[
                  {"year":2021,"receivedManuscriptCount":33,"rejectedManuscriptCount":35},
                  {"year":2024,"receivedManuscriptCount":91,"rejectedManuscriptCount":43}]}}}]
                """);

        var journal = service.importJournalJson(input);

        assertThat(journal.getQuestionnaires()).hasSize(2);
        var historicalSurvey = journal.getQuestionnaires().stream().filter(value -> value.getYear() == 2021)
                .findFirst().orElseThrow().getSurveyData().getSurvey();
        var converter = new SurveyJsonConverter();
        var restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(historicalSurvey));
        assertThat(restored.getStability().getArticlePublished()).singleElement().satisfies(publication -> {
            assertThat(publication.getYear()).isEqualTo(2021);
            assertThat(publication.getReceivedManuscriptCount()).isEqualTo(33);
            assertThat(publication.getRejectedManuscriptCount()).isEqualTo(35);
        });
        verify(repository).save(journal);
    }

    @Test
    void rejectsDuplicateConfiguredYearBeforeSaving() throws Exception {
        var input = fileWithQuestionnaires("1.1", """
                [{"year":2024,"survey":{}},{"year":2024,"survey":{}}]
                """);

        assertThatThrownBy(() -> service.importJournalJson(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate survey year 2024 for journal 123");

        verifyNoInteractions(repository);
    }

    @Test
    void keepsHistoricalDuplicatesAndMissingMetricsWithoutInventingValues() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var input = fileWithQuestionnaires("1.1", """
                [{"year":2020,"survey":{"standards":{"avgManuscriptTimes":{"publication":-1}}}},
                 {"year":2020,"survey":{}},{"year":2024,"survey":{}}]
                """);

        var journal = service.importJournalJson(input);

        assertThat(journal.getQuestionnaires()).hasSize(3);
        var current = journal.getQuestionnaires().stream().filter(value -> value.getYear() == 2024).findFirst().orElseThrow();
        assertThat(current.getSurveyData().getSurvey().getStandards().getAvgManuscriptTimes().getPublication()).isNull();
        verify(repository).save(journal);
    }

    @ParameterizedTest
    @CsvSource({"null,null", "0,false", "7,true"})
    void preservesExtensionValuesThroughImportStorageAndApi(String count, String flag) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String source = extendedSurvey(count, flag, "{\"previous\":0.45,\"current\":0.62}");

        var journal = service.importJournalJson(file("1.1", source));

        assertExtensionRoundTrip(journal, 2024, source);
        verify(repository).save(journal);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1.1"})
    void importsLegacySurveyWithoutDefaultingNewValues(String version) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var journal = service.importJournalJson(file(version, "{}"));
        var converter = new SurveyJsonConverter();
        var survey = journal.getQuestionnaires().iterator().next().getSurveyData().getSurvey();
        var restored = converter.convertToEntityAttribute(converter.convertToDatabaseColumn(survey));
        JsonNode actual = JsonMapper.builder().build().valueToTree(restored);

        for (String path : EXTENSION_PATHS) {
            assertThat(actual.at(path).isMissingNode() || actual.at(path).isNull()).as(path).isTrue();
        }
        assertThat(restored.getPublishingVersions().getElectronicArticleMetadata()).isEmpty();
        assertThat(restored.getStandards().getStatements()).isEmpty();
        assertThat(restored.getStandards().getAuthorInstructions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"previous\":0.45}", "{\"current\":0.62}",
            "{\"previous\":null,\"current\":null}", "{\"previous\":0.0,\"current\":0.62}"})
    void preservesPartialAndMissingJciWithoutInventingValues(String jci) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String source = extendedSurvey("0", "false", jci);

        var journal = service.importJournalJson(file("1.1", source));

        assertExtensionRoundTrip(journal, 2024, source);
    }

    @Test
    void keepsExtendedAnswersOfDifferentSurveyYearsSeparate() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String older = extendedSurvey("0", "false", "{\"previous\":0.45}");
        String newer = extendedSurvey("7", "true", "{\"current\":0.62}");

        var journal = service.importJournalJson(fileWithQuestionnaires("1.1", """
                [{"year":2024,"survey":%s},{"year":2025,"survey":%s}]
                """.formatted(older, newer)));

        assertThat(journal.getQuestionnaires()).hasSize(2);
        assertExtensionRoundTrip(journal, 2024, older);
        assertExtensionRoundTrip(journal, 2025, newer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"fileName\":\"cover.png\"}",
            "{\"fileName\":\"cover.png\",\"contents\":null}",
            "{\"fileName\":\"cover.png\",\"contents\":\"\"}"})
    void importsJournalWithoutCoverWhenCoverHasNoContents(String cover) throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Path covers = directory.resolve("covers");
        ReflectionTestUtils.setField(assembler, "coverSource", covers.toString());
        Path input = directory.resolve("journal-without-cover-contents.json");
        Files.writeString(input, """
                {"schemaVersion":"1.1","journal":{"id":123,"cover":%s}}
                """.formatted(cover));

        var journal = service.importJournalJson(input.toFile());

        assertThat(journal.getId()).isEqualTo(123L);
        assertThat(journal.getCover()).isNull();
        assertThat(covers).doesNotExist();
        verify(repository).save(journal);
    }

    @Test
    void importsCurrentJournalCoverWhileSkippingAllThreeSurveyAttachments() throws Exception {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Path covers = directory.resolve("covers");
        ReflectionTestUtils.setField(assembler, "coverSource", covers.toString());
        Path input = directory.resolve("journal-with-files.json");
        Files.writeString(input, """
                {"schemaVersion":"1.1","journal":{"id":123,"countryCode":"PL",
                 "cover":{"fileName":"current.png","fileType":"image/png","contents":"BAUG"},
                 "questionnaires":[{"year":2024,"survey":%s}]}}
                """.formatted(extendedSurvey("7", "true", "null")));

        var journal = service.importJournalJson(input.toFile());

        assertThat(journal.getCover().getFileName()).isEqualTo("current.png");
        assertThat(journal.getCover().getPath()).isEqualTo("123.png");
        assertThat(journal.getCover().getFileType()).isEqualTo("image/png");
        assertThat(journal.getCover().getSize()).isEqualTo(3L);
        assertThat(Files.readAllBytes(covers.resolve("123.png"))).containsExactly(4, 5, 6);
        try (var files = Files.list(covers)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("123.png");
        }
        assertExtensionRoundTrip(journal, 2024, extendedSurvey("7", "true", "null"));
    }

    private void assertExtensionRoundTrip(Journal journal, int year, String source) {
        var survey = journal.getQuestionnaires().stream().filter(value -> value.getYear() == year)
                .findFirst().orElseThrow().getSurveyData().getSurvey();
        var converter = new SurveyJsonConverter();
        String stored = converter.convertToDatabaseColumn(survey);
        var restored = converter.convertToEntityAttribute(stored);
        SurveyDtoMapper apiMapper = Mappers.getMapper(SurveyDtoMapper.class);
        ReflectionTestUtils.setField(apiMapper, "authorFeeSelectionPolicy", policy);
        var mapper = JsonMapper.builder().build();
        JsonNode expected = mapper.readTree(source);
        JsonNode actual = mapper.valueToTree(apiMapper.toDto(restored));
        for (String path : EXTENSION_PATHS) {
            // Brak składnika JCI pozostaje nullem w istniejącym modelu JournalIndicator.
            if (path.endsWith("/indicatorJCI") && !expected.at(path).isNull()) {
                for (String component : List.of("previous", "current")) {
                    var value = expected.at(path).path(component);
                    assertThat(actual.at(path).path(component)).as(path + "/" + component)
                            .isEqualTo(value.isMissingNode() ? mapper.nullNode() : value);
                }
            } else {
                assertThat(actual.at(path)).as(path).isEqualTo(expected.at(path));
            }
        }
        for (String path : List.of("/publishingVersions/electronicArticleMetadata",
                "/publishingVersions/printedArticleMetadata", "/standards/statements", "/standards/authorInstructions")) {
            assertThat(actual.at(path)).as(path).containsExactlyInAnyOrderElementsOf(expected.at(path));
        }
        for (String path : List.of("/publishingVersions/electronicVersionsPercentage", "/publishingVersions/aop",
                "/stability/englishTitleAndAbstractInAllArticlesPercentage", "/digitalization/electronicVersionsPercentage",
                "/digitalization/scientificQuality/indicatorIF", "/cooperation/authorMetric",
                "/cooperation/reviewerMetric", "/cooperation/directorsMetric", "/description/descriptionPL")) {
            assertThat(actual.at(path)).as(path).isEqualTo(expected.at(path));
        }
        for (String path : List.of("/description/cover", "/description/logo", "/cooperation/reviewersFile")) {
            assertThat(mapper.readTree(stored).at(path).isMissingNode()).as("stored " + path).isTrue();
            assertThat(actual.at(path).isMissingNode()).as("API " + path).isTrue();
        }
    }

    private String extendedSurvey(String count, String flag, String jci) {
        return """
                {"publishingVersions":{"publishedArticlesCount":%1$s,"electronicFullTextArticlesCount":%1$s,
                  "aopArticlesCount":%1$s,"electronicVersionsPercentage":37.5,"aop":false,
                  "electronicArticleMetadata":["DOI","GICID","ORCID","NONE","PUBLICATION_DATE","CONFLICT_OF_INTEREST","SHARING_POLICY"],
                  "printedArticleMetadata":["DOI","GICID","ORCID","NONE","PUBLICATION_DATE","CONFLICT_OF_INTEREST","SHARING_POLICY"]},
                 "stability":{"englishTitleAndAbstractArticlesCount":%1$s,"englishTitleAndAbstractInAllArticlesPercentage":61.5},
                 "standards":{"publishedReviewersListWww":" https://example.org/Reviewers ","publicFormWww":"",
                  "publicCriteriaWww":null,"internationalPoliciesAdopted":%2$s,"internationalPoliciesWww":"https://example.org/policies",
                  "statements":["CONFLICT","FINANCE_SOURCE","AUTHOR_CONTRIBUTION","ORIGINAL_KIND","NONE","AI_USAGE_SCOPE"],
                  "authorInstructions":["ARTICLE_STRUCTURE","LITERATURE_INSTRUCTION","NO_INSTRUCTION","NONE","AI_USAGE_INSTRUCTIONS"]},
                 "digitalization":{"electronicVersionsPercentage":91.0,
                  "infoWebsite":{"directors":false,"directorsWww":"https://example.org/board","editOfficeInfo":false,
                   "editOfficeInfoWww":"https://example.org/editors","antiAbusePolicyInfo":%2$s,"antiAbusePolicyInfoWww":""},
                  "scientificQuality":{"indicatorJCI":%3$s,"indicatorIF":{"previous":1.2,"current":2.4}}},
                 "cooperation":{
                  "authorMetric":{"unique":0,"uniqueExternal":%1$s,"uniqueForeign":%1$s,
                   "externalAffiliationPercentage":75.0,"foreignAffiliationPercentage":25.0},
                  "reviewerMetric":{"unique":10,"uniqueExternal":%1$s,"uniqueForeign":%1$s,
                   "externalAffiliationPercentage":62.0,"foreignAffiliationPercentage":10.0},
                  "directorsMetric":{"unique":null,"uniqueExternal":%1$s,"uniqueForeign":%1$s,
                   "externalAffiliationPercentage":null,"foreignAffiliationPercentage":null},
                  "reviewersFile":{"fileName":"reviewers.pdf","externalId":"001","contents":"AQID"}},
                 "description":{"descriptionPL":"Opis","cover":{"fileName":"survey.png","contents":"AQID"},
                  "logo":{"fileName":"logo.png","localFileUrl":"https://example.org/logo.png"}}}
                """.formatted(count, flag, jci);
    }

    private java.io.File file(String version, String feeName, String fee) throws Exception {
        return file(version, "{\"standards\":{\"" + feeName + "\":" + fee + "}}");
    }

    private java.io.File file(String version, String survey) throws Exception {
        return fileWithQuestionnaires(version, "[{\"year\":2024,\"survey\":" + survey + "}]");
    }

    private java.io.File fileWithQuestionnaires(String version, String questionnaires) throws Exception {
        Path path = Files.createTempFile(directory, "journal-", ".json");
        Files.writeString(path, """
                {"schemaVersion":"%s","journal":{"id":123,"countryCode":"PL",
                 "questionnaires":%s}}
                """.formatted(version, questionnaires));
        return path.toFile();
    }
}
