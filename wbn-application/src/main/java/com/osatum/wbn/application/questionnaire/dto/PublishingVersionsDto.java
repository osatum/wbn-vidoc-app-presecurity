package com.osatum.wbn.application.questionnaire.dto;


import com.osatum.wbn.domain.model.survey.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Sekcja 3: Wersje wydawnicze ({@code SURVEY_CREATE.HEADING_SECTION_3}).
 * <p>
 * Logika formularza (HTML):
 * <ul>
 *   <li>bloki opisowe/ostrzeżenia są wyświetlane zależnie od {@code printedVersion} i {@code electronicVersion},</li>
 *   <li>pola adresu www/login/hasło są aktywne, gdy {@code electronicVersion == true}.</li>
 * </ul>
 */
@Data
/**
 * PublishingVersionsSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.CHOOSE; SURVEY_CREATE.ELECTRONIC_VERSION_HEADER; SURVEY_CREATE.ELECTRONIC_VERSION_WWW_LINK; SURVEY_CREATE.ELECTRONIC_VERSION_WWW_LOGIN; SURVEY_CREATE.ELECTRONIC_VERSION_WWW_PASSWORD1; SURVEY_CREATE.ELECTRONIC_VERSION_WWW_PASSWORD2; SURVEY_CREATE.INFO_FUNCTIONALITY_USE_1; SURVEY_CREATE.INFO_FUNCTIONALITY_USE_2; SURVEY_CREATE.INFO_PUBLISHING_POLICY_HEADER; SURVEY_CREATE.LABEL_AOP; SURVEY_CREATE.LABEL_AOP_TOOLTIP; SURVEY_CREATE.LABEL_CIRCULATION; SURVEY_CREATE.LABEL_CIRCULATION_TOOLTIP; SURVEY_CREATE.LABEL_ELECTRONIC_VERSION; SURVEY_CREATE.LABEL_ELECTRONIC_VERSIONS_PERCENTAGE; SURVEY_CREATE.LABEL_ELECTRONIC_VERSION_WWW; SURVEY_CREATE.LABEL_INFO_COVER; SURVEY_CREATE.LABEL_INFO_COVER_ISSN; SURVEY_CREATE.LABEL_INFO_COVER_MONTH; SURVEY_CREATE.LABEL_INFO_COVER_NONE; SURVEY_CREATE.LABEL_INFO_COVER_NUMBER; SURVEY_CREATE.LABEL_INFO_COVER_TOME; SURVEY_CREATE.LABEL_INFO_COVER_YEAR; SURVEY_CREATE.LABEL_INFO_ELECTRONIC_ACCESS; SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER; SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_EISSN; SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_NONE; SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_TOME_NUMBER_MONTH; SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER_YEAR; SURVEY_CREATE.LABEL_INFO_METADATA … (+25 więcej)</p>
 */
public class PublishingVersionsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Wersja drukowana ({@code SURVEY_CREATE.LABEL_PRINTED_VERSION}). */
    private Boolean printedVersion;

    /** Wersja elektroniczna ({@code SURVEY_CREATE.LABEL_ELECTRONIC_VERSION}). */
    private Boolean electronicVersion;

    /**
     * Adres www, pod którym dostępne są wszystkie wydania elektroniczne z roku oceny ({@code SURVEY_CREATE.LABEL_ELECTRONIC_VERSION_WWW}).
     * <p>W formularzu pole widoczne gdy {@code electronicVersion == true}.</p>
     */
    private String electronicVersionWww;

    private Double electronicVersionsPercentage;

    @Schema(nullable = true)
    private Integer publishedArticlesCount;
    @Schema(nullable = true)
    private Integer electronicFullTextArticlesCount;
    @Schema(nullable = true)
    private Integer aopArticlesCount;

    /** Login do dostępu (tooltip: {@code SURVEY_CREATE.LABEL_ELECTRONIC_VERSION_WWW_TOOLTIP}). */
    private String login;

    /** Hasło do dostępu (tooltip: {@code SURVEY_CREATE.LABEL_ELECTRONIC_VERSION_WWW_TOOLTIP}). */
    private String password;

    /** Nakład / liczba egzemplarzy ({@code SURVEY_CREATE.LABEL_CIRCULATION}). */
    private Integer circulation;

    /**
     * Format udostępniania artykułów/plików (np. PDF, HTML) – multi-select.
     * <p>Źródło: {@code SurveyData.articlesFormats}.</p>
     */
    private List<ArticleFormat> articlesFormats = new ArrayList<>();

    /** Informacje na okładce/numerze wydania ({@code SURVEY_CREATE.LABEL_INFO_COVER}). */
    //private InfoCover infoCover = new InfoCover();
    /**
     * Informacje na okładce / wydaniu drukowanym (sekcja 3).
     */
    private Set<CoverInformation> coverInformation =
            EnumSet.noneOf(CoverInformation.class);

    /** Informacje na stronie/pliku wersji elektronicznej ({@code SURVEY_CREATE.LABEL_INFO_ELECTRONIC_VER}). */
    //private InfoElectronicVer infoElectronicVer = new InfoElectronicVer();
    /**
     * Informacje prezentowane w wersji elektronicznej (sekcja 3).
     */
    private Set<ElectronicVersionInformation> electronicVersionInformation =
            EnumSet.noneOf(ElectronicVersionInformation.class);

    /** Metadane dołączone do artykułów w wersji elektronicznej ({@code SURVEY_CREATE.LABEL_INFO_METADATA}). */
    //private InfoArticleMetadata infoElectronicMetadata = new InfoArticleMetadata();

    /** Metadane dołączone do artykułów w wersji drukowanej ({@code SURVEY_CREATE.LABEL_INFO_METADATA}). */
    //private InfoArticleMetadata infoPrintedMetadata = new InfoArticleMetadata();
    /** Metadane dołączone do artykułów – wersja elektroniczna. */
    private Set<ArticleMetadataItem> electronicArticleMetadata =
            EnumSet.noneOf(ArticleMetadataItem.class);

    /** Metadane dołączone do artykułów – wersja drukowana. */
    private Set<ArticleMetadataItem> printedArticleMetadata =
            EnumSet.noneOf(ArticleMetadataItem.class);
    /** Polityka wydawnicza czasopisma ({@code SURVEY_CREATE.INFO_PUBLISHING_POLICY_HEADER}). */
    //private InfoPublishingPolicy infoPublishingPolicy = new InfoPublishingPolicy();

    /**
     * Polityka wydawnicza czasopisma.
     * Wariant pojedynczego wyboru.
     */
    private PublishingPolicyType publishingPolicy;
    /**
     * Czy stosowane jest publikowanie "ahead of print" ({@code SURVEY_CREATE.LABEL_AOP}).
     */
    private Boolean aop;
}
