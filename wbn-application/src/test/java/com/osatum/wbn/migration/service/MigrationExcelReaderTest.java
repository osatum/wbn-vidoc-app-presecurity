package com.osatum.wbn.migration.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationExcelReaderTest {

    @TempDir
    private Path tempDir;

    @Test
    void streamsFiveThousandDataRowsFromXlsx() throws Exception {
        Path file = tempDir.resolve("records.xlsx");
        createFile(file);
        AtomicInteger rows = new AtomicInteger();
        AtomicReference<String> lastJournalId = new AtomicReference<>();

        MigrationExcelReader.readFirstSheet(file.toFile(), row -> {
            rows.incrementAndGet();
            lastJournalId.set(row.value(0));
        });

        assertThat(rows).hasValue(5_001);
        assertThat(lastJournalId).hasValue("5000");
    }

    @Test
    void readsLegacyXlsWithTheSameRowContract() throws Exception {
        Path file = tempDir.resolve("records.xls");
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            var sheet = workbook.createSheet("records");
            sheet.createRow(0).createCell(0).setCellValue("journal_id");
            sheet.createRow(1).createCell(0).setCellValue(123);
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
        AtomicReference<String> journalId = new AtomicReference<>();

        MigrationExcelReader.readFirstSheet(file.toFile(), row -> journalId.set(row.value(0)));

        assertThat(journalId).hasValue("123");
    }

    private void createFile(Path file) throws Exception {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            var sheet = workbook.createSheet("records");
            sheet.createRow(0).createCell(0).setCellValue("journal_id");
            for (int index = 1; index <= 5_000; index++) {
                sheet.createRow(index).createCell(0).setCellValue(index);
            }
            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            } finally {
                workbook.dispose();
            }
        }
    }
}
