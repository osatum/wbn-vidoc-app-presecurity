package com.osatum.wbn.application.journal.query;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public final class JournalSearchViewRules {
    private static final Set<JournalSearchCriteria.SortBy> BASIC_SORTS = Set.of(
            JournalSearchCriteria.SortBy.SORT_BY_TITLE_ASC, JournalSearchCriteria.SortBy.SORT_BY_TITLE_DESC,
            JournalSearchCriteria.SortBy.SORT_BY_RATING_ICV_ASC, JournalSearchCriteria.SortBy.SORT_BY_RATING_ICV_DESC);

    private JournalSearchViewRules() {
    }

    public static boolean isBasic(JournalSearchCriteria criteria) {
        return criteria.size() == JournalSearchCriteria.PAGE_SIZE
                && BASIC_SORTS.contains(criteria.sortBy())
                && (criteria.editor() == null || criteria.editor().isBlank())
                && !criteria.doiOnly() && !criteria.editorialSystemOnly() && !criteria.newsletterOnly()
                && !criteria.socialMediaOnly() && !criteria.antiPlagiarismOnly() && !criteria.aopOnly()
                && criteria.feeFilters().values().stream().noneMatch(fee -> fee.noFee() || fee.maximumPln() != null)
                && criteria.repositoryIds().isEmpty() && criteria.licenseCodes().isEmpty()
                && criteria.accessModels().isEmpty() && criteria.frequencyCodes().isEmpty()
                && criteria.reviewModels().isEmpty()
                && Stream.of(criteria.firstIssueYearFrom(), criteria.firstIssueYearTo(),
                        criteria.reviewDaysFrom(), criteria.reviewDaysTo(),
                        criteria.initialAssessmentDaysFrom(), criteria.initialAssessmentDaysTo(),
                        criteria.publicationDaysFrom(), criteria.publicationDaysTo(),
                        criteria.rejectionRateFrom(), criteria.rejectionRateTo(),
                        criteria.authorForeignAffiliationPercentageFrom(), criteria.authorForeignAffiliationPercentageTo(),
                        criteria.reviewerForeignAffiliationPercentageFrom(), criteria.reviewerForeignAffiliationPercentageTo())
                .allMatch(Objects::isNull);
    }
}
