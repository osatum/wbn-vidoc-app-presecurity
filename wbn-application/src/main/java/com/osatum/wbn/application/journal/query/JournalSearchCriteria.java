package com.osatum.wbn.application.journal.query;

import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.JournalReviewModel;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Niemutowalny zestaw kryteriów odczytu, niezależny od formularza, JSON i dokumentacji REST.
 * Kolekcje są kopiowane, aby późniejsza edycja żądania nie zmieniła trwającego wyszukiwania.
 */
public record JournalSearchCriteria(
        Integer surveyYear,
        EntityState state,
        String journalTitle,
        String issn,
        String publisher,
        String editor,
        List<String> languageCodes,
        List<String> countryCodes,
        List<String> disciplineCodes,
        RatingStatus ratingStatus,
        BigDecimal icvFrom,
        BigDecimal icvTo,
        boolean mniswOnly,
        List<String> mniswDisciplineCodes,
        boolean doiOnly,
        boolean editorialSystemOnly,
        boolean newsletterOnly,
        boolean socialMediaOnly,
        boolean antiPlagiarismOnly,
        boolean aopOnly,
        boolean otherFeesFreeOnly,
        boolean openAccessFeeFreeOnly,
        BigDecimal openAccessFeeMaxPln,
        boolean publicationFeeFreeOnly,
        BigDecimal publicationFeeMaxPln,
        boolean rapidReviewFeeFreeOnly,
        BigDecimal rapidReviewFeeMaxPln,
        boolean submissionFeeFreeOnly,
        BigDecimal submissionFeeMaxPln,
        List<BigDecimal> repositoryIds,
        List<ArticlePublishedLicense> licenseCodes,
        List<PaidArticleType> accessModels,
        List<JournalFrequency> frequencyCodes,
        List<JournalReviewModel> reviewModels,
        BigDecimal firstIssueYearFrom,
        BigDecimal firstIssueYearTo,
        BigDecimal mniswPointsFrom,
        BigDecimal mniswPointsTo,
        BigDecimal reviewDaysFrom,
        BigDecimal reviewDaysTo,
        BigDecimal initialAssessmentDaysFrom,
        BigDecimal initialAssessmentDaysTo,
        BigDecimal publicationDaysFrom,
        BigDecimal publicationDaysTo,
        BigDecimal rejectionRateFrom,
        BigDecimal rejectionRateTo,
        BigDecimal authorForeignAffiliationPercentageFrom,
        BigDecimal authorForeignAffiliationPercentageTo,
        BigDecimal reviewerForeignAffiliationPercentageFrom,
        BigDecimal reviewerForeignAffiliationPercentageTo,
        SortBy sortBy,
        int size,
        int page) {
    public static final int PAGE_SIZE = 10;
    public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);

    public JournalSearchCriteria {
        languageCodes = languageCodes == null ? List.of() : List.copyOf(languageCodes);
        countryCodes = countryCodes == null ? List.of() : List.copyOf(countryCodes);
        disciplineCodes = disciplineCodes == null ? List.of() : List.copyOf(disciplineCodes);
        mniswDisciplineCodes = mniswDisciplineCodes == null ? List.of() : List.copyOf(mniswDisciplineCodes);
        repositoryIds = repositoryIds == null ? List.of() : List.copyOf(repositoryIds);
        licenseCodes = licenseCodes == null ? List.of() : List.copyOf(licenseCodes);
        accessModels = accessModels == null ? List.of() : List.copyOf(accessModels);
        frequencyCodes = frequencyCodes == null ? List.of() : List.copyOf(frequencyCodes);
        reviewModels = reviewModels == null ? List.of() : List.copyOf(reviewModels);
        sortBy = sortBy == null ? SortBy.SORT_BY_TITLE_ASC : sortBy;
    }

    public Map<FeeCategory, FeeFilter> feeFilters() {
        return Map.of(
                FeeCategory.OTHER, new FeeFilter(otherFeesFreeOnly, null),
                FeeCategory.OPEN_ACCESS, new FeeFilter(openAccessFeeFreeOnly, openAccessFeeMaxPln),
                FeeCategory.PUBLICATION, new FeeFilter(publicationFeeFreeOnly, publicationFeeMaxPln),
                FeeCategory.RAPID_REVIEW, new FeeFilter(rapidReviewFeeFreeOnly, rapidReviewFeeMaxPln),
                FeeCategory.SUBMISSION, new FeeFilter(submissionFeeFreeOnly, submissionFeeMaxPln));
    }

    public record FeeFilter(boolean noFee, BigDecimal maximumPln) {
    }

    public enum SortBy {
        SORT_BY_TITLE_ASC,
        SORT_BY_TITLE_DESC,
        SORT_BY_RATING_ICV_DESC,
        SORT_BY_RATING_ICV_ASC,
        SORT_BY_RATING_MNISW_DESC,
        SORT_BY_RATING_MNISW_ASC,
        SORT_BY_PUBLICATION_DAYS_ASC,
        SORT_BY_PUBLICATION_DAYS_DESC
    }
}
