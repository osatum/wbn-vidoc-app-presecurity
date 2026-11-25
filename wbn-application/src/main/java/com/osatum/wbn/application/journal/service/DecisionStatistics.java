package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Axis;
import java.util.Arrays;
import java.util.Collection;

/** Wspólna interpolacja kwartylów i median zapobiega różnicom między rodzinami benchmarków. */
public final class DecisionStatistics {
    private DecisionStatistics() { }

    public static double[] sorted(Collection<Double> values) {
        if (values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Percentiles require finite values");
        }
        return values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }

    /** PERCENTILE.INC na już posortowanej próbie, bez zaokrągleń używanych dopiero podczas prezentacji. */
    public static double percentile(double[] sorted, double p) {
        if (sorted.length == 0 || !Double.isFinite(p) || p < 0 || p > 1) {
            throw new IllegalArgumentException("Invalid percentile sample or probability");
        }
        double h = (sorted.length - 1) * p;
        int i = (int) Math.floor(h);
        double fraction = h - i;
        return sorted[i] + fraction * (sorted[Math.min(i + 1, sorted.length - 1)] - sorted[i]);
    }

    public static Axis timeAxis(double[] sorted) {
        if (sorted.length < 31 || Arrays.stream(sorted).anyMatch(value -> !Double.isFinite(value) || value < 0)) {
            throw new IllegalArgumentException("A time axis requires at least 31 valid journals");
        }
        // Usunięcie po jednym maksimum/minimum obejmuje wszystkie wykluczenia własnego tytułu, także przy remisach.
        double lower = percentile(Arrays.copyOf(sorted, sorted.length - 1), 0.25);
        double upper = percentile(Arrays.copyOfRange(sorted, 1, sorted.length), 0.75);
        double margin = Math.max(1, 0.25 * (upper - lower));
        return new Axis(Math.max(0, Math.floor(lower - margin)), Math.ceil(upper + margin));
    }
}
