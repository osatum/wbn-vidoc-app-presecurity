package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.view.DecisionFeeView;
import com.osatum.wbn.application.journal.view.DecisionValue;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView.Group;
import com.osatum.wbn.domain.model.survey.model.AuthorsFee;
import com.osatum.wbn.domain.model.survey.model.JournalTitle;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import java.util.List;
import java.util.Map;

/**
 * Modele migawki wejściowej zachowywanej razem z generacją benchmarków.
 * Rozdzielają zapisane źródła, ich kwalifikację i wartości po walidacji, aby kontrola wyników nie wymagała
 * odczytywania później zmienionych ankiet. Zakres danych nie obejmuje osób ani sesji użytkowników.
 */
public final class DecisionPreparedData {
    private DecisionPreparedData() { }

    /** Zachowuje model i szczegóły opłaty obok kwalifikacji pobiera/nie pobiera/brak prawidłowych danych. */
    public record FeeInput(FeeCategory category, AuthorsFee source, DecisionFeeView classification) { }
    public record Manuscripts(Integer year, Integer received, Integer rejected) { }
    /** Identyfikator ankiety jest lokalny; ICV oceny źródłowej i ICV użyty do obliczeń pozostają odrębne. */
    public record Source(Long questionnaireId, String assessmentStatus, Double assessmentIcv, Double icv,
                         Integer evaluationYear, String timestamp, List<JournalTitle> titles,
                         Integer initialAssessment, Integer review, Integer publication,
                         List<Manuscripts> manuscripts, Double foreignAuthors, Double foreignReviewers,
                         Double foreignCouncil, List<FeeInput> fees, String exclusionReason) { }
    /** Jeden tytuł może mieć wiele źródeł kontrolnych, lecz zasila populację najwyżej jedną obserwacją. */
    public record Input(Long journalId, List<Source> sources, Long mainGroupId, String mainGroupState,
                        List<Group> groups, Map<DecisionMetric, DecisionValue<Double>> metrics,
                        String exclusionReason) {
        public boolean included() { return exclusionReason == null; }
    }
    /** Obserwacje zawierają tylko zakwalifikowane tytuły, a wejścia również kandydatów wyłączonych z obliczeń. */
    public record Population(List<DecisionObservation> observations, List<Input> inputs) { }
}
