package com.osatum.wbn.application.questionnaire.dto;


import com.osatum.wbn.application.questionnaire.dto.model.AuthorsFee;
import com.osatum.wbn.application.questionnaire.dto.model.AvgManuscriptTimes;
import com.osatum.wbn.application.questionnaire.dto.model.InfoReviewProcedure;
import com.osatum.wbn.domain.model.survey.model.AuthorInstructionType;
import com.osatum.wbn.domain.model.survey.model.ReviewRule;
import com.osatum.wbn.domain.model.survey.model.StatementType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Sekcja 5: Standardy ({@code SURVEY_CREATE.HEADING_SECTION_5}).
 * <p>
 * Zawiera m.in. publikację procedur, zasady recenzowania, model recenzji, oświadczenia,
 * średnie czasy obsługi manuskryptu oraz opłaty pobierane od autorów.
 */
@Data
/**
 * StandardsSection
 * <p><b>Klucze translate użyte w tej sekcji (HTML):</b> SURVEY_CREATE.APC_LABEL; SURVEY_CREATE.AUTHORS_FEE_MODEL; SURVEY_CREATE.AUTHOR_INSTRUCTION; SURVEY_CREATE.AUTHOR_INSTRUCTION_ARTICLE_INSTRUCTION; SURVEY_CREATE.AUTHOR_INSTRUCTION_ARTICLE_INSTRUCTION_TOOLTIP; SURVEY_CREATE.AUTHOR_INSTRUCTION_HEADER; SURVEY_CREATE.AUTHOR_INSTRUCTION_LITERATURE_INSTRUCTION; SURVEY_CREATE.AUTHOR_INSTRUCTION_LITERATURE_INSTRUCTION_TOOLTIP; SURVEY_CREATE.AUTHOR_INSTRUCTION_NONE; SURVEY_CREATE.AUTHOR_INSTRUCTION_NO_INSTRUCTION; SURVEY_CREATE.CHOOSE; SURVEY_CREATE.EXTERNAL_AFFILIATION_HEADER; SURVEY_CREATE.EXTERNAL_AUTHORS; SURVEY_CREATE.INFO_PUBLISHED_PROCEDURES; SURVEY_CREATE.INITIAL_ASSESSMENT_TIME_TOOLTIP; SURVEY_CREATE.INTERNAL_AUTHORS; SURVEY_CREATE.JOURNAL_NUMBER_WITH_REVIEW_PROCEDURE; SURVEY_CREATE.LABEL_AUTHORS_FEE_MODEL; SURVEY_CREATE.LABEL_CHOOSE_CURRENCY; SURVEY_CREATE.LABEL_COUNTRY_SPECIFIC_FEE; SURVEY_CREATE.LABEL_CURRENCY; SURVEY_CREATE.LABEL_DESCRIPTION; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_MIN2REVIEWERS; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_MIN2REVIEWERS_TOOLTIP; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_NONE; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PREPARE_REVIEW; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLIC_CRITERIA; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLIC_FORM; SURVEY_CREATE.LABEL_INFO_REVIEW_RULES_PUBLIC_FORM_TOOLTIP … (+48 więcej)</p>
 */
