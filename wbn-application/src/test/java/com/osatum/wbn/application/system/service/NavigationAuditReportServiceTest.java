package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.*;
import com.osatum.wbn.application.system.query.*;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportXlsxGenerator;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportXlsGenerator;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportGeneratorRouter;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NavigationAuditReportServiceTest {
    private final NavigationAuditReportQuery query = mock(NavigationAuditReportQuery.class);
    private final Instant now = Instant.parse("2026-10-03T10:00:00Z");
    private final NavigationAuditReportService service = new NavigationAuditReportService(new NavigationAuditReportGeneratorRouter(
            new NavigationAuditReportXlsxGenerator(query, Clock.fixed(now, ZoneOffset.UTC)),
            new NavigationAuditReportXlsGenerator(query, Clock.fixed(now, ZoneOffset.UTC))));

    @Test
    void exportsNumericDatesAndDurationsAndTextNotFormulas() throws Exception {
        var data = new NavigationAuditReportRow(1L, now, "=1+1", "+test@example.test",
                NavigationAuditActorType.USER, now.plusSeconds(1200), null, null);
        when(query.fetch(eq(NavigationAuditReportType.VISITS), any(), any(), isNull(), eq(1000))).thenReturn(List.of(data));
        when(query.firstVisitAt()).thenReturn(Optional.of(now));
        var report = service.export(request(NavigationAuditReportType.VISITS));
        try (var input = report.openStream(); var workbook = new XSSFWorkbook(input)) {
            var row = workbook.getSheet("Raport").getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("=1+1");
            assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(2).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.parse("2026-10-03T12:00:00"));
            assertThat(row.getCell(3).getNumericCellValue()).isCloseTo(1200.0 / 86400, within(0.000001));
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("Otwarta");
        }
        assertThat(Files.exists(report.path())).isFalse();
        verify(query).fetch(NavigationAuditReportType.VISITS, Instant.parse("2026-10-02T22:00:00Z"),
                Instant.parse("2026-10-03T22:00:00Z"), data, 1000);
    }

    @Test
    void exportsEveryRowAcrossMultipleBatches() throws Exception {
        var rows = java.util.stream.LongStream.rangeClosed(1, 1205).mapToObj(id -> new NavigationAuditReportRow(
                id, now, "Test", "test@example.test", NavigationAuditActorType.USER, now, null, "INACTIVITY")).toList();
        when(query.fetch(eq(NavigationAuditReportType.VISITS), any(), any(), isNull(), eq(1000))).thenReturn(rows.subList(0, 1000));
        when(query.fetch(eq(NavigationAuditReportType.VISITS), any(), any(), eq(rows.get(999)), eq(1000))).thenReturn(rows.subList(1000, 1205));
        try (var report = service.export(request(NavigationAuditReportType.VISITS)); var input = Files.newInputStream(report.path());
             var workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getSheet("Raport").getLastRowNum()).isEqualTo(1205);
            assertThat(workbook.getSheet("Raport").getRow(1205).getCell(1).getStringCellValue()).isEqualTo("test@example.test");
        }
    }

    @Test
    void usesCalendarDayBoundariesAcrossDst() throws Exception {
        try (var report = service.export(new NavigationAuditReportRequest(LocalDate.parse("2026-03-29"),
                LocalDate.parse("2026-03-29"), NavigationAuditReportType.PAGE_VIEWS))) {
            verify(query).fetch(NavigationAuditReportType.PAGE_VIEWS, Instant.parse("2026-03-28T23:00:00Z"),
                    Instant.parse("2026-03-29T22:00:00Z"), null, 1000);
            assertThat(Files.size(report.path())).isGreaterThan(0);
        }
    }

    @Test
    void createsValidEmptyWorkbookForEachType() throws Exception {
        for (var type : NavigationAuditReportType.values()) {
            try (var report = service.export(request(type)); var input = Files.newInputStream(report.path());
                 var workbook = WorkbookFactory.create(input)) {
                assertThat(workbook.getSheet("Raport").getRow(1).getCell(0).getStringCellValue()).contains("Brak danych");
                assertThat(workbook.getSheet("Informacje")).isNotNull();
                assertThat(report.filename()).endsWith(type == NavigationAuditReportType.SERVICE_ENTRIES ? ".xls" : ".xlsx");
                assertThat(report.mediaType()).isEqualTo(type == NavigationAuditReportType.SERVICE_ENTRIES
                        ? PreparedNavigationAuditReport.XLS_MEDIA_TYPE : PreparedNavigationAuditReport.XLSX_MEDIA_TYPE);
            }
        }
    }

    @Test
    void rejectsInvalidRangesAndTooManyRowsWithoutTruncation() {
        assertThatThrownBy(() -> service.export(new NavigationAuditReportRequest(LocalDate.parse("2026-10-04"),
                LocalDate.parse("2026-10-03"), NavigationAuditReportType.VISITS))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.export(new NavigationAuditReportRequest(LocalDate.parse("2024-01-01"),
                LocalDate.parse("2026-10-03"), NavigationAuditReportType.VISITS))).isInstanceOf(IllegalArgumentException.class);
        ReflectionTestUtils.setField(service, "maxRows", 1);
        var data = new NavigationAuditReportRow(1L, now, null, null, NavigationAuditActorType.INSTITUTION, now, null, null);
        when(query.fetch(any(), any(), any(), isNull(), anyInt())).thenReturn(List.of(data, data));
        assertThatThrownBy(() -> service.export(request(NavigationAuditReportType.VISITS)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("limit");
    }

    private NavigationAuditReportRequest request(NavigationAuditReportType type) {
        return new NavigationAuditReportRequest(LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-03"), type);
    }
}
