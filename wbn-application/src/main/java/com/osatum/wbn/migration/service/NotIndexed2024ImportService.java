package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.questionnaire.ReasonNotIndexing;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NotIndexed2024ImportService {

    public Map<Long, ReasonNotIndexing> readReasons(String sourcePath) throws Exception {
        Map<Long, ReasonNotIndexing> reasons = new LinkedHashMap<>();
        for (File file : resolveExcelFiles(sourcePath)) {
            readRecords(file).forEach(record -> reasons.put(record.journalId(), record.reasonNotIndexing()));
        }
        return Collections.unmodifiableMap(reasons);
    }

    List<NotIndexed2024Record> readRecords(File file) throws Exception {
        Map<Long, NotIndexed2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                columns[0] = headerColumns(row, file);
                return;
            }
            Long journalId = readJournalId(row.value(columns[0].journalId()));
            if (journalId == null) {
                return;
            }
            ReasonNotIndexing reason = readReason(row.value(columns[0].key()));
            records.put(journalId, new NotIndexed2024Record(
                    journalId,
                    reason,
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    private List<File> resolveExcelFiles(String sourcePath) {
        File source = Path.of(sourcePath).toFile();
        if (!source.exists()) {
            throw new IllegalStateException("Cannot find not-indexed import path: " + sourcePath);
        }
        if (source.isFile()) {
            if (!isExcelFile(source)) {
                throw new IllegalStateException("Not-indexed import path is not an Excel file: " + sourcePath);
            }
            return List.of(source);
        }

        File[] files = source.listFiles(file -> file.isFile() && isExcelFile(file));
        if (files == null) {
            throw new IllegalStateException("Cannot read not-indexed import directory: " + sourcePath);
        }
        return Arrays.stream(files)
                .sorted(Comparator.comparing(File::getName))
                .toList();
    }

    private HeaderColumns headerColumns(
            MigrationExcelReader.RowData header,
            File file
    ) {
        Integer journalId = null;
        Integer key = null;

        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            String value = normalizedHeader(cell.getValue());
            if ("journalid".equals(value)) {
                journalId = cellIndex;
            } else if ("key".equals(value)) {
                key = cellIndex;
            }
        }

        if (journalId == null || key == null) {
            throw new IllegalStateException("Not-indexed file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, key);
    }

    private Long readJournalId(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumber(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid journal_id value: " + value, e);
        }
    }

    private ReasonNotIndexing readReason(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return Arrays.stream(ReasonNotIndexing.values())
                .filter(reason -> reason.name().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid ReasonNotIndexing KEY value: " + value));
    }

    private String normalizeNumber(String value) {
        return value.trim()
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(',', '.');
    }

    private String normalizedHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean isExcelFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".xls") || name.endsWith(".xlsx");
    }

    public record NotIndexed2024Record(
            Long journalId,
            ReasonNotIndexing reasonNotIndexing,
            String sourceFile,
            int rowNumber
    ) {
    }

    private record HeaderColumns(int journalId, int key) {
    }
}
