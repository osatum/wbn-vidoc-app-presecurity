package com.osatum.wbn.application.journal.view;

import java.util.List;

public record JournalDecisionView(Integer surveyYear, AnnualStatus annualStatus,
                                  List<Integer> availableSurveyYears,
                                  JournalDecisionIdentityView identity, Double icv,
                                  DecisionValue.State icvStatus, MniswRatingView mnisw, List<IcvHistory> icvHistory,
                                  JournalDecisionAnnualView annual, DecisionBenchmarksView benchmarks,
                                  boolean benchmarksVisible) {
    public record IcvHistory(Integer year, com.osatum.wbn.domain.entity.RatingStatus status, Double value) { }
    public enum AnnualStatus {
        AVAILABLE, MISSING_SURVEY, NOT_EVALUATED, NOT_INDEXED, UNAVAILABLE, DATA_UPDATING, DATA_NOT_READY
    }
}
