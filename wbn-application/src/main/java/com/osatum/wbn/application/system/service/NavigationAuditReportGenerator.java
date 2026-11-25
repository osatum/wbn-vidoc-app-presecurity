package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.NavigationAuditReportRequest;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import java.time.ZoneId;

/**
 * Kontrakt generatora pliku raportowego, niezależny od warstwy HTTP i implementacji formatu arkusza.
 */
public interface NavigationAuditReportGenerator {
    /**
     * Przygotowuje plik dla wybranego typu raportu i całego zakresu dat, niezależnie od strony listy w interfejsie.
     *
     * @param request typ raportu oraz zakres dni
     * @param zone strefa czasowa używana do interpretacji dat i prezentacji czasu w raporcie
     * @param maxRows maksymalna liczba wierszy danych, bez nagłówka
     * @return gotowy plik tymczasowy, usuwany po zamknięciu zasobu lub jego strumienia
     */
    PreparedNavigationAuditReport generate(NavigationAuditReportRequest request, ZoneId zone, int maxRows);
}
