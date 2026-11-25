package com.osatum.wbn.domain.service.journal;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class JournalSurveyPolicyTest {
    @Test
    void invalidPublishedHistoricalYearCannotBeSilentlySkipped() {
        var historical = new Survey();
        historical.getStandards().getAvgManuscriptTimes().setPublication(-1);
        var journal = journal(questionnaire(2024, historical), questionnaire(2025, new Survey()));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.resolvePublished(journal, 2025, List.of(2025, 2024)))
                .withMessageContaining("year 2024");
    }
    private final JournalSurveyPolicy policy = new JournalSurveyPolicy();

    @Test
    void calculatesRejectionForTheExactSurveyYearWithoutRoundingOrChangingTheSource() {
        var survey = new Survey();
        var current = counts(2024, 3, 1);
        current.setFrequencyCode(JournalFrequency.q);
        survey.getStability().setArticlePublished(List.of(counts(2023, 0, 10), current));

        var resolved = policy.resolve(journal(questionnaire(2024, survey)), 2024).getFirst();

        assertThat(resolved.year()).isEqualTo(2024);
        assertThat(resolved.survey()).isSameAs(survey);
        assertThat(resolved.publication()).isSameAs(current);
        assertThat(resolved.rejectionRate()).isCloseTo(100.0 / 3, within(0.000000000001));
        assertThat(current.getReceivedManuscriptCount()).isEqualTo(3);
        assertThat(current.getRejectedManuscriptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(value = {"3,0,0.0", "3,3,100.0", "3,null,null", "null,1,null"}, nullValues = "null")
    void preservesRealZeroAndUnknownRejection(Integer received, Integer rejected, Double expected) {
        var survey = new Survey();
        survey.getStability().setArticlePublished(List.of(counts(2024, received, rejected)));

        assertThat(policy.resolve(journal(questionnaire(2024, survey)), 2024).getFirst().rejectionRate()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "3,4", "3,-1", "-1,0"})
    void rejectsInvalidCountsWithJournalAndSurveyContext(int received, int rejected) {
        var survey = new Survey();
        survey.getStability().setArticlePublished(List.of(counts(2024, received, rejected)));

        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(2024, survey)), 2024))
                .withMessageContaining("journal 123, year 2024").withMessageContaining("Invalid manuscript counts");
    }

    @ParameterizedTest
    @ValueSource(strings = {"review", "initialAssessment", "publication"})
    void rejectsNegativeDays(String field) {
        var survey = new Survey();
        var times = survey.getStandards().getAvgManuscriptTimes();
        switch (field) {
            case "review" -> times.setReview(-1);
            case "initialAssessment" -> times.setInitialAssessment(-1);
            case "publication" -> times.setPublication(-1);
            default -> throw new IllegalArgumentException(field);
        }

        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(2024, survey)), 2024))
                .withMessageContaining("processing days");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, 101, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsOutOfRangeAndNonFiniteForeignAffiliationForBothRoles(double percentage) {
        for (boolean author : List.of(true, false)) {
            var survey = new Survey();
            var metric = new ScientistMetric();
            metric.setForeignAffiliationPercentage(percentage);
            if (author) {
                survey.getCooperation().setAuthorMetric(metric);
            } else {
                survey.getCooperation().setReviewerMetric(metric);
            }

            assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(2024, survey)), 2024))
                    .withMessageContaining("Affiliation percentage");
        }
    }

    @Test
    void allowsZeroDaysAndPercentageBoundsWithoutUsingExternalAffiliation() {
        var survey = new Survey();
        var times = survey.getStandards().getAvgManuscriptTimes();
        times.setReview(0);
        times.setInitialAssessment(0);
        times.setPublication(0);
        var author = new ScientistMetric();
        author.setForeignAffiliationPercentage(0.0);
        author.setExternalAffiliationPercentage(50.0);
        var reviewer = new ScientistMetric();
        reviewer.setForeignAffiliationPercentage(100.0);
        reviewer.setExternalAffiliationPercentage(20.0);
        survey.getCooperation().setAuthorMetric(author);
        survey.getCooperation().setReviewerMetric(reviewer);

        assertThatCode(() -> policy.validate(journal(questionnaire(2024, survey)), 2024)).doesNotThrowAnyException();
        assertThat(author.getForeignAffiliationPercentage()).isZero();
        assertThat(reviewer.getForeignAffiliationPercentage()).isEqualTo(100.0);
    }

    @Test
    void missingSectionsAndAnnualRowsRemainUnknown() {
        var survey = new Survey();
        survey.setStability(null);
        survey.setStandards(null);
        survey.setCooperation(null);

        var resolved = policy.resolve(journal(questionnaire(2024, survey)), 2024).getFirst();

        assertThat(resolved.publication()).isNull();
        assertThat(resolved.rejectionRate()).isNull();
        var otherYear = new Survey();
        otherYear.getStability().setArticlePublished(List.of(counts(2025, 10, 10)));
        assertThat(policy.resolve(journal(questionnaire(2024, otherYear)), 2024).getFirst().rejectionRate()).isNull();
        assertThat(policy.resolve(new Journal(), 2024)).isEmpty();
    }

    @Test
    void ignoresQuestionnairesWithoutSurveyData() {
        var empty = questionnaire(2024, new Survey());
        empty.setSurveyData(null);
        var missingSurvey = questionnaire(null, null);

        assertThat(policy.resolve(journal(empty, missingSurvey, questionnaire(2024, new Survey())), 2024))
                .singleElement().satisfies(value -> assertThat(value.year()).isEqualTo(2024));
    }

    @Test
    void rejectsMissingOrConflictingSurveyYears() {
        var survey = new Survey();
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(null, survey)), 2024))
                .withMessageContaining("Missing survey year for journal 123");
        survey.setEvaluationYear(2025);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(2024, survey)), 2024))
                .withMessageContaining("Inconsistent survey year");
    }

    @Test
    void rejectsNegativeFirstIssueYearAndDuplicateAnnualRows() {
        var survey = new Survey();
        survey.getStability().setFirstIssueYear(-1);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(2024, survey)), 2024))
                .withMessageContaining("First issue year");
        survey.getStability().setFirstIssueYear(null);
        survey.getStability().setArticlePublished(List.of(counts(2024, 2, 1), counts(2024, 3, 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(2024, survey)), 2024))
                .withMessageContaining("Duplicate publication data");
    }

    @Test
    void skipsHistoricalAmbiguityButRejectsDuplicatesInTheConfiguredYear() {
        var invalidHistorical = new Survey();
        invalidHistorical.getStandards().getAvgManuscriptTimes().setPublication(-1);
        var journal = journal(questionnaire(2020, invalidHistorical), questionnaire(2020, new Survey()),
                questionnaire(2024, new Survey()));

        assertThat(policy.resolve(journal, 2024)).singleElement()
                .satisfies(value -> assertThat(value.year()).isEqualTo(2024));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal, 2020))
                .withMessageContaining("Duplicate survey year 2020 for journal 123");
        assertThat(journal.getQuestionnaires()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {2021, 2025})
    void skipsInvalidCountsOutsideConfiguredYearWithoutChangingSourceData(int otherYear) {
        var invalid = new Survey();
        var invalidCounts = counts(otherYear, 33, 35);
        invalid.getStability().setArticlePublished(List.of(invalidCounts));
        var current = new Survey();
        current.getStability().setArticlePublished(List.of(counts(2024, 91, 43)));
        var journal = journal(questionnaire(otherYear, invalid), questionnaire(2024, current));

        assertThat(policy.resolve(journal, 2024)).singleElement().satisfies(resolved -> {
            assertThat(resolved.year()).isEqualTo(2024);
            assertThat(resolved.survey()).isSameAs(current);
            assertThat(resolved.rejectionRate()).isCloseTo(4300.0 / 91, within(0.000000000001));
        });
        assertThat(journal.getQuestionnaires()).hasSize(2);
        assertThat(invalidCounts.getReceivedManuscriptCount()).isEqualTo(33);
        assertThat(invalidCounts.getRejectedManuscriptCount()).isEqualTo(35);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal, otherYear))
                .withMessageContaining("journal 123, year " + otherYear)
                .withMessageContaining("Invalid manuscript counts");
    }

    @Test
    void skipsOtherInvalidAnnualDataButKeepsValidHistoricalProjections() {
        var invalidDays = new Survey();
        invalidDays.getStandards().getAvgManuscriptTimes().setPublication(-1);
        var conflictingYear = new Survey();
        conflictingYear.setEvaluationYear(2021);
        var duplicateRows = new Survey();
        duplicateRows.getStability().setArticlePublished(List.of(counts(2023, 2, 1), counts(2023, 3, 1)));
        var journal = journal(questionnaire(2020, new Survey()), questionnaire(2021, invalidDays),
                questionnaire(2022, conflictingYear), questionnaire(2023, duplicateRows), questionnaire(2024, new Survey()));
        assertThat(policy.resolve(journal, 2024)).extracting(JournalSurveyPolicy.AnnualSurvey::year)
                .containsExactly(2020, 2024);
        assertThat(journal.getQuestionnaires()).hasSize(5);
        assertThat(invalidDays.getStandards().getAvgManuscriptTimes().getPublication()).isEqualTo(-1);
        assertThat(conflictingYear.getEvaluationYear()).isEqualTo(2021);
        assertThat(duplicateRows.getStability().getArticlePublished()).hasSize(2);
    }

    @Test
    void doesNotSkipInvalidSurveysWhenTheConfiguredYearIsMissing() {
        var invalid = new Survey();
        invalid.getStability().setArticlePublished(List.of(counts(2021, 33, 35)));

        assertThatIllegalArgumentException().isThrownBy(() -> policy.validate(journal(questionnaire(2021, invalid)), null))
                .withMessageContaining("journal 123, year 2021");
    }

    private Journal journal(Questionnaire... questionnaires) {
        var journal = new Journal();
        journal.setId(123L);
        journal.setQuestionnaires(Set.of(questionnaires));
        return journal;
    }

    private Questionnaire questionnaire(Integer year, Survey survey) {
        var questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        var data = new SurveyData();
        data.setSurvey(survey);
        questionnaire.setSurveyData(data);
        return questionnaire;
    }

    private ArticlePublished counts(int year, Integer received, Integer rejected) {
        var publication = new ArticlePublished();
        publication.setYear(year);
        publication.setReceivedManuscriptCount(received);
        publication.setRejectedManuscriptCount(rejected);
        return publication;
    }
}
