package com.osatum.wbn.domain.model.survey.model;

import com.osatum.wbn.domain.model.journal.IssnType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Wiersz ISSN z tabeli sekcji 1.
 * <p>
 * Odpowiada formularzowi "{@code SURVEY_CREATE.ISSN_MODAL_HEADER}" oraz kolumnom:
 * {@code SURVEY_CREATE.T_ISSNS_ISSN}, {@code SURVEY_CREATE.T_ISSNS_ACTUAL},
 * {@code SURVEY_CREATE.T_ISSNS_YEARFROM}, {@code SURVEY_CREATE.T_ISSNS_YEARTO}, {@code SURVEY_CREATE.T_ISSNS_TYPE}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Issn implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Numer ISSN ({@code SURVEY_CREATE.ISSN_MODAL_ISSN}). */
    private String issn;

    /** Czy ISSN jest aktualny ({@code SURVEY_CREATE.ISSN_MODAL_ACTIVE}). */
    private Boolean active;

    /** Rok nadania ({@code SURVEY_CREATE.ISSN_MODAL_YEARFROM}). */
    private Integer yearFrom;

    /** Ostatni znany rok obowiązywania ({@code SURVEY_CREATE.ISSN_MODAL_YEARTO}). */
    private Integer yearTo;

    /**
     * Typ ISSN: np. "E"/"P" ({@code SURVEY_CREATE.ISSN_MODAL_TYPE}, wartości opisane m.in. przez {@code SURVEY_CREATE.ISSN_TYPE.E}, {@code SURVEY_CREATE.ISSN_TYPE.P}).
     */
    private IssnType type;
}
