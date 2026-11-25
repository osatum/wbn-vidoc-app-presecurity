package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.TimeAxis;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.*;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import java.util.*;
import static com.osatum.wbn.application.journal.service.DecisionMetric.*;

/**
 * Surowy wynik jednej metryki przechowywany w generacji razem z diagnostyką dostępności.
 * Liczebność grupy dotyczy stanu przed wykluczeniem tytułu, a liczba poprawnych wartości — pozostałych tytułów.
 * Brak powodu niedostępności oznacza dostępne porównanie; niewłaściwe dla metryki składowe pozostają puste.
 * Teksty zależne od języka i zaokrąglenia prezentacyjne powstają dopiero podczas wyświetlania.
 */
public record DecisionBenchmarkResult(Long journalId, DecisionMetric metric, Long groupId,
                                      int groupSize, int validCount, Double ownValue, String unavailableReason,
                                      Double lowerPercent, Double equalPercent, Boolean allEqual,
                                      Double q1, Double q3, Double median, Long chargedCount, Double chargedPercent) {
    public boolean available() { return unavailableReason == null; }

    /**
     * Odtwarza kontrakt Karty z kompletu zapisanych metryk, bez ponownego liczenia statystyk.
     * Odrzuca duplikaty, rozbieżne grupy i osie z innego rocznika lub generacji zamiast ukrywać uszkodzony zapis.
     */
    public static DecisionBenchmarksView view(int year, UUID id, Long journalId,
                                              List<DecisionBenchmarkResult> results, List<TimeAxis> axes) {
        var values = new EnumMap<DecisionMetric, DecisionBenchmarkResult>(DecisionMetric.class);
        Long group = results.isEmpty() ? null : results.getFirst().groupId();
        for (var result : results) {
            if (!Objects.equals(journalId, result.journalId()) || !Objects.equals(group, result.groupId())
                    || values.put(result.metric(), result) != null) throw new IllegalStateException("Inconsistent stored P2 results");
        }
        if (values.size() != DecisionMetric.values().length) throw new IllegalStateException("Incomplete stored P2 results");
        var axisMap = new EnumMap<DecisionMetric, Axis>(DecisionMetric.class);
        for (var axis : axes) {
            if (axis.surveyYear() != year || !Objects.equals(group, axis.groupId()) || !id.equals(axis.importId())
                    || axisMap.put(axis.metric(), axis.axis()) != null) throw new IllegalStateException("Inconsistent P2 axis snapshot");
        }
        var icv = values.get(ICV);
        return new DecisionBenchmarksView(year, id, group,
                icv.available() ? Comparison.available(new Icv(icv.lowerPercent(), icv.equalPercent(), icv.allEqual())) : Comparison.unavailable(),
                new Times(time(values.get(INITIAL_ASSESSMENT), axisMap), time(values.get(REVIEW), axisMap), time(values.get(PUBLICATION), axisMap)),
                quartiles(values.get(REJECTION_RATE)),
                new International(median(values.get(FOREIGN_AUTHORS)), median(values.get(FOREIGN_REVIEWERS)), median(values.get(FOREIGN_COUNCIL))),
                List.of(FeeCategory.SUBMISSION, FeeCategory.PUBLICATION, FeeCategory.OPEN_ACCESS, FeeCategory.RAPID_REVIEW, FeeCategory.OTHER).stream().map(category -> {
                    var fee = values.get(DecisionMetric.fee(category));
                    return new Fee(category, fee.available() ? Comparison.available(new FeeRate(fee.chargedPercent())) : Comparison.unavailable());
                }).toList());
    }

    private static Comparison<Time> time(DecisionBenchmarkResult value, Map<DecisionMetric, Axis> axes) {
        if (!value.available()) return Comparison.unavailable();
        var axis = axes.get(value.metric());
        if (axis == null || axis.minimum() > value.q1() || axis.maximum() < value.q3()) {
            throw new IllegalStateException("Missing or invalid published P2 time axis");
        }
        return Comparison.available(new Time(value.q1(), value.q3(), axis));
    }

    private static Comparison<Quartiles> quartiles(DecisionBenchmarkResult value) {
        return value.available() ? Comparison.available(new Quartiles(value.q1(), value.q3())) : Comparison.unavailable();
    }
    private static Comparison<Median> median(DecisionBenchmarkResult value) {
        return value.available() ? Comparison.available(new Median(value.median())) : Comparison.unavailable();
    }
}
