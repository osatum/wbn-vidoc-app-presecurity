package com.osatum.wbn.web.navigationaudit.controller;

import com.osatum.wbn.application.system.dto.NavigationVisitActivityDto;
import com.osatum.wbn.application.system.dto.NavigationAuditVisitPageResult;
import com.osatum.wbn.application.system.query.NavigationAuditVisitListQuery;
import com.osatum.wbn.application.system.service.NavigationAuditVisitQueryService;
import com.osatum.wbn.application.system.service.NavigationAuditVisitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Udostępnia wyszukiwanie wizyt dla administratora oraz przyjmowanie aktywności zalogowanego aktora.
 * Uprawnienia do poszczególnych endpointów egzekwuje konfiguracja zabezpieczeń aplikacji.
 */
@RestController
@RequestMapping("/api/navigation-audit/visits")
@RequiredArgsConstructor
@Tag(name = "NavigationAuditVisits")
public class NavigationAuditVisitController {
    private final NavigationAuditVisitService visitService;
    private final NavigationAuditVisitQueryService queryService;

    /**
     * Zwraca stronę wizyt z uwzględnieniem filtrów, bez aktualizowania ich aktywności.
     * Brak treści żądania oznacza domyślne parametry wyszukiwania. Dostęp wymaga uprawnienia ADMIN.
     * Nagłówek no-store zapobiega przechowywaniu odpowiedzi z danymi audytowymi w pamięci podręcznej.
     */
    @PostMapping(value = "/search", produces = "application/json")
    @Operation(operationId = "findNavigationAuditVisits", summary = "Search paged visits (ADMIN)")
    public ResponseEntity<NavigationAuditVisitPageResult> findVisits(@Valid @RequestBody(required = false) NavigationAuditVisitListQuery query) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(queryService.findVisits(query));
    }

    /**
     * Przekazuje zgłoszoną przez frontend aktywność do obsługi wizyty i zwraca HTTP 204.
     * Serwis ustala aktora i sesję logowania z kontekstu uwierzytelnienia, nie z treści żądania.
     */
    @PostMapping("/activity")
    @Operation(operationId = "recordNavigationVisitActivity", summary = "Record explicit user activity for a visit")
    public ResponseEntity<Void> recordActivity(@Valid @RequestBody NavigationVisitActivityDto activity) {
        visitService.acceptActivity(activity);
        return ResponseEntity.noContent().build();
    }
}
