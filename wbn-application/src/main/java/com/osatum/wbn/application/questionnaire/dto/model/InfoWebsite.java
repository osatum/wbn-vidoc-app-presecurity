package com.osatum.wbn.application.questionnaire.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * Informacje obecne na stronie WWW czasopisma (sekcja 6 – {@code SURVEY_CREATE.JOURNAL_WWW_HEADER}).
 */
@Data
public class InfoWebsite implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Informacje o tytułach ({@code survey.infoWebsite.titles}). */
    private Boolean titles = false;

    /** Informacje o tytułach na stronie głównej ({@code survey.infoWebsite.titlesMainSite}). */
    private Boolean titlesMainSite = false;

    /** Informacje o ISSN ({@code survey.infoWebsite.issn}). */
    private Boolean issn = false;

    /** Informacje o ISSN na stronie głównej ({@code survey.infoWebsite.issnMainSite}). */
    private Boolean issnMainSite = false;

    /** Informacje o dyrektorach/organach ({@code survey.infoWebsite.directors}). */
    private Boolean directors = false;

    /** Informacje o redakcji ({@code survey.infoWebsite.editOfficeInfo}). */
    private Boolean editOfficeInfo = false;

    @Schema(nullable = true)
    private String directorsWww;
    @Schema(nullable = true)
    private String editOfficeInfoWww;
    @Schema(nullable = true)
    private Boolean antiAbusePolicyInfo;
    @Schema(nullable = true)
    private String antiAbusePolicyInfoWww;

    /** Żadne z powyższych ({@code survey.infoWebsite.none}). */
    private Boolean none = false;
}
