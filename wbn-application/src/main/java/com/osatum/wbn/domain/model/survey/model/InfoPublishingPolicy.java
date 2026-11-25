package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Polityka wydawnicza czasopisma ({@code SURVEY_CREATE.INFO_PUBLISHING_POLICY_HEADER}).
 */
@Data
public class InfoPublishingPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Wersja elektroniczna jest rozszerzona ({@code SURVEY_CREATE.LABEL_INFO_PUBLISHING_POLICY_EXTENDED_ELECTRONIC_VERSIONS}). */
    private Boolean extendedElectronicVersions = false;

    /** Te same artykuły w obu wersjach ({@code SURVEY_CREATE.LABEL_INFO_PUBLISHING_POLICY_SAME_ARTICLES}). */
    private Boolean sameArticles = false;

    /** Te same artykuły i inne materiały ({@code SURVEY_CREATE.LABEL_INFO_PUBLISHING_POLICY_SAME_ARTICLES_AND_OTHERS}). */
    private Boolean sameArticlesAndOthers = false;

    /** Wersja drukowana zawiera więcej niż elektroniczna ({@code SURVEY_CREATE.LABEL_INFO_PUBLISHING_POLICY_PRINTED_MORE_THAN_ELECTRONIC}). */
    private Boolean printedMoreThanElectronic = false;

    /** Wersja elektroniczna zawiera więcej niż drukowana ({@code SURVEY_CREATE.LABEL_INFO_PUBLISHING_POLICY_ELECTRONIC_MORE_THAN_PRINTED}). */
    private Boolean electronicMoreThanPrinted = false;
}
