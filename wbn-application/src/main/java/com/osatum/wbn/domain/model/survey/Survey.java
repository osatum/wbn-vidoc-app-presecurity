package com.osatum.wbn.domain.model.survey;


import lombok.Data;

import java.io.Serializable;

/**
 * Główny model danych ankiety (JSON DTO) zorganizowany w sekcje zgodne z formularzem HTML.
 * <p>
 * Sekcje odpowiadają nagłówkom z tłumaczeń:
 * <ul>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_1} – Czasopismo</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_2} – Dane uzupełniające</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_3} – Wersje wydawnicze</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_4} – Stabilność</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_5} – Standardy</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_6} – Digitalizacja</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_7} – Kooperacja</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_8} – Redakcja</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_9} – Wydawca</li>
 *   <li>{@code SURVEY_CREATE.HEADING_SECTION_10} – Opis</li>
 * </ul>
 */
@Data
public class Survey implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Rok oceny / rok ankiety.
     * <p>Źródło: pole {@code evaluationYear} w {@code SurveyData}.</p>
     */
    private Integer evaluationYear;

    /** Sekcja 1: Czasopismo ({@code SURVEY_CREATE.HEADING_SECTION_1}). */
    private Journal journal = new Journal();

    /** Sekcja 2: Dane uzupełniające ({@code SURVEY_CREATE.HEADING_SECTION_2}). */
    private SupplementaryData supplementaryData = new SupplementaryData();

    /** Sekcja 3: Wersje wydawnicze ({@code SURVEY_CREATE.HEADING_SECTION_3}). */
    private PublishingVersions publishingVersions = new PublishingVersions();

    /** Sekcja 4: Stabilność ({@code SURVEY_CREATE.HEADING_SECTION_4}). */
    private Stability stability = new Stability();

    /** Sekcja 5: Standardy ({@code SURVEY_CREATE.HEADING_SECTION_5}). */
    private Standards standards = new Standards();

    /** Sekcja 6: Digitalizacja ({@code SURVEY_CREATE.HEADING_SECTION_6}). */
    private Digitalization digitalization = new Digitalization();

    /** Sekcja 7: Kooperacja ({@code SURVEY_CREATE.HEADING_SECTION_7}). */
    private Cooperation cooperation = new Cooperation();

    /** Sekcja 8: Redakcja ({@code SURVEY_CREATE.HEADING_SECTION_8}). */
    private Editorial editorial = new Editorial();

    /** Sekcja 9: Wydawca ({@code SURVEY_CREATE.HEADING_SECTION_9}). */
    private Publisher publisher = new Publisher();

    /** Sekcja 10: Opis ({@code SURVEY_CREATE.HEADING_SECTION_10}). */
    private Description description = new Description();

    /**
     * Znacznik czasu/wersji przesłania danych.
     * <p>Źródło: {@code SurveyData.timestamp}.</p>
     */
    private String timestamp;
}
