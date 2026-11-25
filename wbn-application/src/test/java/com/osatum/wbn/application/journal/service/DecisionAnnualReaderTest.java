package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView.Declaration;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.*;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionAnnualReaderTest {
    private final DecisionProfileReader profiles = mock();
    private final DecisionFeeReader fees = mock();
    private final DecisionAnnualReader reader = new DecisionAnnualReader(profiles, fees);

    @Test
    void readsSelectedAnnualRowAndFinalValuesWithoutCatalogueFallbackOrMutation() {
        var survey = new Survey();
        survey.setEvaluationYear(2025);
        var times = survey.getStandards().getAvgManuscriptTimes();
        times.setInitialAssessment(0); times.setReview(-1); times.setPublication(30);
        var old = row(2024, 50, 25); old.setFrequencyCode(JournalFrequency.a);
        var current = row(2025, 100, 0); current.setFrequencyCode(JournalFrequency.q);
        survey.getStability().setArticlePublished(new ArrayList<>(List.of(old, current)));
        survey.getStability().setFirstIssueYear(2000); survey.getStability().setContinuedYear(2010);
        survey.getStability().setPublishingBreak(false); survey.getPublishingVersions().setAop(false);
        survey.getPublishingVersions().setLogin("DO_NOT_RETURN"); survey.getPublishingVersions().setPassword("DO_NOT_RETURN");
        survey.getCooperation().setAuthorMetric(metric(0.5));
        survey.getCooperation().getAuthorMetric().setExternalAffiliationPercentage(99.0);
        survey.getCooperation().setReviewerMetric(metric(0.0)); survey.getCooperation().setDirectorsMetric(metric(100.0));
        survey.getSupplementaryData().setPaidArticle(PaidArticleType.PAID_ARTICLES);
        survey.getSupplementaryData().setInfoFullTextAccess(List.of("date", "language"));
        survey.getDigitalization().setArticlePublishedLicense(ArticlePublishedLicense.CC_BY);
        var result = reader.read(survey, 2025);
        assertThat(result.surveyYear()).isEqualTo(2025);
        assertThat(result.times().initialAssessment().value()).isZero();
        assertThat(result.times().review().status()).isEqualTo(State.INVALID);
        assertThat(result.times().publication().value()).isEqualTo(30);
        assertThat(result.process().frequency().value()).isEqualTo(JournalFrequency.q);
        assertThat(result.process().firstIssueYear().value()).isEqualTo(2000);
        assertThat(result.process().continuedYear().value()).isEqualTo(2010);
        assertThat(result.process().rejectionRate().value()).isZero();
        assertThat(result.process().aheadOfPrint().value()).isFalse();
        assertThat(result.process().publishingBreak().value()).isFalse();
        assertThat(result.international().authors().value()).isEqualTo(0.5);
        assertThat(result.international().reviewers().value()).isZero();
        assertThat(result.international().scientificCouncil().value()).isEqualTo(100);
        assertThat(result.standards().access().value()).isEqualTo(PaidArticleType.PAID_ARTICLES);
        assertThat(result.standards().accessConditions().value()).containsExactly("date", "language");
        assertThat(result.standards().licence().value()).isEqualTo(ArticlePublishedLicense.CC_BY);
        verify(profiles).profile(survey); verify(profiles).indexing(survey); verify(fees).read(survey.getStandards());
        assertThat(times.getReview()).isEqualTo(-1); assertThat(survey.getStability().getArticlePublished()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, 100.1, Double.NaN, Double.POSITIVE_INFINITY})
    void validatesEachForeignMetricLocallyIncludingCouncil(double bad) {
        var survey = new Survey();
        survey.getCooperation().setAuthorMetric(metric(50.0)); survey.getCooperation().setDirectorsMetric(metric(bad));
        var result = reader.read(survey, 2024);
        assertThat(result.international().authors().status()).isEqualTo(State.AVAILABLE);
        assertThat(result.international().scientificCouncil().status()).isEqualTo(State.INVALID);
        assertThat(result.international().reviewers().status()).isEqualTo(State.MISSING);
    }

    @Test
    void duplicatesAndInvalidCountsOnlyDisableDependentParameters() {
        var survey = new Survey();
        survey.getStability().setArticlePublished(List.of(row(2024, 0, 0)));
        assertThat(reader.read(survey, 2024).process().rejectionRate().status()).isEqualTo(State.INVALID);
        survey.getStability().setArticlePublished(List.of(row(2024, 10, 11)));
        assertThat(reader.read(survey, 2024).process().rejectionRate().status()).isEqualTo(State.INVALID);
        survey.getStability().setArticlePublished(List.of(row(2024, 10, 2), row(2024, 10, 2)));
        assertThat(reader.read(survey, 2024).process().frequency().status()).isEqualTo(State.INVALID);
        assertThat(reader.read(survey, 2025).process().rejectionRate().status()).isEqualTo(State.MISSING);
    }

    @Test
    void standardsDistinguishNoneMissingOtherIndicatedAndConflictingChoices() {
        var survey = new Survey();
        assertThat(reader.read(survey, 2024).standards().antiPlagiarism().status()).isEqualTo(State.MISSING);
        survey.getDigitalization().setEditorialTools(Set.of(EditorialTool.NONE));
        assertThat(reader.read(survey, 2024).standards().antiPlagiarism().value()).isEqualTo(Declaration.NONE);
        survey.getDigitalization().setEditorialTools(Set.of(EditorialTool.MANUSCRIPT_SYSTEM));
        assertThat(reader.read(survey, 2024).standards().editorialSystem().value()).isEqualTo(Declaration.NOT_INDICATED);
        survey.getDigitalization().setEditorialTools(Set.of(EditorialTool.EDITORIAL_SYSTEM, EditorialTool.ANTI_PLAGIARISM_SYSTEM));
        survey.getStandards().setReviewRules(Set.of(ReviewRule.PUBLIC_CRITERIA));
        var result = reader.read(survey, 2024).standards();
        assertThat(result.antiPlagiarism().value()).isEqualTo(Declaration.INDICATED);
        assertThat(result.editorialSystem().value()).isEqualTo(Declaration.INDICATED);
        assertThat(result.publicCriteria().value()).isEqualTo(Declaration.INDICATED);
        survey.getStandards().setReviewRules(Set.of(ReviewRule.NONE, ReviewRule.PUBLIC_CRITERIA));
        assertThat(reader.read(survey, 2024).standards().publicCriteria().status()).isEqualTo(State.INVALID);
    }

    @Test
    void reviewingRetainsAllModelsAndMissingSharesAndRejectsContradictionsWithoutNormalization() {
        var survey = new Survey(); var review = survey.getStandards().getInfoReviewProcedure();
        review.setDoubleBlindReview(new BooleanDouble(true, 40.5)); review.setSingleBlindReview(new BooleanDouble(true, null));
        var result = reader.read(survey, 2024).standards().reviewing();
        assertThat(result.status()).isEqualTo(State.AVAILABLE); assertThat(result.models()).hasSize(3);
        assertThat(result.models().getFirst().share().value()).isEqualTo(40.5);
        assertThat(result.models().get(1).share().status()).isEqualTo(State.MISSING);
        review.setSingleBlindReview(new BooleanDouble(true, 60.0));
        assertThat(reader.read(survey, 2024).standards().reviewing().status()).isEqualTo(State.INVALID);
        review.setSingleBlindReview(null); review.setNone(true);
        assertThat(reader.read(survey, 2024).standards().reviewing().status()).isEqualTo(State.INVALID);
    }

    @Test
    void socialChannelsAreIndependentFromFlagAndLicencesDoNotComeFromAccess() {
        var survey = new Survey(); var social = survey.getDigitalization().getSocialMedias();
        social.setUsed(false); social.setIds(List.of(SocialMedia.X, SocialMedia.X));
        var result = reader.read(survey, 2024).standards();
        assertThat(result.socialMedia().status()).isEqualTo(State.INVALID);
        assertThat(result.socialMedia().value().channels()).containsExactly(SocialMedia.X);
        assertThat(result.licence().status()).isEqualTo(State.MISSING);
        social.setIds(List.of());
        assertThat(reader.read(survey, 2024).standards().socialMedia().value().used()).isFalse();
        survey.getDigitalization().setArticlePublishedLicense(ArticlePublishedLicense.NONE);
        assertThat(reader.read(survey, 2024).standards().licence().value()).isEqualTo(ArticlePublishedLicense.NONE);
    }

    private ScientistMetric metric(Double value) { var result = new ScientistMetric(); result.setForeignAffiliationPercentage(value); return result; }
    private ArticlePublished row(int year, int received, int rejected) { var result = new ArticlePublished(); result.setYear(year); result.setReceivedManuscriptCount(received); result.setRejectedManuscriptCount(rejected); return result; }
}
