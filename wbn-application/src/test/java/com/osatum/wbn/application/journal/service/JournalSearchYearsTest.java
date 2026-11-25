package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.dictionary.service.*;
import com.osatum.wbn.application.journal.assembler.*;
import com.osatum.wbn.application.journal.view.JournalView;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.Title;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.JournalTitle;
import com.osatum.wbn.domain.model.survey.model.TitleType;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalSearchYearsTest {
    private final ConfigurationService configuration = spy(new ConfigurationService());
    private final JournalRepository repository = mock();
    private final JournalViewMapper mapper = mock();
    private final JournalService service = new JournalService(mock(JournalListQueryHandler.class), repository, mapper,
            mock(Validator.class), mock(JournalSearchCriteriaMapper.class), mock(DictionaryMniswDisciplineService.class),
            mock(OperatingProfileService.class), configuration);

    @BeforeEach
    void configureMapper() {
        when(mapper.toView(any(Journal.class), anyInt())).thenAnswer(invocation -> new JournalView());
    }

    @Test
    void historicalQuestionnaireContextKeepsBasicDetailsFromTheCurrentJournal() {
        doReturn(2025).when(configuration).getCurrentRatingYear();
        doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        var journal = new Journal();
        journal.setId(123L);
        var title = new Title();
        title.setContent("Current catalogue title");
        journal.setTitles(Set.of(title));
        journal.setQuestionnaires(Set.of(questionnaire(2024), questionnaire(2025)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        var view = service.getJournal(123L, 2024);

        assertThat(view.getSurveyYear()).isEqualTo(2024);
        assertThat(view.isQuestionnaireAvailable()).isTrue();
        assertThat(view.getAvailableSurveyYears()).containsExactly(2025, 2024);

        verify(mapper).toView(same(journal), eq(2024));
        assertThat(journal.getTitles()).extracting(Title::getContent).containsExactly("Current catalogue title");
        assertThat(journal.getQuestionnaires()).hasSize(2);
    }

    @Test
    void missingHistoricalSurveyDoesNotFallBackToTheCurrentSurvey() {
        doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        var journal = new Journal();
        journal.setQuestionnaires(Set.of(questionnaire(2025)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        var view = service.getJournal(123L, 2024);

        assertThat(view.getSurveyYear()).isEqualTo(2024);
        assertThat(view.isQuestionnaireAvailable()).isFalse();
        assertThat(view.getAvailableSurveyYears()).containsExactly(2025);
        verify(mapper).toView(journal, 2024);
    }

    @Test
    void directEntryUsesTheCurrentYearsSurvey() {
        doReturn(2025).when(configuration).getCurrentRatingYear();
        doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        var journal = new Journal();
        journal.setQuestionnaires(Set.of(questionnaire(2024), questionnaire(2025)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        var view = service.getJournal(123L);

        assertThat(view.getSurveyYear()).isEqualTo(2025);
        assertThat(view.isQuestionnaireAvailable()).isTrue();
        verify(mapper).toView(same(journal), eq(2025));
    }

    @Test
    void switchingQuestionnaireYearsDoesNotReplaceTheCatalogueDetails() {
        doReturn(2025).when(configuration).getCurrentRatingYear();
        doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        var journal = new Journal();
        var title = new Title();
        title.setContent("Current catalogue title");
        journal.setTitles(Set.of(title));
        journal.setQuestionnaires(Set.of(questionnaire(2024), questionnaire(2025)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        var current = service.getJournal(123L, 2025);
        var historical = service.getJournal(123L, 2024);

        verify(mapper).toView(same(journal), eq(2025));
        verify(mapper).toView(same(journal), eq(2024));
        assertThat(current.getSurveyYear()).isEqualTo(2025);
        assertThat(historical.getSurveyYear()).isEqualTo(2024);
        assertThat(current.isQuestionnaireAvailable()).isTrue();
        assertThat(historical.isQuestionnaireAvailable()).isTrue();
        assertThat(journal.getTitles()).extracting(Title::getContent).containsExactly("Current catalogue title");
    }

    @Test
    void missingCurrentSurveyListsOnlyUsablePublishedAlternatives() {
        doReturn(2025).when(configuration).getCurrentRatingYear();
        doReturn(List.of(2025, 2024, 2023)).when(configuration).getAvailableSearchYears();
        var journal = new Journal();
        var empty = new Questionnaire();
        empty.setYear(2023);
        journal.setQuestionnaires(Set.of(questionnaire(2024), questionnaire(2022), empty));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        var view = service.getJournal(123L);

        assertThat(view.getSurveyYear()).isEqualTo(2025);
        assertThat(view.isQuestionnaireAvailable()).isFalse();
        assertThat(view.getAvailableSurveyYears()).containsExactly(2024);
        verify(mapper).toView(journal, 2025);
    }

    @Test
    void journalWithoutQuestionnairesStillHasCatalogueDetails() {
        var journal = new Journal();
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        var view = service.getJournal(123L);

        assertThat(view.isQuestionnaireAvailable()).isFalse();
        assertThat(view.getAvailableSurveyYears()).isEmpty();
        verify(mapper).toView(journal, 2024);
    }

    @Test
    void ambiguousSurveyIsNotSilentlySelected() {
        var journal = new Journal();
        journal.setQuestionnaires(Set.of(questionnaire(2024), questionnaire(2024)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        assertThatIllegalArgumentException().isThrownBy(() -> service.getJournal(123L, 2024))
                .withMessageContaining("Ambiguous questionnaire");
        verifyNoInteractions(mapper);
    }

    @Test
    void unavailableYearIsRejectedBeforeAccessingTheJournal() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.getJournal(123L, 2023));
        verifyNoInteractions(repository, mapper);
    }

    private Questionnaire questionnaire(int year) {
        var title = new JournalTitle(true, "Annual title " + year, null, null, TitleType.ORIGINAL);
        var survey = new Survey();
        survey.getJournal().setTitles(List.of(title));
        var data = new SurveyData();
        data.setSurvey(survey);
        var questionnaire = new Questionnaire();
        questionnaire.setYear(year);
        questionnaire.setSurveyData(data);
        return questionnaire;
    }
}
