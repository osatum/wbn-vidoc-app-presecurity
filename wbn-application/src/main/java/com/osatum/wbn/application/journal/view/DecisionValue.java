package com.osatum.wbn.application.journal.view;

/** Availability of one value, independent of the survey's annual qualification. */
public record DecisionValue<T>(State status, T value) {
    public enum State { AVAILABLE, MISSING, INVALID, UNRESOLVED, RATES_UNAVAILABLE }
    public static <T> DecisionValue<T> available(T value) { return new DecisionValue<>(State.AVAILABLE, value); }
    public static <T> DecisionValue<T> missing() { return new DecisionValue<>(State.MISSING, null); }
    public static <T> DecisionValue<T> invalid() { return new DecisionValue<>(State.INVALID, null); }
}
