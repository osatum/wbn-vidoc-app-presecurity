package com.osatum.wbn.application.questionnaire.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SurveyDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Rok oceny / rok ankiety.
     * <p>Źródło: pole {@code evaluationYear} w {@code SurveyData}.</p>
     */
    private Integer evaluationYear;

    /** Sekcja 1: Czasopismo ({@code SURVEY_CREATE.HEADING_SECTION_1}). */
    private JournalDto journal = new JournalDto();

    /** Sekcja 2: Dane uzupełniające ({@code SURVEY_CREATE.HEADING_SECTION_2}). */
    private SupplementaryDataDto supplementaryData = new SupplementaryDataDto();

    /** Sekcja 3: Wersje wydawnicze ({@code SURVEY_CREATE.HEADING_SECTION_3}). */
    private PublishingVersionsDto publishingVersions = new PublishingVersionsDto();

    /** Sekcja 4: Stabilność ({@code SURVEY_CREATE.HEADING_SECTION_4}). */
    private StabilityDto stability = new StabilityDto();

    /** Sekcja 5: Standardy ({@code SURVEY_CREATE.HEADING_SECTION_5}). */
    private StandardsDto standards = new StandardsDto();

    /** Sekcja 6: Digitalizacja ({@code SURVEY_CREATE.HEADING_SECTION_6}). */
    private DigitalizationDto digitalization = new DigitalizationDto();

    /** Sekcja 7: Kooperacja ({@code SURVEY_CREATE.HEADING_SECTION_7}). */
    private CooperationDto cooperation = new CooperationDto();

    /** Sekcja 8: Redakcja ({@code SURVEY_CREATE.HEADING_SECTION_8}). */
    private EditorialDto editorial = new EditorialDto();

    /** Sekcja 9: Wydawca ({@code SURVEY_CREATE.HEADING_SECTION_9}). */
    private PublisherDto publisher = new PublisherDto();

    /** Sekcja 10: Opis ({@code SURVEY_CREATE.HEADING_SECTION_10}). */
    private DescriptionDto description = new DescriptionDto();

    /**
     * Znacznik czasu/wersji przesłania danych.
     * <p>Źródło: {@code SurveyData.timestamp}.</p>
     */
    private String timestamp;
}
