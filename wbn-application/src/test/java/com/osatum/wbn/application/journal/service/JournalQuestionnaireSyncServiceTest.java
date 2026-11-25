package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.assembler.SurveyToJournalUpdater;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.domain.repository.questionnaire.QuestionnaireRepository;
import com.osatum.wbn.domain.service.journal.JournalSurveyPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalQuestionnaireSyncServiceTest {
    private final JournalRepository repository = mock(JournalRepository.class);
    private final SurveyToJournalUpdater updater = mock(SurveyToJournalUpdater.class);
    private final ConfigurationService configuration = mock(ConfigurationService.class);
    private final JournalQuestionnaireSyncService service = new JournalQuestionnaireSyncService(repository,
            mock(QuestionnaireRepository.class), configuration, updater, new JournalSurveyPolicy());

    @Test
    void rejectsInvalidSurveyBeforeUpdatingJournalOrRatings() {
        var current = questionnaire(2024);
        var later = questionnaire(2025);
        current.getSurveyData().getSurvey().getStandards().getAvgManuscriptTimes().setPublication(-1);
        var journal = journal(current, later);
        when(repository.findById(123L)).thenReturn(Optional.of(journal));
        when(configuration.getCurrentRatingYear()).thenReturn(2024);

        assertThatIllegalArgumentException().isThrownBy(() -> service.updateJournalFromCurrentQuestionnaire(123L))
                .withMessageContaining("journal 123, year 2024");

        verifyNoInteractions(updater);
    }

    @ParameterizedTest
    @ValueSource(ints = {2021, 2025})
    void invalidOtherYearDoesNotBlockCurrentSynchronizationOrRemoveHistoricalRatings(int otherYear) {
        var current = questionnaire(2024);
        var other = questionnaire(otherYear);
        other.getSurveyData().getSurvey().getStandards().getAvgManuscriptTimes().setPublication(-1);
        var journal = journal(current, other);
        when(repository.findById(123L)).thenReturn(Optional.of(journal));
        when(configuration.getCurrentRatingYear()).thenReturn(2024);

        service.updateJournalFromCurrentQuestionnaire(123L);

        var order = inOrder(updater);
        order.verify(updater).update(journal, current.getSurveyData().getSurvey());
        order.verify(updater).updateRatings(eq(journal), argThat(values ->
                values.size() == 2 && values.contains(current) && values.contains(other)));
        assertThat(journal.getQuestionnaires()).hasSize(2);
        assertThat(other.getSurveyData().getSurvey().getStandards().getAvgManuscriptTimes().getPublication()).isEqualTo(-1);
    }

    @Test
    void validatesAndSynchronizesUsingTheSameCurrentRatingYear() {
        var current = questionnaire(2025);
        var historical = questionnaire(2020);
        var duplicate = questionnaire(2020);
        var journal = journal(historical, duplicate, current, questionnaire(2024));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));
        when(configuration.getCurrentRatingYear()).thenReturn(2025);

        service.updateJournalFromCurrentQuestionnaire(123L);

        var order = inOrder(updater);
        order.verify(updater).update(journal, current.getSurveyData().getSurvey());
        order.verify(updater).updateRatings(eq(journal), argThat(values -> values.size() == 4 && values.contains(current)));
    }

    @Test
    void rejectsDuplicateConfiguredYearBeforeAnyModelUpdates() {
        var journal = journal(questionnaire(2025), questionnaire(2025));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));
        when(configuration.getCurrentRatingYear()).thenReturn(2025);

        assertThatIllegalArgumentException().isThrownBy(() -> service.updateJournalFromCurrentQuestionnaire(123L))
                .withMessageContaining("Duplicate survey year 2025");

        verifyNoInteractions(updater);
    }

    @Test
    void journalWithoutQuestionnairesRemainsUnchanged() {
        var journal = new Journal();
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        service.updateJournalFromCurrentQuestionnaire(123L);

        verifyNoInteractions(updater);
    }

    private Journal journal(Questionnaire... questionnaires) {
        var journal = new Journal();
        journal.setId(123L);
        journal.setQuestionnaires(Set.of(questionnaires));
        return journal;
    }

    private Questionnaire questionnaire(int year) {
        var questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        var data = new SurveyData();
        data.setSurvey(new Survey());
        questionnaire.setSurveyData(data);
        return questionnaire;
    }
}
