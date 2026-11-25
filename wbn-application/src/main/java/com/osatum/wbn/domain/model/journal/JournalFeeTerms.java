package com.osatum.wbn.domain.model.journal;

import com.osatum.wbn.domain.model.survey.model.FeeAuthorVariant;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionSource;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionStatus;
import com.osatum.wbn.domain.model.survey.model.FeeType;

import java.util.List;

public record JournalFeeTerms(FeeCategory category, FeeType type, FeeSelectionStatus selectionStatus, FeeSelectionStatus resolutionStatus,
                              Selection selection, List<Detail> details, List<Amount> amounts,
                              boolean noFee, boolean comparable) {
    public JournalFeeTerms {
        details = List.copyOf(details);
        amounts = List.copyOf(amounts);
    }

    public record Selection(String authorCountryCode, FeeSelectionSource source,
                            FeeAuthorVariant overrideVariant, FeeAuthorVariant effectiveVariant) {
    }

    public record Detail(Integer minValue, Integer maxValue, String currencyCode, String description) {
    }

    public record Amount(int detailIndex, Integer value, String currency, String description) {
    }
}
