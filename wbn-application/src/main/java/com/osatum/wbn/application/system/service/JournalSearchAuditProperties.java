package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.journal.query.JournalSearchCriteria;
import com.osatum.wbn.application.journal.query.JournalSearchCriteriaRules;
import com.osatum.wbn.application.system.dto.NavigationAuditEventDto;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Waliduje dozwolony zestaw danych analitycznych P1 przed zapisem audytu.
 * Pełne kryteria są sprawdzane tymi samymi regułami co wyszukiwanie; nieznane pola i przekroczenie limitu
 * 64 KiB UTF-8 powodują odrzucenie zdarzenia, zamiast obcięcia jego treści.
 */
public final class JournalSearchAuditProperties {
    public static final int MAX_BYTES = 65536;
    private static final Set<String> PROPERTIES = Set.of("schemaVersion", "operationId", "origin", "criteria",
            "sortBy", "page", "size", "attempt", "resultCount", "searchView");
    private static final Set<String> TEXT = Set.of("journalTitle", "issn", "publisher");
    private static final Set<String> SELECTIONS = Set.of("countryCodes", "languageCodes", "disciplineCodes", "mniswDisciplineCodes",
            "repositoryIds", "licenseCodes", "accessModels", "frequencyCodes", "reviewModels");
    private static final Set<String> FLAGS = Set.of("mniswOnly", "doiOnly", "editorialSystemOnly", "newsletterOnly",
            "socialMediaOnly", "antiPlagiarismOnly", "aopOnly", "otherFeesFreeOnly", "openAccessFeeFreeOnly",
            "publicationFeeFreeOnly", "rapidReviewFeeFreeOnly", "submissionFeeFreeOnly");
    private static final Set<String> NUMBERS = Set.of("surveyYear", "icvFrom", "icvTo", "mniswPointsFrom", "mniswPointsTo",
            "reviewDaysFrom", "reviewDaysTo", "initialAssessmentDaysFrom", "initialAssessmentDaysTo",
            "publicationDaysFrom", "publicationDaysTo", "rejectionRateFrom", "rejectionRateTo",
            "authorForeignAffiliationPercentageFrom", "authorForeignAffiliationPercentageTo",
            "reviewerForeignAffiliationPercentageFrom", "reviewerForeignAffiliationPercentageTo",
            "firstIssueYearFrom", "firstIssueYearTo", "openAccessFeeMaxPln", "publicationFeeMaxPln",
            "rapidReviewFeeMaxPln", "submissionFeeMaxPln");

    private JournalSearchAuditProperties() {
    }

