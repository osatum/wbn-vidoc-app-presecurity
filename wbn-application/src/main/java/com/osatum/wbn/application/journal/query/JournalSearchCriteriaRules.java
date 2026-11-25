package com.osatum.wbn.application.journal.query;

import com.osatum.wbn.domain.entity.RatingStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wspólne reguły kryteriów dla kontraktu REST, odczytu i audytu, bez zależności od mechanizmu walidacji.
 */
public final class JournalSearchCriteriaRules {
    private JournalSearchCriteriaRules() {
    }

    public static boolean isValid(JournalSearchCriteria criteria) {
        return criteria.page() >= 0
                && isValidPageSize(criteria.size())
                && (criteria.surveyYear() == null || criteria.surveyYear() > 0)
                && criteria.countryCodes().stream().allMatch(value -> !value.isBlank())
                && criteria.languageCodes().stream().allMatch(value -> !value.isBlank())
                && criteria.mniswDisciplineCodes().stream().allMatch(value -> !value.isBlank())
                && criteria.feeFilters().values().stream().allMatch(fee -> isValidFeeLimit(fee.noFee(), fee.maximumPln()))
                && isValidRepositoryIds(criteria.repositoryIds())
                && isValidIntegerRange(criteria.firstIssueYearFrom(), criteria.firstIssueYearTo())
                && isValidMniswRange(criteria.mniswOnly(), criteria.mniswPointsFrom(), criteria.mniswPointsTo())
                && isValidProcessingTimeLimits(criteria.reviewDaysFrom(), criteria.reviewDaysTo(),
                        criteria.initialAssessmentDaysFrom(), criteria.initialAssessmentDaysTo(),
                        criteria.publicationDaysFrom(), criteria.publicationDaysTo())
                && isValidPercentageRange(criteria.rejectionRateFrom(), criteria.rejectionRateTo())
                && isValidPercentageRange(criteria.authorForeignAffiliationPercentageFrom(), criteria.authorForeignAffiliationPercentageTo())
                && isValidPercentageRange(criteria.reviewerForeignAffiliationPercentageFrom(), criteria.reviewerForeignAffiliationPercentageTo())
                && isValidIcvRange(criteria.ratingStatus(), criteria.icvFrom(), criteria.icvTo());
    }

    public static boolean isValidFeeLimit(boolean noFee, BigDecimal maximumPln) {
        return maximumPln == null || (!noFee && maximumPln.signum() >= 0);
    }

    public static boolean isValidPageSize(int size) {
        return JournalSearchCriteria.PAGE_SIZES.contains(size);
    }

    public static boolean isValidRepositoryIds(List<BigDecimal> repositoryIds) {
        return repositoryIds == null || repositoryIds.stream().allMatch(value -> value != null && value.signum() > 0
                && value.stripTrailingZeros().scale() <= 0 && value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0);
    }

    public static boolean isValidIntegerRange(BigDecimal from, BigDecimal to) {
        return isValidIntegerBound(from) && isValidIntegerBound(to) && isOrdered(from, to);
    }

    public static boolean isValidMniswRange(boolean mniswOnly, BigDecimal from, BigDecimal to) {
        return !mniswOnly || isValidIntegerRange(from, to);
    }

    public static boolean isValidProcessingTimeLimits(BigDecimal reviewDaysFrom, BigDecimal reviewDaysTo,
                                                      BigDecimal initialAssessmentDaysFrom, BigDecimal initialAssessmentDaysTo,
                                                      BigDecimal publicationDaysFrom, BigDecimal publicationDaysTo) {
        return isValidIntegerRange(reviewDaysFrom, reviewDaysTo)
                && isValidIntegerRange(initialAssessmentDaysFrom, initialAssessmentDaysTo)
                && isValidIntegerRange(publicationDaysFrom, publicationDaysTo);
    }

    public static boolean isValidPercentageRange(BigDecimal from, BigDecimal to) {
        return isValidPercentage(from) && isValidPercentage(to) && isOrdered(from, to);
    }

    public static boolean isValidIcvRange(RatingStatus ratingStatus, BigDecimal from, BigDecimal to) {
        return isValidIcvValue(from) && isValidIcvValue(to)
                && (ratingStatus == null || ratingStatus == RatingStatus.INDEXED || (from == null && to == null))
                && isOrdered(from, to);
    }

    private static boolean isValidIntegerBound(BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.stripTrailingZeros().scale() <= 0
                && value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0);
    }

    private static boolean isValidPercentage(BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0);
    }

    private static boolean isValidIcvValue(BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.stripTrailingZeros().scale() <= 2);
    }

    private static boolean isOrdered(BigDecimal from, BigDecimal to) {
        return from == null || to == null || from.compareTo(to) <= 0;
    }
}
