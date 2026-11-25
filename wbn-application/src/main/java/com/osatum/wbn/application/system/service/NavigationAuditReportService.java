package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.NavigationAuditReportRequest;
import com.osatum.wbn.application.system.dto.NavigationAuditReportType;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Sprawdza parametry eksportu i przekazuje generatorowi typ raportu, zakres dat oraz limity.
 * Raport powstaje w transakcji tylko do odczytu; nie zmienia zdarzeń, wizyt ani sesji logowania.
 */
@Service
@RequiredArgsConstructor
public class NavigationAuditReportService {
    private static final int XLS_MAX_DATA_ROWS = 65_535;
    private final NavigationAuditReportGenerator generator;
    @Value("${app.navigation-audit.reports.zone:Europe/Warsaw}")
    private String zone = "Europe/Warsaw";
    @Value("${app.navigation-audit.reports.max-days:366}")
    private int maxDays = 366;
    @Value("${app.navigation-audit.reports.max-rows:100000}")
    private int maxRows = 100000;

    /**
     * Przygotowuje raport za zakres obejmujący zarówno dzień początkowy, jak i końcowy.
     * Izolacja REPEATABLE_READ zapewnia spójny obraz danych odczytywanych przez generator w kolejnych partiach.
     * Limit wierszy dotyczy danych, z pominięciem nagłówka zajmującego jeden wiersz arkusza.
     *
     * @param request wymagany typ raportu oraz obie daty graniczne
     * @return gotowy plik tymczasowy; odbiorca odpowiada za jego zamknięcie i usunięcie
     * @throws IllegalArgumentException gdy brakuje parametrów lub zakres przekracza dozwoloną liczbę dni
     * @throws IllegalStateException gdy skonfigurowane limity raportu są nieprawidłowe
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PreparedNavigationAuditReport export(NavigationAuditReportRequest request) {
        if (maxRows < 1 || maxRows > 1048575 || maxDays < 1) {
            throw new IllegalStateException("Invalid report limits.");
        }
        if (request == null || request.dateFrom() == null || request.dateTo() == null || request.reportType() == null) {
            throw new IllegalArgumentException("Report type and both dates are required.");
        }
        if (request.dateTo().isBefore(request.dateFrom())
                || ChronoUnit.DAYS.between(request.dateFrom(), request.dateTo()) >= maxDays) {
            throw new IllegalArgumentException("Invalid report date range. Maximum number of days: " + maxDays);
        }
        int effectiveMaxRows = request.reportType() == NavigationAuditReportType.SERVICE_ENTRIES
                ? Math.min(maxRows, XLS_MAX_DATA_ROWS) : maxRows;
        return generator.generate(request, ZoneId.of(zone), effectiveMaxRows);
    }
}
