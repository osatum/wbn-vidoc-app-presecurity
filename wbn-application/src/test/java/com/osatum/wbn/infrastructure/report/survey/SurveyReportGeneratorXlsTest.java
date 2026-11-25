package com.osatum.wbn.infrastructure.report.survey;

import com.osatum.wbn.application.report.survey.config.SurveyReportProperties;
import com.osatum.wbn.application.report.survey.dto.JournalReportDto;
import com.osatum.wbn.application.report.survey.dto.PublishingStabilityReportDto;
import com.osatum.wbn.application.report.survey.dto.PublishingVersionsReportDto;
import com.osatum.wbn.application.report.survey.dto.SurveyReportDto;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SurveyReportGeneratorXlsTest {

    private final SurveyReportGeneratorXls generator = new SurveyReportGeneratorXls(properties());

    @Test
    void fillsJournalSheetFromTemplate() throws Exception {
        JournalReportDto journal = new JournalReportDto();
        journal.setJournalId("123");
        journal.setPissn("1234-5678");
        journal.setPrintVersion("TAK");
        journal.setElectronicVersion("NIE");

        SurveyReportDto reportDto = new SurveyReportDto();
        reportDto.setJournals(List.of(journal));

        byte[] report = generate(reportDto, "pl");

        assertThat(report).isNotEmpty();

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(report))) {
            var row = workbook.getSheetAt(0).getRow(3);

            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("123");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("1234-5678");
            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("TAK");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("NIE");
            assertThinBlackBorder(row.getCell(0).getCellStyle());
            assertThinBlackBorder(row.getCell(9).getCellStyle());
        }
    }

    @Test
    void writesBooleanLabelsPreparedByAssembler() throws Exception {
        JournalReportDto journal = new JournalReportDto();
        journal.setJournalId("123");
        journal.setPrintVersion("YES");
        journal.setElectronicVersion("NO");

        SurveyReportDto reportDto = new SurveyReportDto();
        reportDto.setJournals(List.of(journal));

        byte[] report = generate(reportDto, "en");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(report))) {
            var row = workbook.getSheetAt(0).getRow(3);

            assertThat(row.getCell(9).getStringCellValue()).isEqualTo("YES");
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("NO");
        }
    }

    @Test
    void fillsPublishingVersionsSheetUsingExplicitColumns() throws Exception {
        PublishingVersionsReportDto publishingVersions = new PublishingVersionsReportDto();
        publishingVersions.setJournalId("123");
        publishingVersions.setElectronicVersion("YES");
        publishingVersions.setArticlesFormats("PDF; XML");
        publishingVersions.setElectronicVersionWww("https://journal.example");
        publishingVersions.setAop("NO");
        publishingVersions.setElectronicVersionsPercentage("75.5");

        SurveyReportDto reportDto = new SurveyReportDto();
        reportDto.setPublishingVersions(List.of(publishingVersions));

        byte[] report = generate(reportDto, "en");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(report))) {
            var row = workbook.getSheetAt(5).getRow(4);

            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("123");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("YES");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("PDF; XML");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("https://journal.example");
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("NO");
            assertThat(row.getCell(12).getStringCellValue()).isEqualTo("75.5");
        }
    }

    @Test
    void streamsFiveThousandRowsBeyondMemoryWindow() throws Exception {
        List<JournalReportDto> journals = IntStream.rangeClosed(1, 5_000)
                .mapToObj(index -> {
                    JournalReportDto journal = new JournalReportDto();
                    journal.setJournalId(Integer.toString(index));
                    journal.setTitleOrginal("Journal " + index);
                    return journal;
                })
                .toList();
        SurveyReportDto reportDto = new SurveyReportDto();
        reportDto.setJournals(journals);

        byte[] report = generate(reportDto, "pl");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(report))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("1");
            assertThat(sheet.getRow(5_002).getCell(0).getStringCellValue()).isEqualTo("5000");
        }
    }

    @Test
    void preservesRowsFollowingStreamedDataSection() throws Exception {
        List<PublishingStabilityReportDto> publishingStabilities = IntStream.rangeClosed(1, 150)
                .mapToObj(index -> {
                    PublishingStabilityReportDto row = new PublishingStabilityReportDto();
                    row.setJournalId(Integer.toString(index));
                    return row;
                })
                .toList();
        SurveyReportDto reportDto = new SurveyReportDto();
        reportDto.setPublishingStabilities(publishingStabilities);

        byte[] report = generate(reportDto, "pl");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(report))) {
            var sheet = workbook.getSheetAt(6);
            assertThat(sheet.getRow(5).getCell(0).getStringCellValue()).isEqualTo("1");
            assertThat(sheet.getRow(154).getCell(0).getStringCellValue()).isEqualTo("150");
            assertThat(sheet.getRow(156)).isNotNull();
            assertThat(sheet.getRow(157)).isNotNull();
        }
    }

    private byte[] generate(SurveyReportDto reportDto, String language) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        generator.generate(reportDto, 2024, language, output);
        return output.toByteArray();
    }

    private static void assertThinBlackBorder(CellStyle style) {
        short black = IndexedColors.BLACK.getIndex();
        assertThat(style.getBorderTop()).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderRight()).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderBottom()).isEqualTo(BorderStyle.THIN);
        assertThat(style.getBorderLeft()).isEqualTo(BorderStyle.THIN);
        assertThat(style.getTopBorderColor()).isEqualTo(black);
        assertThat(style.getRightBorderColor()).isEqualTo(black);
        assertThat(style.getBottomBorderColor()).isEqualTo(black);
        assertThat(style.getLeftBorderColor()).isEqualTo(black);
    }

    private static SurveyReportProperties properties() {
        SurveyReportProperties properties = new SurveyReportProperties();
        properties.setTemplates(Map.of(
                2024,
                Map.of(
                        "pl", "template/questionaris-2024-xls-pl.xlsx",
                        "en", "template/questionaris-2024-xls-en.xlsx"
                )
        ));
        return properties;
    }
}
