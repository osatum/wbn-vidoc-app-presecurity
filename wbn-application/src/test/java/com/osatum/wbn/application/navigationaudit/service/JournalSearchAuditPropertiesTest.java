package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.system.dto.NavigationAuditEventDto;
import com.osatum.wbn.application.system.dto.NavigationAuditResourceDto;
import com.osatum.wbn.application.system.service.JournalSearchAuditProperties;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class JournalSearchAuditPropertiesTest {

    @ParameterizedTest
    @ValueSource(ints = {10, 25, 50, 100})
    void recordsPageSizeWithoutBreakingLegacyAuditPayloads(int size) {
        var values = properties(Map.of());
        values.put("size", size);
        values.put("searchView", "ADVANCED");
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null),
                new ObjectMapper())).contains("\"size\":" + size);
        if (size != 10) {
            values.put("searchView", "BASIC");
            assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(
                    event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), new ObjectMapper()))
                    .isInstanceOf(JournalSearchAuditProperties.InvalidPropertiesException.class);
        }
    }

    @Test
    void keepsReviewModelsInAdvancedAuditAndRejectsThemInBasicSearch() {
        var values = properties(Map.of("reviewModels", List.of("DOUBLE_BLIND_REVIEW", "OPEN_PEER_REVIEW")));
        values.put("searchView", "ADVANCED");
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null),
                new ObjectMapper())).contains("reviewModels", "DOUBLE_BLIND_REVIEW", "OPEN_PEER_REVIEW");
        values.put("searchView", "BASIC");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(
                event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), new ObjectMapper()))
                .isInstanceOf(JournalSearchAuditProperties.InvalidPropertiesException.class);
    }

    @Test
    void preservesProcessingTimeBoundsAndRejectsThemInBasicSearch() throws Exception {
        var criteria = Map.<String, Object>of("reviewDaysFrom", 0, "reviewDaysTo", 0,
                "initialAssessmentDaysFrom", 2, "initialAssessmentDaysTo", 5,
                "publicationDaysFrom", 30, "publicationDaysTo", 60);
        var values = properties(criteria);
        values.put("searchView", "ADVANCED");
        String json = JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null),
                new ObjectMapper());
        assertThat(new ObjectMapper().readValue(json, Map.class).get("criteria")).isEqualTo(criteria);
        values.put("searchView", "BASIC");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(
                event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), new ObjectMapper()))
                .isInstanceOf(JournalSearchAuditProperties.InvalidPropertiesException.class);
    }

    @Test
    void preservesTheSelectedYearInSearchAudit() {
        var values = properties(Map.of("surveyYear", 2024));
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null),
                new ObjectMapper())).contains("surveyYear", "2024");
    }
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requiresAJournalResourceForIssuesClicks() {
        var valid = event(NavigationAuditEventType.JOURNAL_ISSUES_CLICK, properties(Map.of()), null);
        assertThat(JournalSearchAuditProperties.serialize(valid, mapper)).contains("criteria");
        var invalid = new NavigationAuditEventDto(valid.eventId(), valid.eventType(), valid.occurredAt(),
                valid.sessionId(), valid.viewId(), valid.pageKey(), valid.routeTemplate(), valid.url(),
                valid.params(), null, valid.action(), valid.target(), null, null, null, valid.properties());
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(invalid, mapper))
                .isInstanceOf(JournalSearchAuditProperties.InvalidPropertiesException.class);
    }

    static Map<String, Object> properties(Map<String, Object> criteria) {
        return new LinkedHashMap<>(Map.of("schemaVersion", 1, "operationId", UUID.randomUUID().toString(),
                "origin", "SEARCH", "criteria", criteria, "page", 0, "sortBy", "SORT_BY_TITLE_ASC"));
    }

    static NavigationAuditEventDto event(NavigationAuditEventType type, Map<String, Object> properties, Boolean success) {
        return new NavigationAuditEventDto(UUID.randomUUID(), type, Instant.now(), "session", "view",
                "institution.journals.list", "/institution/user/journals", "/institution/user/journals?token=excluded",
                Map.of("token", "excluded"), new NavigationAuditResourceDto("journal", "28032"),
                "client-action", "client-target", null, success, null, properties);
    }

    @Test
    void keepsCompleteValidJsonBeyondTheOld4000CharacterLimit() throws Exception {
        Map<String, Object> values = properties(Map.of("journalTitle", "Bibliographic title ".repeat(350),
                "countryCodes", List.of("pl", "de"), "mniswOnly", true, "mniswPointsFrom", 70,
                "publicationFeeMaxPln", 0, "reviewDaysTo", 0));
        String json = JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper);
        assertThat(json.length()).isGreaterThan(4000);
        assertThat(mapper.readValue(json, Map.class)).isEqualTo(values);
    }

    @Test
    void internalDefaultsDoNotChangeSparseAuditCriteriaOrTheInputMap() throws Exception {
        Map<String, Object> criteria = new LinkedHashMap<>();
        criteria.put("countryCodes", List.of("PL"));
        criteria.put("doiOnly", true);
        var values = properties(criteria);

        String json = JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper);

        assertThat(mapper.readValue(json, Map.class).get("criteria")).isEqualTo(criteria);
        assertThat(values.get("criteria")).isSameAs(criteria);
        assertThat(criteria).containsOnlyKeys("countryCodes", "doiOnly");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BASIC", "ADVANCED"})
    void recordsTheSearchViewWithoutChangingLegacySchemaOrCriteria(String view) {
        var values = properties(Map.of("journalTitle", "Title", "countryCodes", List.of("pl")));
        values.put("searchView", view);
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .contains(view);
    }

    @Test
    void rejectsAnInventedViewOrAdvancedCriteriaDeclaredAsBasic() {
        var values = properties(Map.of("doiOnly", true));
        values.put("searchView", "BASIC");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .isInstanceOf(JournalSearchAuditProperties.InvalidPropertiesException.class);
        values.put("searchView", "BOTH");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .isInstanceOf(JournalSearchAuditProperties.InvalidPropertiesException.class);
    }

    @Test
    void stillRejectsExplicitFalseFlagsInTheCanonicalAuditPayload() {
        var values = properties(Map.of("doiOnly", false));

        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .isInstanceOf(JournalSearchAuditProperties.InvalidPropertiesException.class);
    }

    @Test
    void recordsSharedMainFormCriteriaForBasicSearch() {
        var values = properties(Map.of("countryCodes", List.of("pl", "de"), "languageCodes", List.of("pl", "en"),
                "mniswOnly", true, "mniswDisciplineCodes", List.of("2021"), "mniswPointsFrom", 40, "mniswPointsTo", 140));
        values.put("searchView", "BASIC");
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .contains("BASIC", "mniswDisciplineCodes", "2021", "countryCodes", "languageCodes");
    }

    @Test
    void recordsAppliedMniswCodesAlongsideIndependentJmlCodes() {
        var values = properties(Map.of("mniswOnly", true, "mniswDisciplineCodes", List.of("2021", "2061"),
                "disciplineCodes", List.of("42")));

        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .contains("mniswDisciplineCodes", "2021", "2061", "disciplineCodes", "42");
    }

    @Test
    void rejectsOversizedPayloadWithoutReturningTruncatedJson() {
        var values = properties(Map.of("journalTitle", "ą".repeat(JournalSearchAuditProperties.MAX_BYTES)));
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no truncation");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INDEXED", "NOT_INDEXED", "NOT_EVALUATED"})
    void keepsRestoredIcvStatusInAppliedCriteria(String status) {
        var values = properties(Map.of("ratingStatus", status));
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .contains(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INDEXED", "NOT_INDEXED", "NOT_EVALUATED"})
    void acceptsRemainingAppliedCriteriaAfterRemovingASingleChip(String status) {
        var values = properties(Map.of("ratingStatus", status, "countryCodes", List.of("pl")));
        values.put("origin", "CLEAR");
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_FILTERS_CLEAR, values, null), mapper))
                .contains(status, "countryCodes");
    }

    static Stream<Map<String, Object>> invalidCriteria() {
        return Stream.of(Map.of("token", "secret"), Map.of("editor", "not-a-P1-filter"),
                Map.of("ratingStatus", "invented"), Map.of("ratingStatus", 1),
                Map.of("ratingStatus", "NOT_EVALUATED", "icvFrom", 0),
                Map.of("countryCodes", List.of(Map.of("user", "private"))), Map.of("mniswDisciplineCodes", List.of("1")),
                Map.of("doiOnly", false), Map.of("journalTitle", 123), Map.of("reviewDaysTo", 1.5),
                Map.of("reviewDaysFrom", 1.5), Map.of("initialAssessmentDaysFrom", -1),
                Map.of("publicationDaysFrom", 2147483648L),
                Map.of("reviewDaysFrom", 10, "reviewDaysTo", 5),
                Map.of("initialAssessmentDaysFrom", 10, "initialAssessmentDaysTo", 5),
                Map.of("publicationDaysFrom", 10, "publicationDaysTo", 5),
                Map.of("rejectionRateFrom", 100.1), Map.of("icvFrom", 1.234), Map.of("icvFrom", 70, "icvTo", 10),
                Map.of("repositoryIds", List.of(-1)), Map.of("licenseCodes", List.of("invented")),
                Map.of("reviewModels", List.of("invented")),
                Map.of("mniswPointsFrom", 70), Map.of("publicationFeeFreeOnly", true, "publicationFeeMaxPln", 1));
    }

    @ParameterizedTest
    @MethodSource("invalidCriteria")
    void rejectsUnknownFieldsAndInvalidOrInactiveCriteria(Map<String, Object> criteria) {
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(
                event(NavigationAuditEventType.JOURNAL_SEARCH, properties(criteria), null), mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnapprovedMetadataAndInconsistentOperationTypes() {
        var values = properties(Map.of());
        values.put("authorization", "must-not-be-saved");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH, values, null), mapper))
                .isInstanceOf(IllegalArgumentException.class);
        values.remove("authorization");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_FILTERS_CLEAR, values, null), mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void distinguishesFailureFromASuccessfulEmptyResult() {
        var values = properties(Map.of());
        values.put("attempt", 1);
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH_RESULT, values, false), mapper)).contains("attempt");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH_EMPTY, values, false), mapper))
                .isInstanceOf(IllegalArgumentException.class);
        values.put("resultCount", 0);
        assertThat(JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH_EMPTY, values, true), mapper)).contains("resultCount");
        assertThatThrownBy(() -> JournalSearchAuditProperties.serialize(event(NavigationAuditEventType.JOURNAL_SEARCH_RESULT, values, false), mapper))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
