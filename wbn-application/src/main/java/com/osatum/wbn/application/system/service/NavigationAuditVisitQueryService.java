package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.NavigationAuditVisitPageResult;
import com.osatum.wbn.application.system.query.NavigationAuditVisitListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

/**
 * Obsługuje odczyt stronicowanej listy wizyt: sprawdza filtry i przelicza daty na granice czasowe.
 * Korzysta ze strefy czasowej raportów, aby lista i eksport XLSX interpretowały dni w ten sam sposób.
 * Sam odczyt listy nie przedłuża ani nie zamyka wizyt.
 */
@Service
@RequiredArgsConstructor
public class NavigationAuditVisitQueryService {
    private final NavigationAuditVisitListQueryHandler queryHandler;
    @Value("${app.navigation-audit.reports.zone:Europe/Warsaw}")
    private String zone = "Europe/Warsaw";

    /**
     * Zwraca dane strony, liczniki paginacji oraz strefę czasową potrzebną do prezentacji dat.
     * Izolacja REPEATABLE_READ zapewnia wspólny obraz danych dla odczytu strony i liczby wyników.
     * Każda z granic dat może być pominięta; podana data końcowa obejmuje cały wskazany dzień.
     *
     * @param request filtry i paginacja; null oznacza domyślne zapytanie
     * @return strona wizyt wraz z metadanymi paginacji i strefą czasową
     * @throws IllegalArgumentException gdy paginacja, długość filtrów lub zakres dat są nieprawidłowe
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public NavigationAuditVisitPageResult findVisits(NavigationAuditVisitListQuery request) {
        var query = request == null ? new NavigationAuditVisitListQuery() : request;
        if (query.getPage() < 0 || query.getSize() < 1 || query.getSize() > 100 || query.getFirst() < 0
                || (long) query.getPage() * query.getSize() > Integer.MAX_VALUE - query.getSize()) {
            throw new IllegalArgumentException("Invalid pagination. Page size must be between 1 and 100.");
        }
        if ((query.getInstitution() != null && query.getInstitution().length() > 200)
                || (query.getUser() != null && query.getUser().length() > 200)) {
            throw new IllegalArgumentException("Search filters must not exceed 200 characters.");
        }
        if (query.getDateFrom() != null && query.getDateTo() != null
                && query.getDateTo().isBefore(query.getDateFrom())) {
            throw new IllegalArgumentException("The end date must not precede the start date.");
        }
        if ((query.getDateFrom() != null && (query.getDateFrom().getYear() < 1 || query.getDateFrom().getYear() > 9999))
                || (query.getDateTo() != null && (query.getDateTo().getYear() < 1 || query.getDateTo().getYear() > 9999))) {
            throw new IllegalArgumentException("Dates must use years between 0001 and 9999.");
        }
        // Przedział jest domknięty z lewej i otwarty z prawej; początki lokalnych dni uwzględniają zmianę czasu.
        var timeZone = ZoneId.of(zone);
        var from = query.getDateFrom() == null ? null : query.getDateFrom().atStartOfDay(timeZone).toInstant();
        var until = query.getDateTo() == null ? null : query.getDateTo().plusDays(1).atStartOfDay(timeZone).toInstant();
        var page = queryHandler.search(query, from, until);
        return new NavigationAuditVisitPageResult(page.data(), page.totalElements(), page.totalPages(), page.size(), timeZone.getId());
    }
}
