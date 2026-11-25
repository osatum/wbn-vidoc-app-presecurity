package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Koszt zakupu jednego artykułu ({@code SURVEY_CREATE.ARTICLE_COST_LABEL}).
 */
@Data
public class ArticleCost implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Wysokość opłaty ({@code SURVEY_CREATE.ARTICLE_COST}). */
    private Integer value;

    /** Kod waluty (np. PLN/EUR). */
    private String currencyCode;
}
