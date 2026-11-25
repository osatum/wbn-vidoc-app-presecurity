package com.osatum.wbn.domain.model.survey.model;

import java.io.Serializable;

/**
 * Instrukcje dla autorów ({@code SURVEY_CREATE.LABEL_INFO_AUTHOR_INSTRUCTION}).
 *
 * Logika formularza:
 * - zaznaczenie NO_INSTRUCTION ukrywa pozostałe opcje,
 * - NONE wyklucza pozostałe checkboxy.
 */
public enum AuthorInstructionType implements Serializable {

    /** Struktura artykułu */
    ARTICLE_STRUCTURE,

    /** Instrukcja cytowań / literatury */
    LITERATURE_INSTRUCTION,

    /** Brak instrukcji (ukrywa pozostałe w formularzu) */
    NO_INSTRUCTION,

    /** Instrukcje określające dozwolony zakres wykorzystania AI. */
    AI_USAGE_INSTRUCTIONS,

    /** Żadne z powyższych */
    NONE
}
