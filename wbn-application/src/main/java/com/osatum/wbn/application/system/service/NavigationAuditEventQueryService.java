package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.NavigationAuditEventListDto;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.shared.list.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Obsługuje odczyt listy zdarzeń nawigacji i normalizuje podstawowe parametry paginacji.
 * Nie zapisuje zdarzeń ani nie zmienia czasu aktywności wizyt.
 */
@Service
@RequiredArgsConstructor
public class NavigationAuditEventQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NavigationAuditEventListQueryHandler queryHandler;

    /**
     * Zwraca stronicowaną listę zdarzeń audytu nawigacji zgodnie z filtrami zapytania.
     * Nieprawidłowy rozmiar strony zastępuje wartością 20, a ujemny numer strony wartością 0.
     *
     * @param query filtry i paginacja; null oznacza domyślne zapytanie
     * @return dane strony wraz z metadanymi paginacji
     */
    @Transactional(readOnly = true)
    public PageResult<NavigationAuditEventListDto> findEvents(NavigationAuditEventListQuery query) {
        return queryHandler.execute(normalize(query));
    }

    /**
     * Uzupełnia brakujące zapytanie i koryguje paginację, zachowując przekazane filtry.
     */
    private static NavigationAuditEventListQuery normalize(NavigationAuditEventListQuery query) {
        NavigationAuditEventListQuery normalized = query == null ? new NavigationAuditEventListQuery() : query;

        if (normalized.getSize() < 1) {
            normalized.setSize(DEFAULT_PAGE_SIZE);
        }

        if (normalized.getPage() < 0) {
            normalized.setPage(0);
        }

        return normalized;
    }
}
