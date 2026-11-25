package com.osatum.wbn.domain.model.survey.model;

import java.io.Serializable;

/**
 * Zasady recenzowania – odpowiada polom formularza:
 *
 * SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLISHED_REVIEWERS_LIST
 * SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_MIN2REVIEWERS
 * SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_REVIEWER_STATEMENT
 * SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PREPARE_REVIEW
 * SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLIC_FORM
 * SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLIC_CRITERIA
 * SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_NONE
 */
public enum ReviewRule implements Serializable {

    /** Publikowana lista recenzentów */
    PUBLISHED_REVIEWERS_LIST,

    /** Minimum 2 recenzentów */
    MIN_2_REVIEWERS,

    /** Oświadczenie recenzenta */
    REVIEWER_STATEMENT,

    /** Przygotowanie recenzji */
    PREPARE_REVIEW,

    /** Publiczny formularz */
    PUBLIC_FORM,

    /** Publiczne kryteria */
    PUBLIC_CRITERIA,

    /** Żadne z powyższych */
    NONE
}