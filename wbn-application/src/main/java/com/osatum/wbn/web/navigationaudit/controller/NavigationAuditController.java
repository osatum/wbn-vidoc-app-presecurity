package com.osatum.wbn.web.navigationaudit.controller;

import com.osatum.wbn.application.system.dto.NavigationAuditBatchDto;
import com.osatum.wbn.application.system.dto.NavigationAuditEventListDto;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.system.service.NavigationAuditEventService;
import com.osatum.wbn.application.system.service.NavigationAuditEventQueryService;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.web.error.ApiErrorResponse;
import com.osatum.wbn.web.shared.request.ClientRequestMetadataResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Przyjmuje zdarzenia nawigacji z frontendu i udostępnia administratorowi ich stronicowaną listę.
 * Konfiguracja zabezpieczeń dopuszcza anonimowe zgłoszenia zdarzeń, ale wymaga ADMIN do wyszukiwania.
 * Akcje P2, w tym kliknięcie wydań E14, wymagają ponadto dostępu do Karty decyzji sprawdzanego w serwisie.
 * Przyjęcie paczki nie oznacza akceptacji każdego rekordu; walidacja i deduplikacja należą do warstwy usług.
 */
@RestController
@RequestMapping("/api/navigation-audit/events")
@RequiredArgsConstructor
@Tag(name = "NavigationAudit", description = "Zapis statystyk poruszania się użytkowników po aplikacji")
public class NavigationAuditController {

    private final NavigationAuditEventService navigationAuditEventService;
    private final NavigationAuditEventQueryService navigationAuditEventQueryService;
    private final ClientRequestMetadataResolver requestMetadataResolver;

    /**
     * Przekazuje filtry i paginację do serwisu odczytu; brak treści żądania oznacza domyślne zapytanie.
     * Odczyt statystyk nie zapisuje aktywności ani nie przedłuża wizyty.
     */
    @PostMapping("/search")
    @Operation(
            summary = "Wyszukiwanie zdarzeń nawigacji",
            description = "Zwraca stronicowaną listę zdarzeń nawigacji użytkowników z możliwością filtrowania po dacie, instytucji, użytkowniku, stronie, typie zdarzenia oraz identyfikatorze czasopisma."
    )
    @ApiResponse(responseCode = "200", description = "Zwrócono stronicowaną listę zdarzeń nawigacji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "403", description = "Użytkownik nie ma uprawnień do przeglądania zdarzeń nawigacji",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public PageResult<NavigationAuditEventListDto> findEvents(@RequestBody(required = false) NavigationAuditEventListQuery query) {
        return navigationAuditEventQueryService.findEvents(query);
    }

    /**
     * Przyjmuje zwalidowaną paczkę zdarzeń oraz metadane żądania, takie jak adres IP i User-Agent.
     * Tożsamość aktora ustala serwis na podstawie uwierzytelnienia, a dozwolone zdarzenia określa rejestr stron.
     * Zwraca HTTP 202 bez treści odpowiedzi; obsługa paczki odbywa się w ramach wywołania serwisu.
     * Odrzucenie pojedynczej akcji P1/P2 nie zmienia tego potwierdzenia przyjęcia całej paczki.
     */
    @PostMapping("/batch")
    @Operation(
            summary = "Przyjęcie paczki zdarzeń nawigacji",
            description = "P1-U14: JOURNAL_ISSUES_CLICK (p1_journal_issues_click) wymaga resource=journal/ID i kontekstu wyszukiwania P1; target ustala serwer. "
                    + "Przyjmuje zdarzenia nawigacji, pobierania plików, P1 oraz JOURNAL_DECISION_ACTION na stronie institution.journals.decision. P2: action=E01 lub E03–E14 (E02 zarezerwowane), resource=journal/ID, properties: schemaVersion=1, journalId, rzeczywisty surveyYear Karty i opcjonalna para sourceSearchView (BASIC/ADVANCED) + sourceSurveyYear listy. E14 odpowiada p2_journal_issues_click; target jest publicznym adresem wydań wyliczonym przez ConfigurationService. Aktor, sesja i effectiveProfile P2 pochodzą z serwera; wymagany dostęp DECISION_CARD. P2 pomija IP, User-Agent, parametry URL klienta i treści ankiet. P1 zachowuje pełne properties do 64 KiB UTF-8 i opcjonalne size. Niepoprawny rekord jest odrzucany bez blokowania pozostałych zdarzeń. HTTP 202 potwierdza przyjęcie paczki, nie zapis każdego rekordu. Powtórzony eventId nie tworzy kolejnego wpisu."
    )
    public ResponseEntity<Void> collect(
            @Valid @RequestBody NavigationAuditBatchDto batch,
            HttpServletRequest request
    ) {
        navigationAuditEventService.accept(batch, requestMetadataResolver.resolve(request));
        return ResponseEntity.accepted().build();
    }
}
