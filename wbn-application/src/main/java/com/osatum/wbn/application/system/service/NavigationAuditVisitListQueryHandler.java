package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.system.dto.NavigationAuditVisitListDto;
import com.osatum.wbn.application.system.query.NavigationAuditVisitListQuery;

import java.time.Instant;

/**
 * Oddziela odczyt listy wizyt z bazy danych od walidacji i przygotowania zapytania w serwisie.
 */
public interface NavigationAuditVisitListQueryHandler {
    /**
     * Wyszukuje jedną stronę wizyt i wylicza metadane paginacji dla tych samych filtrów.
     *
     * @param query filtry oraz parametry stronicowania
     * @param from włączona dolna granica czasu rozpoczęcia wizyty; null oznacza brak ograniczenia
     * @param until wyłączona górna granica czasu rozpoczęcia wizyty; null oznacza brak ograniczenia
     * @return dane strony i liczniki wyników, bez modyfikowania wizyt
     */
    PageResult<NavigationAuditVisitListDto> search(NavigationAuditVisitListQuery query, Instant from, Instant until);
}
