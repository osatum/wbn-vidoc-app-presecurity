package com.osatum.wbn.application.journal.repository;

import com.osatum.wbn.domain.model.survey.Survey;
import java.util.List;

/**
 * Odczyt rocznych kandydatów do analityki wraz z pochodzeniem oceny i techniczną kwalifikacją źródła.
 * Zachowuje dane potrzebne do rozliczenia różnic między statusem źródłowym PUBLISHED i projekcją INDEXED.
 */
public interface DecisionPopulationRepository {
    record Entry(Long journalId, Double icv, Survey survey, Long questionnaireId,
                 String assessmentStatus, Double assessmentIcv, String exclusionReason) {
        public Entry(Long journalId, Double icv, Survey survey) {
            this(journalId, icv, survey, null, "PUBLISHED", icv, null);
        }
    }
    List<Entry> findCandidates(int surveyYear);
}
