package com.osatum.wbn.migration.service;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class MigrationExcelReader {
    private MigrationExcelReader() {
    }

    static void readFirstSheet(File file, Consumer<RowData> rowConsumer) throws Exception {
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            readXlsx(file, rowConsumer);
            return;
        }
        readWorkbook(file, rowConsumer);
    }

    private static void readXlsx(File file, Consumer<RowData> rowConsumer) throws Exception {
        try (OPCPackage openPackage = OPCPackage.open(file, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(openPackage, true);
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                return;
            }
            try (InputStream sheet = sheets.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(
                        reader.getStylesTable(),
                        reader.getSharedStringsTable(),
                        new StreamingSheetHandler(rowConsumer),
                        new DataFormatter(Locale.ROOT),
                        false
                ));
                parser.parse(new InputSource(sheet));
            }
        }
    }

    private static void readWorkbook(File file, Consumer<RowData> rowConsumer) throws Exception {
        try (FileInputStream inputStream = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                return;
            }
            var sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return;
            }
            var formatter = new DataFormatter(Locale.ROOT);
            var evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Map<Integer, String> values = new LinkedHashMap<>();
                for (
                        int columnIndex = row.getFirstCellNum();
                        columnIndex >= 0 && columnIndex < row.getLastCellNum();
                        columnIndex++
                ) {
                    String value = formatter.formatCellValue(row.getCell(columnIndex), evaluator);
                    if (value != null && !value.isBlank()) {
                        values.put(columnIndex, value);
                    }
                }
                rowConsumer.accept(new RowData(rowIndex, values));
            }
        }
    }

    record RowData(int rowIndex, Map<Integer, String> values) {
        RowData {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        String value(int columnIndex) {
            return values.get(columnIndex);
        }
    }

    private static final class StreamingSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Consumer<RowData> rowConsumer;
        private Map<Integer, String> values;

        private StreamingSheetHandler(Consumer<RowData> rowConsumer) {
            this.rowConsumer = rowConsumer;
        }

        @Override
        public void startRow(int rowIndex) {
            values = new LinkedHashMap<>();
        }

        @Override
        public void endRow(int rowIndex) {
            rowConsumer.accept(new RowData(rowIndex, values));
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null || formattedValue == null || formattedValue.isBlank()) {
                return;
            }
            values.put((int) new CellReference(cellReference).getCol(), formattedValue);
        }
    }
}
