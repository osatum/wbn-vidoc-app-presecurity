package com.osatum.wbn.domain.model.analytics;

public enum JournalAnalyticsType {
    //Impact Factor:
    IF,
    //Scimago Journal Rank (SJR):
    SJR,
    // Total Cites:
    TC,
    //Source Normalized Impact per Paper (SNIP):
    SNIP,
    //CiteScore:
    CS,
    // Article Influence:
    AI,
    // Category Normalized Citation Impact:
    CNCI,
    // Liczba publikacji z podziałem na rok
    //private List<ArticlePublished> articlesPublished;
    /*
     Liczba artykulow
     */
    ART_AMOUNT,
    /*
    Liczba artykulow naukowych
     */
    ART_SCIENTIFIC_AMOUNT,
    // liczba manuskryptów odrzuconych
    ART_MANUSCRIPT_RECEIVED,

    AUTHOR_UNIQUE,
    AUTHOR_EXTERNAL,
    AUTHOR_FOREIGN,

    REVIEWER_UNIQUE,
    REVIEWER_EXTERNAL,
    REVIEWER_FOREIGN,
    REVIEWER_COUNTRY,

    DIRECTOR_UNIQUE,
    DIRECTOR_EXTERNAL,
    DIRECTOR_FOREIGN,

}
