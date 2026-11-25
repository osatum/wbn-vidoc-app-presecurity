package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Oświadczenia ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT}).
 */
@Data
public class InfoStatement implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Oświadczenie o konflikcie interesów ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT_CONFLICT}). */
    private Boolean conflict = false;

    /** Oświadczenie o źródłach finansowania ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT_FINANCE_SOURCE}). */
    private Boolean financeSource = false;

    /** Oświadczenie o wkładzie autora ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT_AUTHOR_CONTRIBUTION}). */
    private Boolean authorContribution = false;

    /** Oświadczenie o oryginalności/rodzaju pracy ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT_ORIGINAL_KIND}). */
    private Boolean originalKind = false;

    /** Żadne z powyższych ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT_NONE}). */
    private Boolean none = false;
}
