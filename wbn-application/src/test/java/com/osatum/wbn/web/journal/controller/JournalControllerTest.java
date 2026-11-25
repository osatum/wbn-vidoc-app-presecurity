package com.osatum.wbn.web.journal.controller;

import com.osatum.wbn.application.analytics.service.AnalyticsJournalService;
import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.application.journal.service.JournalService;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.application.journal.view.JournalSearchMetadata;
import com.osatum.wbn.application.journal.view.JournalView;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.domain.entity.RatingStatus;
import com.osatum.wbn.web.error.ApiExceptionHandler;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class JournalControllerTest {
    @Test
    void countsAcceptAppliedCriteriaAndRejectInvalidRanges() throws Exception {
        mvc.perform(post("/api/journal/search-journals/counts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"searchView\":\"ADVANCED\",\"surveyYear\":2024,\"countryCodes\":[\"pl\"],\"icvFrom\":10}"))
                .andExpect(status().isOk());
        var query = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getSearchCounts(query.capture());
        assertThat(query.getValue().getCountryCodes()).containsExactly("pl");
        assertThat(query.getValue().getSurveyYear()).isEqualTo(2024);
        clearInvocations(service);
        mvc.perform(post("/api/journal/search-journals/counts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"icvFrom\":100,\"icvTo\":10}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void returnsMissingQuestionnaireContextWithCatalogueDetailsAndPublishedAlternatives() throws Exception {
        var view = new JournalView();
        view.setId(123L);
        view.setSurveyYear(2025);
        view.setQuestionnaireAvailable(false);
        view.setAvailableSurveyYears(List.of(2024));
        when(service.getJournal(123L)).thenReturn(view);

        mvc.perform(get("/api/journal/journal/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id").value(123))
                .andExpect(jsonPath("surveyYear").value(2025))
                .andExpect(jsonPath("questionnaireAvailable").value(false))
                .andExpect(jsonPath("availableSurveyYears[0]").value(2024));
    }

    @Test
    void forwardsThePublishedYearToSearchMetadataAndDetails() throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"surveyYear\":2024}")).andExpect(status().isOk());
        var query = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(query.capture());
        assertThat(query.getValue().getSurveyYear()).isEqualTo(2024);
        mvc.perform(get("/api/journal/search-journals/metadata").param("surveyYear", "2024"))
                .andExpect(status().isOk());
        verify(service).getSearchMetadata(null, 2024);
        mvc.perform(get("/api/journal/journal/123").param("surveyYear", "2024")).andExpect(status().isOk());
        verify(service).getJournal(123L, 2024);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "2024.5", "2147483648"})
    void rejectsInvalidSurveyYearsBeforeCallingTheService(String year) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"surveyYear\":" + year + "}")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    private final JournalService service = mock(JournalService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ObjectProvider<JournalService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        when(service.getJournals(any())).thenReturn(new PageResult<>(List.of(), 0, 0, 10));
        mvc = standaloneSetup(new JournalController(provider, mock(AnalyticsJournalService.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"BASIC", "ADVANCED"})
    void acceptsAndForwardsTheExplicitSearchView(String view) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"searchView\":\"" + view + "\"}"))
                .andExpect(status().isOk());
        var captured = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(captured.capture());
        assertThat(captured.getValue().getSearchView().name()).isEqualTo(view);
        mvc.perform(get("/api/journal/search-journals/metadata").param("searchView", view)).andExpect(status().isOk());
        verify(service).getSearchMetadata(com.osatum.wbn.domain.model.institution.SearchView.valueOf(view));
    }

    @Test
    void rejectsProfileNamesUsedAsSearchViews() throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"searchView\":\"BOTH\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/journal/search-journals/metadata").param("searchView", "BOTH"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(JournalListQuery.SortBy.class)
    void acceptsEachOfTheEightSortDirections(JournalListQuery.SortBy sort) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortBy\":\"" + sort.name() + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<JournalListQuery> captured = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(captured.capture());
        assertThat(captured.getValue().getSortBy()).isEqualTo(sort);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"page\":0,\"size\":10}", "{\"page\":1001}", "{\"page\":2147483647}"})
    void acceptsZeroBasedPagesAndAlwaysDefaultsToTenResults(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        ArgumentCaptor<JournalListQuery> captured = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(captured.capture());
        assertThat(captured.getValue().getSize()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"page\":-1}", "{\"size\":0}", "{\"size\":1}", "{\"size\":15}", "{\"size\":101}", "{\"size\":-10}", "{\"page\":2147483648}",
            "{\"page\":1.5}", "{\"size\":10.5}", "{\"page\":null}", "{\"size\":null}"})
    void rejectsInvalidPagingBeforeSearching(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void documentsSupportedPageSizesAndZeroBasedPagesInOpenApi() {
        var schema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(JournalListQuery.class)).schema;
        Schema<?> size = (Schema<?>) schema.getProperties().get("size");
        Schema<?> page = (Schema<?>) schema.getProperties().get("page");
        assertThat(size.getMinimum()).isEqualByComparingTo("10");
        assertThat(size.getMaximum()).isEqualByComparingTo("100");
        assertThat(page.getMinimum()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void acceptsSupportedPageSizesWithoutChangingTheirValue(int size) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page\":2,\"size\":" + size + "}"))
                .andExpect(status().isOk());
        var captured = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(captured.capture());
        assertThat(captured.getValue().getSize()).isEqualTo(size);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"sortBy\":\"SORT_BY_RELEVANCE_DESC\"}"})
    void defaultsToTitleAscendingAndMigratesTheLegacySortAlias(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        ArgumentCaptor<JournalListQuery> captured = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(captured.capture());
        assertThat(captured.getValue().getSortBy()).isEqualTo(JournalListQuery.SortBy.SORT_BY_TITLE_ASC);
    }

    @Test
    void rejectsUnknownSortDirectionsAndExposesOnlyEightValuesInOpenApi() throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortBy\":\"SORT_BY_REJECTION_RATE_DESC\"}"))
                .andExpect(status().isBadRequest());
        var schema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(JournalListQuery.class));
        Schema<?> sort = (Schema<?>) schema.schema.getProperties().get("sortBy");
        assertThat(sort.getEnum()).hasSize(8);
        assertThat(sort.getEnum().toString()).doesNotContain("RELEVANCE", "REJECTION");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"otherFeesFreeOnly\":true}", "{\"openAccessFeeFreeOnly\":true}", "{\"publicationFeeMaxPln\":0}",
            "{\"openAccessFeeMaxPln\":123.4567,\"submissionFeeFreeOnly\":true}",
            "{\"rapidReviewFeeMaxPln\":500,\"submissionFeeMaxPln\":10}"
    })
    void acceptsIndependentFeeCategoriesAndExactNonNegativeLimits(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).getJournals(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"openAccessFeeMaxPln\":-1}", "{\"publicationFeeMaxPln\":-0.01}",
            "{\"rapidReviewFeeMaxPln\":-1}", "{\"submissionFeeMaxPln\":-1}",
            "{\"openAccessFeeFreeOnly\":true,\"openAccessFeeMaxPln\":0}",
            "{\"publicationFeeFreeOnly\":true,\"publicationFeeMaxPln\":1}",
            "{\"rapidReviewFeeFreeOnly\":true,\"rapidReviewFeeMaxPln\":1}",
            "{\"submissionFeeFreeOnly\":true,\"submissionFeeMaxPln\":1}"
    })
    void rejectsNegativeOrContradictoryFeeLimitsBeforeSearch(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void reportsApplicationValidationAs400WithoutChangingTheErrorContract() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var invalidQuery = new JournalListQuery();
            invalidQuery.setPage(-1);
            var exception = new ConstraintViolationException("Private application detail",
                    factory.getValidator().validate(invalidQuery));
            when(service.getJournals(any())).thenThrow(exception);

            mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.fieldErrors.page").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Private application detail"))));
        }
    }

    @Test
    void reportsUnavailableExchangeRatesAsAnIntegrationErrorWithoutTechnicalDetails() throws Exception {
        when(service.getJournals(any())).thenThrow(new com.osatum.wbn.application.journal.service.ExchangeRatesUnavailableException("private detail"));
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("EXCHANGE_RATES_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private detail"))));
    }

    @Test
    void exposesDecimalBoundsWithoutAddingValidationPropertiesToOpenApi() {
        var schema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(JournalListQuery.class));
        Schema<?> minimum = (Schema<?>) schema.schema.getProperties().get("icvFrom");
        Schema<?> maximum = (Schema<?>) schema.schema.getProperties().get("icvTo");
        assertThat(minimum.getType()).isEqualTo("number");
        assertThat(maximum.getType()).isEqualTo("number");
        assertThat(schema.schema.getProperties()).doesNotContainKeys("icvRangeValid", "percentageRangesValid", "processingTimeLimitsValid", "mniswRangeValid", "firstIssueYearRangeValid", "repositoryIdsValid", "feeCriteriaValid", "feeFilters");
        assertThat(((Schema<?>) schema.schema.getProperties().get("mniswOnly")).getType()).isEqualTo("boolean");
        assertThat(((Schema<?>) schema.schema.getProperties().get("mniswPointsFrom")).getType()).isEqualTo("integer");
        assertThat(((Schema<?>) schema.schema.getProperties().get("reviewDaysTo")).getType()).isEqualTo("integer");
        for (String field : List.of("reviewDaysFrom", "initialAssessmentDaysFrom", "publicationDaysFrom")) {
            Schema<?> bound = (Schema<?>) schema.schema.getProperties().get(field);
            assertThat(bound.getType()).isEqualTo("integer");
            assertThat(bound.getMinimum()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(bound.getMaximum()).isEqualByComparingTo(BigDecimal.valueOf(Integer.MAX_VALUE));
        }
        assertThat(((Schema<?>) schema.schema.getProperties().get("rejectionRateFrom")).getType()).isEqualTo("number");
        Schema<?> countries = (Schema<?>) schema.schema.getProperties().get("countryCodes");
        Schema<?> languages = (Schema<?>) schema.schema.getProperties().get("languageCodes");
        assertThat(countries.getType()).isEqualTo("array");
        assertThat(countries.getItems().getType()).isEqualTo("string");
        assertThat(languages.getType()).isEqualTo("array");
        assertThat(languages.getItems().getType()).isEqualTo("string");
        var response = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(JournalListView.class));
        assertThat(response.schema.getProperties()).doesNotContainKey("rejectionRate");
    }

    @Test
    void acceptsDecimalIcvBoundsWithoutRounding() throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"icvFrom\":80.25,\"icvTo\":95.75,\"size\":10}"))
                .andExpect(status().isOk());

        ArgumentCaptor<JournalListQuery> query = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(query.capture());
        assertThat(query.getValue().getIcvFrom()).isEqualByComparingTo("80.25");
        assertThat(query.getValue().getIcvTo()).isEqualByComparingTo("95.75");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"doiOnly\":true,\"editorialSystemOnly\":true,\"newsletterOnly\":true,\"socialMediaOnly\":true,\"antiPlagiarismOnly\":true,\"aopOnly\":true}",
            "{\"repositoryIds\":[10,20],\"licenseCodes\":[\"CC_BY\",\"NONE\"],\"accessModels\":[\"FREE_ARTICLES\",\"PAID_ARTICLES\"],\"frequencyCodes\":[\"q\",\"m\"]}",
            "{\"repositoryIds\":[],\"licenseCodes\":[],\"accessModels\":[],\"frequencyCodes\":[]}",
            "{\"firstIssueYearFrom\":1960}", "{\"firstIssueYearTo\":2024}",
            "{\"firstIssueYearFrom\":1960,\"firstIssueYearTo\":1960}"
    })
    void acceptsNonFeeFiltersAndPartialYearRanges(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).getJournals(any());
    }

    @Test
    void passesSelectedReviewModelsToTheSearchService() throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"surveyYear\":2024,\"reviewModels\":[\"DOUBLE_BLIND_REVIEW\",\"OPEN_PEER_REVIEW\"]}"))
                .andExpect(status().isOk());
        var query = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(query.capture());
        assertThat(query.getValue().getReviewModels()).containsExactly(
                com.osatum.wbn.domain.model.journal.JournalReviewModel.DOUBLE_BLIND_REVIEW,
                com.osatum.wbn.domain.model.journal.JournalReviewModel.OPEN_PEER_REVIEW);
        assertThat(query.getValue().getSurveyYear()).isEqualTo(2024);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"repositoryIds\":[0]}", "{\"repositoryIds\":[-1]}", "{\"repositoryIds\":[null]}",
            "{\"repositoryIds\":[10.5]}", "{\"repositoryIds\":[9223372036854775808]}",
            "{\"licenseCodes\":[\"UNKNOWN\"]}", "{\"licenseCodes\":[null]}", "{\"accessModels\":[\"OA\"]}",
            "{\"frequencyCodes\":[\"unknown\"]}", "{\"frequencyCodes\":[null]}",
            "{\"reviewModels\":[\"UNKNOWN\"]}", "{\"reviewModels\":[null]}",
            "{\"firstIssueYearFrom\":2024,\"firstIssueYearTo\":1960}",
            "{\"firstIssueYearFrom\":1960.5}", "{\"firstIssueYearTo\":-1}", "{\"firstIssueYearTo\":2147483648}"
    })
    void rejectsInvalidNonFeeFiltersWithoutTruncatingIdsOrYears(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(service, never()).getJournals(any());
    }

    @Test
    void returnsMniswPointClassesWithoutExposingTheListYear() throws Exception {
        when(service.getSearchMetadata()).thenReturn(new JournalSearchMetadata(List.of(20, 40, 70, 140),
                new java.math.BigDecimal("80.25"), new java.math.BigDecimal("95.75"),
                java.util.Map.of("pl", 2L), java.util.Map.of("en", 3L), java.util.Map.of("42", 1L),
                java.util.Map.of("10", 2L), java.util.Map.of("CC_BY", 1L), java.util.Map.of("FREE_ARTICLES", 1L),
                java.util.Map.of("q", 1L), java.util.Map.of("doiOnly", 2L), java.util.Map.of("2021", 2L),
                java.util.Map.of("DOUBLE_BLIND_REVIEW", 2L)));
        mvc.perform(get("/api/journal/search-journals/metadata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mniswPointClasses[2]").value(70))
                .andExpect(jsonPath("$.icvMin").value(80.25))
                .andExpect(jsonPath("$.icvMax").value(95.75))
                .andExpect(jsonPath("$.countryCounts.pl").value(2))
                .andExpect(jsonPath("$.languageCounts.en").value(3))
                .andExpect(jsonPath("$.disciplineCounts['42']").value(1))
                .andExpect(jsonPath("$.mniswDisciplineCounts['2021']").value(2))
                .andExpect(jsonPath("$.reviewModelCounts.DOUBLE_BLIND_REVIEW").value(2))
                .andExpect(jsonPath("$.mniswYear").doesNotExist());
        verify(service).getSearchMetadata();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"mniswOnly\":true}", "{\"mniswOnly\":true,\"mniswPointsFrom\":20}",
            "{\"mniswOnly\":true,\"mniswPointsTo\":70}",
            "{\"mniswOnly\":true,\"mniswPointsFrom\":70,\"mniswPointsTo\":70}",
            "{\"mniswOnly\":false,\"mniswPointsFrom\":100,\"mniswPointsTo\":20}"
    })
    void acceptsMniswMembershipPartialAndClosedRangesOrInactiveBounds(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).getJournals(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"mniswOnly\":true,\"mniswPointsFrom\":20.5}",
            "{\"mniswOnly\":true,\"mniswPointsTo\":-1}",
            "{\"mniswOnly\":true,\"mniswPointsTo\":2147483648}",
            "{\"mniswOnly\":true,\"mniswPointsFrom\":140,\"mniswPointsTo\":70}"
    })
    void rejectsInvalidActiveMniswRanges(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.mniswRangeValid").isNotEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void returnsExistingErrorFormatWhenPointClassIsNoLongerAvailable() throws Exception {
        when(service.getJournals(any())).thenThrow(new IllegalArgumentException("MNiSW bounds must use available point classes"));
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mniswOnly\":true,\"mniswPointsFrom\":50}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"reviewDaysTo\":0,\"initialAssessmentDaysTo\":5,\"publicationDaysTo\":60}",
            "{\"reviewDaysTo\":2147483647}",
            "{\"reviewDaysFrom\":0,\"initialAssessmentDaysFrom\":5,\"publicationDaysFrom\":2147483647}",
            "{\"reviewDaysFrom\":0,\"reviewDaysTo\":0,\"initialAssessmentDaysFrom\":2,\"initialAssessmentDaysTo\":5,\"publicationDaysFrom\":30,\"publicationDaysTo\":60}",
            "{\"rejectionRateFrom\":0,\"rejectionRateTo\":100}",
            "{\"rejectionRateFrom\":33.33333}",
            "{\"authorForeignAffiliationPercentageTo\":25.5}",
            "{\"reviewerForeignAffiliationPercentageFrom\":20.25,\"reviewerForeignAffiliationPercentageTo\":20.25}"
    })
    void acceptsIndependentWholeDayLimitsAndPartialDecimalPercentages(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).getJournals(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"reviewDaysTo\":1.5}", "{\"initialAssessmentDaysTo\":-1}",
            "{\"publicationDaysTo\":2147483648}", "{\"publicationDaysTo\":-0.1}",
            "{\"reviewDaysFrom\":1.5}", "{\"initialAssessmentDaysFrom\":-1}",
            "{\"publicationDaysFrom\":2147483648}",
            "{\"reviewDaysFrom\":11,\"reviewDaysTo\":10}",
            "{\"initialAssessmentDaysFrom\":6,\"initialAssessmentDaysTo\":5}",
            "{\"publicationDaysFrom\":61,\"publicationDaysTo\":60}",
            "{\"rejectionRateFrom\":-0.01}", "{\"rejectionRateTo\":100.01}",
            "{\"rejectionRateFrom\":60,\"rejectionRateTo\":50}",
            "{\"authorForeignAffiliationPercentageFrom\":30,\"authorForeignAffiliationPercentageTo\":20}",
            "{\"reviewerForeignAffiliationPercentageFrom\":100.1}"
    })
    void rejectsInvalidProcessingAndPercentageBounds(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"countryCodes\":[\"PL\",\"DE\"],\"languageCodes\":[\"en\",\"pl\"]}",
            "{\"countryCodes\":[],\"languageCodes\":[]}",
            "{\"countryCode\":\"PL\",\"languageCode\":\"en\"}"
    })
    void acceptsMultipleOrLegacyDictionaryCodes(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).getJournals(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"countryCodes\":[\"PL\",\"\"]}", "{\"languageCodes\":[\"  \"]}",
            "{\"countryCodes\":[null]}", "{\"languageCodes\":[null]}",
            "{\"countryCode\":\"PL\",\"countryCodes\":[\"\"]}"
    })
    void rejectsBlankOrNullDictionaryCodes(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"icvFrom\":80.25}", "{\"icvTo\":80.25}",
            "{\"icvFrom\":0,\"icvTo\":0}", "{\"icvFrom\":80.25,\"icvTo\":80.250}"})
    void acceptsEmptyPartialAndClosedRanges(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(service).getJournals(any());
    }

    @ParameterizedTest
    @EnumSource(RatingStatus.class)
    void acceptsRestoredIcvStatusFilter(RatingStatus ratingStatus) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratingStatus\":\"" + ratingStatus.name() + "\"}"))
                .andExpect(status().isOk());
        var captured = ArgumentCaptor.forClass(JournalListQuery.class);
        verify(service).getJournals(captured.capture());
        assertThat(captured.getValue().getRatingStatus()).isEqualTo(ratingStatus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"ratingStatus\":\"NOT_INDEXED\",\"icvFrom\":0}",
            "{\"ratingStatus\":\"NOT_EVALUATED\",\"icvTo\":10}"})
    void rejectsStatusAndPointContradictionBeforeSearching(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.icvRangeValid").isNotEmpty());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"icvFrom\":95.75,\"icvTo\":80.25}", "{\"icvFrom\":-0.01}",
            "{\"icvTo\":-1}", "{\"icvFrom\":80.251}", "{\"icvTo\":0.001}"})
    void rejectsInvalidRangesUsingExistingErrorFormat(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.icvRangeValid").isNotEmpty());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"icvFrom\":\"invalid\"}", "{\"icvTo\":{}}",
            "{\"countryCodes\":\"PL\"}", "{\"languageCodes\":{}}"})
    void rejectsMalformedSearchValuesWithoutSearching(String body) throws Exception {
        mvc.perform(post("/api/journal/search-journals").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }
}
