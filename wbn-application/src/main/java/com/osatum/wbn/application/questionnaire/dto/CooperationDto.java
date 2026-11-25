package com.osatum.wbn.application.questionnaire.dto;


import com.osatum.wbn.application.questionnaire.dto.model.Cooperator;
import com.osatum.wbn.application.questionnaire.dto.model.ScientistMetric;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sekcja 7: Kooperacja ({@code SURVEY_CREATE.HEADING_SECTION_7}).
 * <p>
 * Obejmuje umiędzynarodowienie treści oraz współpracę (autorzy/recenzenci/dyrektorzy) –
 * wraz z udziałami procentowymi.
 */
@Data
/**
 * CooperationSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> DROPZONE.DROP_FILES_HERE_TO_UPLOAD; SURVEY_CREATE.ADD; SURVEY_CREATE.AFFILIATION; SURVEY_CREATE.AUTHORS_REWIEWER_SCIENTIFIC_COUNCIL_INFO; SURVEY_CREATE.COUNTRY; SURVEY_CREATE.DELETE; SURVEY_CREATE.EDIT; SURVEY_CREATE.EMAIL; SURVEY_CREATE.FIRST_AND_LAST_NAME; SURVEY_CREATE.LABEL_ACTIONS; SURVEY_CREATE.LABEL_AUTHORS; SURVEY_CREATE.LABEL_COMMITTEE; SURVEY_CREATE.LABEL_COUNTRY; SURVEY_CREATE.LABEL_EIC_AFFILIATION; SURVEY_CREATE.LABEL_EIC_AFFILIATION_DESCRIPTION; SURVEY_CREATE.LABEL_REVIEWERS; SURVEY_CREATE.LABEL_ROLE; SURVEY_CREATE.LABEL_SCIENTIFIC_UNIT; SURVEY_CREATE.NO_PERCENTAGE_EXTERNAL; SURVEY_CREATE.NO_PERCENTAGE_FOREIGN; SURVEY_CREATE.NO_SCIENTIST_UNIQUE; SURVEY_CREATE.REVIEWERS_DIRECTORS_INFO; SURVEY_CREATE.REVIEWERS_EXTERNAL_LIST_LABEL_TOOLTIP; SURVEY_CREATE.REVIEWERS_LIST_LABEL; SURVEY_CREATE.REVIEWER_AUTHORS_QUESTIONS_LABEL_TOOLTIP</p>
 */
public class CooperationDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Czy tytuł i abstrakt we wszystkich artykułach (historyczne pole; {@code survey.titleAndAbstractInAllArticles}).
     */
    private Boolean titleAndAbstractInAllArticles;

    private ScientistMetric authorMetric;
    // recenzenci
    private ScientistMetric reviewerMetric;
    // czlonkoie rady naukowej
    private ScientistMetric directorsMetric;


    /**
     * Lista recenzentów (od wersji 1.3) – {@code survey.reviewersList}.  * <p><b>translate:</b> SURVEY_CREATE.ADD, SURVEY_CREATE.AFFILIATION, SURVEY_CREATE.COUNTRY, SURVEY_CREATE.EDIT, SURVEY_CREATE.EMAIL</p>
     */
    private List<Cooperator> reviewersList = new ArrayList<>();

    /**
     * Lista dyrektorów (od wersji 1.3) – {@code survey.directorsList}.
     */
    private List<Cooperator> directorsList = new ArrayList<>();

    /**
     * Pole tekstowe "reviewers" (historyczne) – {@code survey.reviewers}.  * <p><b>translate:</b> DROPZONE.DROP_FILES_HERE_TO_UPLOAD, SURVEY_CREATE.REVIEWERS_EXTERNAL_LIST_LABEL_TOOLTIP</p>
     */
    private String reviewers;
}
