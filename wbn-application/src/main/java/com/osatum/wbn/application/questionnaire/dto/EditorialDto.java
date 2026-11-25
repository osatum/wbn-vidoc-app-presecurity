package com.osatum.wbn.application.questionnaire.dto;


import com.osatum.wbn.application.questionnaire.dto.model.JournalInstitution;
import com.osatum.wbn.application.questionnaire.dto.model.Person;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sekcja 8: Redakcja ({@code SURVEY_CREATE.HEADING_SECTION_8}).
 * <p>
 * W formularzu prezentowana jako tabela osób redakcji ({@code SURVEY_CREATE.T_EDITOR_HEADER}).
 */
@Data
/**
 * EditorialSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.ADD; SURVEY_CREATE.DELETE; SURVEY_CREATE.EDIT; SURVEY_CREATE.T_EDITOR_DEPARTMENT; SURVEY_CREATE.T_EDITOR_EMAIL; SURVEY_CREATE.T_EDITOR_FUNCTION; SURVEY_CREATE.T_EDITOR_HEADER; SURVEY_CREATE.T_EDITOR_NAME; SURVEY_CREATE.T_EDITOR_TITLE; SURVEY_CREATE.T_REDACTION_COUNTRY; SURVEY_CREATE.T_REDACTION_DEPARTMENT; SURVEY_CREATE.T_REDACTION_EMAIL; SURVEY_CREATE.T_REDACTION_HEADER; SURVEY_CREATE.T_REDACTION_NAME; SURVEY_CREATE.T_REDACTION_TELEPHONE</p>
 */
public class EditorialDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Lista redaktorów ({@code msg.editors}).
     * <p>Źródło: {@code SurveyData.editors}.</p>
     */
    private List<Person> editors = new ArrayList<>();

    /**
     * Instytucje redakcji (rola 'E' w {@code SurveyData.institutions}) – tabela "Redakcja" w sekcji 8.
     */
    private List<JournalInstitution> redactionInstitutions = new ArrayList<>();
}
