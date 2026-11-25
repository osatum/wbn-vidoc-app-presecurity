package com.osatum.wbn.domain.model.survey;


import com.osatum.wbn.domain.model.journal.JournalKind;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.survey.model.*;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sekcja 2: Dane uzupełniające ({@code SURVEY_CREATE.HEADING_SECTION_2}).
 * <p>
 * Sekcja łączy informacje o charakterze czasopisma, afiliacji, dyscyplinach/profilach
 * oraz dostępie do pełnych treści.
 */
@Data
/**
 * SupplementaryDataSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.ARTICLE_COST; SURVEY_CREATE.CHECK_SUGGESTED_MAIN_SCIENCE_AREA; SURVEY_CREATE.FULL_TEXT_OPTION_1; SURVEY_CREATE.FULL_TEXT_OPTION_2; SURVEY_CREATE.FULL_TEXT_OPTION_3; SURVEY_CREATE.FULL_TEXT_OPTION_EX_1; SURVEY_CREATE.FULL_TEXT_OPTION_EX_2; SURVEY_CREATE.FULL_TEXT_OPTION_EX_3; SURVEY_CREATE.INFO_JOURNAL_CHARACTER; SURVEY_CREATE.INFO_MAIN_TR_CATEGORY; SURVEY_CREATE.LABEL_AFFILIATION; SURVEY_CREATE.LABEL_AFFILIATION_COUNTRY; SURVEY_CREATE.LABEL_AFFILIATION_INFO; SURVEY_CREATE.LABEL_CHOOSE_AFF_COUNTRY; SURVEY_CREATE.LABEL_CHOOSE_CHARACTER; SURVEY_CREATE.LABEL_CHOOSE_COUNTRY; SURVEY_CREATE.LABEL_CHOOSE_CURRENCY; SURVEY_CREATE.LABEL_CHOOSE_EIC_AFF_COUNTRY; SURVEY_CREATE.LABEL_COUNTRY; SURVEY_CREATE.LABEL_CURRENCY; SURVEY_CREATE.LABEL_EIC_AFFILIATION; SURVEY_CREATE.LABEL_EIC_AFFILIATION_COUNTRY; SURVEY_CREATE.LABEL_FULL_TEXT; SURVEY_CREATE.LABEL_JOURNAL_CHARACTER; SURVEY_CREATE.LABEL_MAIN_ASJC; SURVEY_CREATE.LABEL_MAIN_PROFILE; SURVEY_CREATE.LABEL_MAIN_SCIENCE_AREA; SURVEY_CREATE.LABEL_MAIN_TR; SURVEY_CREATE.LABEL_OTHER_ASJCS; SURVEY_CREATE.LABEL_OTHER_PROFILES … (+3 więcej)</p>
 */
public class SupplementaryData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Charakter czasopisma ({@code SURVEY_CREATE.LABEL_JOURNAL_CHARACTER}).
     * <p>
     * W HTML: wybór z listy; dla wartości innych niż naukowa formularz pokazuje ostrzeżenie
     * (ng-if: {@code survey.kindId != 's' && survey.kindId != null}).
     * </p>
     */
    private JournalKind kind;

    /** Kraj pochodzenia czasopisma ({@code SURVEY_CREATE.LABEL_COUNTRY}). */
    private Country country;

    /**
     * Podmiot, przy którym czasopismo jest afiliowane ({@code SURVEY_CREATE.LABEL_AFFILIATION}).
     * <p>
     * Z logiki formularza: należy wypełnić co najmniej jedno z pól afiliacji – czasopisma lub Redaktora Naczelnego
     * ({@code SURVEY_CREATE.LABEL_AFFILIATION_INFO}).
     * </p>
     */
    private String affiliationName;

    /** Kraj afiliacji czasopisma ({@code SURVEY_CREATE.LABEL_AFFILIATION_COUNTRY}). */
    private Country affiliationCountry;

    /** Afiliacja Redaktora Naczelnego ({@code SURVEY_CREATE.LABEL_EIC_AFFILIATION}). */
    private String eicAffiliationName;

    /** Kraj afiliacji Redaktora Naczelnego ({@code SURVEY_CREATE.LABEL_EIC_AFFILIATION_COUNTRY}). */
    private Country eicAffiliationCountry;

    /**
     * Profile/dyscypliny – kategorie naukowe (główne i pozostałe).
     * <p>Źródło: {@code SurveyData.scientificCategories}.</p>
     */
    private List<Category> scientificCategories = new ArrayList<>();

    /**
     * Główny obszar nauk
     */
    private Category mainScienceArea;

    /**
     * Kategorie Web of Science / TR ({@code SURVEY_CREATE.LABEL_MAIN_TR}, {@code SURVEY_CREATE.LABEL_OTHER_TRS}).
     * <p>Źródło: {@code SurveyData.trCategories}.</p>
     */
    private List<Category> trCategories = new ArrayList<>();

    /**
     * Sugerowany inny główny obszar nauk ({@code SURVEY_CREATE.LABEL_SUGGESTED_MAIN_SCIENCE_AREA}).
     * <p>Źródło: {@code SurveyData.suggestedMainScientificCategory}.</p>
     */
    private Long suggestedMainScientificCategory;

    /**
     * Kategorie Scimago / ASJC ({@code SURVEY_CREATE.LABEL_MAIN_ASJC}, {@code SURVEY_CREATE.LABEL_OTHER_ASJCS}).
     * <p>Źródło: {@code SurveyData.asjcCategories}.</p>
     */
    private List<Category> asjcCategories = new ArrayList<>();

    /**
     * Dostęp do pełnych treści ({@code SURVEY_CREATE.LABEL_FULL_TEXT}).
     * <p>
     * W HTML reprezentowane zestawem opcji oraz doprecyzowań.
     * Źródło: {@code SurveyData.infoFullTextAccess}.
     * </p>
     */
    private List<String> infoFullTextAccess = new ArrayList<>();

    private PaidArticleType paidArticle;

    /**
     * Koszt zakupu jednego artykułu naukowego ({@code SURVEY_CREATE.ARTICLE_COST_LABEL}).
     * <p>Źródło: {@code SurveyData.articleCost}.</p>
     */
    private ArticleCost articleCost = new ArticleCost();

    /**
     * Rodzaj wersji elektronicznej ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_ACCESS}).
     * <p>Źródło: {@code SurveyData.infoElectronicAccess}.</p>
     */
    private InfoElectronicAccess infoElectronicAccess = new InfoElectronicAccess();
}
