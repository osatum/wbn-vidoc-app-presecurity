package com.osatum.wbn.application.report.survey.sevice;

import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoAssembler;
import com.osatum.wbn.application.report.survey.assembler.SurveyReportDtoParameters;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.RatingIcv;
import com.osatum.wbn.domain.model.questionnaire.RatingIcvStatus;
import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurveyReportJournalServiceTest {

    private final JournalRepository journalRepository = mock(JournalRepository.class);
    private final SurveyReportDtoAssembler assembler = mock(SurveyReportDtoAssembler.class);
    private final SurveyReportJournalService service = new SurveyReportJournalService(journalRepository, assembler);

    @Test
    void assemblesPublishedQuestionnaireInPolishAndEnglish() {
        Questionnaire questionnaire = questionnaire(RatingIcvStatus.PUBLISHED, null);
        when(journalRepository.findById(1L)).thenReturn(Optional.of(journal(questionnaire)));
        when(assembler.toDto(any(SurveyReportDtoParameters.class))).thenReturn(new SurveyReportDto());

        var result = service.assemble(1L, 2024);

        assertThat(result).isPresent();
        ArgumentCaptor<SurveyReportDtoParameters> parameters = ArgumentCaptor.forClass(SurveyReportDtoParameters.class);
        verify(assembler, org.mockito.Mockito.times(2)).toDto(parameters.capture());
        assertThat(parameters.getAllValues())
                .extracting(value -> value.getLocale().getLanguage())
                .containsExactly("pl", "en");
    }

    @Test
    void assemblesNotIndexedQuestionnaireWithVisibleReason() {
        Questionnaire questionnaire = questionnaire(RatingIcvStatus.NOT_INDEXED, ReasonNotIndexing.dec06);
        when(journalRepository.findById(1L)).thenReturn(Optional.of(journal(questionnaire)));
        when(assembler.toDto(any(SurveyReportDtoParameters.class))).thenReturn(new SurveyReportDto());

        var result = service.assemble(1L, 2024);

        assertThat(result).isPresent();
        verify(assembler, org.mockito.Mockito.times(2)).toDto(any(SurveyReportDtoParameters.class));
    }

    @Test
    void assemblesOnlyRequestedLanguage() {
        Questionnaire questionnaire = questionnaire(RatingIcvStatus.PUBLISHED, null);
        when(journalRepository.findById(1L)).thenReturn(Optional.of(journal(questionnaire)));
        when(assembler.toDto(any(SurveyReportDtoParameters.class))).thenReturn(new SurveyReportDto());

        var result = service.assemble(1L, 2024, Locale.ENGLISH);

        assertThat(result).isPresent();
        ArgumentCaptor<SurveyReportDtoParameters> parameters = ArgumentCaptor.forClass(SurveyReportDtoParameters.class);
        verify(assembler).toDto(parameters.capture());
        assertThat(parameters.getValue().getLocale()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void skipsQuestionnaireWithInvisibleReason() {
        Questionnaire questionnaire = questionnaire(RatingIcvStatus.NOT_INDEXED, ReasonNotIndexing.dec01);
        when(journalRepository.findById(1L)).thenReturn(Optional.of(journal(questionnaire)));

        var result = service.assemble(1L, 2024);

        assertThat(result).isEmpty();
        verify(assembler, never()).toDto(any(SurveyReportDtoParameters.class));
    }

    @Test
    void skipsQuestionnaireWithDifferentStatusOrYear() {
        Questionnaire questionnaire = questionnaire(RatingIcvStatus.FOR_APPROVAL, null);
        questionnaire.setYear(2023);
        when(journalRepository.findById(1L)).thenReturn(Optional.of(journal(questionnaire)));

        var result = service.assemble(1L, 2024);

        assertThat(result).isEmpty();
        verify(assembler, never()).toDto(any(SurveyReportDtoParameters.class));
    }

    private Journal journal(Questionnaire questionnaire) {
        Journal journal = new Journal();
        journal.setQuestionnaires(Set.of(questionnaire));
        return journal;
    }

    private Questionnaire questionnaire(RatingIcvStatus status, ReasonNotIndexing reason) {
        RatingIcv rating = new RatingIcv();
        rating.setRatingStatus(status);
        rating.setReasonNotIndexing(reason);
        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setYear(2024);
        questionnaire.setRating(rating);
        return questionnaire;
    }
}
