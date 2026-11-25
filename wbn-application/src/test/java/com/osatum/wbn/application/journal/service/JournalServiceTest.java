package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.assembler.JournalViewMapper;
import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.application.journal.assembler.JournalSearchCriteriaMapper;
import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import com.osatum.wbn.application.journal.authorization.CanJournalView;
import com.osatum.wbn.application.journal.view.JournalSearchMetadata;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.domain.model.institution.SearchView;
import org.springframework.security.access.AccessDeniedException;
import com.osatum.wbn.domain.model.journal.JournalFrequency;
import com.osatum.wbn.domain.model.journal.PaidArticleType;
import com.osatum.wbn.domain.model.survey.model.ArticlePublishedLicense;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalServiceTest {
    @Test
    void basicCountsDoNotExposeAdvancedGroups() {
        when(operatingProfiles.requireSearchView(SearchView.BASIC)).thenReturn(SearchView.BASIC);
        var counts = java.util.Map.of("pl", 1L);
        when(handler.getSearchCounts(any())).thenReturn(new com.osatum.wbn.application.journal.view.JournalSearchCounts(
                counts, counts, counts, counts, counts, counts, counts, counts, counts, counts));
        var query = new JournalListQuery();
        query.setSearchView(SearchView.BASIC);
        var result = service.getSearchCounts(query);
        assertThat(result.countryCounts()).isEqualTo(counts);
        assertThat(result.mniswDisciplineCounts()).isEqualTo(counts);
        assertThat(result.featureCounts()).isEmpty();
        assertThat(result.repositoryCounts()).isEmpty();
        assertThat(result.reviewModelCounts()).isEmpty();
    }

    @Test
    void countsUseTheSameValidationAndProfileChecksAsResults() {
        var query = new JournalListQuery();
        query.setCountryCodes(List.of("pl"));
        service.getSearchCounts(query);
        var criteria = ArgumentCaptor.forClass(JournalSearchCriteria.class);
        verify(handler).getSearchCounts(criteria.capture());
        assertThat(criteria.getValue().countryCodes()).containsExactly("pl");
        clearInvocations(handler);
        query.setIcvFrom(java.math.BigDecimal.TEN);
        query.setIcvTo(java.math.BigDecimal.ONE);
        assertThat(catchThrowableOfType(() -> service.getSearchCounts(query), ConstraintViolationException.class)).isNotNull();
        verifyNoInteractions(handler);
    }

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private final JournalListQueryHandler handler = mock(JournalListQueryHandler.class);
    private final JournalSearchCriteriaMapper criteriaMapper = Mappers.getMapper(JournalSearchCriteriaMapper.class);
    private final DictionaryMniswDisciplineService mniswDisciplines = mock();
    private final com.osatum.wbn.application.configuration.service.OperatingProfileService operatingProfiles = mock();

    private final JournalService service = new JournalService(
            handler,
            mock(JournalRepository.class),
            mock(JournalViewMapper.class),
            VALIDATOR_FACTORY.getValidator(),
            criteriaMapper,
            mniswDisciplines,
            operatingProfiles,
            new com.osatum.wbn.application.configuration.service.ConfigurationService()
    );

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void validatesMniswCodesAgainstTheSeparateDictionaryAndKeepsJmlIndependent() {
        var query = new JournalListQuery();
        query.setMniswOnly(true);
        query.setMniswDisciplineCodes(List.of(" 2021 ", "2061", "2021"));
        query.setDisciplineCodes(new String[]{"42"});
        when(mniswDisciplines.getByCode("2021")).thenReturn(new DictionaryMniswDiscipline());
        when(mniswDisciplines.getByCode("2061")).thenReturn(new DictionaryMniswDiscipline());

        service.getJournals(query);

        var captured = ArgumentCaptor.forClass(JournalSearchCriteria.class);
        verify(handler).execute(captured.capture());
        assertThat(captured.getValue().mniswDisciplineCodes()).containsExactly("2021", "2061");
        assertThat(captured.getValue().disciplineCodes()).containsExactly("42");
    }

    @Test
    void rejectsUnknownMniswCodesBeforeSearching() {
        var query = new JournalListQuery();
        query.setMniswOnly(true);
        query.setMniswDisciplineCodes(List.of("unknown"));

        assertThatIllegalArgumentException().isThrownBy(() -> service.getJournals(query))
                .withMessageContaining("Unknown MNiSW discipline code");
        verifyNoInteractions(handler);
    }

    @Test
    void ignoresMniswCodesWithoutTheParentFilter() {
        var query = new JournalListQuery();
        query.setMniswDisciplineCodes(List.of("unknown"));

        service.getJournals(query);

        verifyNoInteractions(mniswDisciplines);
        var captured = ArgumentCaptor.forClass(JournalSearchCriteria.class);
        verify(handler).execute(captured.capture());
        assertThat(captured.getValue().mniswDisciplineCodes()).isEmpty();
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("invalidQueries")
    void rejectsInvalidInternalRequestsBeforeCallingTheSearchAdapter(JournalListQuery query, String field) {
        var exception = catchThrowableOfType(() -> service.getJournals(query), ConstraintViolationException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getConstraintViolations()).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).startsWith(field));
        verifyNoInteractions(handler);
    }

    @Test
    void rejectsNullCriteriaBeforeCallingTheSearchAdapter() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.getJournals(null))
                .withMessageContaining("criteria must not be null");

        verifyNoInteractions(handler);
    }

    @Test
    void reportsAllInvalidCriteriaInOneValidationResult() {
        var query = new JournalListQuery();
        query.setPage(-1);
        query.setPublicationFeeFreeOnly(true);
        query.setPublicationFeeMaxPln(BigDecimal.ZERO);
        query.setReviewDaysTo(new BigDecimal("1.5"));
        query.setRejectionRateTo(new BigDecimal("100.1"));

        var exception = catchThrowableOfType(() -> service.getJournals(query), ConstraintViolationException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getConstraintViolations()).extracting(violation -> violation.getPropertyPath().toString())
                .contains("page", "feeCriteriaValid", "processingTimeLimitsValid", "percentageRangesValid");
        verifyNoInteractions(handler);
    }

    @ParameterizedTest
    @MethodSource("validQueries")
    void mapsValidRequestsToIndependentCriteriaWithoutChangingResults(JournalListQuery query) {
        var result = new PageResult<JournalListView>(List.of(), 0, 0, JournalListQuery.PAGE_SIZE);
        when(handler.execute(any(JournalSearchCriteria.class))).thenReturn(result);

        assertThat(service.getJournals(query)).isSameAs(result);

        var captured = ArgumentCaptor.forClass(JournalSearchCriteria.class);
        verify(handler).execute(captured.capture());
        assertThat(captured.getValue()).isEqualTo(criteriaMapper.toCriteria(query));
        verifyNoMoreInteractions(handler);
    }

    @Test
    void searchRetainsApplicationAuthorization() throws Exception {
        assertThat(JournalService.class.getMethod("getJournals", JournalListQuery.class)
                .isAnnotationPresent(CanJournalView.class)).isTrue();
        assertThat(JournalService.class.getMethod("getSearchCounts", JournalListQuery.class)
                .isAnnotationPresent(CanJournalView.class)).isTrue();
    }

    @Test
    void returnsDefaultJournalListQuery() {
        JournalListQuery query = service.getDefaultJournalListQuery();

        assertThat(query.getPage()).isZero();
        assertThat(query.getSize()).isEqualTo(10);
        assertThat(query.getFirst()).isZero();
        assertThat(query.getSortBy()).isEqualTo(JournalListQuery.SortBy.SORT_BY_TITLE_ASC);
    }

    @Test
    void metadataUsesTheExistingJournalAuthorizationAndDelegatesToTheIndex() throws Exception {
        var metadata = new JournalSearchMetadata(List.of(20, 70), null, null, java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of());
        when(handler.getSearchMetadata()).thenReturn(metadata);
        assertThat(service.getSearchMetadata()).isSameAs(metadata);
        verify(handler).getSearchMetadata();
        assertThat(JournalService.class.getMethod("getSearchMetadata").isAnnotationPresent(CanJournalView.class)).isTrue();
    }

    static Stream<Arguments> invalidQueries() {
        return Stream.of(
                invalid("page", query -> query.setPage(-1)),
                invalid("size", query -> query.setSize(0)),
                invalid("pageSizeValid", query -> query.setSize(15)),
                invalid("countryCodes", query -> query.setCountryCodes(List.of("PL", " "))),
                invalid("countryCodes", query -> query.setCountryCodes(Collections.singletonList(null))),
                invalid("languageCodes", query -> query.setLanguageCodes(List.of(""))),
                invalid("languageCodes", query -> query.setLanguageCodes(Collections.singletonList(null))),
                invalid("repositoryIdsValid", query -> query.setRepositoryIds(List.of(BigDecimal.ZERO))),
                invalid("repositoryIdsValid", query -> query.setRepositoryIds(List.of(new BigDecimal("-1")))),
                invalid("repositoryIdsValid", query -> query.setRepositoryIds(List.of(new BigDecimal("10.5")))),
                invalid("repositoryIdsValid", query -> query.setRepositoryIds(List.of(new BigDecimal("9223372036854775808")))),
                invalid("repositoryIdsValid", query -> query.setRepositoryIds(Collections.singletonList(null))),
                invalid("licenseCodes", query -> query.setLicenseCodes(Collections.singletonList(null))),
                invalid("accessModels", query -> query.setAccessModels(Collections.singletonList(null))),
                invalid("frequencyCodes", query -> query.setFrequencyCodes(Collections.singletonList(null))),
                invalid("feeCriteriaValid", query -> query.setOpenAccessFeeMaxPln(new BigDecimal("-0.01"))),
                invalid("feeCriteriaValid", query -> query.setPublicationFeeMaxPln(new BigDecimal("-1"))),
                invalid("feeCriteriaValid", query -> query.setRapidReviewFeeMaxPln(new BigDecimal("-1"))),
                invalid("feeCriteriaValid", query -> query.setSubmissionFeeMaxPln(new BigDecimal("-1"))),
                invalid("feeCriteriaValid", query -> {
                    query.setOpenAccessFeeFreeOnly(true);
                    query.setOpenAccessFeeMaxPln(BigDecimal.ZERO);
                }),
                invalid("feeCriteriaValid", query -> {
                    query.setPublicationFeeFreeOnly(true);
                    query.setPublicationFeeMaxPln(BigDecimal.ONE);
                }),
                invalid("feeCriteriaValid", query -> {
                    query.setRapidReviewFeeFreeOnly(true);
                    query.setRapidReviewFeeMaxPln(BigDecimal.ONE);
                }),
                invalid("feeCriteriaValid", query -> {
                    query.setSubmissionFeeFreeOnly(true);
                    query.setSubmissionFeeMaxPln(BigDecimal.ONE);
                }),
                invalid("firstIssueYearRangeValid", query -> query.setFirstIssueYearFrom(new BigDecimal("1960.5"))),
                invalid("firstIssueYearRangeValid", query -> query.setFirstIssueYearTo(new BigDecimal("2147483648"))),
                invalid("firstIssueYearRangeValid", query -> {
                    query.setFirstIssueYearFrom(new BigDecimal("2024"));
                    query.setFirstIssueYearTo(new BigDecimal("1960"));
                }),
                invalid("mniswRangeValid", query -> {
                    query.setMniswOnly(true);
                    query.setMniswPointsFrom(new BigDecimal("20.5"));
                }),
                invalid("mniswRangeValid", query -> {
                    query.setMniswOnly(true);
                    query.setMniswPointsTo(new BigDecimal("-1"));
                }),
                invalid("mniswRangeValid", query -> {
                    query.setMniswOnly(true);
                    query.setMniswPointsFrom(new BigDecimal("140"));
                    query.setMniswPointsTo(new BigDecimal("70"));
                }),
                invalid("processingTimeLimitsValid", query -> query.setReviewDaysTo(new BigDecimal("1.5"))),
                invalid("processingTimeLimitsValid", query -> query.setInitialAssessmentDaysTo(new BigDecimal("-1"))),
                invalid("processingTimeLimitsValid", query -> query.setPublicationDaysTo(new BigDecimal("2147483648"))),
                invalid("percentageRangesValid", query -> query.setRejectionRateFrom(new BigDecimal("-0.01"))),
                invalid("percentageRangesValid", query -> query.setRejectionRateTo(new BigDecimal("100.01"))),
                invalid("percentageRangesValid", query -> {
                    query.setAuthorForeignAffiliationPercentageFrom(new BigDecimal("30"));
                    query.setAuthorForeignAffiliationPercentageTo(new BigDecimal("20"));
                }),
                invalid("percentageRangesValid", query -> query.setReviewerForeignAffiliationPercentageTo(new BigDecimal("101"))),
                invalid("icvRangeValid", query -> query.setIcvFrom(new BigDecimal("-0.01"))),
                invalid("icvRangeValid", query -> query.setIcvTo(new BigDecimal("80.251"))),
                invalid("icvRangeValid", query -> {
                    query.setIcvFrom(new BigDecimal("95.75"));
                    query.setIcvTo(new BigDecimal("80.25"));
                }),
                invalid("icvRangeValid", query -> {
                    query.setRatingStatus(RatingStatus.NOT_INDEXED);
                    query.setIcvFrom(BigDecimal.ZERO);
                }),
                invalid("icvRangeValid", query -> {
                    query.setRatingStatus(RatingStatus.NOT_EVALUATED);
                    query.setIcvTo(BigDecimal.ONE);
                }));
    }

    @Test
    void rejectsAnUnavailableViewBeforeQueryingTheIndex() {
        var query = new JournalListQuery();
        query.setSearchView(SearchView.ADVANCED);
        when(operatingProfiles.requireSearchView(SearchView.ADVANCED)).thenThrow(new AccessDeniedException("Unavailable"));
        assertThatThrownBy(() -> service.getJournals(query)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getSearchCounts(query)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(handler);
    }

    @ParameterizedTest
    @MethodSource("advancedCriteria")
    void rejectsAdvancedCriteriaInBasicEvenWhenSubmittedDirectly(Consumer<JournalListQuery> customize) {
        var query = new JournalListQuery();
        query.setSearchView(SearchView.BASIC);
        customize.accept(query);
        when(operatingProfiles.requireSearchView(SearchView.BASIC)).thenReturn(SearchView.BASIC);
        assertThatIllegalArgumentException().isThrownBy(() -> service.getJournals(query))
                .withMessageContaining("Advanced criteria");
        assertThatIllegalArgumentException().isThrownBy(() -> service.getSearchCounts(query))
                .withMessageContaining("Advanced criteria");
        verifyNoInteractions(handler);
    }

    @Test
    void keepsLegacyBasicCriteriaAndDecimalIcvOnTheSharedEngine() {
        var query = new JournalListQuery();
        query.setCountryCode("PL");
        query.setLanguageCode("en");
        query.setJournalTitle("Journal");
        query.setPublisher("Publisher");
        query.setDisciplineCodes(new String[]{"42"});
        query.setRatingStatus(RatingStatus.INDEXED);
        query.setIcvFrom(new BigDecimal("80.25"));
        when(operatingProfiles.requireSearchView(null)).thenReturn(SearchView.BASIC);
        service.getJournals(query);
        verify(handler).execute(criteriaMapper.toCriteria(query));
    }

    @Test
    void acceptsSharedMainFormCriteriaInBasicSearchAndCounts() {
        when(operatingProfiles.requireSearchView(SearchView.BASIC)).thenReturn(SearchView.BASIC);
        when(mniswDisciplines.getByCode("2021")).thenReturn(new DictionaryMniswDiscipline());
        var counts = java.util.Map.of("2021", 2L);
        when(handler.getSearchCounts(any())).thenReturn(new com.osatum.wbn.application.journal.view.JournalSearchCounts(
                counts, counts, counts, counts, counts, counts, counts, counts, counts, counts));
        var query = new JournalListQuery();
        query.setSearchView(SearchView.BASIC);
        query.setCountryCodes(List.of("pl", "de"));
        query.setLanguageCodes(List.of("pl", "en"));
        query.setMniswOnly(true);
        query.setMniswDisciplineCodes(List.of("2021"));
        query.setMniswPointsFrom(new BigDecimal("40"));
        query.setMniswPointsTo(new BigDecimal("140"));
        service.getJournals(query);
        assertThat(service.getSearchCounts(query).mniswDisciplineCounts()).isEqualTo(counts);
        var criteria = criteriaMapper.toCriteria(query);
        verify(handler).execute(criteria);
        verify(handler).getSearchCounts(criteria);
        assertThat(criteria.countryCodes()).containsExactly("pl", "de");
        assertThat(criteria.languageCodes()).containsExactly("pl", "en");
        assertThat(criteria.mniswDisciplineCodes()).containsExactly("2021");
        assertThat(criteria.mniswOnly()).isTrue();
        assertThat(criteria.mniswPointsFrom()).isEqualByComparingTo("40");
        assertThat(criteria.mniswPointsTo()).isEqualByComparingTo("140");
    }

    @Test
    void basicMetadataKeepsIcvAndBasicDictionariesButNotAdvancedFilters() {
        when(operatingProfiles.requireSearchView(SearchView.BASIC)).thenReturn(SearchView.BASIC);
        var counts = java.util.Map.of("pl", 2L);
        var metadata = new JournalSearchMetadata(List.of(20), BigDecimal.ZERO, BigDecimal.TEN,
                counts, counts, counts, counts, counts, counts, counts, counts, counts, counts);
        when(handler.getSearchMetadata()).thenReturn(metadata);
        var basic = service.getSearchMetadata(SearchView.BASIC);
        assertThat(basic.icvMax()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(basic.countryCounts()).isEqualTo(counts);
        assertThat(basic.mniswPointClasses()).containsExactly(20);
        assertThat(basic.mniswDisciplineCounts()).isEqualTo(counts);
        assertThat(basic.repositoryCounts()).isEmpty();
        assertThat(basic.reviewModelCounts()).isEmpty();
        when(operatingProfiles.requireSearchView(SearchView.ADVANCED)).thenThrow(new AccessDeniedException("Unavailable"));
        clearInvocations(handler);
        assertThatThrownBy(() -> service.getSearchMetadata(SearchView.ADVANCED)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(handler);
    }

    static Stream<Consumer<JournalListQuery>> advancedCriteria() {
        return Stream.of(
                query -> query.setReviewDaysFrom(BigDecimal.ZERO),
                query -> query.setInitialAssessmentDaysFrom(BigDecimal.ZERO),
                query -> query.setPublicationDaysFrom(BigDecimal.ZERO),
                query -> query.setSize(25), query -> query.setSize(50), query -> query.setSize(100),
                query -> query.setDoiOnly(true), query -> query.setEditorialSystemOnly(true),
                query -> query.setNewsletterOnly(true), query -> query.setSocialMediaOnly(true),
                query -> query.setAntiPlagiarismOnly(true), query -> query.setAopOnly(true),
                query -> query.setOtherFeesFreeOnly(true), query -> query.setOpenAccessFeeFreeOnly(true),
                query -> query.setPublicationFeeFreeOnly(true), query -> query.setRapidReviewFeeFreeOnly(true),
                query -> query.setSubmissionFeeFreeOnly(true),
                query -> query.setPublicationFeeMaxPln(BigDecimal.ZERO),
                query -> query.setOpenAccessFeeMaxPln(BigDecimal.ZERO),
                query -> query.setRapidReviewFeeMaxPln(BigDecimal.ZERO),
                query -> query.setSubmissionFeeMaxPln(BigDecimal.ZERO),
                query -> query.setRepositoryIds(List.of(BigDecimal.ONE)),
                query -> query.setLicenseCodes(List.of(ArticlePublishedLicense.CC_BY)),
                query -> query.setAccessModels(List.of(PaidArticleType.FREE_ARTICLES)),
                query -> query.setFrequencyCodes(List.of(JournalFrequency.q)),
                query -> query.setReviewModels(List.of(com.osatum.wbn.domain.model.journal.JournalReviewModel.DOUBLE_BLIND_REVIEW)),
                query -> query.setFirstIssueYearFrom(BigDecimal.ZERO),
                query -> query.setFirstIssueYearTo(BigDecimal.ZERO),
                query -> query.setReviewDaysTo(BigDecimal.ZERO),
                query -> query.setInitialAssessmentDaysTo(BigDecimal.ZERO),
                query -> query.setPublicationDaysTo(BigDecimal.ZERO),
                query -> query.setRejectionRateFrom(BigDecimal.ZERO),
                query -> query.setRejectionRateTo(BigDecimal.ZERO),
                query -> query.setAuthorForeignAffiliationPercentageFrom(BigDecimal.ZERO),
                query -> query.setAuthorForeignAffiliationPercentageTo(BigDecimal.ZERO),
                query -> query.setReviewerForeignAffiliationPercentageFrom(BigDecimal.ZERO),
                query -> query.setReviewerForeignAffiliationPercentageTo(BigDecimal.ZERO),
                query -> query.setEditor("Publisher"),
                query -> query.setSortBy(JournalListQuery.SortBy.SORT_BY_RATING_MNISW_ASC),
                query -> query.setSortBy(JournalListQuery.SortBy.SORT_BY_PUBLICATION_DAYS_ASC));
    }

    static Stream<JournalListQuery> validQueries() {
        var zeroBounds = new JournalListQuery();
        zeroBounds.setIcvFrom(BigDecimal.ZERO);
        zeroBounds.setIcvTo(BigDecimal.ZERO);
        zeroBounds.setPublicationFeeMaxPln(BigDecimal.ZERO);
        zeroBounds.setReviewDaysTo(BigDecimal.ZERO);
        zeroBounds.setInitialAssessmentDaysTo(BigDecimal.ZERO);
        zeroBounds.setPublicationDaysTo(BigDecimal.ZERO);
        zeroBounds.setRejectionRateFrom(BigDecimal.ZERO);
        zeroBounds.setRejectionRateTo(new BigDecimal("100"));

        var partialBounds = new JournalListQuery();
        partialBounds.setIcvFrom(new BigDecimal("80.250"));
        partialBounds.setAuthorForeignAffiliationPercentageTo(new BigDecimal("33.33333"));
        partialBounds.setReviewerForeignAffiliationPercentageFrom(new BigDecimal("20.25"));
        partialBounds.setFirstIssueYearTo(new BigDecimal("2024"));
        partialBounds.setMniswOnly(true);
        partialBounds.setMniswPointsFrom(new BigDecimal("20"));
        partialBounds.setSubmissionFeeMaxPln(new BigDecimal("123.4567"));

        var maximumBounds = new JournalListQuery();
        maximumBounds.setPage(Integer.MAX_VALUE);
        maximumBounds.setReviewDaysTo(BigDecimal.valueOf(Integer.MAX_VALUE));
        maximumBounds.setRepositoryIds(List.of(BigDecimal.valueOf(Long.MAX_VALUE)));
        maximumBounds.setLicenseCodes(List.of(ArticlePublishedLicense.CC_BY));
        maximumBounds.setAccessModels(List.of(PaidArticleType.FREE_ARTICLES));
        maximumBounds.setFrequencyCodes(List.of(JournalFrequency.q));

        var legacy = new JournalListQuery();
        legacy.setCountryCode("PL");
        legacy.setLanguageCode("en");
        legacy.setEditor("Publisher");
        legacy.setFirst(100);
        legacy.setSortBy(null);
        legacy.setMniswPointsFrom(new BigDecimal("100"));
        legacy.setMniswPointsTo(new BigDecimal("20"));

        var emptyLists = new JournalListQuery();
        emptyLists.setCountryCodes(List.of());
        emptyLists.setLanguageCodes(List.of());
        emptyLists.setRepositoryIds(List.of());
        emptyLists.setLicenseCodes(List.of());
        emptyLists.setAccessModels(List.of());
        emptyLists.setFrequencyCodes(List.of());

        return Stream.concat(Stream.of(new JournalListQuery(), zeroBounds, partialBounds, maximumBounds, legacy, emptyLists),
                Stream.of(RatingStatus.NOT_INDEXED, RatingStatus.NOT_EVALUATED).map(status -> {
                    var query = new JournalListQuery();
                    query.setRatingStatus(status);
                    return query;
                }));
    }

    private static Arguments invalid(String field, Consumer<JournalListQuery> customize) {
        var query = new JournalListQuery();
        customize.accept(query);
        return Arguments.of(query, field);
    }
}
