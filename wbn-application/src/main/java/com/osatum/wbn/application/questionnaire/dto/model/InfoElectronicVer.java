package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Informacje znajdujące się na stronie/pliku wersji elektronicznej ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER}).
 */
@Data
public class InfoElectronicVer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ISSN / e-ISSN ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_EISSN}). */
    private Boolean eissn = false;

    /** Rok ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_YEAR}). */
    private Boolean year = false;

    /** Tom / numer / zeszyt / miesiąc ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_TOME_NUMBER_MONTH}). */
    private Boolean volumeNumberIssueMonth = false;

    /** Żadne z powyższych ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_NONE}). */
    private Boolean none = false;
}
