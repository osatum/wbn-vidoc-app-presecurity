package com.osatum.wbn.infrastructure.report;

import com.osatum.wbn.application.shared.exception.NavigationAuditReportGenerationException;
import com.osatum.wbn.application.system.dto.NavigationAuditReportRequest;
import com.osatum.wbn.application.system.dto.NavigationAuditReportType;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import com.osatum.wbn.application.system.query.NavigationAuditReportQuery;
import com.osatum.wbn.application.system.query.NavigationAuditReportRow;
import com.osatum.wbn.application.system.service.NavigationAuditReportGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class NavigationAuditReportXlsGenerator implements NavigationAuditReportGenerator {
    private final NavigationAuditReportQuery query;
    private final Clock clock;

    @Override
    public PreparedNavigationAuditReport generate(NavigationAuditReportRequest request, ZoneId zone, int maxRows) {
        if (request.reportType() != NavigationAuditReportType.SERVICE_ENTRIES
                || maxRows < 1 || maxRows >= SpreadsheetVersion.EXCEL97.getMaxRows()) {
            throw new IllegalArgumentException("Invalid XLS report type or row limit.");
        }
        Path path = null;
        try {
            var from = request.dateFrom().atStartOfDay(zone).toInstant();
            var until = request.dateTo().plusDays(1).atStartOfDay(zone).toInstant();
            var preparedAt = clock.instant();
            path = Files.createTempFile("wbn-navigation-entry-report-", ".xls");
            try (var workbook = new HSSFWorkbook()) {
                var dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
                var headerStyle = workbook.createCellStyle();
                var font = workbook.createFont();
                font.setBold(true);
                headerStyle.setFont(font);
                var sheet = workbook.createSheet("Raport");
                String[] headers = {"Instytucja", "Użytkownik", "Typ aktora", "Data wejścia",
                        "Zdarzenie", "Strona", "Szablon trasy", "ID zdarzenia"};
                var header = sheet.createRow(0);
                for (int column = 0; column < headers.length; column++) {
                    header.createCell(column).setCellValue(headers[column]);
                    header.getCell(column).setCellStyle(headerStyle);
                    sheet.setColumnWidth(column, (column >= 5 ? 48 : 32) * 256);
                }
                sheet.createFreezePane(0, 1);
                int rowCount = 0;
                NavigationAuditReportRow cursor = null;
                while (true) {
                    var rows = query.fetch(request.reportType(), from, until, cursor, 1000);
                    if (rows.isEmpty()) {
                        break;
                    }
                    for (var data : rows) {
                        if (++rowCount > maxRows) {
                            throw new IllegalArgumentException("Report row limit exceeded (" + maxRows
                                    + "). Select a shorter date range.");
                        }
                        var row = sheet.createRow(rowCount);
                        row.createCell(0).setCellValue(data.institution() == null ? "Brak instytucji" : data.institution());
                        row.createCell(1).setCellValue(userLabel(data));
                        row.createCell(2).setCellValue(data.actorType().name());
                        row.createCell(3).setCellValue(LocalDateTime.ofInstant(data.occurredAt(), zone));
                        row.getCell(3).setCellStyle(dateStyle);
                        row.createCell(4).setCellValue("Wejście do serwisu");
                        row.createCell(5).setCellValue(data.pageKey() == null ? "" : data.pageKey());
                        row.createCell(6).setCellValue(data.routeTemplate() == null ? "" : data.routeTemplate());
                        row.createCell(7).setCellValue(data.eventId() == null ? "" : data.eventId().toString());
                    }
                    cursor = rows.getLast();
                }
                sheet.setAutoFilter(new CellRangeAddress(0, rowCount, 0, headers.length - 1));
                if (rowCount == 0) {
                    sheet.createRow(1).createCell(0).setCellValue("Brak danych w wybranym zakresie.");
                }
                var info = workbook.createSheet("Informacje");
                text(info, 0, "Typ raportu", request.reportType().name());
                text(info, 1, "Zakres dat (oba dni włącznie)", request.dateFrom() + " — " + request.dateTo());
                text(info, 2, "Strefa czasowa", zone.getId());
                text(info, 3, "Przygotowano", LocalDateTime.ofInstant(preparedAt, zone).toString());
                text(info, 4, "Liczba rekordów", Integer.toString(rowCount));
                text(info, 5, "Limit wierszy danych", Integer.toString(maxRows));
                text(info, 6, "Definicja wejścia", "Jeden wiersz na zapisane SERVICE_ENTRY, także anonimowe. Nie oznacza logowania ani wizyty.");
                text(info, 7, "Pomiar", "Klient zgłasza wejście przy braku poprzedniej aktywności, przerwie ponad 30 minut lub zmianie zakresu dostępu.");
                text(info, 8, "Ograniczenia", "Tylko dostarczone zdarzenia audytu. Nie jest to liczba unikalnych osób ani wszystkich faktycznych odwiedzin.");
                info.setColumnWidth(0, 36 * 256);
                info.setColumnWidth(1, 100 * 256);
                try (var output = Files.newOutputStream(path)) {
                    workbook.write(output);
                }
            }
            return new PreparedNavigationAuditReport(path,
                    "navigation-service_entries-" + request.dateFrom() + "-" + request.dateTo() + ".xls",
                    PreparedNavigationAuditReport.XLS_MEDIA_TYPE);
        } catch (IllegalArgumentException exception) {
            deleteFailedFile(path, exception);
            throw exception;
        } catch (Exception exception) {
            deleteFailedFile(path, exception);
            throw new NavigationAuditReportGenerationException(exception);
        }
    }

    private String userLabel(NavigationAuditReportRow data) {
        if (data.user() != null) {
            return data.user();
        }
        return switch (data.actorType()) {
            case INSTITUTION -> "Dostęp instytucjonalny";
            case ANONYMOUS -> "Użytkownik anonimowy";
            case USER -> "Użytkownik zanonimizowany";
        };
    }

    private void text(Sheet sheet, int index, String label, String value) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void deleteFailedFile(Path path, Exception cause) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception cleanup) {
                cause.addSuppressed(cleanup);
            }
        }
    }
}
