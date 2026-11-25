package com.osatum.wbn.application.journal.service;

import java.util.Map;
import java.util.Set;

/** Jeden publiczny identyfikator w rocznej populacji; projekcja nie wynosi encji źródłowych poza odczyt. */
public record DecisionObservation(Long journalId, Set<Long> groups, Map<DecisionMetric, Double> values) {
    public DecisionObservation {
        groups = Set.copyOf(groups);
        values = Map.copyOf(values);
        if (journalId == null || journalId <= 0 || values.values().stream().anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("Invalid decision observation");
        }
    }
}
