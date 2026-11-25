package com.osatum.wbn.infrastructure.elasticsearch.listener;

import com.osatum.wbn.application.journal.event.JournalRecreateEvent;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ArticlePublished;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.domain.service.journal.JournalSurveyPolicy;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.simple.JournalSurveyDocumentAssembler;
import com.osatum.wbn.infrastructure.elasticsearch.service.ElasticsearchIndexRecreator;
import com.osatum.wbn.infrastructure.elasticsearch.service.JournalSearchProjectionWriter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalEventsListenerTest {
    private final JournalRepository repository = mock(JournalRepository.class);
    private final JournalSearchProjectionWriter writer = mock(JournalSearchProjectionWriter.class);
    private final ElasticsearchIndexRecreator recreator = mock(ElasticsearchIndexRecreator.class);
    private final JournalEventsListener listener = new JournalEventsListener(repository,
                new JournalSurveyDocumentAssembler(new ConfigurationService(),
                        new com.osatum.wbn.domain.service.journal.JournalFeePolicy(new com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy()),
                        new JournalSurveyPolicy()), writer, recreator);

    @Test
    void replacesAllAnnualProjectionsOnSaveIncludingRemovedYears() {
        var journal = journal();
        when(repository.findById(123L)).thenReturn(Optional.of(journal));
        listener.on(new JournalSavedEvent(123L));
        var order = inOrder(writer);
        order.verify(writer).deleteJournalDocuments(123L);
        var documents = ArgumentCaptor.forClass(JournalDocument.class);
        order.verify(writer, times(2)).save(documents.capture());
        assertThat(documents.getAllValues()).extracting(JournalDocument::getSurveyYear).containsExactlyInAnyOrder(2024, 2025);
        journal.setQuestionnaires(Set.of());
        listener.on(new JournalSavedEvent(123L));
        verify(writer, times(2)).deleteJournalDocuments(123L);
        verify(writer, times(2)).save(any());
        verify(writer, never()).refresh();
    }

    @Test
    void validatesBeforeDeletingExistingDocuments() {
        var journal = journal();
        journal.getQuestionnaires().stream().filter(questionnaire -> questionnaire.getYear() == 2024)
                .findFirst().orElseThrow().getSurveyData().getSurvey().setEvaluationYear(1900);
        when(repository.findById(123L)).thenReturn(Optional.of(journal));
        assertThatIllegalArgumentException().isThrownBy(() -> listener.on(new JournalSavedEvent(123L)));
        verifyNoInteractions(writer);
    }

    @Test
    void invalidHistoricalCountsAreExcludedWhenReplacingAllJournalProjections() {
        var historical = questionnaire(2021);
        var counts = new ArticlePublished();
        counts.setYear(2021);
        counts.setReceivedManuscriptCount(33);
        counts.setRejectedManuscriptCount(35);
        historical.getSurveyData().getSurvey().getStability().setArticlePublished(List.of(counts));
        var journal = journal();
        journal.setQuestionnaires(Set.of(historical, questionnaire(2024)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        listener.on(new JournalSavedEvent(123L));

        var order = inOrder(writer);
        order.verify(writer).deleteJournalDocuments(123L);
        var document = ArgumentCaptor.forClass(JournalDocument.class);
        order.verify(writer).save(document.capture());
        assertThat(document.getValue().getDocumentId()).isEqualTo("123:2024");
        verifyNoMoreInteractions(writer);
        assertThat(journal.getQuestionnaires()).hasSize(2);
        assertThat(counts.getReceivedManuscriptCount()).isEqualTo(33);
        assertThat(counts.getRejectedManuscriptCount()).isEqualTo(35);
    }

    @Test
    void historicalAmbiguityDoesNotBlockSavingUniqueConfiguredYear() {
        var journal = journal();
        journal.setQuestionnaires(Set.of(questionnaire(2020), questionnaire(2020), questionnaire(2024)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        listener.on(new JournalSavedEvent(123L));

        verify(writer).deleteJournalDocuments(123L);
        var document = ArgumentCaptor.forClass(JournalDocument.class);
        verify(writer).save(document.capture());
        assertThat(document.getValue().getSurveyYear()).isEqualTo(2024);
    }

    @Test
    void ambiguityInConfiguredYearDoesNotRemoveExistingIndexDocuments() {
        var journal = journal();
        journal.setQuestionnaires(Set.of(questionnaire(2024), questionnaire(2024), questionnaire(2025)));
        when(repository.findById(123L)).thenReturn(Optional.of(journal));

        assertThatIllegalArgumentException().isThrownBy(() -> listener.on(new JournalSavedEvent(123L)))
                .withMessageContaining("Duplicate survey year 2024");

        verifyNoInteractions(writer);
    }

    @Test
    void rebuildUsesTheSameAnnualProjectionAndRefreshesOnce() {
        when(repository.findAll()).thenReturn(List.of(journal()));
        listener.on(new JournalRecreateEvent());
        var order = inOrder(recreator, writer);
        order.verify(recreator).recreateIndex(JournalDocument.class);
        var documents = ArgumentCaptor.forClass(JournalDocument.class);
        order.verify(writer, times(2)).save(documents.capture());
        assertThat(documents.getAllValues()).extracting(JournalDocument::getDocumentId).containsExactlyInAnyOrder("123:2024", "123:2025");
        order.verify(writer).refresh();
        verifyNoMoreInteractions(writer);
    }

    @Test
    void rebuildSkipsInvalidOtherYearsAndKeepsValidProjections() {
        var journal = journal();
        var later = journal.getQuestionnaires().stream().filter(questionnaire -> questionnaire.getYear() == 2025)
                .findFirst().orElseThrow();
        later.setYear(2026);
        later.getSurveyData().getSurvey().getStandards().getAvgManuscriptTimes().setPublication(-1);
        when(repository.findAll()).thenReturn(List.of(journal));

        listener.on(new JournalRecreateEvent());

        var order = inOrder(recreator, writer);
        order.verify(recreator).recreateIndex(JournalDocument.class);
        var document = ArgumentCaptor.forClass(JournalDocument.class);
        order.verify(writer).save(document.capture());
        assertThat(document.getValue().getDocumentId()).isEqualTo("123:2024");
        order.verify(writer).refresh();
        verifyNoMoreInteractions(writer);
        assertThat(journal.getQuestionnaires()).hasSize(2);
        assertThat(later.getSurveyData().getSurvey().getStandards().getAvgManuscriptTimes().getPublication()).isEqualTo(-1);
    }

    @Test
    void missingJournalDoesNotModifyTheIndex() {
        when(repository.findById(123L)).thenReturn(Optional.empty());

        listener.on(new JournalSavedEvent(123L));

        verifyNoInteractions(writer, recreator);
    }

    @Test
    void rebuildingAnEmptyDatasetStillRefreshesTheRecreatedIndexOnce() {
        when(repository.findAll()).thenReturn(List.of());

        listener.on(new JournalRecreateEvent());

        var order = inOrder(recreator, writer);
        order.verify(recreator).recreateIndex(JournalDocument.class);
        order.verify(writer).refresh();
        verifyNoMoreInteractions(writer);
    }

    private Journal journal() {
        var journal = new Journal();
        journal.setId(123L);
        journal.setQuestionnaires(Set.of(questionnaire(2024), questionnaire(2025)));
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
