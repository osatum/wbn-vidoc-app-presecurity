package com.osatum.wbn.application.questionnaire.dto;


import lombok.Data;

import java.io.Serializable;

/**
 * Sekcja 10: Opis ({@code SURVEY_CREATE.HEADING_SECTION_10}).
 */
@Data
/**
 * DescriptionSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> DROPZONE.DROP_FILES_HERE_TO_UPLOAD; SURVEY_CREATE.COVER_MAX_SIZE; SURVEY_CREATE.DESCRIPTION_INFO_1; SURVEY_CREATE.LABEL_COVER; SURVEY_CREATE.LABEL_DESCRIPTION_EN; SURVEY_CREATE.LABEL_DESCRIPTION_PL; SURVEY_CREATE.LABEL_LOGO; SURVEY_CREATE.LOGO_MAX_SIZE</p>
 */
public class DescriptionDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Opis w języku polskim ({@code SURVEY_CREATE.DESCRIPTION_PL}). */
    private String descriptionPL;

    /** Opis w języku angielskim ({@code SURVEY_CREATE.DESCRIPTION_EN}). */
    private String descriptionEN;
}
