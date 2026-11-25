package com.osatum.wbn.application.journal.assembler;

import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import com.osatum.wbn.application.journal.query.JournalSearchCriteriaRules;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class JournalSearchCriteriaMapperTest {
    private final JournalSearchCriteriaMapper mapper = Mappers.getMapper(JournalSearchCriteriaMapper.class);

    @Test
    void coversEveryRequestFieldExceptLegacyAliasesFixedPaginationAndAuthorizedViewContext() {
        var sourceFields = Stream.concat(Arrays.stream(JournalListQuery.class.getDeclaredFields()),
                        Arrays.stream(JournalListQuery.class.getSuperclass().getDeclaredFields()))
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getName()).collect(Collectors.toSet());
        sourceFields.removeAll(Set.of("countryCode", "languageCode", "first", "searchView"));

        assertThat(Arrays.stream(JournalSearchCriteria.class.getRecordComponents()).map(component -> component.getName()).toList())
                .containsExactlyInAnyOrderElementsOf(sourceFields);
    }

    @Test
    void mapsEveryCriteriaComponentIncludingLegacyPublisherAndExactDecimalBounds() throws Exception {
        var query = fullRequest();

        var criteria = mapper.toCriteria(query);

        for (var component : JournalSearchCriteria.class.getRecordComponents()) {
            Object expected = switch (component.getName()) {
                case "countryCodes" -> List.of("pl", "de");
                case "languageCodes" -> List.of("en", "pl");
                case "disciplineCodes" -> Arrays.asList(query.getDisciplineCodes());
                case "sortBy" -> JournalSearchCriteria.SortBy.valueOf(query.getSortBy().name());
                default -> {
                    String name = component.getName();
                    String getter = (component.getType() == boolean.class ? "is" : "get")
                            + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    yield JournalListQuery.class.getMethod(getter).invoke(query);
                }
            };
            assertThat(component.getAccessor().invoke(criteria)).as(component.getName()).isEqualTo(expected);
        }
        assertThat(JournalSearchCriteriaRules.isValid(criteria)).isTrue();
        assertThat(query.getCountryCodes()).containsExactly(" PL ", "DE", "pl");
        assertThat(query.getLanguageCodes()).containsExactly("EN", "pl", " en ");
    }

    @Test
    void takesDefensiveCopiesOfEveryCollectionAndTheSourceArray() {
        var query = fullRequest();
        var criteria = mapper.toCriteria(query);

        query.getCountryCodes().clear();
        query.getLanguageCodes().clear();
        query.getRepositoryIds().clear();
        query.getLicenseCodes().clear();
        query.getAccessModels().clear();
        query.getFrequencyCodes().clear();
        query.getReviewModels().clear();
        query.getMniswDisciplineCodes().clear();
        query.getDisciplineCodes()[0] = "changed";
        query.setJournalTitle("Changed title");
        query.setPublicationFeeMaxPln(BigDecimal.TEN);
        query.setPage(0);

        for (var values : List.of(criteria.countryCodes(), criteria.languageCodes(), criteria.repositoryIds(),
                criteria.licenseCodes(), criteria.accessModels(), criteria.frequencyCodes(), criteria.disciplineCodes(),
                criteria.mniswDisciplineCodes(), criteria.reviewModels())) {
            assertThat(values).isNotEmpty();
            assertThatThrownBy(values::clear).isInstanceOf(UnsupportedOperationException.class);
        }
        assertThat(criteria.disciplineCodes()).containsExactly("10", "20");
        assertThat(criteria.journalTitle()).isEqualTo("Alpha Journal");
        assertThat(criteria.publicationFeeMaxPln()).isEqualTo(BigDecimal.ZERO);
        assertThat(criteria.page()).isEqualTo(123);
        assertThatThrownBy(() -> criteria.feeFilters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(JournalSearchCriteria.class.isRecord()).isTrue();
        assertThat(JournalSearchCriteria.class.getRecordComponents()).allSatisfy(component ->
                assertThat(component.getType().isArray()).as(component.getName()).isFalse());
    }

    @Test
    void handlesLegacyCodesButExplicitListsIncludingEmptyListsTakePrecedence() {
        var query = new JournalListQuery();
        query.setCountryCode(" PL ");
        query.setLanguageCode(" EN ");
        assertThat(mapper.toCriteria(query).countryCodes()).containsExactly("pl");
        assertThat(mapper.toCriteria(query).languageCodes()).containsExactly("en");

        query.setCountryCodes(List.of());
        query.setLanguageCodes(List.of());
        assertThat(mapper.toCriteria(query).countryCodes()).isEmpty();
        assertThat(mapper.toCriteria(query).languageCodes()).isEmpty();

        query.setCountryCodes(List.of(" DE ", "de"));
        query.setLanguageCodes(List.of(" PL ", "pl"));
        assertThat(mapper.toCriteria(query).countryCodes()).containsExactly("de");
        assertThat(mapper.toCriteria(query).languageCodes()).containsExactly("pl");
        assertThat(query.getCountryCode()).isEqualTo(" PL ");
        assertThat(query.getLanguageCode()).isEqualTo(" EN ");
    }

    @ParameterizedTest
    @EnumSource(JournalListQuery.SortBy.class)
    void mapsAllEightSortsWithoutBindingTheReadModelToTheRestEnum(JournalListQuery.SortBy sort) {
        var query = new JournalListQuery();
        query.setSortBy(sort);

        assertThat(mapper.toCriteria(query).sortBy()).isEqualTo(JournalSearchCriteria.SortBy.valueOf(sort.name()));
    }

    @Test
    void defaultsNullSortAndPreservesTheLegacyAliasAtTheBoundary() {
        var query = new JournalListQuery();
        query.setSortBy(null);
        var criteria = mapper.toCriteria(query);

        assertThat(criteria.sortBy()).isEqualTo(JournalSearchCriteria.SortBy.SORT_BY_TITLE_ASC);
        assertThat(criteria.countryCodes()).isEmpty();
        assertThat(criteria.repositoryIds()).isEmpty();
        assertThat(query.getSortBy()).isNull();
        query.setSortBy(JournalListQuery.SortBy.fromValue("SORT_BY_RELEVANCE_DESC"));
        assertThat(mapper.toCriteria(query).sortBy()).isEqualTo(JournalSearchCriteria.SortBy.SORT_BY_TITLE_ASC);
    }

    @Test
    void keepsFiveIndependentFeeCategoriesAndOriginalDecimalPrecision() {
        var criteria = mapper.toCriteria(fullRequest());

        assertThat(criteria.feeFilters()).hasSize(5);
        assertThat(criteria.feeFilters().get(FeeCategory.OTHER)).isEqualTo(new JournalSearchCriteria.FeeFilter(true, null));
        assertThat(criteria.feeFilters().get(FeeCategory.OPEN_ACCESS).maximumPln()).isEqualTo(new BigDecimal("123.4567"));
        assertThat(criteria.feeFilters().get(FeeCategory.PUBLICATION).maximumPln()).isEqualTo(BigDecimal.ZERO);
        assertThat(criteria.feeFilters().get(FeeCategory.RAPID_REVIEW).maximumPln()).isEqualTo(new BigDecimal("500"));
        assertThat(criteria.feeFilters().get(FeeCategory.SUBMISSION)).isEqualTo(new JournalSearchCriteria.FeeFilter(true, null));
        assertThat(criteria.rejectionRateFrom()).isEqualTo(new BigDecimal("33.33333"));
    }

    @Test
    void doesNotTruncateInvalidIntegralValuesAndSharesTheExistingConditionalRules() {
        var query = new JournalListQuery();
        query.setReviewDaysTo(new BigDecimal("1.5"));
        var criteria = mapper.toCriteria(query);
        assertThat(criteria.reviewDaysTo()).isEqualTo(new BigDecimal("1.5"));
        assertThat(query.isProcessingTimeLimitsValid()).isFalse();
        assertThat(JournalSearchCriteriaRules.isValid(criteria)).isFalse();

        query.setReviewDaysTo(null);
        query.setMniswPointsFrom(new BigDecimal("140.5"));
        query.setMniswPointsTo(new BigDecimal("20"));
        assertThat(query.isMniswRangeValid()).isTrue();
        assertThat(JournalSearchCriteriaRules.isValid(mapper.toCriteria(query))).isTrue();
        query.setMniswOnly(true);
        assertThat(query.isMniswRangeValid()).isFalse();
        assertThat(JournalSearchCriteriaRules.isValid(mapper.toCriteria(query))).isFalse();
    }

    private JournalListQuery fullRequest() {
        var query = new JournalListQuery();
        query.setState(EntityState.ACTIVE);
        query.setJournalTitle("Alpha Journal");
        query.setIssn("1234-5678");
        query.setPublisher("Publisher");
        query.setEditor("Legacy publisher");
        query.setCountryCodes(new ArrayList<>(List.of(" PL ", "DE", "pl")));
        query.setLanguageCodes(new ArrayList<>(List.of("EN", "pl", " en ")));
        query.setDisciplineCodes(new String[]{"10", "20"});
        query.setRatingStatus(RatingStatus.INDEXED);
        query.setIcvFrom(new BigDecimal("80.25"));
        query.setIcvTo(new BigDecimal("95.75"));
        query.setMniswOnly(true);
        query.setMniswDisciplineCodes(new ArrayList<>(List.of("2021", "2061")));
        query.setDoiOnly(true);
        query.setEditorialSystemOnly(true);
        query.setNewsletterOnly(true);
        query.setSocialMediaOnly(true);
        query.setAntiPlagiarismOnly(true);
        query.setAopOnly(true);
        query.setOtherFeesFreeOnly(true);
        query.setOpenAccessFeeMaxPln(new BigDecimal("123.4567"));
        query.setPublicationFeeMaxPln(BigDecimal.ZERO);
        query.setRapidReviewFeeMaxPln(new BigDecimal("500"));
        query.setSubmissionFeeFreeOnly(true);
        query.setRepositoryIds(new ArrayList<>(List.of(BigDecimal.TEN, new BigDecimal("20"))));
        query.setLicenseCodes(new ArrayList<>(List.of(ArticlePublishedLicense.CC_BY)));
        query.setAccessModels(new ArrayList<>(List.of(PaidArticleType.FREE_ARTICLES)));
        query.setFrequencyCodes(new ArrayList<>(List.of(JournalFrequency.q)));
        query.setReviewModels(new ArrayList<>(List.of(com.osatum.wbn.domain.model.journal.JournalReviewModel.DOUBLE_BLIND_REVIEW)));
        query.setFirstIssueYearFrom(new BigDecimal("1960"));
        query.setFirstIssueYearTo(new BigDecimal("2024"));
        query.setMniswPointsFrom(new BigDecimal("20"));
        query.setMniswPointsTo(new BigDecimal("140"));
        query.setReviewDaysFrom(BigDecimal.ZERO);
        query.setReviewDaysTo(BigDecimal.ZERO);
        query.setInitialAssessmentDaysFrom(new BigDecimal("2"));
        query.setInitialAssessmentDaysTo(new BigDecimal("5"));
        query.setPublicationDaysFrom(new BigDecimal("30"));
        query.setPublicationDaysTo(new BigDecimal("100"));
        query.setRejectionRateFrom(new BigDecimal("33.33333"));
        query.setRejectionRateTo(new BigDecimal("50"));
        query.setAuthorForeignAffiliationPercentageFrom(new BigDecimal("20.25"));
        query.setAuthorForeignAffiliationPercentageTo(new BigDecimal("30"));
        query.setReviewerForeignAffiliationPercentageFrom(new BigDecimal("25"));
        query.setReviewerForeignAffiliationPercentageTo(new BigDecimal("50"));
        query.setSortBy(JournalListQuery.SortBy.SORT_BY_RATING_MNISW_DESC);
        query.setPage(123);
        return query;
    }
}
