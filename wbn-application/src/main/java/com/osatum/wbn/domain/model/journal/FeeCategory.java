package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.survey.Standards;
import com.osatum.wbn.domain.model.survey.model.AuthorsFee;

public enum FeeCategory {
    OTHER, OPEN_ACCESS, PUBLICATION, RAPID_REVIEW, SUBMISSION;

    public AuthorsFee from(Standards standards) {
        if (standards == null) {
            return null;
        }
        return switch (this) {
            case OTHER -> standards.getApc();
            case OPEN_ACCESS -> standards.getOpenAccessFee();
            case PUBLICATION -> standards.getPublicationFee();
            case RAPID_REVIEW -> standards.getRapidReview();
            case SUBMISSION -> standards.getSubmissionFee();
        };
    }
}
