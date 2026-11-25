package com.osatum.wbn.domain.model.survey.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Instrukcje dla autorów ({@code SURVEY_CREATE.LABEL_INFO_AUTHOR_INSTRUCTION}).
 * <p>
 * Logika formularza (sekcja 5): gdy {@code noInstruction == true}, pozostałe checkboxy są ukrywane
 * (ng-if: {@code survey.infoAuthorInstruction.noInstruction != true}).
 * </p>
 */
@Data
public class InfoAuthorInstruction implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Struktura artykułu ({@code SURVEY_CREATE.LABEL_INFO_AUTHOR_INSTRUCTION_ARTICLE_STRUCTURE}). */
    private Boolean articleStructure = false;

    /** Instrukcja cytowań/literatury ({@code SURVEY_CREATE.LABEL_INFO_AUTHOR_INSTRUCTION_LITERATURE}). */
    private Boolean literatureInstruction = false;

    /** Brak instrukcji ({@code SURVEY_CREATE.LABEL_INFO_AUTHOR_INSTRUCTION_NO_INSTRUCTION}). */
    private Boolean noInstruction = false;

    /** Żadne z powyższych ({@code SURVEY_CREATE.LABEL_INFO_AUTHOR_INSTRUCTION_NONE}). */
    private Boolean none = false;
}
