package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.TimeAxis;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView;
import org.springframework.stereotype.Component;
import java.util.*;
import static com.osatum.wbn.application.journal.service.DecisionMetric.*;

/**
 * Wyznacza surowe porównania w głównej grupie Level 1 i diagnostykę próby użytej w obliczeniach.
 * Wyklucza oceniany tytuł przed sprawdzeniem progu 30 innych czasopism i poprawnych wartości danej metryki.
 * Nie odczytuje bazy, nie zaokrągla wyników do prezentacji i nie decyduje o ich publikacji.
 */
@Component
public class DecisionBenchmarkCalculator {
    public DecisionBenchmarksView calculate(int year, UUID importId, Long journalId, Long groupId,
                                           Map<DecisionMetric, Double> own, List<DecisionObservation> population,
                                           List<TimeAxis> axes) {
        return DecisionBenchmarkResult.view(year, importId, journalId, results(journalId, groupId, own, population), axes);
    }

    /** Zachowuje rekord każdej metryki także przy braku porównania, aby eksport nie gubił niedostępnych wyników. */
    public List<DecisionBenchmarkResult> results(Long journalId, Long groupId, Map<DecisionMetric, Double> own,
                                                  List<DecisionObservation> population) {
        var group = groupId == null ? List.<DecisionObservation>of() : population.stream()
                .filter(value -> value.groups().contains(groupId)).toList();
        var others = group.stream().filter(value -> !value.journalId().equals(journalId)).toList();
        var result = new ArrayList<DecisionBenchmarkResult>();
        for (var metric : DecisionMetric.values()) {
            var values = others.stream().map(value -> value.values().get(metric)).filter(Objects::nonNull).toList();
            String reason = groupId == null ? "MISSING_OR_INVALID_MAIN_GROUP" : !own.containsKey(metric) ? "MISSING_OR_INVALID_OWN_VALUE"
                    : others.size() < 30 ? "INSUFFICIENT_OTHER_JOURNALS" : values.size() < 30 ? "INSUFFICIENT_VALID_OBSERVATIONS" : null;
            Double lower = null, equal = null, q1 = null, q3 = null, median = null, chargedPercent = null;
            Boolean allEqual = null;
            Long charged = metric.isFee() ? values.stream().filter(value -> value > 0).count() : null;
            if (reason == null) {
                if (metric == ICV) {
                    long lowerCount = values.stream().filter(value -> value < own.get(ICV)).count();
                    long equalCount = values.stream().filter(value -> value.doubleValue() == own.get(ICV)).count();
                    lower = 100.0 * lowerCount / values.size(); equal = 100.0 * equalCount / values.size();
                    allEqual = equalCount == values.size();
                } else if (metric.isTime() || metric == REJECTION_RATE) {
                    var sorted = DecisionStatistics.sorted(values);
                    q1 = DecisionStatistics.percentile(sorted, .25); q3 = DecisionStatistics.percentile(sorted, .75);
                } else if (metric == FOREIGN_AUTHORS || metric == FOREIGN_REVIEWERS || metric == FOREIGN_COUNCIL) {
                    median = DecisionStatistics.percentile(DecisionStatistics.sorted(values), .5);
                } else {
                    chargedPercent = 100.0 * charged / values.size();
                }
            }
            result.add(new DecisionBenchmarkResult(journalId, metric, groupId, group.size(), values.size(), own.get(metric),
                    reason, lower, equal, allEqual, q1, q3, median, charged, chargedPercent));
        }
        return List.copyOf(result);
    }
}
