package com.osatum.wbn.domain.model.survey.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Wiersz tytułu z tabeli sekcji 1.
 * <p>
 * Odpowiada formularzowi "{@code SURVEY_CREATE.TITLE_MODAL_HEADER}" oraz kolumnom:
 * {@code SURVEY_CREATE.T_TITLES_TITLE}, {@code SURVEY_CREATE.T_TITLES_ACTUAL},
 * {@code SURVEY_CREATE.T_TITLES_YEARFROM}, {@code SURVEY_CREATE.T_TITLES_YEARTO}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JournalTitle implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Czy tytuł jest aktualny ({@code SURVEY_CREATE.TITLE_MODAL_ACTIVE}). */
    private Boolean active;

    /** Treść tytułu ({@code SURVEY_CREATE.TITLE_MODAL_TITLE}). */
    private String title;

    /** Rok nadania ({@code SURVEY_CREATE.TITLE_MODAL_YEARFROM}). */
    private Integer yearFrom;

    /** Ostatni znany rok obowiązywania ({@code SURVEY_CREATE.TITLE_MODAL_YEARTO}). */
    private Integer yearTo;

    /**
     * Typ tytułu (np. oryginalny, angielski, skrócony, do cytowań).
     * <p>W formularzu używany przy dodawaniu tytułu: {@code SURVEY_CREATE.T_TITLES_ORIGINAL}, {@code SURVEY_CREATE.T_TITLES_ENGLISH}, {@code SURVEY_CREATE.T_TITLES_SHORT}, {@code SURVEY_CREATE.T_TITLES_CITATION}.</p>
     */
    private TitleType titleType;
}
