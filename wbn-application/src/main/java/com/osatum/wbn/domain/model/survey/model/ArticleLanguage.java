package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Język artykułów w czasopiśmie ({@code survey.languages}).
 * <p>
 * W modelu źródłowym język przechowywany jest jako kod oraz liczności w dwóch latach (year1/year2).
 * DTO zachowuje tę strukturę na potrzeby migracji JSON.
 * </p>
  * <p><b>translate:</b> SURVEY_CREATE.ADD, SURVEY_CREATE.DELETE, SURVEY_CREATE.EDIT, SURVEY_CREATE.T_ARTICLE_AMOUNT1, SURVEY_CREATE.T_ARTICLE_AMOUNT2, SURVEY_CREATE.T_ARTICLE_LANGUAGE_CODE</p>
 */
@Data
public class ArticleLanguage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Kod języka (np. en, pl) – {@code ArticleLanguageSurvey.languageCode}. */
    private Language language;

    /** Rok 1 (np. rok oceny-1) – {@code ArticleLanguageSurvey.year1}. */
    private Integer previousYear;

    /** Liczba artykułów w roku 1 – {@code ArticleLanguageSurvey.amount1}. */
    private Integer previousValue;

    /** Rok 2 – {@code ArticleLanguageSurvey.year2}. */
    private Integer currentYear;

    /** Liczba artykułów w roku 2 – {@code ArticleLanguageSurvey.amount2}. */
    private Integer currentValue;
}
