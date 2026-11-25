package com.osatum.wbn.infrastructure.report;

import com.osatum.wbn.application.shared.exception.NavigationAuditReportGenerationException;
import com.osatum.wbn.application.system.dto.NavigationAuditReportRequest;
import com.osatum.wbn.application.system.dto.NavigationAuditReportType;
import com.osatum.wbn.application.system.query.NavigationAuditReportQuery;
import com.osatum.wbn.application.system.query.NavigationAuditReportRow;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import com.osatum.wbn.application.system.service.NavigationAuditReportGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NavigationAuditReportXlsxGenerator implements NavigationAuditReportGenerator {
    private final NavigationAuditReportQuery query;
    private final Clock clock;

    @Override
    public PreparedNavigationAuditReport generate(NavigationAuditReportRequest request, ZoneId reportZone, int maxRows) {
        Path path = null;
        try {
            var from = request.dateFrom().atStartOfDay(reportZone).toInstant();
            var until = request.dateTo().plusDays(1).atStartOfDay(reportZone).toInstant();
            var preparedAt = clock.instant();
            path = Files.createTempFile("wbn-navigation-report-", ".xlsx");
            var workbook = new SXSSFWorkbook(100);
            try {
                workbook.setCompressTempFiles(true);
                var dateStyle = workbook.createCellStyle();
                dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
                var durationStyle = workbook.createCellStyle();
                durationStyle.setDataFormat(workbook.createDataFormat().getFormat("[h]:mm:ss"));
                var headerStyle = workbook.createCellStyle();
                var font = workbook.createFont();
                font.setBold(true);
                headerStyle.setFont(font);
                Sheet sheet = workbook.createSheet("Raport");
                String[] headers = headers(request.reportType());
                var header = sheet.createRow(0);
                for (int column = 0; column < headers.length; column++) {
                    header.createCell(column).setCellValue(headers[column]);
                    header.getCell(column).setCellStyle(headerStyle);
                    sheet.setColumnWidth(column, (column == 3 ? 60 : 32) * 256);
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
                            throw new IllegalArgumentException("Report row limit exceeded. Select a shorter date range.");
                        }
                        var row = sheet.createRow(rowCount);
                        row.createCell(0).setCellValue(data.institution() == null ? "Brak instytucji" : data.institution());
                        row.createCell(1).setCellValue(data.user() != null ? data.user()
                                : data.actorType().name().equals("INSTITUTION") ? "Dostęp instytucjonalny" : "Użytkownik zanonimizowany");
                        row.createCell(2).setCellValue(LocalDateTime.ofInstant(data.occurredAt(), reportZone));
                        row.getCell(2).setCellStyle(dateStyle);
                        if (request.reportType() == NavigationAuditReportType.VISITS) {
                            row.createCell(3).setCellValue(Duration.between(data.occurredAt(), data.lastActivityAt()).toMillis() / 86400000.0);
                            row.getCell(3).setCellStyle(durationStyle);
                            row.createCell(4).setCellValue(data.endReason() == null ? "Otwarta" : data.endReason());
                        } else {
                            row.createCell(3).setCellValue(data.description() == null ? "" : data.description());
                        }
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
                text(info, 2, "Strefa czasowa", reportZone.getId());
                text(info, 3, "Przygotowano", LocalDateTime.ofInstant(preparedAt, reportZone).toString());
                text(info, 4, "Liczba rekordów", Integer.toString(rowCount));
                text(info, 5, "Definicja wizyty", "Czas do ostatniej aktywności, bez doliczania bezczynności. Wizyty wybierane po dacie rozpoczęcia.");
                text(info, 6, "Dane wizyt dostępne od", query.firstVisitAt().map(value -> LocalDateTime.ofInstant(value, reportZone).toString()).orElse("Brak zarejestrowanych wizyt"));
                text(info, 7, "Dane historyczne", "Brak wizyt przed uruchomieniem nowego pomiaru nie oznacza braku aktywności.");
                text(info, 8, "Pobranie", "Zdarzenie oznacza uruchomienie pobrania, nie potwierdza zapisu pliku na dysku.");
                info.setColumnWidth(0, 36 * 256);
                info.setColumnWidth(1, 100 * 256);
                try (var output = Files.newOutputStream(path)) {
                    workbook.write(output);
                }
            } finally {
                try {
                    for (Sheet sheet : workbook) {
                        ((SXSSFSheet) sheet).flushRows();
                    }
                } finally {
                    workbook.close();
                }
            }
            return new PreparedNavigationAuditReport(path, "navigation-" + request.reportType().name().toLowerCase(Locale.ROOT)
                    + "-" + request.dateFrom() + "-" + request.dateTo() + ".xlsx");
        } catch (IllegalArgumentException exception) {
            deleteFailedFile(path, exception);
            throw exception;
        } catch (Exception exception) {
            deleteFailedFile(path, exception);
            throw new NavigationAuditReportGenerationException(exception);
        }
    }

    private String[] headers(NavigationAuditReportType type) {
        return switch (type) {
            case VISITS -> new String[]{"Instytucja", "Użytkownik", "Data rozpoczęcia wizyty", "Czas trwania wizyty", "Status / powód zakończenia"};
            case PAGE_VIEWS -> new String[]{"Instytucja", "Użytkownik", "Data", "Zdarzenie"};
            case FILE_DOWNLOADS -> new String[]{"Instytucja", "Użytkownik", "Data", "Pobranie XLS/XLSX"};
            case SERVICE_ENTRIES -> throw new IllegalArgumentException("Service entries require XLS format.");
        };
    }

    private void text(Sheet sheet, int index, String label, String value) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private void deleteFailedFile(Path path, Exception exception) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
        }
    }
}
