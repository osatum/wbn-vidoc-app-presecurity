package com.osatum.wbn.domain.model.survey.model;

import java.io.Serializable;

/**
 * Informacje znajdujące się na stronie / w pliku wersji elektronicznej
 * ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER}).
 *
 * Odpowiada checkboxom:
 * SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_EISSN
 * SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_YEAR
 * SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_TOME_NUMBER_MONTH
 * SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_NONE
 */
public enum ElectronicVersionInformation implements Serializable {

    /** ISSN / e-ISSN */
    EISSN,

    /** Rok */
    YEAR,

    /** Tom / numer / zeszyt / miesiąc */
    VOLUME_NUMBER_ISSUE_MONTH,

    /** Żadne z powyższych (wyklucza inne) */
    NONE
}
