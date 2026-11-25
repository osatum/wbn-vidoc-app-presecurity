package com.osatum.wbn.domain.model.survey.model;


import java.io.Serializable;

/**
 * Polityka wydawnicza czasopisma
 * ({@code SURVEY_CREATE.INFO_PUBLISHING_POLICY_HEADER}).
 *
 * Warianty są wzajemnie wykluczające się.
 */
public enum PublishingPolicyType implements Serializable {

    /** Wersja elektroniczna jest rozszerzona */
    EXTENDED_ELECTRONIC_VERSIONS,

    /** Te same artykuły w obu wersjach */
    SAME_ARTICLES,

    /** Te same artykuły i inne materiały */
    SAME_ARTICLES_AND_OTHERS,

    /** Wersja drukowana zawiera więcej niż elektroniczna */
    PRINTED_MORE_THAN_ELECTRONIC,

    /** Wersja elektroniczna zawiera więcej niż drukowana */
    ELECTRONIC_MORE_THAN_PRINTED
}