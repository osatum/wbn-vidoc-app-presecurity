package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.FeeCategory;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DecisionBenchmarksView(Integer surveyYear, UUID importId, Long groupId,
                                     Comparison<Icv> icv, Times times, Comparison<Quartiles> rejectionRate,
                                     International international, List<Fee> fees) {
    public enum Availability { AVAILABLE, UNAVAILABLE }
    public record Comparison<T>(Availability status, T value) {
        public static <T> Comparison<T> available(T value) {
            return new Comparison<>(Availability.AVAILABLE, Objects.requireNonNull(value));
        }
        public static <T> Comparison<T> unavailable() { return new Comparison<>(Availability.UNAVAILABLE, null); }
    }
    public record Icv(double lowerPercent, double equalPercent, boolean allEqual) { }
    public record Quartiles(double q1, double q3) { }
    public record Axis(double minimum, double maximum) {
        public Axis {
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum < 0 || maximum <= minimum) {
                throw new IllegalArgumentException("Invalid time axis");
            }
        }
    }
    public record Time(double q1, double q3, Axis axis) { }
    public record Times(Comparison<Time> initialAssessment, Comparison<Time> review, Comparison<Time> publication) { }
    public record Median(double median) { }
    public record International(Comparison<Median> authors, Comparison<Median> reviewers, Comparison<Median> scientificCouncil) { }
    public record FeeRate(double chargedPercent) { }
    public record Fee(FeeCategory category, Comparison<FeeRate> comparison) { }
}
