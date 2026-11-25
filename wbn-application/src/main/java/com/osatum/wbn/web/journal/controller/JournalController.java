package com.osatum.wbn.web.journal.controller;

import com.osatum.wbn.application.analytics.service.AnalyticsJournalService;
import com.osatum.wbn.application.analytics.view.AnalyticsJournalView;
import com.osatum.wbn.application.journal.query.JournalListQuery;
import com.osatum.wbn.domain.model.institution.SearchView;
import com.osatum.wbn.application.journal.service.JournalService;
import com.osatum.wbn.application.journal.view.JournalListView;
import com.osatum.wbn.application.journal.view.JournalSearchMetadata;
import com.osatum.wbn.application.journal.view.JournalSearchCounts;
import com.osatum.wbn.application.journal.view.JournalView;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.web.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/**
 * Udostępnia wyszukiwanie P1, metadane filtrów oraz dane karty czasopisma.
 * Walidację kryteriów wykonuje przed delegacją, a uprawnienia do wyszukiwania i karty sprawdza serwis.
 */
@Tag(name = "journal", description = "Wyszukiwanie i podgląd czasopism")
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final ObjectProvider<JournalService> journalService;
    private final AnalyticsJournalService analyticsJournalService;

    /**
     * Przyjmuje zastosowane kryteria; rok ankiety i obowiązujące lata ocen ustala backend,
     * niezależnie od obecności nowszych ankiet. Odpowiedź obejmuje stronę oraz pełny licznik dopasowań.
     */
    @PostMapping("/search-journals")
    @Operation(
            summary = "Wyszukiwanie czasopism",
            description = "Zwraca stronicowaną listę czasopism zgodnie z przekazanymi kryteriami wyszukiwania."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę czasopism",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do podglądu czasopism")
    @ApiResponse(responseCode = "400", description = "Nieprawidłowe kryteria wyszukiwania",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Wyszukiwanie czasopism jest niedostępne",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Kursy walut wymagane do porównania opłat są niedostępne",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResult<JournalListView> searchJournals(@Valid @RequestBody JournalListQuery query) {
        return service().getJournals(query);
    }

    /**
     * Zwraca globalne zakresy i liczniki dla roku wyszukiwania, nie podgląd wyniku edytowanego formularza.
     */
    @GetMapping("/search-journals/metadata")
    @Operation(summary = "Metadane filtrów czasopism", operationId = "getJournalSearchMetadata",
            description = "Zwraca rzeczywiste klasy punktowe obowiązującego wykazu MNiSW dla roku ankiety ustalonego w backendzie, niezależnie od filtrów formularza.")
    @ApiResponse(responseCode = "200", description = "Zwrócono metadane filtrów",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JournalSearchMetadata.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do podglądu czasopism")
    public JournalSearchMetadata getJournalSearchMetadata(@RequestParam(name = "searchView", required = false) SearchView searchView,
                                                        @RequestParam(name = "surveyYear", required = false) Integer surveyYear) {
        return surveyYear == null ? (searchView == null ? service().getSearchMetadata() : service().getSearchMetadata(searchView))
                : service().getSearchMetadata(searchView, surveyYear);
    }

    @PostMapping("/search-journals/counts")
    @Operation(summary = "Liczniki zastosowanych filtrów", operationId = "getJournalSearchCounts",
            description = "Uwzględnia pozostałe grupy filtrów. W obrębie wielowyboru OR pomija ograniczenie własnej grupy; dla baz i checkboxów AND zachowuje wszystkie warunki. Nie zmienia zakresów walidacji.")
    public JournalSearchCounts getJournalSearchCounts(@Valid @RequestBody JournalListQuery query) {
        return service().getSearchCounts(query);
    }

    @GetMapping("/search-journals/default-query")
    @Operation(
            summary = "Domyślne kryteria wyszukiwania czasopism",
            description = "Zwraca domyślny stan kryteriów wyszukiwania czasopism."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono domyślne kryteria wyszukiwania czasopism",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JournalListQuery.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do podglądu czasopism")
    public JournalListQuery getDefaultJournalListQuery() {
        return service().getDefaultJournalListQuery();
    }

    @GetMapping(value="/journal/{id}")
    @Operation(
            summary = "Szczegóły czasopisma",
            description = "Zwraca szczegółowe dane czasopisma o podanym identyfikatorze."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono szczegóły czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JournalView.class)))
    @ApiResponse(responseCode = "404", description = "Nie znaleziono czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "500", description = "Błąd podczas pobierania szczegółów czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public JournalView getJournal(@PathVariable(name = "id") Long id,
                                  @RequestParam(name = "surveyYear", required = false) Integer surveyYear) {
        return surveyYear == null ? service().getJournal(id) : service().getJournal(id, surveyYear);
    }

    @GetMapping(value="/journal/{id}/analytics")
    @Operation(
            summary = "Analityka czasopisma",
            description = "Zwraca dane analityczne czasopisma uĹĽywane do prezentacji wykresĂłw kwestionariusza."
    )
    @ApiResponse(responseCode = "200", description = "ZwrĂłcono dane analityczne czasopisma",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnalyticsJournalView.class)))
    @ApiResponse(responseCode = "403", description = "UĹĽytkownik nie ma uprawnieĹ„ do podglÄ…du czasopisma")
    public AnalyticsJournalView getAnalyticsJournal(@PathVariable(name = "id") Long id,
                                                   @RequestParam(name = "surveyYear", required = false) Integer surveyYear) {
        return surveyYear == null ? analyticsJournalService.getAnalyticsJournal(id) : analyticsJournalService.getAnalyticsJournal(id, surveyYear);
    }

    private JournalService service() {
        JournalService service = journalService.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Journal search is unavailable.");
        }
        return service;
    }

}
