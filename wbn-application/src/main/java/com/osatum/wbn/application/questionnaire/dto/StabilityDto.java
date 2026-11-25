package com.osatum.wbn.application.questionnaire.dto;


import com.osatum.wbn.application.questionnaire.dto.model.ArticleLanguage;
import com.osatum.wbn.application.questionnaire.dto.model.ArticlePublished;
import com.osatum.wbn.domain.model.survey.model.FirstNumberMonth;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sekcja 4: Stabilność ({@code SURVEY_CREATE.HEADING_SECTION_4}).
 */
@Data
/**
 * StabilitySection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.ADD; SURVEY_CREATE.CHOOSE; SURVEY_CREATE.DELETE; SURVEY_CREATE.EDIT; SURVEY_CREATE.FIRST_NUMBER_LABEL; SURVEY_CREATE.INFO_FUNCTIONALITY_USE_1; SURVEY_CREATE.INFO_FUNCTIONALITY_USE_2; SURVEY_CREATE.LABEL_CONCATENATE_NUMBER; SURVEY_CREATE.LABEL_CONTINUED_YEAR; SURVEY_CREATE.LABEL_FIRST_ISSUE_YEAR; SURVEY_CREATE.LABEL_FIRST_ISSUE_YEAR_STATEMENT; SURVEY_CREATE.LABEL_FIRST_NUMBER_APRIL; SURVEY_CREATE.LABEL_FIRST_NUMBER_AUGUST; SURVEY_CREATE.LABEL_FIRST_NUMBER_DECEMBER; SURVEY_CREATE.LABEL_FIRST_NUMBER_DEC_BEFORE; SURVEY_CREATE.LABEL_FIRST_NUMBER_FEBRUARY; SURVEY_CREATE.LABEL_FIRST_NUMBER_JANUARY; SURVEY_CREATE.LABEL_FIRST_NUMBER_JAN_NEXT; SURVEY_CREATE.LABEL_FIRST_NUMBER_JULY; SURVEY_CREATE.LABEL_FIRST_NUMBER_JUNE; SURVEY_CREATE.LABEL_FIRST_NUMBER_MARCH; SURVEY_CREATE.LABEL_FIRST_NUMBER_MAY; SURVEY_CREATE.LABEL_FIRST_NUMBER_NOVEMBER; SURVEY_CREATE.LABEL_FIRST_NUMBER_OCTOBER; SURVEY_CREATE.LABEL_FIRST_NUMBER_SEPTEMBER; SURVEY_CREATE.LABEL_FREQUENCY_ID; SURVEY_CREATE.LABEL_PUBLISHING_BREAK; SURVEY_CREATE.LABEL_UNIQUE_AUTHORS; SURVEY_CREATE.LABEL_UNIQUE_REVIEWERS; SURVEY_CREATE.NO … (+18 więcej)</p>
 */
public class StabilityDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Rok wydania pierwszego numeru ({@code SURVEY_CREATE.LABEL_FIRST_ISSUE_YEAR}).
     * <p>W HTML wyświetlane jest ostrzeżenie, gdy {@code firstIssueYear >= evaluationYear}.</p>
     */
    private Integer firstIssueYear;

    /**
     * Rok kontynuacji/ciągłości wydawania ({@code msg.continuedYear} – walidacja w modelu źródłowym).
     */
    private Integer continuedYear;

    /**
     * Częstotliwość wydawania (słownik; w starym modelu {@code frequencyId}).
     * <p>W HTML wymagane – jeżeli {@code frequencyId == null} wyświetlane są komunikaty walidacyjne.</p>
     */
    private Long frequencyId;

    /**
     * Miesiąc pierwszego numeru ({@code SurveyData.firstNumberMonth}).
     */
    private FirstNumberMonth firstNumberMonth;

    /**
     * Dane o publikowanych artykułach w latach (tabela roczna) – {@code survey.articlePublishedSurveyList}
     * ({@code SURVEY_CREATE.T_ARTICLE_PUBLISHED_SURVEY_LIST}).
     * <p>
     * Logika z HTML (section4.html):
     * <ul>
     *   <li>Po zmianie {@code continuedYear} wykonywane jest {@code validateArticles()}.</li>
     *   <li>W wersjach &gt;= 1.3 tabela jest prezentowana w układzie kolumnowym (lata jako kolumny) i
     *       może wskazywać niepoprawne lata (np. {@code invalidYears}).</li>
     * </ul>
     * </p>
      * <p><b>translate:</b> SURVEY_CREATE.EDIT, SURVEY_CREATE.T_FREQUENCY, SURVEY_CREATE.T_PUBLISHED_ISSUE_AMOUNT, SURVEY_CREATE.T_REVIEWED_AMOUNT</p>
 */
    private List<ArticlePublished> articlePublished = new ArrayList<>();

    /**
     * Języki artykułów ({@code survey.languages}) – tabela "Języki" w section4.html
     * ({@code SURVEY_CREATE.T_LANGUAGES}).
     * <p>
     *
     * {@code languages} znajduje się w bloku "Umiędzynarodowienie", formularz prezentuje i waliduje je
     * w sekcji 4 (Stabilność). Po zmianie {@code continuedYear} wywoływane jest {@code validateLanguages()}.
     * </p>
     */
    private List<ArticleLanguage> articleLanguages = new ArrayList<>();

    /**
     * Czy wystąpiła przerwa w publikowaniu ({@code SURVEY_CREATE.LABEL_PUBLISHING_BREAK}).
     */
    private Boolean publishingBreak;

    /**
     * Czy łączono numery ({@code SURVEY_CREATE.LABEL_CONCATENATE_NUMBER}).
     */
    private Boolean concatenateNumber;

    private Double englishTitleAndAbstractInAllArticlesPercentage;
    @Schema(nullable = true)
    private Integer englishTitleAndAbstractArticlesCount;
}
