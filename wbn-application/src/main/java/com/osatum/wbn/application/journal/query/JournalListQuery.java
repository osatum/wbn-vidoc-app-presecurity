package com.osatum.wbn.application.journal.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.SearchView;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.JournalReviewModel;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mutowalny kontrakt wejściowy REST; po walidacji jest mapowany na niezależne kryteria aplikacyjne.
 */
@Getter
@Setter
public class JournalListQuery extends com.osatum.wbn.application.shared.list.PageRequest {

    public static final int PAGE_SIZE = JournalSearchCriteria.PAGE_SIZE;

    public JournalListQuery() {
        setSize(PAGE_SIZE);
    }

    @Override
    @Min(0)
    @Schema(minimum = "0", defaultValue = "0", description = "Numer strony liczony od zera")
    public int getPage() {
        return super.getPage();
    }

    @Override
    @Min(PAGE_SIZE)
    @Max(100)
    @Schema(minimum = "10", maximum = "100", allowableValues = {"10", "25", "50", "100"}, defaultValue = "10",
            description = "Liczba wyników na stronie: 10 w BASIC, 10, 25, 50 lub 100 w ADVANCED")
    public int getSize() {
        return super.getSize();
    }

    @JsonIgnore
    @AssertTrue(message = "Page size must be one of 10, 25, 50 or 100")
    public boolean isPageSizeValid() {
        return JournalSearchCriteriaRules.isValidPageSize(getSize());
    }

    @JsonSetter("page")
    public void readPage(BigDecimal page) {
        setPage(page.intValueExact());
    }

    @JsonSetter("size")
    public void readSize(BigDecimal size) {
        setSize(size.intValueExact());
    }

    public enum SortBy {
        SORT_BY_TITLE_ASC,
        SORT_BY_TITLE_DESC,
        SORT_BY_RATING_ICV_DESC,
        SORT_BY_RATING_ICV_ASC,
        SORT_BY_RATING_MNISW_DESC,
        SORT_BY_RATING_MNISW_ASC,
        SORT_BY_PUBLICATION_DAYS_ASC,
        SORT_BY_PUBLICATION_DAYS_DESC;

        @JsonCreator
        public static SortBy fromValue(String value) {
            return "SORT_BY_RELEVANCE_DESC".equals(value) ? SORT_BY_TITLE_ASC : valueOf(value);
        }
    }

    private EntityState state;
    @Schema(description = "Wybrany widok wyszukiwania. Brak wartości wybiera domyślny widok efektywnego profilu jednostki.")
    private SearchView searchView;
    @Positive
    @Schema(description = "Opublikowany rok ankiety i ICV. Brak wartości oznacza bieżący rok oceny.")
    private Integer surveyYear;

    @JsonSetter("surveyYear")
    public void readSurveyYear(BigDecimal value) {
        surveyYear = value == null ? null : value.intValueExact();
    }
    private String journalTitle;
    private String issn;
    private String publisher;
    private String editor;
    private String languageCode;
    private String countryCode;
    private List<@NotBlank String> languageCodes;
    private List<@NotBlank String> countryCodes;
    private String[] disciplineCodes;
    @Schema(description = "Opcjonalny status ICV obowiązującego roku. Brak wartości nie ogranicza wyników do czasopism indeksowanych.")
    private RatingStatus ratingStatus;
    private BigDecimal icvFrom;
    private BigDecimal icvTo;
    private boolean mniswOnly;
    private List<@NotBlank String> mniswDisciplineCodes;
    private boolean doiOnly;
    private boolean editorialSystemOnly;
    private boolean newsletterOnly;
    private boolean socialMediaOnly;
    private boolean antiPlagiarismOnly;
    private boolean aopOnly;
    private boolean otherFeesFreeOnly;
    private boolean openAccessFeeFreeOnly;
    @Schema(minimum = "0")
    private BigDecimal openAccessFeeMaxPln;
    private boolean publicationFeeFreeOnly;
    @Schema(minimum = "0")
    private BigDecimal publicationFeeMaxPln;
    private boolean rapidReviewFeeFreeOnly;
    @Schema(minimum = "0")
    private BigDecimal rapidReviewFeeMaxPln;
    private boolean submissionFeeFreeOnly;
    @Schema(minimum = "0")
    private BigDecimal submissionFeeMaxPln;
    @ArraySchema(schema = @Schema(type = "integer", format = "int64", minimum = "1", maximum = "9223372036854775807"))
    private List<@NotNull @Positive BigDecimal> repositoryIds;
    private List<@NotNull ArticlePublishedLicense> licenseCodes;
    private List<@NotNull PaidArticleType> accessModels;
    private List<@NotNull JournalFrequency> frequencyCodes;
    @Schema(description = "Modele recenzowania potwierdzone w ankiecie wybranego roku; wiele wartości łączy się przez OR.")
    private List<@NotNull JournalReviewModel> reviewModels;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal firstIssueYearFrom;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal firstIssueYearTo;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal mniswPointsFrom;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal mniswPointsTo;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal reviewDaysFrom;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal reviewDaysTo;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal initialAssessmentDaysFrom;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal initialAssessmentDaysTo;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal publicationDaysFrom;
    @Schema(type = "integer", format = "int32", minimum = "0", maximum = "2147483647")
    private BigDecimal publicationDaysTo;
    @Schema(minimum = "0", maximum = "100")
    private BigDecimal rejectionRateFrom;
    @Schema(minimum = "0", maximum = "100")
    private BigDecimal rejectionRateTo;
    @Schema(minimum = "0", maximum = "100")
    private BigDecimal authorForeignAffiliationPercentageFrom;
    @Schema(minimum = "0", maximum = "100")
    private BigDecimal authorForeignAffiliationPercentageTo;
    @Schema(minimum = "0", maximum = "100")
    private BigDecimal reviewerForeignAffiliationPercentageFrom;
    @Schema(minimum = "0", maximum = "100")
    private BigDecimal reviewerForeignAffiliationPercentageTo;
    private SortBy sortBy = SortBy.SORT_BY_TITLE_ASC;

