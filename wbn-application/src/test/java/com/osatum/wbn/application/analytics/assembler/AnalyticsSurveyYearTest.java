package com.osatum.wbn.application.analytics.assembler;

import com.osatum.wbn.application.analytics.repository.AnalyticsJournalRepository;
import com.osatum.wbn.application.analytics.service.AnalyticsJournalService;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.domain.model.analytics.JournalAnalyticsType;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcv;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalyticsSurveyYearTest {
    @Test
    void historicalChartsUseTheSelectedSurveyRatherThanRevisionsInTheCurrentSurvey() {
        var configuration = spy(new ConfigurationService());
        doReturn(2025).when(configuration).getCurrentRatingYear();
        doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        AnalyticsJournalRepository analytics = mock();
        JournalRepository journals = mock();
        AnalyticsJournalViewAssembler assembler = mock();
        var service = new AnalyticsJournalService(assembler, analytics, configuration, journals);
        var journal = new Journal();
        journal.setId(123L);
        journal.setQuestionnaires(Set.of(questionnaire(2024, 10), questionnaire(2025, 999)));
        when(journals.findById(123L)).thenReturn(Optional.of(journal));

        service.getAnalyticsJournal(123L, 2024);

        var parameters = ArgumentCaptor.forClass(AnalyticsJournalViewParameter.class);
        verify(assembler).toDto(parameters.capture());
        assertThat(parameters.getValue().getEntity()).filteredOn(item -> item.getType() == JournalAnalyticsType.ART_SCIENTIFIC_AMOUNT)
                .singleElement().satisfies(item -> {
                    assertThat(item.getYear()).isEqualTo(2024);
                    assertThat(item.getValue()).isEqualTo(10.0);
                });
        verifyNoInteractions(analytics);
    }

    private Questionnaire questionnaire(int year, int amountFor2024) {
        var publication = new ArticlePublished();
        publication.setYear(2024);
        publication.setScientificAmount(amountFor2024);
        var survey = new Survey();
        survey.getStability().setArticlePublished(List.of(publication));
        var data = new SurveyData();
        data.setSurvey(survey);
        var questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        questionnaire.setSurveyData(data);
        var rating = new RatingIcv();
        rating.setRatingStatus(RatingIcvStatus.PUBLISHED);
        questionnaire.setRating(rating);
        return questionnaire;
    }
}