    /**
     * Sprawdza korelację operacji, rodzaj zdarzenia, kryteria i dane próby, a następnie zapisuje pełny JSON.
     * Wynik oraz numer próby dotyczą odpowiedzi wyszukiwarki, nie samego kliknięcia Szukaj lub karty.
     */
    public static String serialize(NavigationAuditEventDto event, ObjectMapper mapper) {
        try {
            Map<String, Object> properties = event.properties();
            require(properties != null && PROPERTIES.containsAll(properties.keySet()));
            require(integer(properties.get("schemaVersion")) == 1);
            require(properties.get("operationId") instanceof String operationId
                    && UUID.fromString(operationId).toString().equals(operationId));
            require(Set.of("INITIAL", "RETURN", "SEARCH", "CLEAR").contains(properties.get("origin")));
            require(properties.get("criteria") instanceof Map<?, ?>);
            Map<?, ?> criteria = (Map<?, ?>) properties.get("criteria");
            criteria.forEach((key, value) -> require(validCriterion(key, value)));
            var canonicalCriteria = criteriaWithDefaults(criteria);
            if (properties.containsKey("size")) canonicalCriteria.put("size", integer(properties.get("size")));
            JournalSearchCriteria query = mapper.convertValue(canonicalCriteria, JournalSearchCriteria.class);
            require(JournalSearchCriteriaRules.isValid(query));
            if (properties.containsKey("searchView")) {
                require(Set.of("BASIC", "ADVANCED").contains(properties.get("searchView")));
                require(!"BASIC".equals(properties.get("searchView"))
                        || com.osatum.wbn.application.journal.query.JournalSearchViewRules.isBasic(query));
            }
            require(query.mniswOnly() || (!criteria.containsKey("mniswPointsFrom") && !criteria.containsKey("mniswPointsTo")
                    && !criteria.containsKey("mniswDisciplineCodes")));
            require(integer(properties.get("page")) >= 0);
            JournalSearchCriteria.SortBy.valueOf((String) properties.get("sortBy"));
            NavigationAuditEventType type = event.eventType();
            if (type == NavigationAuditEventType.JOURNAL_SEARCH) require("SEARCH".equals(properties.get("origin")));
            if (type == NavigationAuditEventType.JOURNAL_FILTERS_CLEAR) {
                // Usunięcie jednego chipa również jest CLEAR, dlatego pozostałe kryteria mogą być niepuste.
                require("CLEAR".equals(properties.get("origin")));
            }
            if (type == NavigationAuditEventType.JOURNAL_SEARCH_RESULT || type == NavigationAuditEventType.JOURNAL_SEARCH_EMPTY) {
                require(integer(properties.get("attempt")) > 0 && event.success() != null);
                require(Boolean.TRUE.equals(event.success())
                        ? wholeNumber(properties.get("resultCount")).signum() >= 0
                        : !properties.containsKey("resultCount"));
            } else {
                require(!properties.containsKey("attempt") && !properties.containsKey("resultCount"));
            }
            if (type == NavigationAuditEventType.JOURNAL_SEARCH_EMPTY) {
                require(Boolean.TRUE.equals(event.success()) && wholeNumber(properties.get("resultCount")).signum() == 0);
            }
            if (type == NavigationAuditEventType.JOURNAL_CARD_OPEN || type == NavigationAuditEventType.JOURNAL_ISSUES_CLICK) {
                require(event.resource() != null && "journal".equals(event.resource().type())
                        && Long.parseLong(event.resource().id()) > 0);
            }
            String json = mapper.writeValueAsString(properties);
            require(json.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES);
            return json;
        } catch (Exception exception) {
            throw new InvalidPropertiesException(exception);
        }
    }

    /**
     * Odtwarza pominięte checkboxy jako false wyłącznie w kopii do odczytu; zapisany JSON audytu nie jest zmieniany.
     */
    private static Map<String, Object> criteriaWithDefaults(Map<?, ?> criteria) {
        Map<String, Object> values = new LinkedHashMap<>();
        FLAGS.forEach(flag -> values.put(flag, false));
        values.put("page", 0);
        values.put("size", JournalSearchCriteria.PAGE_SIZE);
        criteria.forEach((key, value) -> values.put((String) key, value));
        return values;
    }

    private static boolean validCriterion(Object key, Object value) {
        if ("surveyYear".equals(key)) {
            return value instanceof Number && wholeNumber(value).signum() > 0
                    && wholeNumber(value).compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        }
        if ("ratingStatus".equals(key)) {
            return value instanceof String status && Set.of("INDEXED", "NOT_INDEXED", "NOT_EVALUATED").contains(status);
        }
        if (TEXT.contains(key)) return value instanceof String;
        if (FLAGS.contains(key)) return Boolean.TRUE.equals(value);
        if (NUMBERS.contains(key)) return value instanceof Number;
        if (!SELECTIONS.contains(key) || !(value instanceof List<?> values) || values.isEmpty()) return false;
        return "repositoryIds".equals(key)
                ? values.stream().allMatch(item -> item instanceof Number)
                : values.stream().allMatch(item -> item instanceof String text && !text.isBlank());
    }

    private static BigDecimal wholeNumber(Object value) {
        require(value instanceof Number);
        BigDecimal number = new BigDecimal(value.toString());
        require(number.stripTrailingZeros().scale() <= 0);
        return number;
    }

    private static int integer(Object value) {
        return wholeNumber(value).intValueExact();
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid P1 analytics properties.");
    }

    public static final class InvalidPropertiesException extends IllegalArgumentException {
        private InvalidPropertiesException(Exception cause) {
            super("Invalid P1 analytics properties (maximum 64 KiB; no truncation).", cause);
        }
    }
}
