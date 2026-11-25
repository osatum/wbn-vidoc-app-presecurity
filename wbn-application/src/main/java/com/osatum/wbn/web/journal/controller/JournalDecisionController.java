package com.osatum.wbn.web.journal.controller;

import com.osatum.wbn.application.journal.service.JournalDecisionService;
import com.osatum.wbn.application.journal.view.JournalDecisionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Udostępnia Kartę decyzji niezależnie od Pełnych danych JML.
 * Serwis rozstrzyga dostęp, rocznik i gotowość danych, a konfiguracja środowiska określa widoczność porównań.
 * Kontroler nie pozwala włączyć benchmarków parametrem żądania ani wyborem innego roku.
 * Pobranie Karty odczytuje zapisane wyniki; nie uruchamia importu ani odbudowy analityki.
 */
@Tag(name = "journal")
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalDecisionController {
    private final JournalDecisionService service;

    /** Brak danych rocznych jest stanem poprawnej odpowiedzi, a nie powodem przekierowania na inny rok. */
    @GetMapping("/journal/{id}/decision")
    @Operation(summary = "Karta decyzji — katalog, dane wybranego roku i niezależny wykaz MNiSW", operationId = "getJournalDecision",
            description = "Brak części rocznej zwraca katalog i annualStatus; annual jest wtedy null. "
                    + "Każdy parametr i benchmark ma osobny stan dostępności; porównania obejmują ten sam rok i identyfikator przygotowania. "
                    + "benchmarksVisible określa globalną prezentację porównań niezależnie od roku i użytkownika; przy false benchmarks jest null, a dane własne pozostają dostępne. "
                    + "DATA_UPDATING i DATA_NOT_READY blokują odczyt źródeł w trakcie lub przed ukończeniem przygotowania analityki.")
    @ApiResponse(responseCode = "200", description = "Dane Karty i benchmarki wybranego roku albo jawny stan niedostępności analityki")
    @ApiResponse(responseCode = "400", description = "Nieprawidłowy identyfikator lub niedozwolony rok")
    @ApiResponse(responseCode = "403", description = "Brak uprawnienia do Karty decyzji")
    @ApiResponse(responseCode = "404", description = "Nie znaleziono czasopisma")
    public JournalDecisionView getDecision(@PathVariable("id") Long id,
                                            @RequestParam(name = "surveyYear", required = false) Integer surveyYear) {
        return service.getDecision(id, surveyYear);
    }
}
