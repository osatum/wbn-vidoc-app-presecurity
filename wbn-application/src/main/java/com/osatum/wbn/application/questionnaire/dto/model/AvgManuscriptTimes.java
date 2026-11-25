package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Średnie czasy obsługi manuskryptu ({@code msg.avgManuscriptTimes}).
 * <p>
 * Pola liczbowe (zwykle dni) w formularzu sekcji 5.
 * </p>
 */
@Data
public class AvgManuscriptTimes implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Wstępna ocena (initial assessment) – {@code survey.avgManuscriptTimes.initialAssessment}.  * <p><b>translate:</b> SURVEY_CREATE.INITIAL_ASSESSMENT_TIME_TOOLTIP, SURVEY_CREATE.MANUSCRIPT_PROCEDURE_TIME_HEADER, SURVEY_CREATE.REVIEW_TIME_TOOLTIP</p>
 */
    private Integer initialAssessment;

    /** Recenzja (review) – {@code survey.avgManuscriptTimes.review}. */
    private Integer review;

    /** Publikacja (publication) – {@code survey.avgManuscriptTimes.publication}. */
    private Integer publication;
}
