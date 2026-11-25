package com.osatum.wbn.application.analytics.assembler;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcv;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Cooperation;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ToJournalAnalyticsAssemblerTest {

    private final ToJournalAnalyticsAssembler assembler = new ToJournalAnalyticsAssembler();

    @Test
    void toDtoAddsNullCooperationMetricsForMissingYear() {
        Journal journal = new Journal();
        journal.setId(10L);
        journal.setQuestionnaires(new LinkedHashSet<>(List.of(
                questionnaire(2020, 10, 20D),
                questionnaire(2021, 11, 21D),
                questionnaire(2022, 12, 22D),
                questionnaire(2024, 14, 24D)
        )));

        List<JournalAnalytics> analytics = assembler.toDto(
                new ToJournalAnalyticsParameters(journal, null, null)
        );

        assertThat(analytics)
                .filteredOn(item -> JournalAnalyticsType.AUTHOR_UNIQUE.equals(item.getType()))
                .extracting(JournalAnalytics::getYear, JournalAnalytics::getValue)
                .containsExactlyInAnyOrder(
                        tuple(2020, 10D),
                        tuple(2021, 11D),
                        tuple(2022, 12D),
                        tuple(2023, null),
                        tuple(2024, 14D)
                );
        assertThat(analytics)
                .filteredOn(item -> JournalAnalyticsType.REVIEWER_FOREIGN.equals(item.getType()))
                .extracting(JournalAnalytics::getYear, JournalAnalytics::getValue)
                .containsExactlyInAnyOrder(
                        tuple(2020, 20D),
                        tuple(2021, 21D),
                        tuple(2022, 22D),
                        tuple(2023, null),
                        tuple(2024, 24D)
                );
        assertThat(analytics).hasSize(10);
    }

    private Questionnaire questionnaire(Integer year, Integer authorUnique, Double reviewerForeign) {
        ScientistMetric authorMetric = new ScientistMetric();
        authorMetric.setUnique(authorUnique);

        ScientistMetric reviewerMetric = new ScientistMetric();
        reviewerMetric.setForeignAffiliationPercentage(reviewerForeign);

        Cooperation cooperation = new Cooperation();
        cooperation.setAuthorMetric(authorMetric);
        cooperation.setReviewerMetric(reviewerMetric);

        Survey survey = new Survey();
        survey.setCooperation(cooperation);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(survey);

        RatingIcv rating = new RatingIcv();
        rating.setRatingStatus(RatingIcvStatus.PUBLISHED);

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        questionnaire.setRating(rating);
        questionnaire.setSurveyData(surveyData);
        return questionnaire;
    }
}
