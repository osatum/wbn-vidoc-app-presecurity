package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotIndexed2024ImportServiceTest {
    private final NotIndexed2024ImportService service = new NotIndexed2024ImportService();

    @TempDir
    Path tempDir;

    @Test
    void readsNotIndexedReasonsFromExcelFile() throws Exception {
        Path file = tempDir.resolve("not-indexed.xlsx");
        createNotIndexedFile(file);

        Map<Long, ReasonNotIndexing> reasons = service.readReasons(file.toString());

        assertThat(reasons).hasSize(3);
        assertThat(reasons).containsEntry(123L, ReasonNotIndexing.dec06);
        assertThat(reasons).containsEntry(456L, ReasonNotIndexing.dec07);
        assertThat(reasons).containsKey(789L);
        assertThat(reasons.get(789L)).isNull();
    }

    @Test
    void failsForInvalidReasonKey() throws Exception {
        Path file = tempDir.resolve("not-indexed-invalid.xlsx");
        createInvalidNotIndexedFile(file);

        assertThatThrownBy(() -> service.readReasons(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid ReasonNotIndexing KEY value");
    }

    private void createNotIndexedFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("not-indexed");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("KEY");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue("dec06");

            var second = sheet.createRow(2);
            second.createCell(0).setCellValue("456");
            second.createCell(1).setCellValue("DEC07");

            var third = sheet.createRow(3);
            third.createCell(0).setCellValue(789);
            third.createCell(1).setBlank();

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private void createInvalidNotIndexedFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("not-indexed");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("KEY");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue("unknown");

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }
}
