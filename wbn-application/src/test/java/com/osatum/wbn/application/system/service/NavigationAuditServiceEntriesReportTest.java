package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.system.dto.NavigationAuditReportRequest;
import com.osatum.wbn.application.system.dto.NavigationAuditReportType;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import com.osatum.wbn.application.system.query.NavigationAuditReportQuery;
import com.osatum.wbn.application.system.query.NavigationAuditReportRow;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportGeneratorRouter;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportXlsGenerator;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportXlsxGenerator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NavigationAuditServiceEntriesReportTest {
    private final NavigationAuditReportQuery query = mock(NavigationAuditReportQuery.class);
    private final Instant now = Instant.parse("2026-10-05T10:00:00Z");
    private final NavigationAuditReportService service = new NavigationAuditReportService(new NavigationAuditReportGeneratorRouter(
            new NavigationAuditReportXlsxGenerator(query, Clock.fixed(now, ZoneOffset.UTC)),
            new NavigationAuditReportXlsGenerator(query, Clock.fixed(now, ZoneOffset.UTC))));

    @Test
    void exportsRealXlsWithAllColumnsSafeTextAndEntryMetadata() throws Exception {
        var rows = List.of(row(1, NavigationAuditActorType.USER, "=test@example.test"),
                row(2, NavigationAuditActorType.ANONYMOUS, null),
                row(3, NavigationAuditActorType.INSTITUTION, null), row(4, NavigationAuditActorType.USER, null));
        when(query.fetch(eq(NavigationAuditReportType.SERVICE_ENTRIES), any(), any(), isNull(), eq(1000))).thenReturn(rows);
        var report = service.export(request());
        assertThat(report.filename()).isEqualTo("navigation-service_entries-2026-10-05-2026-10-05.xls");
        assertThat(report.mediaType()).isEqualTo(PreparedNavigationAuditReport.XLS_MEDIA_TYPE);
        try (var input = report.openStream(); var workbook = new HSSFWorkbook(input)) {
            var sheet = workbook.getSheet("Raport");
            assertThat(sheet.getLastRowNum()).isEqualTo(4);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 8);
            var data = sheet.getRow(1);
            assertThat(data.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(data.getCell(0).getStringCellValue()).isEqualTo("=Instytucja Łódź");
            assertThat(data.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(data.getCell(1).getStringCellValue()).isEqualTo("=test@example.test");
            assertThat(data.getCell(2).getStringCellValue()).isEqualTo("USER");
            assertThat(data.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(data.getCell(3).getLocalDateTimeCellValue()).isEqualTo(LocalDateTime.parse("2026-10-05T12:00:00"));
            assertThat(data.getCell(4).getStringCellValue()).isEqualTo("Wejście do serwisu");
            assertThat(data.getCell(5).getStringCellValue()).isEqualTo("public.home");
            assertThat(data.getCell(6).getStringCellValue()).isEqualTo("/");
            assertThat(data.getCell(7).getStringCellValue()).isEqualTo(new UUID(0, 1).toString());
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("Użytkownik anonimowy");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("Dostęp instytucjonalny");
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Użytkownik zanonimizowany");
            var info = workbook.getSheet("Informacje");
            assertThat(info.getRow(4).getCell(1).getStringCellValue()).isEqualTo("4");
            assertThat(info.getRow(5).getCell(1).getStringCellValue()).isEqualTo("65535");
            assertThat(info.getRow(6).getCell(1).getStringCellValue()).contains("SERVICE_ENTRY");
            assertThat(info.getRow(8).getCell(1).getStringCellValue()).contains("unikalnych osób");
        }
        assertThat(report.path()).doesNotExist();
        verify(query, never()).firstVisitAt();
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29,2026-03-28T23:00:00Z,2026-03-29T22:00:00Z",
            "2026-10-25,2026-10-24T22:00:00Z,2026-10-25T23:00:00Z"})
    void usesBothDstDayBoundaries(String date, String from, String until) throws Exception {
        try (var report = service.export(new NavigationAuditReportRequest(LocalDate.parse(date), LocalDate.parse(date),
                NavigationAuditReportType.SERVICE_ENTRIES))) {
            verify(query).fetch(NavigationAuditReportType.SERVICE_ENTRIES, Instant.parse(from), Instant.parse(until), null, 1000);
            assertThat(report.path()).exists();
        }
    }

    @Test
    void exportsAllBatchesAndHonorsLowerConfiguredLimit() throws Exception {
        stubRows(1205);
        ReflectionTestUtils.setField(service, "maxRows", 1205);
        try (var report = service.export(request()); var input = Files.newInputStream(report.path());
             var workbook = new HSSFWorkbook(input)) {
            assertThat(workbook.getSheet("Raport").getLastRowNum()).isEqualTo(1205);
            assertThat(workbook.getSheet("Raport").getRow(1205).getCell(7).getStringCellValue())
                    .isEqualTo(new UUID(0, 1205).toString());
            assertThat(workbook.getSheet("Informacje").getRow(5).getCell(1).getStringCellValue()).isEqualTo("1205");
        }
        ReflectionTestUtils.setField(service, "maxRows", 1204);
        var filesBefore = temporaryReports();
        assertThatThrownBy(() -> service.export(request())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1204");
        assertThat(temporaryReports()).containsExactlyInAnyOrderElementsOf(filesBefore);
    }

    @Test
    void accepts65535RowsAndRejects65536WithoutLeakingTemporaryFiles() throws Exception {
        stubRows(65535);
        long started = System.nanoTime();
        long heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        try (var report = service.export(request())) {
            long generateMillis = (System.nanoTime() - started) / 1_000_000;
            long heapAfterGenerate = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            try (var input = Files.newInputStream(report.path()); var workbook = new HSSFWorkbook(input)) {
                assertThat(workbook.getSheet("Raport").getLastRowNum()).isEqualTo(65535);
                assertThat(workbook.getSheet("Raport").getRow(65535).getCell(7).getStringCellValue())
                        .isEqualTo(new UUID(0, 65535).toString());
            }
            System.out.printf("SERVICE_ENTRIES XLS boundary: rows=65535, bytes=%d, generate-ms=%d, generate-and-read-ms=%d, heap-before-mib=%d, heap-after-generate-mib=%d%n",
                    Files.size(report.path()), generateMillis, (System.nanoTime() - started) / 1_000_000,
                    heapBefore / 1024 / 1024, heapAfterGenerate / 1024 / 1024);
        }
        stubRows(65536);
        var filesBefore = temporaryReports();
        assertThatThrownBy(() -> service.export(request())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("65535");
        assertThat(temporaryReports()).containsExactlyInAnyOrderElementsOf(filesBefore);
    }

    @Test
    void cleansUpWhenReadingReportDataFails() throws Exception {
        var filesBefore = temporaryReports();
        when(query.fetch(any(), any(), any(), any(), anyInt())).thenThrow(new IllegalStateException("Query unavailable"));
        assertThatThrownBy(() -> service.export(request()))
                .isInstanceOf(com.osatum.wbn.application.shared.exception.NavigationAuditReportGenerationException.class);
        assertThat(temporaryReports()).containsExactlyInAnyOrderElementsOf(filesBefore);
    }

    private void stubRows(long count) {
        when(query.fetch(eq(NavigationAuditReportType.SERVICE_ENTRIES), any(), any(), any(), eq(1000))).thenAnswer(invocation -> {
            NavigationAuditReportRow cursor = invocation.getArgument(3);
            long firstId = cursor == null ? 1 : cursor.id() + 1;
            return LongStream.range(firstId, Math.min(firstId + 1000, count + 1))
                    .mapToObj(id -> row(id, NavigationAuditActorType.ANONYMOUS, null)).toList();
        });
    }

    private NavigationAuditReportRow row(long id, NavigationAuditActorType actor, String user) {
        return new NavigationAuditReportRow(id, now, "=Instytucja Łódź", user, actor, null,
                "Wejście do serwisu", null, new UUID(0, id), "public.home", "/");
    }

    private NavigationAuditReportRequest request() {
        return new NavigationAuditReportRequest(LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-05"), NavigationAuditReportType.SERVICE_ENTRIES);
    }

    private List<Path> temporaryReports() throws Exception {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(path -> path.getFileName().toString().startsWith("wbn-navigation-entry-report-")).toList();
        }
    }
}
