package com.osatum.wbn.application.questionnaire.dto.model;

import lombok.Data;

import java.io.Serializable;

/**
 * Szczegół opłaty – pojedynczy wiersz / próg / wariant.
 */
@Data
public class FeeDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Minimalna wartość (jeśli występuje). */
    private Integer minValue;

    /** Maksymalna wartość (jeśli występuje). */
    private Integer maxValue;

    /** Kod waluty. */
    private String currencyCode;

    /** Opis wariantu (np. "Polska", "Zagranica" albo opis progu). */
    private String description;
}
