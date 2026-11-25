package com.osatum.wbn.application.questionnaire.dto.model;

import com.osatum.wbn.application.journal.view.CountryView;
import lombok.Data;

import java.io.Serializable;

/**
 * Osoba współpracująca (recenzent/dyrektor) – {@code survey.reviewersList} / {@code survey.directorsList}.
  * <p><b>translate:</b> SURVEY_CREATE.ADD, SURVEY_CREATE.AFFILIATION, SURVEY_CREATE.COUNTRY, SURVEY_CREATE.EDIT, SURVEY_CREATE.EMAIL</p>
 */
@Data
public class Cooperator implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Imię i nazwisko ({@code CooperatorSurvey.fullName}). */
    private String fullName;

    /** Afiliacja ({@code CooperatorSurvey.affiliation}). */
    private String affiliation;

    /** Kraj ({@code CooperatorSurvey.country}). */
    private CountryView country;

    /** E-mail ({@code CooperatorSurvey.email}). */
    private String email;
}
