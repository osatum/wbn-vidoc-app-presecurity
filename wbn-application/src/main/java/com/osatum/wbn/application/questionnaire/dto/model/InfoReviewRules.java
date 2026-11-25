package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Zasady recenzowania ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES}).
 */
@Data
public class InfoReviewRules implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Publikowana lista recenzentów ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLISHED_REVIEWERS_LIST}). */
    private Boolean publishedReviewersList = false;

    /** Minimum 2 recenzentów ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_MIN2REVIEWERS}). */
    private Boolean min2Reviewers = false;

    /** Oświadczenie recenzenta ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_REVIEWER_STATEMENT}). */
    private Boolean reviewerStatement = false;

    /** Przygotowanie recenzji ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PREPARE_REVIEW}). */
    private Boolean prepareReview = false;

    /** Publiczny formularz ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLIC_FORM}). */
    private Boolean publicForm = false;

    /** Publiczne kryteria ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLIC_CRITERIA}). */
    private Boolean publicCriteria = false;

    /** Żadne z powyższych ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_NONE}). */
    private Boolean none = false;
}
