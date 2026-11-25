package com.osatum.wbn.domain.model.survey.model;

import java.io.Serializable;

/**
 * Metadane dołączone do artykułów ({@code SURVEY_CREATE.LABEL_INFO_METADATA}).
 * <p>W formularzu występuje oddzielnie dla wersji elektronicznej i drukowanej.</p>
 *
 * Odpowiada checkboxom:
 * SURVEY_CREATE.LABEL_INFO_METADATA_NATIONAL_KEYWORDS
 * SURVEY_CREATE.LABEL_INFO_METADATA_ENGLISH_KEYWORDS
 * SURVEY_CREATE.LABEL_INFO_METADATA_AUTHOR_AFFILIATION
 * SURVEY_CREATE.LABEL_INFO_METADATA_SEND_DATE
 * SURVEY_CREATE.LABEL_INFO_METADATA_ACCEPTED_DATE
 * SURVEY_CREATE.LABEL_INFO_METADATA_AUTHOR_STATEMENT
 * SURVEY_CREATE.LABEL_INFO_METADATA_DOI
 * SURVEY_CREATE.LABEL_INFO_METADATA_GICID (jeśli masz taki klucz; w modelu było gicid)
 * SURVEY_CREATE.LABEL_INFO_METADATA_NONE
 */
public enum ArticleMetadataItem implements Serializable {

    /** Słowa kluczowe w języku oryginalnym */
    NATIONAL_KEYWORDS,

    /** Słowa kluczowe w języku angielskim */
    ENGLISH_KEYWORDS,

    /** Afiliacja autora */
    AUTHOR_AFFILIATION,

    /** Data przesłania artykułu do redakcji */
    SEND_DATE,

    /** Data akceptacji artykułu przez redakcję */
    ACCEPTED_DATE,

    /** Oświadczenie autora o źródłach finansowania */
    AUTHOR_STATEMENT,

    /** DOI artykułu */
    DOI,

    /** Identyfikator GICID (jeśli stosowany w systemie) */
    GICID,

    /** Identyfikator ORCID (jeśli stosowany w systemie) */
    ORCID,

    /** Informacja o dacie publikacji artykułu. */
    PUBLICATION_DATE,

    /** Informacja o konflikcie interesów. */
    CONFLICT_OF_INTEREST,

    /** Zasady udostępniania pracy. */
    SHARING_POLICY,
    /** Żadne z powyższych (wyklucza pozostałe) */
    NONE
}
