package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.system.dto.NavigationAuditEventDto;
import com.osatum.wbn.application.system.dto.NavigationAuditResourceDto;
import com.osatum.wbn.application.system.service.JournalDecisionAuditProperties;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JournalDecisionAuditPropertiesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {"E01", "E02", "E03", "E04", "E05", "E06", "E07", "E08", "E09", "E10", "E11", "E12", "E13", "E14"})
    void keepsAllActiveCodesAndSeparatesSourceYearViewFromCardAndServerProfile(String action) {
        var json = JournalDecisionAuditProperties.serialize(event(action, properties()), mapper, OperatingProfile.BOTH);
        assertThat(mapper.readTree(json).get("surveyYear").asInt()).isEqualTo(2024);
        assertThat(mapper.readTree(json).get("sourceSurveyYear").asInt()).isEqualTo(2025);
        assertThat(mapper.readTree(json).get("sourceSearchView").asString()).isEqualTo("BASIC");
        assertThat(mapper.readTree(json).get("effectiveProfile").asString()).isEqualTo("BOTH");
    }

    @Test
    void supportsDirectNavigationWithoutInventingSourceAndServerAdvancedProfile() {
        var values = properties();
        values.remove("sourceSearchView");
        values.remove("sourceSurveyYear");
        assertThat(JournalDecisionAuditProperties.serialize(event("E01", values), mapper, OperatingProfile.ADVANCED))
                .contains("ADVANCED").doesNotContain("sourceSearchView", "sourceSurveyYear");
    }

    @ParameterizedTest
    @ValueSource(strings = {"E00", "E15", "PAGE_VIEW", "e01", ""})
    void rejectsUnknownActions(String action) {
        assertInvalid(event(action, properties()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"survey", "userId", "institutionId", "effectiveProfile", "url", "criteria", "description"})
    void rejectsExtraFieldsRatherThanRecordingSurveysActorsUrlsOrForgedProfile(String key) {
        var values = properties();
        values.put(key, "must-not-be-stored");
        assertInvalid(event("E01", values));
    }

    @Test
    void rejectsInvalidNumbersSchemaSourceAndMismatchedResources() {
        for (var key : new String[]{"journalId", "surveyYear", "sourceSurveyYear", "schemaVersion"}) {
            for (var value : new Object[]{0, -1, 1.5, "2024", Double.NaN}) {
                var values = properties();
                values.put(key, value);
                assertInvalid(event("E01", values));
            }
        }
        var values = properties();
        values.put("journalId", 124);
        assertInvalid(event("E01", values));
        values = properties();
        values.put("sourceSearchView", "BOTH");
        assertInvalid(event("E01", values));
        values = properties();
        values.remove("sourceSurveyYear");
        assertInvalid(event("E01", values));
        assertThatThrownBy(() -> JournalDecisionAuditProperties.serialize(event("E01", properties()), mapper, OperatingProfile.BASIC))
                .isInstanceOf(JournalDecisionAuditProperties.InvalidPropertiesException.class);
    }

    private void assertInvalid(NavigationAuditEventDto dto) {
        assertThatThrownBy(() -> JournalDecisionAuditProperties.serialize(dto, mapper, OperatingProfile.BOTH))
                .isInstanceOf(JournalDecisionAuditProperties.InvalidPropertiesException.class);
    }

    static Map<String, Object> properties() {
        return new LinkedHashMap<>(Map.of("schemaVersion", 1, "journalId", 123, "surveyYear", 2024,
                "sourceSearchView", "BASIC", "sourceSurveyYear", 2025));
    }

    static NavigationAuditEventDto event(String action, Map<String, Object> properties) {
        return new NavigationAuditEventDto(UUID.randomUUID(), NavigationAuditEventType.JOURNAL_DECISION_ACTION,
                Instant.now(), "p2-session", "p2-view", "institution.journals.decision",
                "/institution/user/journals/:id/decision", "/untrusted-url?secret=not-saved",
                Map.of("secret", "not-saved"), new NavigationAuditResourceDto("journal", "123"),
                action, "untrusted-target", 100, true, 200, properties);
    }
}
