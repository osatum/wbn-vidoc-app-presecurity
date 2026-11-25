package com.osatum.wbn.domain.model.journal;

public enum PaidArticleType {
    /**
     * Czy wybrane artykuły są bezpłatne ({@code SURVEY_CREATE.FULL_TEXT_OPTION_1}).
     * <p>Źródło: {@code SurveyData.freeArticles}.</p>
     */
    /* Wybrane arykuły są bezpłatne */
    FREE_ARTICLES,
    /**
     * Czy wszystkie artykuły są płatne ({@code SURVEY_CREATE.FULL_TEXT_OPTION_2}).
     * <p>Źródło: {@code SurveyData.allArticlesPaid}.</p>
     */
    /* Wszystkie artykuły są płatne */
    ALL_ARTICLES_PAID,
    /**
     * Czy część artykułów jest płatna ({@code SURVEY_CREATE.FULL_TEXT_OPTION_3}).
     * <p>
     * W HTML: gdy {@code paidArticles == true} wyświetlane są doprecyzowania i koszt ({@code SURVEY_CREATE.ARTICLE_COST_LABEL}).
     * </p>
     */
    /* Część artykułów jest płatna */
    PAID_ARTICLES;
}