public class StandardsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Czy opublikowano procedury ({@code SURVEY_CREATE.LABEL_PUBLISHED_PROCEDURES}) – wykorzystywane w wersji formularza 1.0. */
    private Boolean publishedProcedures;

    /**
     * Czy opublikowano procedury recenzowania ({@code SURVEY_CREATE.LABEL_PUBLISHED_REVIEW_PROCEDURES}).
     * <p>
     * Logika formularza: gdy {@code publishedReviewProcedures == true}, wymagany jest link ({@code linkToReviewProcedure}).
     * </p>
     */
    private Boolean publishedReviewProcedures;

    /** Link do procedury recenzowania ({@code SURVEY_CREATE.LINK_TO_REVIEW_PROCEDURE}). */
    private String linkToReviewProcedure;

    /** Liczba numerów z opisaną procedurą recenzowania ({@code SURVEY_CREATE.JOURNAL_NUMBER_WITH_REVIEW_PROCEDURE}) – w HTML pole było ukryte/zakomentowane, ale dane mogą występować w modelu. */
    private Integer journalNumberWithReviewProcedure;

    /** Zasady recenzowania ({@code SURVEY_CREATE.LABEL_INFO_REVIEW_RULES}). */
    private Set<ReviewRule> reviewRules = EnumSet.noneOf(ReviewRule.class);

    @Schema(nullable = true)
    private String publishedReviewersListWww;
    @Schema(nullable = true)
    private String publicFormWww;
    @Schema(nullable = true)
    private String publicCriteriaWww;
    @Schema(nullable = true)
    private Boolean internationalPoliciesAdopted;
    @Schema(nullable = true)
    private String internationalPoliciesWww;

    /** Model procedury recenzowania ({@code SURVEY_CREATE.LABEL_REVIEW_PROCEDURE}). */
    private InfoReviewProcedure infoReviewProcedure = new InfoReviewProcedure();

    /**
     * Odsetek recenzentów zewnętrznych ({@code msg.percentageExternalReviewers}).
     */
    private Double percentageExternalReviewers;

    /** Oświadczenia (konflikt interesów, finansowanie, wkład autora) ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT}). */
    //private InfoStatement infoStatement = new InfoStatement();

    /**
     * Oświadczenia – grupa checkboxów ({@code SURVEY_CREATE.LABEL_INFO_STATEMENT}).
     */
    private Set<StatementType> statements = EnumSet.noneOf(StatementType.class);

    /** Instrukcje dla autorów ({@code SURVEY_CREATE.LABEL_INFO_AUTHOR_INSTRUCTION}). */
    //private InfoAuthorInstruction infoAuthorInstruction = new InfoAuthorInstruction();
    private Set<AuthorInstructionType> authorInstructions =
            EnumSet.noneOf(AuthorInstructionType.class);

    /** Link do instrukcji dla autorów ({@code SURVEY_CREATE.LABEL_INSTRUCTION_LINK}). */
    private String instructionLink;

    /** Liczba instrukcji / numeracja ({@code SURVEY_CREATE.LABEL_INSTRUCTION_NUMBER}). */
    private Integer instructionNumber;

    /**
     * Średnie czasy obsługi manuskryptu ({@code msg.avgManuscriptTimes}).
     */
    private AvgManuscriptTimes avgManuscriptTimes = new AvgManuscriptTimes();

    /** Opłata za zgłoszenie (submission fee). */
    private AuthorsFee submissionFee = new AuthorsFee();

    /** Opłata za publikację (publication fee). */
    private AuthorsFee publicationFee = new AuthorsFee();

    /** Opłata open access. */
    private AuthorsFee openAccessFee = new AuthorsFee();

    /** Opłata za szybką recenzję (rapid review). */
    private AuthorsFee rapidReview = new AuthorsFee();

    /** APC – article processing charge. */
    private AuthorsFee apc = new AuthorsFee();

    /**
     * Czy opublikowano informację o kosztach obsługi manuskryptu ({@code survey.manuscriptProcessingCostPublished}).
      * <p><b>translate:</b> SURVEY_CREATE.MANUSCRIPT_PROCESSING_COST_PUBLISHED_LABEL, SURVEY_CREATE.NO, SURVEY_CREATE.YES</p>
 */
    private Boolean manuscriptProcessingCostPublished;

    /** URL do informacji o kosztach obsługi manuskryptu ({@code survey.manuscriptProcessingCostUrl}). */
    private String manuscriptProcessingCostUrl;

    /** Czy opublikowano informację o czasie obsługi manuskryptu ({@code survey.manuscriptProcessingTimePublished}).  * <p><b>translate:</b> SURVEY_CREATE.MANUSCRIPT_PROCESSING_TIME_PUBLISHED_LABEL, SURVEY_CREATE.NO, SURVEY_CREATE.YES</p>
 */
    private Boolean manuscriptProcessingTimePublished;

    /** URL do informacji o czasie obsługi manuskryptu ({@code survey.manuscriptProcessingTimeUrl}). */
    private String manuscriptProcessingTimeUrl;

    /**
     * Czy wymagany jest ORCID ({@code survey.orcidRequired}).
     */
    private Boolean orcidRequired;
}
