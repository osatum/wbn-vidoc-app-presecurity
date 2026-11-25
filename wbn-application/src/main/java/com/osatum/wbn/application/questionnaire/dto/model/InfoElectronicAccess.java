package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Rodzaj wersji elektronicznej ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_ACCESS}).
 * <p>
 * Checkboxy w formularzu: pliki na www, artykuły na www, nośniki CD/DVD albo "żadne".
 */
@Data
public class InfoElectronicAccess implements Serializable {

    private static final long serialVersionUID = 1L;

    /** pliki zamieszczone na stronie internetowej ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_ACCESS_FILES}). */
    private Boolean files = false;

    /** publikacje zamieszczone bezpośrednio na stronie internetowej ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_ACCESS_ARTICLES_ON_WWW}). */
    private Boolean articlesOnWww = false;

    /** publikacje na nośnikach danych CD/DVD ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_ACCESS_CDS}). */
    private Boolean cds = false;

    /** żadne z powyższych ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_ACCESS_NONE}). */
    private Boolean none = false;
}
