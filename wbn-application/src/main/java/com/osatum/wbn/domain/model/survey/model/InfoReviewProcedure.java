package com.osatum.wbn.domain.model.survey.model;


import lombok.Data;

import java.io.Serializable;

/**
 * Procedura recenzowania ({@code SURVEY_CREATE.LABEL_REVIEW_PROCEDURE}).
 * <p>
 * Logika formularza:
 * <ul>
 *   <li>jeżeli zaznaczono dany typ recenzji, pojawia się pole procentowe (np. {@code doubleBlindReviewPercentage}),</li>
 *   <li>gdy nie zaznaczono żadnego typu, formularz pokazuje komunikat walidacyjny.</li>
 * </ul>
 */
@Data
public class InfoReviewProcedure implements Serializable {


    private static final long serialVersionUID = 1L;

    private BooleanDouble doubleBlindReview;

    /** Double-blind review ({@code SURVEY_CREATE.LABEL_REVIEW_PROCEDURE_DOUBLE_BLIND_REVIEW}). */
    //private Boolean doubleBlindReview = false;

    /** Udział procentowy double-blind (pole aktywne gdy {@code doubleBlindReview == true}). */
    //private Double doubleBlindReviewPercentage;

    private BooleanDouble singleBlindReview;

    /** Single-blind review ({@code SURVEY_CREATE.LABEL_REVIEW_PROCEDURE_SINGLE_BLIND_REVIEW}). */
    //private Boolean singleBlindReview = false;

    /** Udział procentowy single-blind (pole aktywne gdy {@code singleBlindReview == true}). */
    //private Double singleBlindReviewPercentage;

    private BooleanDouble openPeerReview;

    /** Open peer review ({@code SURVEY_CREATE.LABEL_REVIEW_PROCEDURE_OPEN_PEER_REVIEW}). */
    //private Boolean openPeerReview = false;

    /** Udział procentowy open peer review (pole aktywne gdy {@code openPeerReview == true}). */
    //private Double openPeerReviewPercentage;

    /** Żadne z powyższych ({@code SURVEY_CREATE.LABEL_REVIEW_PROCEDURE_NONE}). */
    private Boolean none = false;
}
