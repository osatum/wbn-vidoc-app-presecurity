package com.osatum.wbn.domain.model.survey.model;

import java.io.Serializable;

/**
 * Oświadczenia ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT}).
 *
 * Odpowiada checkboxom:
 * SURVEY_CREATE.LABEL_INFO_STATEMENT_CONFLICT
 * SURVEY_CREATE.LABEL_INFO_STATEMENT_FINANCE_SOURCE
 * SURVEY_CREATE.LABEL_INFO_STATEMENT_AUTHOR_CONTRIBUTION
 * SURVEY_CREATE.LABEL_INFO_STATEMENT_ORIGINAL_KIND
 * SURVEY_CREATE.LABEL_INFO_STATEMENT_NONE
 */
public enum StatementType implements Serializable {

    /** Oświadczenie o konflikcie interesów */
    CONFLICT,

    /** Oświadczenie o źródłach finansowania */
    FINANCE_SOURCE,

    /** Oświadczenie o wkładzie autora */
    AUTHOR_CONTRIBUTION,

    /** Oświadczenie o oryginalności/rodzaju pracy */
    ORIGINAL_KIND,

    /** Oświadczenie o zakresie wykorzystania narzędzi AI. */
    AI_USAGE_SCOPE,

    /** Żadne z powyższych (wyklucza pozostałe) */
    NONE
}
