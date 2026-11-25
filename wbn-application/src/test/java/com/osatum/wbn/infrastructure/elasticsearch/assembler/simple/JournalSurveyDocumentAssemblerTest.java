package com.osatum.wbn.infrastructure.elasticsearch.assembler.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.model.journal.Title;
import com.osatum.wbn.domain.model.journal.TitleCode;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.domain.model.questionnaire.SurveyJsonConverter;
import com.osatum.wbn.domain.service.journal.JournalSurveyPolicy;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class JournalSurveyDocumentAssemblerTest {
    private final JournalSurveyDocumentAssembler assembler = new JournalSurveyDocumentAssembler(new ConfigurationService(),
            new com.osatum.wbn.domain.service.journal.JournalFeePolicy(new com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy()),
            new JournalSurveyPolicy());

    @Test
    void projectsConfirmedReviewModelsFromTheirOwnSurveyYearWithoutUsingPercentagesAsFlags() {
        var survey2024 = new Survey();
        var review2024 = new com.osatum.wbn.domain.model.survey.model.InfoReviewProcedure();
        review2024.setDoubleBlindReview(new com.osatum.wbn.domain.model.survey.model.BooleanDouble(true, 0.0));
        review2024.setSingleBlindReview(new com.osatum.wbn.domain.model.survey.model.BooleanDouble(false, 80.0));
        review2024.setOpenPeerReview(new com.osatum.wbn.domain.model.survey.model.BooleanDouble(true, null));
        survey2024.getStandards().setInfoReviewProcedure(review2024);
        var survey2025 = new Survey();
        var review2025 = new com.osatum.wbn.domain.model.survey.model.InfoReviewProcedure();
        review2025.setDoubleBlindReview(new com.osatum.wbn.domain.model.survey.model.BooleanDouble(null, 100.0));
        review2025.setSingleBlindReview(new com.osatum.wbn.domain.model.survey.model.BooleanDouble(true, 100.0));
        survey2025.getStandards().setInfoReviewProcedure(review2025);

        var documents = assembler.toDocuments(journal(questionnaire(2024, survey2024), questionnaire(2025, survey2025)));

        assertThat(documents).filteredOn(document -> document.getSurveyYear() == 2024).singleElement()
                .satisfies(document -> assertThat(document.getReviewModels()).containsExactly("DOUBLE_BLIND_REVIEW", "OPEN_PEER_REVIEW"));
        assertThat(documents).filteredOn(document -> document.getSurveyYear() == 2025).singleElement()
                .satisfies(document -> assertThat(document.getReviewModels()).containsExactly("SINGLE_BLIND_REVIEW"));
    }

    @Test
    void missingReviewStandardsDoNotConfirmAnyReviewModel() {
        var survey = new Survey();
        survey.getStandards().setInfoReviewProcedure(null);
        assertThat(assembler.toDocuments(journal(questionnaire(2024, survey))))
                .singleElement().satisfies(document -> assertThat(document.getReviewModels()).isEmpty());
        survey.setStandards(null);
        assertThat(assembler.toDocuments(journal(questionnaire(2024, survey))))
                .singleElement().satisfies(document -> assertThat(document.getReviewModels()).isEmpty());
    }

    @Test
    void isolatesAnnualMetricsAndSelectsP1FeesByAnnualCountryWithoutChangingSource() throws Exception {
        Journal journal = new Journal();
        journal.setId(990001L);
        var catalogCountry = new DictionaryCountry();
        catalogCountry.setCode("DE");
        journal.setCountry(catalogCountry);
        var catalogTitle = new Title();
        catalogTitle.setCode(TitleCode.ORIGINAL);
        catalogTitle.setContent("Latest global title must not replace 2024");
        journal.setTitles(Set.of(catalogTitle));
        journal.setQuestionnaires(new HashSet<>());
        var mapper = new ObjectMapper();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/p1/journal-surveys-2024-2025.json"))) {
            for (var source : mapper.readTree(input).path("journal").path("questionnaires")) {
                Survey survey = new SurveyJsonConverter().convertToEntityAttribute(source.path("survey").toString());
                journal.getQuestionnaires().add(questionnaire(source.path("year").asInt(), survey));
            }
        }

        var documents = assembler.toDocuments(journal);

        assertThat(documents).extracting(document -> document.getDocumentId())
                .containsExactlyInAnyOrder("990001:2024", "990001:2025");
        assertThat(documents).allSatisfy(document -> assertThat(document.getId()).isEqualTo(990001L));
        var document2024 = documents.stream().filter(document -> document.getSurveyYear() == 2024).findFirst().orElseThrow();
        var document2025 = documents.stream().filter(document -> document.getSurveyYear() == 2025).findFirst().orElseThrow();
        assertThat(document2024.getCountryCode()).isEqualTo("PL");
        assertThat(document2025.getCountryCode()).isEqualTo("DE");
        assertThat(document2024.getTitles()).extracting(title -> title.getContent()).containsExactly("P1 Annual 2024");
        assertThat(document2025.getTitles()).extracting(title -> title.getContent()).containsExactly("P1 Annual 2025");
        assertThat(document2024.getTitleSortContentIcu()).isEqualTo("P1 Annual 2024");
        assertThat(document2025.getTitleSortContentIcu()).isEqualTo("P1 Annual 2025");
        assertThat(document2024.getTitleSortScriptRank()).isZero();
        assertThat(document2024.getFees()).hasSize(5).filteredOn(fee -> fee.category() == com.osatum.wbn.domain.model.journal.FeeCategory.PUBLICATION)
                .singleElement().satisfies(fee -> {
                    assertThat(fee.selection().effectiveVariant().name()).isEqualTo("INTERNAL_AUTHORS");
                    assertThat(fee.amounts()).singleElement().satisfies(amount -> {
                        assertThat(amount.value()).isEqualTo(50);
                        assertThat(amount.currency()).isEqualTo("PLN");
                    });
                    assertThat(fee.details()).extracting(com.osatum.wbn.domain.model.journal.JournalFeeTerms.Detail::maxValue).containsExactly(150, 50);
                    assertThat(fee.details()).extracting(com.osatum.wbn.domain.model.journal.JournalFeeTerms.Detail::currencyCode).containsExactly("EUR", "PLN");
                });
        assertThat(document2025.getFees()).filteredOn(fee -> fee.category() == com.osatum.wbn.domain.model.journal.FeeCategory.PUBLICATION)
                .singleElement().satisfies(fee -> assertThat(fee.details()).extracting(com.osatum.wbn.domain.model.journal.JournalFeeTerms.Detail::maxValue).containsExactly(120, 250));
        assertThat(document2024.getIssns()).extracting(issn -> issn.getContent()).containsExactly("9999-0001");
        assertThat(document2024.getInstitutions()).extracting(institution -> institution.getName())
                .containsExactlyInAnyOrder("Synthetic Publisher 2024", "Synthetic Co-publisher 2024");
        assertThat(document2024.getLanguages()).extracting(language -> language.getCode()).containsExactlyInAnyOrder("pl", "fr");
        assertThat(document2025.getLanguages()).extracting(language -> language.getCode()).containsExactly("de");
        assertThat(document2024.getScientificCategories()).extracting(category -> category.getCode()).containsExactly("9001");
        assertThat(document2024.getDoiType()).isEqualTo("PART");
        assertThat(document2025.getDoiType()).isEqualTo("NONE");
        assertThat(document2025.getDoi()).isEqualTo("10.9999");
        assertThat(document2024.getEditorialTools()).containsExactly("ANTI_PLAGIARISM_SYSTEM", "EDITORIAL_SYSTEM", "NEWSLETTERS");
        assertThat(document2025.getEditorialTools()).containsExactly("MANUSCRIPT_SYSTEM");
        assertThat(document2024.getSocialMediaUsed()).isTrue();
        assertThat(document2025.getSocialMediaUsed()).isFalse();
        assertThat(document2024.getRepositoryIds()).containsExactly("10", "20");
        assertThat(document2025.getRepositoryIds()).containsExactly("30");
        assertThat(document2024.getArticlePublishedLicense()).isEqualTo("CC_BY");
        assertThat(document2025.getArticlePublishedLicense()).isEqualTo("CC_BY_NC");
        assertThat(document2024.getPaidArticle()).isEqualTo("FREE_ARTICLES");
        assertThat(document2025.getPaidArticle()).isEqualTo("ALL_ARTICLES_PAID");
        assertThat(document2024.getAop()).isTrue();
        assertThat(document2025.getAop()).isFalse();
        assertThat(document2024.getFrequencyCode()).isEqualTo("q");
        assertThat(document2025.getFrequencyCode()).isEqualTo("m");
        assertThat(document2024.getFirstIssueYear()).isEqualTo(1960);
        assertThat(document2025.getFirstIssueYear()).isEqualTo(1970);
        assertThat(document2024.getReviewDays()).isEqualTo(30);
        assertThat(document2024.getInitialAssessmentDays()).isEqualTo(5);
        assertThat(document2024.getPublicationDays()).isEqualTo(60);
        assertThat(document2025.getPublicationDays()).isEqualTo(90);
        assertThat(document2024.getRejectionRate()).isEqualTo(50.0);
        assertThat(document2025.getRejectionRate()).isEqualTo(25.0);
        assertThat(document2024.getAuthorForeignAffiliationPercentage()).isEqualTo(20.0);
        assertThat(document2024.getReviewerForeignAffiliationPercentage()).isEqualTo(25.0);
        assertThat(document2025.getAuthorForeignAffiliationPercentage()).isEqualTo(30.0);
        assertThat(document2025.getReviewerForeignAffiliationPercentage()).isEqualTo(40.0);
        assertThat(journal.getCountry()).isSameAs(catalogCountry);
        assertThat(journal.getTitles()).containsExactly(catalogTitle);
        assertThat(journal.getQuestionnaires()).allSatisfy(questionnaire ->
                assertThat(questionnaire.getSurveyData().getSurvey().getStandards().getPublicationFee()
                        .getCountrySpecificFeeSelection().getEffectiveVariant().name()).isEqualTo("EXTERNAL_AUTHORS"));
    }

    @Test
    void historicalDuplicateYearDoesNotBlockUnique2024Survey(CapturedOutput output) {
        var firstHistorical = questionnaire(2020, new Survey());
        firstHistorical.setId(9001L);
        var secondHistorical = questionnaire(2020, new Survey());
        secondHistorical.setId(9002L);
        var currentSurvey = new Survey();
        currentSurvey.getStandards().getAvgManuscriptTimes().setPublication(60);
        var current = questionnaire(2024, currentSurvey);
        current.setId(9003L);

        var documents = assembler.toDocuments(journal(firstHistorical, secondHistorical, current));

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.getSurveyYear()).isEqualTo(2024);
            assertThat(document.getPublicationDays()).isEqualTo(60);
        });
        assertThat(output.getOut()).contains("Skipping ambiguous survey year 2020 for journal 123")
                .contains("outside configured P1 year 2024", "9001", "9002");
    }

    @ParameterizedTest
    @ValueSource(ints = {2021, 2026})
    void invalidOtherYearDoesNotBlockOrReplaceTheConfiguredProjection(int otherYear, CapturedOutput output) {
        var invalidSurvey = new Survey();
        var invalidCounts = counts(otherYear, 33, 35);
        invalidSurvey.getStability().setArticlePublished(List.of(invalidCounts));
        var currentSurvey = new Survey();
        currentSurvey.getStability().setArticlePublished(List.of(counts(otherYear, 33, 35), counts(2024, 91, 43)));
        var journal = journal(questionnaire(otherYear, invalidSurvey), questionnaire(2024, currentSurvey));

        assertThat(assembler.toDocuments(journal)).singleElement().satisfies(document -> {
            assertThat(document.getDocumentId()).isEqualTo("123:2024");
            assertThat(document.getSurveyYear()).isEqualTo(2024);
            assertThat(document.getRejectionRate()).isCloseTo(4300.0 / 91, within(0.000000000001));
        });
        assertThat(output.getOut()).contains("WARN", "Skipping invalid survey year " + otherYear + " for journal 123",
                "outside configured P1 year 2024", "receivedManuscriptCount=33", "rejectedManuscriptCount=35");
        assertThat(journal.getQuestionnaires()).hasSize(2);
        assertThat(invalidCounts.getReceivedManuscriptCount()).isEqualTo(33);
        assertThat(invalidCounts.getRejectedManuscriptCount()).isEqualTo(35);

        var configuration = mock(ConfigurationService.class);
        when(configuration.getCurrentRatingYear()).thenReturn(otherYear);
        var configuredAssembler = new JournalSurveyDocumentAssembler(configuration,
                new com.osatum.wbn.domain.service.journal.JournalFeePolicy(new com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy()),
                new JournalSurveyPolicy());
        assertThatIllegalArgumentException().isThrownBy(() -> configuredAssembler.toDocuments(journal))
                .withMessageContaining("journal 123, year " + otherYear);
    }

    @Test
    void invalidHistoricalSurveyDoesNotProvideFallbackForAnAbsentConfiguredYear() {
        var invalidSurvey = new Survey();
        invalidSurvey.getStability().setArticlePublished(List.of(counts(2021, 33, 35)));

        assertThat(assembler.toDocuments(journal(questionnaire(2021, invalidSurvey)))).isEmpty();
    }

    @Test
    void duplicateConfiguredYearIsReportedWithoutSelectingAnArbitraryQuestionnaire() {
        var first = questionnaire(2024, new Survey());
        first.setId(9001L);
        var second = questionnaire(2024, new Survey());
        second.setId(9002L);

        assertThatIllegalArgumentException().isThrownBy(() -> assembler.toDocuments(journal(first, second)))
                .withMessageContaining("Duplicate survey year 2024 for journal 123")
                .withMessageContaining("questionnaireIds=").withMessageContaining("9001").withMessageContaining("9002");
    }

    @Test
    void configuredYearIsNotHardcodedTo2024() {
        var configuration = mock(ConfigurationService.class);
        when(configuration.getCurrentRatingYear()).thenReturn(2025);
        var configuredAssembler = new JournalSurveyDocumentAssembler(configuration,
                new com.osatum.wbn.domain.service.journal.JournalFeePolicy(new com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy()),
                new JournalSurveyPolicy());
        var journal = journal(questionnaire(2024, new Survey()), questionnaire(2024, new Survey()), questionnaire(2025, new Survey()));

        assertThat(configuredAssembler.toDocuments(journal)).singleElement()
                .satisfies(document -> assertThat(document.getSurveyYear()).isEqualTo(2025));
        journal.setQuestionnaires(Set.of(questionnaire(2025, new Survey()), questionnaire(2025, new Survey())));
        assertThatIllegalArgumentException().isThrownBy(() -> configuredAssembler.toDocuments(journal))
                .withMessageContaining("Duplicate survey year 2025");
    }

    @Test
    void emptyQuestionnaireDoesNotMakeTheUsableSurveyYearAmbiguous() {
        var empty = questionnaire(2024, new Survey());
        empty.setSurveyData(null);
        var valid = questionnaire(2024, new Survey());
        assertThat(assembler.toDocuments(journal(empty, valid))).singleElement()
                .satisfies(document -> assertThat(document.getSurveyYear()).isEqualTo(2024));
    }

    @Test
    void doesNotRoundRejectionBeforeFilteringAndPreservesRealZero() {
        var survey = new Survey();
        survey.getStability().setArticlePublished(List.of(counts(2023, 10, 10), counts(2024, 3, 1)));
        survey.getStandards().getAvgManuscriptTimes().setReview(0);

        var document = assembler.toDocuments(journal(questionnaire(2024, survey))).getFirst();

        assertThat(document.getRejectionRate()).isCloseTo(100.0 / 3, within(0.000000000001));
        assertThat(document.getReviewDays()).isZero();
        survey.getStability().setArticlePublished(List.of(counts(2024, 3, 0)));
        assertThat(assembler.toDocuments(journal(questionnaire(2024, survey))).getFirst().getRejectionRate()).isZero();
    }

    @Test
    void missingValuesAreNotReplacedWithZeroOrAnotherSurveyYear() {
        var survey = new Survey();
        survey.getStability().setArticlePublished(List.of(counts(2023, 10, 5)));
        var document = assembler.toDocuments(journal(questionnaire(2024, survey))).getFirst();
        assertThat(document.getRejectionRate()).isNull();
        assertThat(document.getPublicationDays()).isNull();
        assertThat(document.getAuthorForeignAffiliationPercentage()).isNull();
        assertThat(assembler.toDocuments(new Journal())).isEmpty();
        var noData = questionnaire(2025, survey);
        noData.setSurveyData(null);
        assertThat(assembler.toDocuments(journal(noData))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "3,4", "3,-1", "-1,0"})
    void rejectsInvalidManuscriptCountsInsteadOfIndexingZero(int received, int rejected) {
        var survey = new Survey();
        survey.getStability().setArticlePublished(List.of(counts(2024, received, rejected)));
        assertThatIllegalArgumentException().isThrownBy(() -> assembler.toDocuments(journal(questionnaire(2024, survey))));
    }

    @Test
    void rejectsConflictingYearsAndDuplicateAnnualRows() {
        var survey = new Survey();
        survey.setEvaluationYear(2025);
        assertThatIllegalArgumentException().isThrownBy(() -> assembler.toDocuments(journal(questionnaire(2024, survey))));
        survey.setEvaluationYear(2024);
        assertThatIllegalArgumentException().isThrownBy(() -> assembler.toDocuments(
                journal(questionnaire(2024, survey), questionnaire(2024, new Survey()))));
        survey.getStability().setArticlePublished(List.of(counts(2024, 2, 1), counts(2024, 3, 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> assembler.toDocuments(journal(questionnaire(2024, survey))));
    }

    @Test
    void elasticsearchMappingKeepsCompositeDocumentIdSeparateFromThePublicJournalId() {
        var converter = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
        converter.afterPropertiesSet();
        var document = assembler.toDocuments(journal(questionnaire(2024, new Survey()))).getFirst();
        var source = Document.create();
        converter.write(document, source);
        source.setId("123:2024");

        var restored = converter.read(JournalDocument.class, source);

        assertThat(restored.getDocumentId()).isEqualTo("123:2024");
        assertThat(restored.getId()).isEqualTo(123L);
        assertThat(restored.getSurveyYear()).isEqualTo(2024);
    }

    @Test
    void missingNonFeeSourcesAreNotReplacedByFalseZeroOrAnotherYear() {
        Survey survey = new Survey();
        survey.setDigitalization(null);
        survey.setPublishingVersions(null);
        survey.setStability(null);
        survey.setSupplementaryData(null);
        JournalDocument document = assembler.toDocuments(journal(questionnaire(2024, survey))).getFirst();
        assertThat(document.getDoiType()).isNull();
        assertThat(document.getEditorialTools()).isNull();
        assertThat(document.getSocialMediaUsed()).isNull();
        assertThat(document.getRepositoryIds()).isNull();
        assertThat(document.getArticlePublishedLicense()).isNull();
        assertThat(document.getPaidArticle()).isNull();
        assertThat(document.getAop()).isNull();
        assertThat(document.getFirstIssueYear()).isNull();
        assertThat(document.getFrequencyCode()).isNull();
    }

    @Test
    void negativeFirstIssueYearIsNotSilentlyCorrected() {
        Survey survey = new Survey();
        survey.getStability().setFirstIssueYear(-1);
        assertThatIllegalArgumentException().isThrownBy(() -> assembler.toDocuments(journal(questionnaire(2024, survey))))
                .withMessageContaining("First issue year");
    }

    private Journal journal(Questionnaire... questionnaires) {
        var journal = new Journal();
        journal.setId(123L);
        journal.setQuestionnaires(new HashSet<>(List.of(questionnaires)));
        return journal;
    }

    private Questionnaire questionnaire(int year, Survey survey) {
        var questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        var data = new SurveyData();
        data.setSurvey(survey);
        questionnaire.setSurveyData(data);
        return questionnaire;
    }

    private ArticlePublished counts(int year, int received, int rejected) {
        var row = new ArticlePublished();
        row.setYear(year);
        row.setReceivedManuscriptCount(received);
        row.setRejectedManuscriptCount(rejected);
        return row;
    }
}
