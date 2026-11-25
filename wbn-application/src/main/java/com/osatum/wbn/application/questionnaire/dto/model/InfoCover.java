package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Informacje znajdujące się na okładce / wydaniu drukowanym.
 * <p>Checkboxy w formularzu (sekcja 3) – m.in. ISSN, rok, tom, numer, miesiąc lub "żadne".</p>
 */
@Data
public class InfoCover implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ISSN ({@code SURVEY_CREATE.LABEL_INFO_COVER_ISSN}). */
    private Boolean issn = false;

    /** Rok ({@code SURVEY_CREATE.LABEL_INFO_COVER_YEAR}). */
    private Boolean year = false;

    /** Tom ({@code SURVEY_CREATE.LABEL_INFO_COVER_TOME}). */
    private Boolean tome = false;

    /** Numer / zeszyt ({@code SURVEY_CREATE.LABEL_INFO_COVER_NUMBER}). */
    private Boolean number = false;

    /** Miesiąc ({@code SURVEY_CREATE.LABEL_INFO_COVER_MONTH}). */
    private Boolean month = false;

    /** Żadne z powyższych ({@code SURVEY_CREATE.LABEL_INFO_COVER_NONE}). */
    private Boolean none = false;
}
