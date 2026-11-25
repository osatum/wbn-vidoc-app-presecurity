package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.assembler.SurveyToJournalUpdater;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.domain.repository.questionnaire.QuestionnaireRepository;
import com.osatum.wbn.domain.service.journal.JournalSurveyPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Synchronizuje dane czasopisma z ankietą wskazanego roku oceny, a nie z najnowszą dostępną ankietą.
 * Historia ocen jest aktualizowana osobno, bez zmiany roku wyszukiwania P1.
 */
@Service
@RequiredArgsConstructor
public class JournalQuestionnaireSyncService {
    private final JournalRepository journalRepository;
    private final QuestionnaireRepository questionnaireRepository;
    private final ConfigurationService configurationService;
    private final SurveyToJournalUpdater updater;
    private final JournalSurveyPolicy surveyPolicy;

    /**
     * Sprawdza ankiety przed modyfikacją modelu, aktualizuje bieżący rok oceny i odświeża oceny historyczne.
     */
    @Transactional
    public void updateJournalFromCurrentQuestionnaire(Long journalId) {
        Journal journal = journalRepository.findById(journalId)
                .orElseThrow(() -> new NoSuchElementException("Journal not found for id: " + journalId));

        if (journal.getQuestionnaires() == null){
            return;
        }

        Integer year = configurationService.getCurrentRatingYear();
        surveyPolicy.resolvePublished(journal, year, configurationService.getAvailableSearchYears());
        Optional<Questionnaire> oQuestionnaire = journal.getQuestionnaires().stream()
                .filter(x-> x.getYear().equals(year))
                .findFirst();
        if (oQuestionnaire.isPresent()) {
            Questionnaire questionnaire = oQuestionnaire.get();
            if (questionnaire.getSurveyData() != null && questionnaire.getSurveyData().getSurvey() != null) {
                updater.update(journal, questionnaire.getSurveyData().getSurvey());
            }
        }

        updater.updateRatings(journal,journal.getQuestionnaires().stream().toList());
    }
}
