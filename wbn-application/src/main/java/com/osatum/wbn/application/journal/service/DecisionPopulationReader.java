package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.application.journal.repository.DecisionPopulationRepository;
import com.osatum.wbn.application.journal.repository.DecisionPopulationRepository.Entry;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView.Group;
import com.osatum.wbn.domain.model.survey.Survey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.osatum.wbn.application.journal.service.DecisionPreparedData.*;

/**
 * Przygotowuje populację obliczeniową rocznika oraz ograniczoną migawkę wejść potrzebną do kontroli wyników.
 * Grupuje źródła według czasopisma, usuwa identyczne obserwacje i wyklucza sprzeczne dane.
 * Zachowuje także wyłączonych kandydatów, aby można było rozliczyć PUBLISHED względem faktycznej populacji P2.
 * Migawka obejmuje dane benchmarków i nazwy grup, bez list osób ani informacji o sesjach użytkowników.
 */
@Service
@RequiredArgsConstructor
public class DecisionPopulationReader {
    private final DecisionPopulationRepository repository;
    private final DictionaryScientificCategoryService categories;
    private final DecisionProfileReader profiles;
    private final DecisionMetricReader metrics;
    private final DecisionFeeReader fees;

    /** Jeden odczyt kandydatów i jedna migawka słownika zapewniają wspólne wejścia dla wyników i eksportu. */
    public Population read(int year) {
        var source = repository.findCandidates(year);
        var dictionary = categories.getAll().stream().collect(Collectors.toMap(value -> value.getId(), Function.identity()));
        var byJournal = new LinkedHashMap<Long, List<Entry>>();
        for (var entry : source) {
            if (entry.journalId() == null || entry.journalId() <= 0) throw new IllegalStateException("Invalid P2 source journal");
            byJournal.computeIfAbsent(entry.journalId(), key -> new ArrayList<>()).add(entry);
        }
        var observations = new ArrayList<DecisionObservation>();
        var inputs = new ArrayList<Input>();
        for (var journal : byJournal.entrySet()) {
            var entries = journal.getValue();
            var first = entries.stream().filter(entry -> reason(entry, year) == null).findFirst().orElse(entries.getFirst());
            var survey = first.survey() == null ? new Survey() : first.survey();
            var profile = profiles.profile(survey, dictionary::get);
            var groups = profile.memberships().value() == null ? List.<Group>of() : profile.memberships().value();
            var states = metrics.states(survey, year, first.icv());
            var candidates = entries.stream().filter(entry -> reason(entry, year) == null)
                    .map(entry -> new DecisionObservation(entry.journalId(),
                            membership(entry.survey(), dictionary), metrics.read(entry.survey(), year, entry.icv())))
                    .distinct().toList();
            String exclusion = candidates.size() > 1 ? "AMBIGUOUS_OBSERVATIONS"
                    : candidates.isEmpty() ? reason(first, year) : null;
            if (exclusion == null) observations.add(candidates.getFirst());
            inputs.add(new Input(journal.getKey(), entries.stream().map(entry -> source(entry, year)).distinct().toList(),
                    profile.mainGroup().value() == null ? null : profile.mainGroup().value().id(),
                    profile.mainGroup().status().name(), List.copyOf(groups), states, exclusion));
        }
        return new Population(List.copyOf(observations), List.copyOf(inputs));
    }

    private Set<Long> membership(Survey survey, Map<Long, com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory> dictionary) {
        var memberships = profiles.profile(survey, dictionary::get).memberships().value();
        return memberships == null ? Set.of() : memberships.stream().map(Group::id).collect(Collectors.toSet());
    }

    private String reason(Entry entry, int year) {
        if (entry.exclusionReason() != null) return entry.exclusionReason();
        if (entry.survey() == null) return "MISSING_ACTIVE_SURVEY";
        return entry.survey().getEvaluationYear() != null && entry.survey().getEvaluationYear() != year ? "ASSESSMENT_YEAR_MISMATCH" : null;
    }

    private Source source(Entry entry, int year) {
        var survey = entry.survey() == null ? new Survey() : entry.survey();
        var times = survey.getStandards() == null ? null : survey.getStandards().getAvgManuscriptTimes();
        var cooperation = survey.getCooperation();
        var feeInputs = fees.classify(survey.getStandards()).stream()
                .map(fee -> new FeeInput(fee.category(), fee.category().from(survey.getStandards()), fee)).toList();
        return new Source(entry.questionnaireId(), entry.assessmentStatus(), entry.assessmentIcv(), entry.icv(),
                survey.getEvaluationYear(), survey.getTimestamp(),
                survey.getJournal() == null || survey.getJournal().getTitles() == null ? List.of() : survey.getJournal().getTitles(),
                times == null ? null : times.getInitialAssessment(), times == null ? null : times.getReview(), times == null ? null : times.getPublication(),
                DecisionAnnualReader.annualRows(survey, year).stream()
                        .map(row -> new Manuscripts(row.getYear(), row.getReceivedManuscriptCount(), row.getRejectedManuscriptCount())).toList(),
                cooperation == null || cooperation.getAuthorMetric() == null ? null : cooperation.getAuthorMetric().getForeignAffiliationPercentage(),
                cooperation == null || cooperation.getReviewerMetric() == null ? null : cooperation.getReviewerMetric().getForeignAffiliationPercentage(),
                cooperation == null || cooperation.getDirectorsMetric() == null ? null : cooperation.getDirectorsMetric().getForeignAffiliationPercentage(),
                feeInputs, reason(entry, year));
    }
}
