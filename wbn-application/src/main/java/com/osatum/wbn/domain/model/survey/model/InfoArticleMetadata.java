package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Metadane dołączone do artykułów ({@code SURVEY_CREATE.LABEL_INFO_METADATA}).
 * <p>W formularzu występuje oddzielnie dla wersji elektronicznej i drukowanej.</p>
 */
@Data
public class InfoArticleMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Słowa kluczowe w języku oryginalnym ({@code SURVEY_CREATE.LABEL_INFO_METADATA_NATIONAL_KEYWORDS}). */
    private Boolean nationalKeywords = false;

    /** Słowa kluczowe w języku angielskim ({@code SURVEY_CREATE.LABEL_INFO_METADATA_ENGLISH_KEYWORDS}). */
    private Boolean englishKeywords = false;

    /** Afiliacja autora ({@code SURVEY_CREATE.LABEL_INFO_METADATA_AUTHOR_AFFILIATION}). */
    private Boolean authorAffiliation = false;

    /** Data przesłania artykułu do redakcji ({@code SURVEY_CREATE.LABEL_INFO_METADATA_SEND_DATE}). */
    private Boolean sendDate = false;

    /** Data akceptacji artykułu przez redakcję ({@code SURVEY_CREATE.LABEL_INFO_METADATA_ACCEPTED_DATE}). */
    private Boolean acceptedDate = false;

    /** Oświadczenie autora o źródłach finansowania ({@code SURVEY_CREATE.LABEL_INFO_METADATA_AUTHOR_STATEMENT}). */
    private Boolean authorStatement = false;

    /** DOI artykułu ({@code SURVEY_CREATE.LABEL_INFO_METADATA_DOI}). */
    private Boolean doi = false;

    /** Identyfikator GICID (jeśli stosowany w systemie). */
    private Boolean gicid = false;

    /** Żadne z powyższych ({@code SURVEY_CREATE.LABEL_INFO_METADATA_NONE}). */
    private Boolean none = false;
}
