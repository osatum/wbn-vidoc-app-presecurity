package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.NavigationAuditEventDto;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Waliduje zamknięty zestaw akcji i atrybutów audytu Karty decyzji.
 * E13 opisuje WWW czasopisma, a E14 jego publiczne wydania; E02 rejestruje rozwinięcie opisu.
 * Profil jest uzupełniany z kontekstu serwera, a dodatkowe pola klienta są odrzucane zamiast zapisywane.
 */
public final class JournalDecisionAuditProperties {
    public static final Set<String> ACTIONS = Set.of("E01", "E02", "E03", "E04", "E05", "E06", "E07",
            "E08", "E09", "E10", "E11", "E12", "E13", "E14");
    private static final Set<String> FIELDS = Set.of("schemaVersion", "journalId", "surveyYear",
            "sourceSearchView", "sourceSurveyYear");

    private JournalDecisionAuditProperties() {
    }

    public static String serialize(NavigationAuditEventDto event, ObjectMapper mapper, OperatingProfile profile) {
        try {
            Map<String, Object> properties = event.properties();
            require(properties != null && FIELDS.containsAll(properties.keySet()));
            require(whole(properties.get("schemaVersion")) == 1);
            require(ACTIONS.contains(event.action()));
            long journalId = whole(properties.get("journalId"));
            // Identyfikator musi zachować dokładność również po stronie JavaScript.
            require(journalId > 0 && journalId <= 9_007_199_254_740_991L);
            require(validYear(properties.get("surveyYear")));
            require(event.resource() != null && "journal".equals(event.resource().type())
                    && Long.toString(journalId).equals(event.resource().id()));
            require(properties.containsKey("sourceSearchView") == properties.containsKey("sourceSurveyYear"));
            if (properties.containsKey("sourceSearchView")) {
                require(Set.of("BASIC", "ADVANCED").contains(properties.get("sourceSearchView")));
                require(validYear(properties.get("sourceSurveyYear")));
            }
            require(profile == OperatingProfile.ADVANCED || profile == OperatingProfile.BOTH);
            var canonical = new LinkedHashMap<>(properties);
            // Profil pochodzi z aktualnego kontekstu serwera, a nie z formularza lub danych zdarzenia.
            canonical.put("effectiveProfile", profile.name());
            return mapper.writeValueAsString(canonical);
        } catch (Exception exception) {
            throw new InvalidPropertiesException(exception);
        }
    }

    private static boolean validYear(Object value) {
        long year = whole(value);
        return year > 0 && year <= 9999;
    }

    private static long whole(Object value) {
        require(value instanceof Number);
        return new BigDecimal(value.toString()).longValueExact();
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid P2 analytics properties.");
    }

    public static final class InvalidPropertiesException extends IllegalArgumentException {
        private InvalidPropertiesException(Exception cause) {
            super("Invalid P2 analytics properties.", cause);
        }
    }
}
