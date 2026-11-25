package com.osatum.wbn.application.configuration.service;

import com.osatum.wbn.application.configuration.dto.RatingYearsDto;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

/**
 * Udostępnia centralną konfigurację lat ocen, publicznych linków JML i dostępności funkcji P2.
 * Rok ankiet i niezależny rok wykazu MNiSW nie wynikają z kalendarza ani z najnowszego rekordu.
 * Globalna prezentacja benchmarków jest oddzielona od dostępu do Karty i gotowości jej danych.
 * Ten sam szablon linku do wydań służy nawigacji oraz ustaleniu celu zdarzenia E14 na serwerze.
 */
@Service
public class ConfigurationService {

    private static final Integer DEFAULT_RATING_YEAR = 2025;
    private static final Integer CURRENT_MNISW_YEAR = 2024;
    private static final List<Integer> AVAILABLE_SEARCH_YEARS = List.of(2025, 2024);

    @Value("${app.migration.p2-benchmark-years:2024}")
    private String p2BenchmarkYears = "2024";

    /**
     * Jawny zakres przygotowania benchmarków, domyślnie 2024; kolejne lata dodaje operator konfiguracją.
     * Nie wynika z kalendarza, lat wyszukiwarki ani decyzji o pokazaniu porównań.
     */
    public List<Integer> getP2BenchmarkYears() {
        try {
            return java.util.Arrays.stream(p2BenchmarkYears.split(",", -1)).map(String::trim).map(Integer::valueOf).toList();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid P2 benchmark years configuration", exception);
        }
    }

    @PostConstruct
    public void validateP2BenchmarkYears() {
        var years = getP2BenchmarkYears();
        if (years.isEmpty() || years.stream().anyMatch(year -> year <= 0)
                || years.stream().distinct().count() != years.size()
                || !getAvailableSearchYears().containsAll(years)) {
            throw new IllegalStateException("P2 benchmark years must be unique supported assessment years");
        }
    }

    @Value("${app.journal.issues-url-template:https://journals.indexcopernicus.com/search/journal/issue?issueId=all&journalId={journalId}}")
    private String journalIssuesUrlTemplate = "https://journals.indexcopernicus.com/search/journal/issue?issueId=all&journalId={journalId}";

    public String getJournalIssuesUrlTemplate() {
        return journalIssuesUrlTemplate;
    }

    public String getJournalIssuesUrl(Long journalId) {
        if (journalId == null || journalId <= 0) throw new IllegalArgumentException("Invalid journal identifier");
        return getJournalIssuesUrlTemplate().replace("{journalId}", journalId.toString());
    }

    @PostConstruct
    public void validateJournalLinks() {
        String template = getJournalIssuesUrlTemplate();
        if (template == null || template.indexOf("{journalId}") < 0
                || template.indexOf("{journalId}") != template.lastIndexOf("{journalId}")) {
            throw new IllegalStateException("Journal issues URL must contain one {journalId} placeholder");
        }
        URI uri = URI.create(getJournalIssuesUrl(1L));
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalStateException("Journal issues URL must be a public HTTPS URL");
        }
    }

    public List<Integer> getAvailableSearchYears() {
        return AVAILABLE_SEARCH_YEARS;
    }

    @PostConstruct
    public void validateSearchYears() {
        List<Integer> years = getAvailableSearchYears();
        if (years.isEmpty() || years.stream().anyMatch(year -> year == null || year <= 0)
                || !years.equals(years.stream().distinct().sorted(Comparator.reverseOrder()).toList())
                || !years.contains(getCurrentRatingYear())) {
            throw new IllegalStateException("Available search years must be unique, descending and include the current rating year");
        }
    }

    public Integer resolveSearchYear(Integer requestedYear) {
        if (requestedYear == null) {
            return getCurrentRatingYear();
        }
        if (!getAvailableSearchYears().contains(requestedYear)) {
            throw new IllegalArgumentException("Unavailable survey year: " + requestedYear);
        }
        return requestedYear;
    }

    @Value("${app.operating-profile.default:BOTH}")
    private OperatingProfile defaultOperatingProfile = OperatingProfile.BOTH;

    // Flaga umożliwia wycofanie Karty bez zmiany profilu instytucji i dostępu do Pełnych danych JML.
    @Value("${app.decision-card.enabled:false}")
    private boolean decisionCardEnabled;

    @Value("${app.decision-card.benchmarks-visible:false}")
    private boolean decisionBenchmarksVisible;

    /** Zmiana wymaga konfiguracji środowiska; uzupełnienie danych nie włącza porównań automatycznie. */
    public boolean isDecisionBenchmarksVisible() {
        return decisionBenchmarksVisible;
    }

    public boolean isDecisionCardEnabled() {
        return decisionCardEnabled;
    }

    public OperatingProfile getDefaultOperatingProfile() {
        return defaultOperatingProfile;
    }

    /**
     * Wskazuje centralnie obowiązujący wykaz, aktualizowany niezależnie od ankiet rocznych.
     */
    public Integer getCurrentMniswYear() {
        return CURRENT_MNISW_YEAR;
    }

    /**
     * Zwraca wspólny rok wyszukiwania, metadanych, ankiet, raportów i ocen ICV.
     */
    public Integer getCurrentRatingYear() {
        return DEFAULT_RATING_YEAR;
    }

    /**
     * Zwraca zestaw lat oceny udostępniany frontendowi w konfiguracji aplikacji.
     */
    public RatingYearsDto getRatingYears() {
        Integer currentYear = getCurrentRatingYear();
        return new RatingYearsDto(currentYear, currentYear - 1);
    }

}