    @JsonIgnore
    @AssertTrue(message = "Fee limits must be non-negative and cannot be combined with no-fee in the same category")
    public boolean isFeeCriteriaValid() {
        return JournalSearchCriteriaRules.isValidFeeLimit(openAccessFeeFreeOnly, openAccessFeeMaxPln)
                && JournalSearchCriteriaRules.isValidFeeLimit(publicationFeeFreeOnly, publicationFeeMaxPln)
                && JournalSearchCriteriaRules.isValidFeeLimit(rapidReviewFeeFreeOnly, rapidReviewFeeMaxPln)
                && JournalSearchCriteriaRules.isValidFeeLimit(submissionFeeFreeOnly, submissionFeeMaxPln);
    }

    @JsonIgnore
    @AssertTrue(message = "Repository IDs must be positive whole numbers within the long range")
    public boolean isRepositoryIdsValid() {
        return JournalSearchCriteriaRules.isValidRepositoryIds(repositoryIds);
    }

    @JsonIgnore
    @AssertTrue(message = "First issue years must be non-negative whole years and satisfy minimum <= maximum")
    public boolean isFirstIssueYearRangeValid() {
        return JournalSearchCriteriaRules.isValidIntegerRange(firstIssueYearFrom, firstIssueYearTo);
    }

    @JsonIgnore
    @AssertTrue(message = "MNiSW bounds must be non-negative whole points and satisfy minimum <= maximum")
    public boolean isMniswRangeValid() {
        return JournalSearchCriteriaRules.isValidMniswRange(mniswOnly, mniswPointsFrom, mniswPointsTo);
    }

    @JsonIgnore
    @AssertTrue(message = "Processing time bounds must be non-negative whole days within the integer range and satisfy minimum <= maximum")
    public boolean isProcessingTimeLimitsValid() {
        return JournalSearchCriteriaRules.isValidProcessingTimeLimits(reviewDaysFrom, reviewDaysTo,
                initialAssessmentDaysFrom, initialAssessmentDaysTo, publicationDaysFrom, publicationDaysTo);
    }

    @JsonIgnore
    @AssertTrue(message = "Percentage bounds must be between 0 and 100 and satisfy minimum <= maximum")
    public boolean isPercentageRangesValid() {
        return JournalSearchCriteriaRules.isValidPercentageRange(rejectionRateFrom, rejectionRateTo)
                && JournalSearchCriteriaRules.isValidPercentageRange(authorForeignAffiliationPercentageFrom, authorForeignAffiliationPercentageTo)
                && JournalSearchCriteriaRules.isValidPercentageRange(reviewerForeignAffiliationPercentageFrom, reviewerForeignAffiliationPercentageTo);
    }

    @JsonIgnore
    @AssertTrue(message = "ICV bounds require indexed journals, must be non-negative, have at most two decimal places and satisfy minimum <= maximum")
    public boolean isIcvRangeValid() {
        return JournalSearchCriteriaRules.isValidIcvRange(ratingStatus, icvFrom, icvTo);
    }
}
