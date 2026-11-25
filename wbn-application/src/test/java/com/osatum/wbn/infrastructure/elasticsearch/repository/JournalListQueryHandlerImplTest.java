package com.osatum.wbn.infrastructure.elasticsearch.repository;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.DoubleTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.application.journal.assembler.JournalSearchCriteriaMapper;
import org.mapstruct.factory.Mappers;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.JournalSearchResponseAssembler;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JournalListQueryHandlerImplTest {

    @Test
    void publishedHistoricalYearControlsSurveyIcvAndMetadataWithoutChangingTheDefault() {
        var configuration = spy(new ConfigurationService());
        doReturn(2025).when(configuration).getCurrentRatingYear();
        doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        var configuredHandler = new JournalListQueryHandlerImpl(mock(JournalSearchResponseAssembler.class), operations,
                configuration, feeSupport, new JournalSearchPageLoader(operations));
        var hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(NativeQuery.class), eq(JournalDocument.class))).thenReturn(hits);
        var request = new JournalListQuery();
        request.setSurveyYear(2024);
        request.setRatingStatus(RatingStatus.INDEXED);

        configuredHandler.execute(criteriaMapper.toCriteria(request));

        var captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captured.capture(), eq(JournalDocument.class));
        assertQualified(captured.getValue().getQuery().bool().filter().getFirst(), 2024, 2024);
        clearInvocations(operations);
        doReturn(metadataHits(70)).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
        configuredHandler.getSearchMetadata(2024);
        verify(operations).search(captured.capture(), eq(JournalDocument.class));
        assertBaseQualified(captured.getValue().getQuery(), 2024);
        assertThat(captured.getValue().getAggregations().get("icv").aggregations().get("current").filter().toString())
                .contains("2024").doesNotContain("2025");
        assertThat(configuration.getCurrentRatingYear()).isEqualTo(2025);
    }

    @Test
    void rejectsUnpublishedYearBeforeSearchingTheIndex() {
        var request = new JournalListQuery();
        request.setSurveyYear(2026);
        assertThatIllegalArgumentException().isThrownBy(() -> handler.execute(criteriaMapper.toCriteria(request)));
        assertThatIllegalArgumentException().isThrownBy(() -> handler.getSearchMetadata(2026));
        assertThatIllegalArgumentException().isThrownBy(() -> handler.getSearchCounts(criteriaMapper.toCriteria(request)));
        verifyNoInteractions(operations);
    }
    private final JournalSearchCriteriaMapper criteriaMapper = Mappers.getMapper(JournalSearchCriteriaMapper.class);
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final JournalFeeQuerySupport feeSupport = new JournalFeeQuerySupport(
            mock(com.osatum.wbn.application.journal.service.ExchangeRateService.class),
            new com.osatum.wbn.domain.service.journal.JournalFeePolicy(new com.osatum.wbn.domain.service.journal.AuthorFeeSelectionPolicy()), operations);
    private final JournalListQueryHandlerImpl handler = new JournalListQueryHandlerImpl(
            mock(JournalSearchResponseAssembler.class), operations, new ConfigurationService(), feeSupport, new JournalSearchPageLoader(operations));

    @Test
    void absentStatusDoesNotSilentlyRestrictTheSearchToIndexedJournals() {
        var qualification = search(new JournalListQuery()).bool().filter().getFirst();

        assertThat(qualification.bool().filter()).hasSize(2);
        assertThat(qualification.toString()).contains("ACTIVE", "surveyYear", "2024")
                .doesNotContain("ratings");
    }

    @Test
    void explicitIndexedStatusRequiresTheCurrentIcvRating() {
        var request = new JournalListQuery();
        request.setRatingStatus(RatingStatus.INDEXED);

        assertQualified(search(request).bool().filter().getFirst(), 2024, 2024);
    }

    @Test
    void combinesCountryAndLanguageFiltersWithOrWithinEachList() {
        JournalListQuery request = new JournalListQuery();
        request.setCountryCodes(List.of("PL", " DE ", "pl"));
        request.setLanguageCodes(List.of("en", "PL", " en "));

        var filters = search(request).bool().filter();

        var country = filters.stream().filter(Query::isTerms).findFirst().orElseThrow();
        assertThat(country.terms().field()).isEqualTo("countryCode");
        assertThat(termValues(country)).containsExactly("pl", "de");
        var language = filters.stream().filter(Query::isNested).findFirst().orElseThrow().nested();
        assertThat(language.path()).isEqualTo("languages");
        assertThat(language.query().terms().field()).isEqualTo("languages.code");
        assertThat(termValues(language.query())).containsExactly("en", "pl");
    }

    @Test
    void supportsLegacySingleCountryAndLanguage() {
        JournalListQuery request = new JournalListQuery();
        request.setCountryCode(" PL ");
        request.setLanguageCode("EN");

        var filters = search(request).bool().filter();

        assertThat(termValues(filters.stream().filter(Query::isTerms).findFirst().orElseThrow())).containsExactly("pl");
        var language = filters.stream().filter(Query::isNested).findFirst().orElseThrow().nested().query();
        assertThat(termValues(language)).containsExactly("en");
    }

    @Test
    void emptyListsRemoveBothLegacyDictionaryRestrictions() {
        JournalListQuery request = new JournalListQuery();
        request.setCountryCode("PL");
        request.setLanguageCode("en");
        request.setCountryCodes(List.of());
        request.setLanguageCodes(List.of());

        assertThat(search(request).bool().filter()).noneMatch(query -> query.isTerms() || query.isNested());
    }

    @Test
    void explicitListsOverrideLegacyValuesIncludingAnEmptySelection() {
        JournalListQuery request = new JournalListQuery();
        request.setCountryCode("PL");
        request.setLanguageCode("en");
        request.setCountryCodes(List.of("DE"));
        request.setLanguageCodes(List.of());

        var filters = search(request).bool().filter();

        assertThat(termValues(filters.stream().filter(Query::isTerms).findFirst().orElseThrow())).containsExactly("de");
        assertThat(filters).noneMatch(Query::isNested);
    }

    @Test
    void usesCurrentRatingYearForBothSurveyAndIcvFilters() {
        var configuration = spy(new ConfigurationService());
        when(configuration.getCurrentRatingYear()).thenReturn(2025);
        var configuredHandler = new JournalListQueryHandlerImpl(mock(JournalSearchResponseAssembler.class), operations, configuration, feeSupport, new JournalSearchPageLoader(operations));
        var hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(NativeQuery.class), eq(JournalDocument.class))).thenReturn(hits);
        var request = new JournalListQuery();
        request.setSize(10);
        request.setIcvFrom(BigDecimal.ZERO);
        configuredHandler.execute(criteriaMapper.toCriteria(request));
        var captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captured.capture(), eq(JournalDocument.class));
        var filters = captured.getValue().getQuery().bool().filter();
        assertBaseQualified(filters.getFirst(), 2025);
        assertThat(filters.stream().filter(Query::isNested).findFirst().orElseThrow().nested().query().toString())
                .contains("ratings.year", "2025").doesNotContain("2024");
    }

    @Test
    void combinesIndependentTimeAndPercentageRangesBeforePagination() {
        var request = new JournalListQuery();
        request.setReviewDaysTo(BigDecimal.ZERO);
        request.setInitialAssessmentDaysTo(BigDecimal.valueOf(5));
        request.setPublicationDaysTo(BigDecimal.valueOf(60));
        request.setRejectionRateFrom(new BigDecimal("33.33333"));
        request.setRejectionRateTo(BigDecimal.valueOf(50));
        request.setAuthorForeignAffiliationPercentageFrom(new BigDecimal("20.25"));
        request.setReviewerForeignAffiliationPercentageTo(BigDecimal.valueOf(25));

        var filters = search(request).bool().filter();
        assertRange(filters, "reviewDays", null, "0");
        assertRange(filters, "initialAssessmentDays", null, "5");
        assertRange(filters, "publicationDays", null, "60");
        assertRange(filters, "rejectionRate", "33.33333", "50");
        assertRange(filters, "authorForeignAffiliationPercentage", "20.25", null);
        assertRange(filters, "reviewerForeignAffiliationPercentage", null, "25");
        assertBaseQualified(filters.getFirst(), 2024);
    }

    @Test
    void appliesInclusiveProcessingTimeRangesAndIndependentPartialBounds() {
        var request = new JournalListQuery();
        request.setReviewDaysFrom(BigDecimal.ZERO);
        request.setReviewDaysTo(BigDecimal.ZERO);
        request.setInitialAssessmentDaysFrom(BigDecimal.valueOf(2));
        request.setInitialAssessmentDaysTo(BigDecimal.valueOf(5));
        request.setPublicationDaysFrom(BigDecimal.valueOf(30));
        request.setPublicationDaysTo(BigDecimal.valueOf(60));
        var filters = search(request).bool().filter();
        assertRange(filters, "reviewDays", "0", "0");
        assertRange(filters, "initialAssessmentDays", "2", "5");
        assertRange(filters, "publicationDays", "30", "60");

        request.setReviewDaysTo(null);
        request.setInitialAssessmentDaysTo(null);
        request.setPublicationDaysTo(null);
        clearInvocations(operations);
        var minimumOnly = search(request).bool().filter();
        assertRange(minimumOnly, "reviewDays", "0", null);
        assertRange(minimumOnly, "initialAssessmentDays", "2", null);
        assertRange(minimumOnly, "publicationDays", "30", null);
    }

    @Test
    void exposesOnlySearchAndMetadataOperations() {
        assertThat(JournalListQueryHandlerImpl.class.getDeclaredMethods())
                .filteredOn(method -> Modifier.isPublic(method.getModifiers()))
                .extracting(method -> method.getName())
                .containsExactlyInAnyOrder("execute", "getSearchMetadata", "getSearchMetadata", "getSearchCounts");
    }

    @Test
    void mniswCheckboxRequiresAnEntryFromTheCurrentListWithoutAnIcvStatusCondition() {
        var request = new JournalListQuery();
        request.setMniswOnly(true);

        var filter = search(request).bool().filter().stream().filter(Query::isNested).findFirst().orElseThrow().nested();

        assertThat(filter.path()).isEqualTo("ratings");
        assertThat(filter.query().toString()).contains("mnw", "ratings.year", "2024")
                .doesNotContain("ratings.ratingStatus", "ratings.valueNum");
    }

    @Test
    void inactiveMniswBoundsDoNotRestrictTheSearchOrRequestMetadata() {
        var request = new JournalListQuery();
        request.setMniswPointsFrom(BigDecimal.valueOf(50));
        request.setMniswDisciplineCodes(List.of("101"));
        assertThat(search(request).bool().filter()).noneMatch(Query::isNested);
    }

    @Test
    void combinesMniswDisciplineOrAndPointsInsideTheSameCurrentRating() {
        var request = new JournalListQuery();
        request.setMniswOnly(true);
        request.setMniswPointsFrom(BigDecimal.valueOf(70));
        request.setMniswPointsTo(BigDecimal.valueOf(70));
        request.setMniswDisciplineCodes(List.of("101", "2021"));
        var resultHits = mock(SearchHits.class);
        when(resultHits.getSearchHits()).thenReturn(List.of());
        doReturn(metadataHits(70), resultHits).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));

        handler.execute(criteriaMapper.toCriteria(request));

        var captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations, times(2)).search(captured.capture(), eq(JournalDocument.class));
        var rating = captured.getAllValues().getLast().getQuery().bool().filter().stream()
                .filter(Query::isNested).filter(query -> query.toString().contains("ratings.disciplines"))
                .findFirst().orElseThrow().nested();
        assertThat(rating.path()).isEqualTo("ratings");
        assertThat(rating.query().toString()).contains("ratings.year", "2024", "mnw", "ratings.valueNum");
        var disciplines = rating.query().bool().must().stream().filter(Query::isNested).findFirst().orElseThrow().nested();
        assertThat(disciplines.path()).isEqualTo("ratings.disciplines");
        assertThat(termValues(disciplines.query())).containsExactly("101", "2021");
    }

    @Test
    void metadataUsesTheSurveyYearAndCurrentMniswListAndReturnsSortedRealClasses() {
        doReturn(metadataHits(140, 20, 70, 20)).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));

        assertThat(handler.getSearchMetadata().mniswPointClasses()).containsExactly(20, 70, 140);

        var captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captured.capture(), eq(JournalDocument.class));
        var query = captured.getValue();
        assertThat(query.getMaxResults()).isZero();
        assertThat(query.getQuery().toString()).contains("surveyYear", "2024", "ACTIVE")
                .doesNotContain("ratings.ratingStatus");
        var aggregation = query.getAggregations().get("mnisw");
        assertThat(aggregation.nested().path()).isEqualTo("ratings");
        assertThat(aggregation.aggregations().get("current").filter().toString()).contains("mnw", "2024");
        assertThat(aggregation.aggregations().get("current").aggregations().get("classes").terms().field()).isEqualTo("ratings.valueNum");
    }

    @ParameterizedTest
    @CsvSource({"20,140", "70,", ",140", "70,70"})
    void usesOneNestedMniswRatingForInclusiveBoundsAndValidatesRealClasses(String from, String to) {
        var request = new JournalListQuery();
        request.setSize(10);
        request.setMniswOnly(true);
        request.setMniswPointsFrom(from == null ? null : new BigDecimal(from));
        request.setMniswPointsTo(to == null ? null : new BigDecimal(to));
        var resultHits = mock(SearchHits.class);
        when(resultHits.getSearchHits()).thenReturn(List.of());
        doReturn(metadataHits(20, 70, 140), resultHits).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));

        handler.execute(criteriaMapper.toCriteria(request));

        var captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations, times(2)).search(captured.capture(), eq(JournalDocument.class));
        var conditions = captured.getAllValues().getLast().getQuery().bool().filter().stream()
                .filter(Query::isNested).findFirst().orElseThrow().nested().query().bool().must();
        assertThat(conditions.toString()).contains("mnw", "ratings.year", "2024");
        assertRange(conditions, "ratings.valueNum", from, to);
    }

    @Test
    void rejectsAPointThatIsNotAClassInTheCurrentListBeforeSearchingResults() {
        var request = new JournalListQuery();
        request.setMniswOnly(true);
        request.setMniswPointsFrom(BigDecimal.valueOf(50));
        doReturn(metadataHits(20, 70, 140)).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));

        assertThatIllegalArgumentException().isThrownBy(() -> handler.execute(criteriaMapper.toCriteria(request))).withMessageContaining("point classes");
        verify(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
    }

    @Test
    void doesNotReplaceMissingMetadataWithInventedClasses() {
        var hits = mock(SearchHits.class);
        when(operations.search(any(NativeQuery.class), eq(JournalDocument.class))).thenReturn(hits);
        assertThatIllegalStateException().isThrownBy(handler::getSearchMetadata);
    }

    @Test
    void rejectsFractionalSourceClassesInsteadOfRounding() {
        doReturn(metadataHits(20.5)).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
        assertThatIllegalStateException().isThrownBy(handler::getSearchMetadata).withMessageContaining("Invalid MNiSW point class");
    }

    @Test
    void emptyMniswListReturnsNoInventedPointClasses() {
        doReturn(metadataHits()).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
        assertThat(handler.getSearchMetadata().mniswPointClasses()).isEmpty();
    }

    @Test
    void supportsIntegerTermsWithoutChangingPointClasses() {
        var classes = Aggregate.of(value -> value.lterms(terms -> terms.buckets(bucket -> bucket.array(List.of(
                LongTermsBucket.of(point -> point.key(70L).docCount(1)),
                LongTermsBucket.of(point -> point.key(20L).docCount(1))))).sumOtherDocCount(0L)));
        doReturn(metadataHitsForClasses(classes)).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
        assertThat(handler.getSearchMetadata().mniswPointClasses()).containsExactly(20, 70);
    }

    @Test
    void truncatedClassAggregationIsAnErrorInsteadOfAPartialDictionary() {
        var classes = Aggregate.of(value -> value.dterms(terms -> terms.buckets(bucket -> bucket.array(List.of()))
                .sumOtherDocCount(1L)));
        doReturn(metadataHitsForClasses(classes)).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
        assertThatIllegalStateException().isThrownBy(handler::getSearchMetadata).withMessageContaining("incomplete");
    }

    @Test
    void metadataKeepsMniswYearIndependentOfSurveyYear() {
        var configuration = spy(new ConfigurationService());
        when(configuration.getCurrentRatingYear()).thenReturn(2025);
        when(configuration.getCurrentMniswYear()).thenReturn(2024);
        var configuredHandler = new JournalListQueryHandlerImpl(mock(JournalSearchResponseAssembler.class), operations, configuration, feeSupport, new JournalSearchPageLoader(operations));
        doReturn(metadataHits(70)).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));

        configuredHandler.getSearchMetadata();

        var captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captured.capture(), eq(JournalDocument.class));
        assertThat(captured.getValue().getQuery().toString()).contains("surveyYear", "2025", "ACTIVE")
                .doesNotContain("ratings.ratingStatus");
        assertThat(captured.getValue().getAggregations().get("icv").aggregations().get("current").filter().toString())
                .contains("2025").doesNotContain("2024");
        assertThat(captured.getValue().getAggregations().get("mnisw").aggregations().get("current").filter().toString())
                .contains("2024").doesNotContain("2025");
    }

    private SearchHits<JournalDocument> metadataHits(double... points) {
        var buckets = Arrays.stream(points).mapToObj(point -> DoubleTermsBucket.of(bucket -> bucket.key(point).docCount(1))).toList();
        var classes = Aggregate.of(value -> value.dterms(terms -> terms.buckets(bucket -> bucket.array(buckets))
                .sumOtherDocCount(0L).docCountErrorUpperBound(0L)));
        return metadataHitsForClasses(classes);
    }

    private SearchHits<JournalDocument> metadataHitsForClasses(Aggregate classes) {
        return metadataHitsForClasses(classes, Map.of());
    }

    private SearchHits<JournalDocument> metadataHitsForClasses(Aggregate classes, Map<String, Aggregate> overrides) {
        SearchHits<JournalDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        var emptyTerms = Aggregate.of(value -> value.sterms(terms -> terms.buckets(bucket -> bucket.array(List.of()))
                .sumOtherDocCount(0L).docCountErrorUpperBound(0L)));
        var nestedTerms = Aggregate.of(value -> value.nested(nested -> nested.docCount(0).aggregations("values", emptyTerms)));
        var current = Aggregate.of(value -> value.filter(filter -> filter.docCount(10).aggregations("classes", classes)
                .aggregations("disciplines", nestedTerms)));
        var aggregate = Aggregate.of(value -> value.nested(nested -> nested.docCount(10).aggregations("current", current)));
        var icv = Aggregate.of(value -> value.nested(nested -> nested.docCount(0).aggregations("current", selected -> selected
                .filter(filter -> filter.docCount(0).aggregations("range", range -> range
                        .stats(stats -> stats.count(0).sum(0.0)))))));
        var aggregates = new java.util.HashMap<>(Map.of("mnisw", aggregate, "icv", icv,
                "countries", emptyTerms, "languages", nestedTerms, "disciplines", nestedTerms));
        for (String name : List.of("repositories", "licenses", "accessModels", "frequencies", "reviewModels")) {
            aggregates.put(name, emptyTerms);
        }
        aggregates.put("features", Aggregate.of(value -> value.filters(filters -> filters
                .buckets(buckets -> buckets.keyed(Map.of())))));
        aggregates.putAll(overrides);
        doReturn(new ElasticsearchAggregations(aggregates)).when(hits).getAggregations();
        return hits;
    }

    @ParameterizedTest
    @CsvSource({"1,0", "0,1"})
    void rejectsTruncatedOrApproximateDictionaryCounts(long otherCount, long countError) {
        var terms = Aggregate.of(value -> value.sterms(values -> values.buckets(bucket -> bucket.array(List.of()))
                .sumOtherDocCount(otherCount).docCountErrorUpperBound(countError)));
        var classes = Aggregate.of(value -> value.dterms(values -> values.buckets(bucket -> bucket.array(List.of()))));
        doReturn(metadataHitsForClasses(classes, Map.of("countries", terms))).when(operations)
                .search(any(NativeQuery.class), eq(JournalDocument.class));
        assertThatIllegalStateException().isThrownBy(handler::getSearchMetadata).withMessageContaining("incomplete");
    }

    @Test
    void rejectsInvalidIcvMetadataInsteadOfInventingASliderRange() {
        var icv = Aggregate.of(value -> value.nested(nested -> nested.docCount(1).aggregations("current", selected -> selected
                .filter(filter -> filter.docCount(1).aggregations("range", range -> range
                        .stats(stats -> stats.count(1).sum(-1.0).min(-1.0).max(-1.0)))))));
        var classes = Aggregate.of(value -> value.dterms(values -> values.buckets(bucket -> bucket.array(List.of()))));
        doReturn(metadataHitsForClasses(classes, Map.of("icv", icv))).when(operations)
                .search(any(NativeQuery.class), eq(JournalDocument.class));
        assertThatIllegalStateException().isThrownBy(handler::getSearchMetadata).withMessageContaining("Invalid ICV");
    }

    @Test
    void explicitStatusReplacesOnlyIcvQualificationAndCannotDisableActiveStateOrSurveyYear() {
        var request = new JournalListQuery();
        request.setState(com.osatum.wbn.domain.model.EntityState.INACTIVE);
        request.setRatingStatus(RatingStatus.NOT_INDEXED);
        var query = search(request);
        assertThat(query.toString()).contains("ACTIVE", "surveyYear", "2024", "INACTIVE", "not_indexed")
                .doesNotContain("\"value\":\"indexed\"");
    }

    @Test
    void unevaluatedIncludesExplicitStatusOrMissingCurrentRatingButNotAnotherYearFallback() {
        var request = new JournalListQuery();
        request.setRatingStatus(RatingStatus.NOT_EVALUATED);
        var status = search(request).bool().filter().getFirst().bool().filter().getLast().bool();
        assertThat(status.minimumShouldMatch()).isEqualTo("1");
        assertThat(status.should()).hasSize(2);
        assertThat(status.should().getFirst().toString()).contains("not_evaluated", "2024", "icv");
        assertThat(status.should().getLast().bool().mustNot().getFirst().toString()).contains("2024", "icv")
                .doesNotContain("2025");
    }

    @ParameterizedTest
    @EnumSource(value = RatingStatus.class, names = {"NOT_INDEXED", "NOT_EVALUATED"})
    void rejectsIcvPointsForNonIndexedStatusesBeforeSearching(RatingStatus status) {
        var request = new JournalListQuery();
        request.setRatingStatus(status);
        request.setIcvFrom(BigDecimal.ZERO);
        assertThatIllegalArgumentException().isThrownBy(() -> handler.execute(criteriaMapper.toCriteria(request)));
        verifyNoInteractions(operations);
    }

    @Test
    void appliesIcvBoundsWithoutAnyStatusCheckbox() {
        var request = new JournalListQuery();
        request.setIcvFrom(new BigDecimal("80.25"));
        var conditions = search(request).bool().filter().stream().filter(Query::isNested)
                .findFirst().orElseThrow().nested().query().bool().must();
        assertRange(conditions, "ratings.valueNum", "80.25", null);
        assertThat(conditions.toString()).contains("indexed", "2024", "icv");
    }

    @Test
    void emptyQualifiedSetHasNoInventedIcvRangeOrDictionaryCounts() {
        doReturn(metadataHits()).when(operations).search(any(NativeQuery.class), eq(JournalDocument.class));
        var metadata = handler.getSearchMetadata();
        assertThat(metadata.icvMin()).isNull();
        assertThat(metadata.icvMax()).isNull();
        assertThat(metadata.countryCounts()).isEmpty();
        assertThat(metadata.languageCounts()).isEmpty();
        assertThat(metadata.disciplineCounts()).isEmpty();
    }

    private void assertBaseQualified(Query query, int surveyYear) {
        assertThat(query.bool().filter()).anySatisfy(condition -> {
            assertThat(condition.isTerm()).isTrue();
            assertThat(condition.term().field()).isEqualTo("state");
            assertThat(condition.term().value().stringValue()).isEqualTo("ACTIVE");
        }).anySatisfy(condition -> {
            assertThat(condition.isTerm()).isTrue();
            assertThat(condition.term().field()).isEqualTo("surveyYear");
            assertThat(condition.term().value().longValue()).isEqualTo(surveyYear);
        });
    }

    private void assertQualified(Query query, int surveyYear, int icvYear) {
        assertBaseQualified(query, surveyYear);
        assertThat(query.bool().filter()).anySatisfy(condition -> {
            assertThat(condition.isNested()).isTrue();
            var rating = condition.nested().query().bool().must();
            assertThat(rating.toString()).contains("ratings.ratingCode", "icv", "ratings.ratingStatus", "indexed");
            assertThat(rating).anySatisfy(value -> {
                assertThat(value.term().field()).isEqualTo("ratings.year");
                assertThat(value.term().value().longValue()).isEqualTo(icvYear);
            });
        });
    }

    private void assertRange(List<Query> filters, String field, String minimum, String maximum) {
        var range = filters.stream().filter(Query::isRange).map(filter -> filter.range().untyped())
                .filter(value -> field.equals(value.field())).findFirst().orElseThrow();
        assertThat(range.gt()).isNull();
        assertThat(range.lt()).isNull();
        assertThat(range.gte() == null ? null : range.gte().toString()).isEqualTo(minimum);
        assertThat(range.lte() == null ? null : range.lte().toString()).isEqualTo(maximum);
    }

    @ParameterizedTest
    @CsvSource({"80.25,95.75", "80.25,", ",95.75", "80.25,80.25", "0,0"})
    void buildsInclusiveDecimalIcvRangeForConfiguredYear(String from, String to) {
        SearchHits<JournalDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(NativeQuery.class), eq(JournalDocument.class))).thenReturn(hits);
        JournalListQuery request = new JournalListQuery();
        request.setSize(10);
        request.setRatingStatus(RatingStatus.INDEXED);
        request.setIcvFrom(from == null ? null : new BigDecimal(from));
        request.setIcvTo(to == null ? null : new BigDecimal(to));

        handler.execute(criteriaMapper.toCriteria(request));

        ArgumentCaptor<NativeQuery> captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captured.capture(), eq(JournalDocument.class));
        var conditions = captured.getValue().getQuery().bool().filter().stream().filter(Query::isNested)
                .findFirst().orElseThrow().nested().query().bool().must();
        assertThat(conditions).anySatisfy(condition -> {
            assertThat(condition.isTerm()).isTrue();
            assertThat(condition.term().field()).isEqualTo("ratings.year");
            assertThat(condition.term().value().longValue()).isEqualTo(2024);
        });
        var range = conditions.stream().filter(condition -> condition.isRange()).findFirst().orElseThrow().range().untyped();
        assertThat(range.field()).isEqualTo("ratings.valueNum");
        assertThat(range.gt()).isNull();
        assertThat(range.lt()).isNull();
        assertThat(range.gte() == null ? null : range.gte().toString()).isEqualTo(from);
        assertThat(range.lte() == null ? null : range.lte().toString()).isEqualTo(to);
    }

    @Test
    void combinesLiteralTitleFragmentsAndPublisherNamesWithAndInsteadOfRelevance() {
        JournalListQuery request = new JournalListQuery();
        request.setJournalTitle("Alpha*  Beta?");
        request.setPublisher("Some Press");
        request.setIssn("1234 - 567x");
        var query = search(request);
        assertThat(query.bool().should()).isEmpty();
        assertThat(query.toString()).doesNotContain("fuzziness");
        var titles = query.bool().filter().stream().filter(Query::isNested)
                .filter(value -> "titles".equals(value.nested().path())).findFirst().orElseThrow();
        assertThat(titles.nested().query().bool().filter()).extracting(value -> value.wildcard().value())
                .containsExactly("*Alpha\\**", "*Beta\\?*");
        var issns = query.bool().filter().stream().filter(Query::isNested)
                .filter(value -> "issns".equals(value.nested().path())).findFirst().orElseThrow();
        assertThat(issns.nested().query().wildcard().field()).isEqualTo("issns.content.search");
        assertThat(issns.nested().query().wildcard().value()).isEqualTo("*1234567x*");
    }

    private Query search(JournalListQuery request) {
        SearchHits<JournalDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(NativeQuery.class), eq(JournalDocument.class))).thenReturn(hits);
        request.setSize(10);
        handler.execute(criteriaMapper.toCriteria(request));
        ArgumentCaptor<NativeQuery> captured = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captured.capture(), eq(JournalDocument.class));
        return captured.getValue().getQuery();
    }

    @Test
    void selectedReviewModelsUseOrAndKeepTheSurveyAndOtherFilters() {
        var request = new JournalListQuery();
        request.setSurveyYear(2024);
        request.setReviewModels(List.of(
                com.osatum.wbn.domain.model.journal.JournalReviewModel.DOUBLE_BLIND_REVIEW,
                com.osatum.wbn.domain.model.journal.JournalReviewModel.OPEN_PEER_REVIEW,
                com.osatum.wbn.domain.model.journal.JournalReviewModel.DOUBLE_BLIND_REVIEW));
        request.setFrequencyCodes(List.of(com.osatum.wbn.domain.model.journal.JournalFrequency.q));

        var filters = search(request).bool().filter();

        assertBaseQualified(filters.getFirst(), 2024);
        assertThat(filters).filteredOn(value -> value.isTerms() && "reviewModels".equals(value.terms().field()))
                .singleElement().satisfies(value -> assertThat(termValues(value))
                        .containsExactly("DOUBLE_BLIND_REVIEW", "OPEN_PEER_REVIEW"));
        assertThat(filters).anySatisfy(value -> {
            assertThat(value.isTerms()).isTrue();
            assertThat(value.terms().field()).isEqualTo("frequencyCode");
            assertThat(termValues(value)).containsExactly("q");
        });
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {10, 25, 50, 100})
    void returnsTheRequestedSizeAndRecomputesTotalPages(int size) {
        SearchHits<JournalDocument> hits = mock(SearchHits.class);
        when(hits.getSearchHits()).thenReturn(List.of());
        when(hits.getTotalHits()).thenReturn(1199L);
        when(operations.search(any(NativeQuery.class), eq(JournalDocument.class))).thenReturn(hits);
        var request = new JournalListQuery();
        request.setSize(size);
        var result = handler.execute(criteriaMapper.toCriteria(request));
        assertThat(result.size()).isEqualTo(size);
        assertThat(result.totalElements()).isEqualTo(1199);
        assertThat(result.totalPages()).isEqualTo(Math.ceilDiv(1199, size));
    }

    private List<String> termValues(Query query) {
        return query.terms().terms().value().stream().map(FieldValue::stringValue).toList();
    }
}
