package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.domain.model.journal.FeeCategory;

/** Wspólne kody 13 metryk używane przez obliczenia, zapis w bazie i eksport kontrolny P2. */
public enum DecisionMetric {
    ICV, INITIAL_ASSESSMENT, REVIEW, PUBLICATION, REJECTION_RATE,
    FOREIGN_AUTHORS, FOREIGN_REVIEWERS, FOREIGN_COUNCIL,
    SUBMISSION_FEE, PUBLICATION_FEE, OPEN_ACCESS_FEE, RAPID_REVIEW_FEE, OTHER_FEE;

    public boolean isTime() {
        return this == INITIAL_ASSESSMENT || this == REVIEW || this == PUBLICATION;
    }

    public boolean isFee() {
        return switch (this) {
            case SUBMISSION_FEE, PUBLICATION_FEE, OPEN_ACCESS_FEE, RAPID_REVIEW_FEE, OTHER_FEE -> true;
            default -> false;
        };
    }

    public static DecisionMetric fee(FeeCategory category) {
        return switch (category) {
            case SUBMISSION -> SUBMISSION_FEE;
            case PUBLICATION -> PUBLICATION_FEE;
            case OPEN_ACCESS -> OPEN_ACCESS_FEE;
            case RAPID_REVIEW -> RAPID_REVIEW_FEE;
            case OTHER -> OTHER_FEE;
        };
    }
}
