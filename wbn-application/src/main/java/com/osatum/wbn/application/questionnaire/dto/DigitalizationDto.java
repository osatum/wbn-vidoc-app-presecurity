package com.osatum.wbn.application.questionnaire.dto;


import com.osatum.wbn.application.questionnaire.dto.model.ExternalRepositories;
import com.osatum.wbn.application.questionnaire.dto.model.InfoWebsite;
import com.osatum.wbn.application.questionnaire.dto.model.PublishingTools;
import com.osatum.wbn.application.questionnaire.dto.model.ScientificQuality;
import com.osatum.wbn.application.questionnaire.dto.model.SocialMedias;
import com.osatum.wbn.domain.model.survey.model.*;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Sekcja 6: Digitalizacja ({@code SURVEY_CREATE.HEADING_SECTION_6}).
 * <p>
 * W tej sekcji formularz obejmuje m.in. stronę www czasopisma, narzędzia redakcyjne,
 * systemy publikacji, identyfikatory (DOI), repozytoria oraz wskaźniki jakości naukowej.
 */
@Data
/**
 * DigitalizationSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.ADD_OTHER; SURVEY_CREATE.CHOOSE; SURVEY_CREATE.LABEL_CC_LICENCE_AUTHOR_PREFERENCE; SURVEY_CREATE.LABEL_CC_LICENSE; SURVEY_CREATE.LABEL_CC_LICENSE_CC_BY; SURVEY_CREATE.LABEL_CC_LICENSE_CC_BY_NC; SURVEY_CREATE.LABEL_CC_LICENSE_CC_BY_NC_ND; SURVEY_CREATE.LABEL_CC_LICENSE_CC_BY_NC_SA; SURVEY_CREATE.LABEL_CC_LICENSE_CC_BY_ND; SURVEY_CREATE.LABEL_CC_LICENSE_CC_BY_SA; SURVEY_CREATE.LABEL_CC_LICENSE_NONE; SURVEY_CREATE.LABEL_CITATIONS_STYLE_TOOLTIP; SURVEY_CREATE.LABEL_CITATIONS_TOOLS_TOOLTIP; SURVEY_CREATE.LABEL_DOI_PREFIX_TOOLTIP; SURVEY_CREATE.LABEL_EDITORIAL_TOOLS_EDITORIAL_SYSTEM_TOOLTIP; SURVEY_CREATE.LABEL_EDITORIAL_TOOLS_EXPORT_TOOLS_TOOLTIP; SURVEY_CREATE.LABEL_EDITORIAL_TOOLS_MANUSCRIPT_SYSTEM_TOOLTIP; SURVEY_CREATE.LABEL_EDITORIAL_TOOLS_NEWSLETTERS_TOOLTIP; SURVEY_CREATE.LABEL_EDITORIAL_TOOLS_STATISTICS_TOOLTIP; SURVEY_CREATE.LABEL_ORCID_REQUIRED; SURVEY_CREATE.LABEL_PUBLISHING_TOOLS_OTHER; SURVEY_CREATE.LABEL_PUBLISHING_TOOLS_TOOLTIP; SURVEY_CREATE.LABEL_SOCIAL_MEDIAS_OTHER; SURVEY_CREATE.LABEL_SYSTEM_FOR_PUBLISHED_ARTICLES; SURVEY_CREATE.LABEL_SYSTEM_FOR_PUBLISHED_ARTICLES_NAME; SURVEY_CREATE.LABEL_WEBSITE_LANGUAGES_TOOLTIP; SURVEY_CREATE.NO; SURVEY_CREATE.SELECT; SURVEY_CREATE.T_EXTERNAL_REPOSITORIES_INFO; SURVEY_CREATE.YES … (+1 więcej)</p>
 */
public class DigitalizationDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Czy czasopismo posiada stronę WWW (w starym formularzu: {@code survey.hasWebsite}).
     */
    private Boolean hasWebsite;

    /** Adres strony WWW ({@code survey.website}). */
    private String website;

    /** Informacje, które znajdują się na stronie WWW ({@code survey.infoWebsite.*}). */
    private InfoWebsite infoWebsite = new InfoWebsite();

    /** Języki strony WWW (multi-select) ({@code survey.websiteLanguages}).  * <p><b>translate:</b> SURVEY_CREATE.LABEL_WEBSITE_LANGUAGES_TOOLTIP, SURVEY_CREATE.SELECT</p>
 */
    private List<Language> websiteLanguages = new ArrayList<>();

    /** Narzędzia redakcyjne i edytorskie ({@code survey.infoEditorialTools.*}). */
    //private InfoEditorialTools infoEditorialTools = new InfoEditorialTools();
    /**
     * Narzędzia redakcyjne i edytorskie – grupa checkboxów.
     */
    private Set<EditorialTool> editorialTools = EnumSet.noneOf(EditorialTool.class);

    /** Narzędzia do publikowania (od wersji 1.4) ({@code survey.publishingTools.*}). */
    private PublishingTools publishingTools = new PublishingTools();

    /** Style cytowań (multi-select) ({@code survey.citationsStyle}). */
    private Set<BibliographicStyle> citationsStyle = EnumSet.noneOf(BibliographicStyle.class);

    /** Narzędzia cytowań (multi-select) ({@code survey.citationsTools}). */
    private Set<BibliographicTools> citationsTools = EnumSet.noneOf(BibliographicTools.class);

    /** Preferowane dane dot. cytowań (multi-select) ({@code survey.citationsPreferredData}). */
    private List<String> citationsPreferredData = new ArrayList<>();

    /** Licencja publikowanych artykułów ({@code survey.articlePublishedLicense}). */
    private ArticlePublishedLicense articlePublishedLicense;

    /** Udział wersji elektronicznych (procent) ({@code survey.electronicVersionsPercentage}).  * <p><b>translate:</b> SURVEY_CREATE.LABEL_ELECTRONIC_VERSIONS_PERCENTAGE, SURVEY_CREATE.LABEL_INFO_PUBLISHING_POLICY_EXTENDED_ELECTRONIC_VERSIONS</p>
 */
    private Double electronicVersionsPercentage;

    /** Prefiks DOI ({@code survey.doiPrefix}). */
    private String doiPrefix;

    /** Typ DOI ({@code survey.doiType}) – przechowywany jako tekst, niezależnie od enumów źródłowych. */
    private DoiType doiType;

    /** Czy wymagany jest identyfikator ORCID od autorów ({@code survey.orcidRequired}). */
    private Boolean orcidRequired;

    /** Czy używany jest system do publikacji artykułów ({@code survey.systemForPublishedArticlesUsed}). */
    private Boolean systemForPublishedArticlesUsed;

    /** Nazwa systemu do publikacji artykułów ({@code survey.systemForPublishedArticlesName}) – aktywna gdy {@code systemForPublishedArticlesUsed == true}. */
    private String systemForPublishedArticlesName;

    /** Media społecznościowe ({@code survey.socialMedias.*}). */
    private SocialMedias socialMedias = new SocialMedias();

    /** Repozytoria zewnętrzne ({@code survey.externalRepositories.*}). */
    private ExternalRepositories externalRepositories = new ExternalRepositories();

    /** Wskaźniki jakości naukowej (IF/SJR/SNIP/TC/CS/AI/CNCI) – poprzedni i bieżący. */
    private ScientificQuality scientificQuality = new ScientificQuality();
}
