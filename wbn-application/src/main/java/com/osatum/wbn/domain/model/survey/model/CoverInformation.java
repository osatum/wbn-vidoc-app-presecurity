package com.osatum.wbn.domain.model.survey.model;

import java.io.Serializable;

/**
 * Informacje znajdujące się na okładce / wydaniu drukowanym.
 *
 * Odpowiada checkboxom:
 * SURVEY_CREATE.LABEL_INFO_COVER_ISSN
 * SURVEY_CREATE.LABEL_INFO_COVER_YEAR
 * SURVEY_CREATE.LABEL_INFO_COVER_TOME
 * SURVEY_CREATE.LABEL_INFO_COVER_NUMBER
 * SURVEY_CREATE.LABEL_INFO_COVER_MONTH
 * SURVEY_CREATE.LABEL_INFO_COVER_NONE
 */
public enum CoverInformation implements Serializable {

    /** ISSN */
    ISSN,

    /** Rok */
    YEAR,

    /** Tom */
    TOME,

    /** Numer / zeszyt */
    NUMBER,

    /** Miesiąc */
    MONTH,

    /** Żadne z powyższych (wyklucza inne) */
    NONE
}