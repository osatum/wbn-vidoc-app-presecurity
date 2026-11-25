package com.osatum.wbn.web.migration;

import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Snapshot;
import com.osatum.wbn.migration.service.DecisionAnalyticsJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Udostępnia operatorowi odbudowę analityki P2 i odczyt stanu jej przygotowania.
 * Odbudowa nie importuje ani nie usuwa źródeł; uprawnienia i wykluczanie równoległych zadań sprawdza serwis.
 * Gotowość analityki nie oznacza włączenia benchmarków na Karcie decyzji.
 * Roczniki określa konfiguracja backendu; kontroler nie przyjmuje ich z żądania użytkownika.
 * Odbudowa zastępuje zapisane wyniki, wejścia kontrolne i osie, zachowując źródłowe ankiety i oceny.
 */
@RestController
@RequestMapping("/api/migration/decision-analytics")
@Tag(name = "Migration")
@RequiredArgsConstructor
public class DecisionAnalyticsController {
    private final DecisionAnalyticsJobService service;

    /** HTTP 200 potwierdza uruchomienie zadania; jego zakończenie sprawdza się przez status. Dostęp weryfikuje serwis. */
    @PostMapping("/rebuild")
    @Operation(summary = "Przygotuj i zapisz benchmarki P2 z istniejących danych bez importu i usuwania źródeł",
            description = "Zakres określa app.migration.p2-benchmark-years, domyślnie 2024. "
                    + "Przygotowanie zapisuje wejścia, wyniki i osie jednej generacji; nie zmienia widoczności ani roku domyślnego.")
    @ApiResponse(responseCode = "200", description = "Uruchomiono przygotowanie wszystkich skonfigurowanych roczników")
    @ApiResponse(responseCode = "403", description = "Wymagany administrator operatora systemowego")
    @ApiResponse(responseCode = "409", description = "Trwa inne zadanie migracyjne lub przygotowanie analityki")
    public ResponseEntity<Snapshot> rebuild() {
        return service.start().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(409).build());
    }

    /** Odczyt statusu nie rozpoczyna obliczeń; uprawnienie operatora sprawdza wywoływany serwis. */
    @GetMapping("/status")
    @Operation(summary = "Odczytaj trwałą gotowość analityki P2")
    public Snapshot status() { return service.status(); }
}
