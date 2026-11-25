package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class CountrySpecificFeeSelection implements Serializable {
    private static final long serialVersionUID = 1L;

    private String authorCountryCode;
    private FeeAuthorVariant overrideVariant;
    private FeeAuthorVariant effectiveVariant;
    private FeeSelectionSource source;
}
