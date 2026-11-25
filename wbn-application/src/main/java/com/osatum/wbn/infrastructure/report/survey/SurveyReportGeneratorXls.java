package com.osatum.wbn.infrastructure.report.survey;

import com.osatum.wbn.application.report.survey.config.SurveyReportProperties;
import com.osatum.wbn.application.report.survey.dto.*;
import com.osatum.wbn.application.report.survey.sevice.SurveyReportGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class SurveyReportGeneratorXls implements SurveyReportGenerator {

    private final SurveyReportProperties properties;
    private static final String EMPTY_CELL_VALUE = "-";
    private static final int ROW_ACCESS_WINDOW_SIZE = 100;

    private static final List<SheetDefinition<?>> SHEETS = List.of(
            sheet(0, 3, SurveyReportDto::getJournals,
                    JournalReportDto::getJournalId,
                    JournalReportDto::getPissn,
                    JournalReportDto::getEissn,
                    JournalReportDto::getTitleOrginal,
                    JournalReportDto::getTitleEnglish,
                    JournalReportDto::getTitleShort,
                    JournalReportDto::getWebsite,
                    JournalReportDto::getCountry,
                    JournalReportDto::getKind,
                    JournalReportDto::getPrintVersion,
                    JournalReportDto::getElectronicVersion,
                    JournalReportDto::getPaidArticles
            ),
            sheet(1, 4, SurveyReportDto::getScientificProfiles,
                    ScientificProfileReportDto::getJournalId,
                    ScientificProfileReportDto::getMainScientificProfile,
                    ScientificProfileReportDto::getOtherScientificProfiles,
                    ScientificProfileReportDto::getMainTrCategory,
                    ScientificProfileReportDto::getOtherTrCategories,
                    ScientificProfileReportDto::getMainAsjcCategory,
                    ScientificProfileReportDto::getOtherAsjcCategories
            ),
            sheet(2, 3, SurveyReportDto::getEditorialAddresses,
                    EditorialAddressReportDto::getJournalId,
                    EditorialAddressReportDto::getEditorial,
                    EditorialAddressReportDto::getAddress,
                    EditorialAddressReportDto::getPhone,
                    EditorialAddressReportDto::getEmail
            ),
            sheet(3, 3, SurveyReportDto::getEditorialBoards,
                    EditorialBoardReportDto::getJournalId,
                    EditorialBoardReportDto::getFunction,
                    EditorialBoardReportDto::getTitle,
                    EditorialBoardReportDto::getFullName,
                    EditorialBoardReportDto::getInstitution
            ),
            sheet(4, 3, SurveyReportDto::getPublishers,
                    PublisherReportDto::getJournalId,
                    PublisherReportDto::getPublisher,
                    PublisherReportDto::getAddress,
                    PublisherReportDto::getPhone,
                    PublisherReportDto::getEmail
            ),
            sheet(5, 4, SurveyReportDto::getPublishingVersions,
                    PublishingVersionsReportDto::getJournalId,
                    PublishingVersionsReportDto::getElectronicVersion,
                    PublishingVersionsReportDto::getArticlesFormats,
                    PublishingVersionsReportDto::getElectronicVersionWww,
                    PublishingVersionsReportDto::getElectronicVersionInformation,
                    PublishingVersionsReportDto::getElectronicArticleMetadata,
                    PublishingVersionsReportDto::getAop,
                    PublishingVersionsReportDto::getPrintedVersion,
                    PublishingVersionsReportDto::getCoverInformation,
                    PublishingVersionsReportDto::getCirculation,
                    PublishingVersionsReportDto::getPrintedArticleMetadata,
                    PublishingVersionsReportDto::getPublishingPolicy,
                    PublishingVersionsReportDto::getElectronicVersionsPercentage
            ),
            sheet(6, 5, SurveyReportDto::getPublishingStabilities,
                    PublishingStabilityReportDto::getJournalId,
                    PublishingStabilityReportDto::getArticlePublished1Amount,
                    PublishingStabilityReportDto::getArticlePublished1ScientificAmount,
                    PublishingStabilityReportDto::getArticlePublished1ReceivedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished1RejectedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished1PublishedIssuesAmount,
                    PublishingStabilityReportDto::getArticlePublished1FrequencyCode,
                    PublishingStabilityReportDto::getArticlePublished2Amount,
                    PublishingStabilityReportDto::getArticlePublished2ScientificAmount,
                    PublishingStabilityReportDto::getArticlePublished2ReceivedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished2RejectedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished2PublishedIssuesAmount,
                    PublishingStabilityReportDto::getArticlePublished2FrequencyCode,
                    PublishingStabilityReportDto::getArticlePublished3Amount,
                    PublishingStabilityReportDto::getArticlePublished3ScientificAmount,
                    PublishingStabilityReportDto::getArticlePublished3ReceivedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished3RejectedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished3PublishedIssuesAmount,
                    PublishingStabilityReportDto::getArticlePublished3FrequencyCode,
                    PublishingStabilityReportDto::getArticlePublished4Amount,
                    PublishingStabilityReportDto::getArticlePublished4ScientificAmount,
                    PublishingStabilityReportDto::getArticlePublished4ReceivedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished4RejectedManuscriptCount,
                    PublishingStabilityReportDto::getArticlePublished4PublishedIssuesAmount,
                    PublishingStabilityReportDto::getArticlePublished4FrequencyCode,
                    PublishingStabilityReportDto::getNd,
                    PublishingStabilityReportDto::getConcatenateNumberPrevious,
                    PublishingStabilityReportDto::getPublishingBreakPrevious,
                    PublishingStabilityReportDto::getEnglishTitleAndAbstractInAllArticlesPercentage,
                    PublishingStabilityReportDto::getConcatenateNumber,
                    PublishingStabilityReportDto::getPublishingBreak
            ),
            sheet(7, 2, SurveyReportDto::getPublishingStabilitiesLanguages,
                    PublishingStabilityLanguagesReportDto::getJournalId,
                    PublishingStabilityLanguagesReportDto::getLanguage,
                    PublishingStabilityLanguagesReportDto::getPreviousValue,
                    PublishingStabilityLanguagesReportDto::getCurrentValue
            ),
            sheet(8, 4, SurveyReportDto::getReviewStandards,
                    ReviewStandardsReportDto::getJournalId,
                    ReviewStandardsReportDto::getLinkToReviewProcedure,
                    ReviewStandardsReportDto::getInfoReviewProcedure,
                    ReviewStandardsReportDto::getPublishedReviewersList,
                    ReviewStandardsReportDto::getMin2Reviewers,
                    ReviewStandardsReportDto::getPublicForm,
                    ReviewStandardsReportDto::getPublicCriteria,
                    ReviewStandardsReportDto::getReviewerStatement,
                    ReviewStandardsReportDto::getPrepareReview
            ),
            sheet(9, 4, SurveyReportDto::getAuthorInformation,
                    AuthorInformationReportDto::getJournalId,
                    AuthorInformationReportDto::getInstructionLink,
                    AuthorInformationReportDto::getArticleStructureInstruction,
                    AuthorInformationReportDto::getLiteratureInstruction,
                    AuthorInformationReportDto::getOriginalKindStatement,
                    AuthorInformationReportDto::getAuthorContributionStatement,
                    AuthorInformationReportDto::getFinanceSourceStatement,
                    AuthorInformationReportDto::getOrcidRequired,
                    AuthorInformationReportDto::getCitationsStyle,
                    AuthorInformationReportDto::getManuscriptProcessingTimeUrl,
                    AuthorInformationReportDto::getAvgInitialAssessment,
                    AuthorInformationReportDto::getAvgReview,
                    AuthorInformationReportDto::getAvgPublication,
                    AuthorInformationReportDto::getManuscriptProcessingCostUrl,
                    AuthorInformationReportDto::getSubmissionFeeType,
                    AuthorInformationReportDto::getPublicationFeeType,
                    AuthorInformationReportDto::getOpenAccessFeeType,
                    AuthorInformationReportDto::getApcFeeType,
                    AuthorInformationReportDto::getRapidReviewFeeType
            ),
            sheet(10, 4, SurveyReportDto::getDigitalization,
                    DigitalizationReportDto::getJournalId,
                    DigitalizationReportDto::getWebsite,
                    DigitalizationReportDto::getWebsiteLanguages,
                    DigitalizationReportDto::getDoiPrefix,
                    DigitalizationReportDto::getDoiType,
                    DigitalizationReportDto::getInfoWebsiteTitles,
                    DigitalizationReportDto::getInfoWebsiteIssn,
                    DigitalizationReportDto::getInfoWebsiteDirectors,
                    DigitalizationReportDto::getInfoWebsiteEditOfficeInfo,
                    DigitalizationReportDto::getArticlePublishedLicense,
                    DigitalizationReportDto::getSystemForPublishedArticlesName,
                    DigitalizationReportDto::getSocialMediaIds,
                    DigitalizationReportDto::getPublishingToolIds,
                    DigitalizationReportDto::getCitationsTools,
                    DigitalizationReportDto::getAntiPlagiarismSystem,
                    DigitalizationReportDto::getStatistics,
                    DigitalizationReportDto::getNewsletters
            ),
            sheet(11, 5, SurveyReportDto::getScientificDatabases,
                    ScientificDatabaseReportDto::getJournalId,
                    ScientificDatabaseReportDto::getExternalRepositoriesGeneral,
                    ScientificDatabaseReportDto::getIndicatorSjrPrevious,
                    ScientificDatabaseReportDto::getIndicatorTcPrevious,
                    ScientificDatabaseReportDto::getIndicatorSnipPrevious,
                    ScientificDatabaseReportDto::getIndicatorCsPrevious,
                    ScientificDatabaseReportDto::getIndicatorIfPrevious,
                    ScientificDatabaseReportDto::getIndicatorAiPrevious,
                    ScientificDatabaseReportDto::getIndicatorCnciPrevious,
                    ScientificDatabaseReportDto::getIndicatorSjrCurrent,
                    ScientificDatabaseReportDto::getIndicatorTcCurrent,
                    ScientificDatabaseReportDto::getIndicatorSnipCurrent,
                    ScientificDatabaseReportDto::getIndicatorCsCurrent,
                    ScientificDatabaseReportDto::getIndicatorIfCurrent,
                    ScientificDatabaseReportDto::getIndicatorAiCurrent,
                    ScientificDatabaseReportDto::getIndicatorCnciCurrent
            ),
            sheet(12, 4, SurveyReportDto::getCooperation,
                    CooperationReportDto::getJournalId,
                    CooperationReportDto::getAuthorUnique,
                    CooperationReportDto::getAuthorExternalAffiliationPercentage,
                    CooperationReportDto::getAuthorForeignAffiliationPercentage,
                    CooperationReportDto::getReviewerUnique,
                    CooperationReportDto::getReviewerExternalAffiliationPercentage,
                    CooperationReportDto::getReviewerForeignAffiliationPercentage,
                    CooperationReportDto::getDirectorsUnique,
                    CooperationReportDto::getDirectorsExternalAffiliationPercentage,
                    CooperationReportDto::getDirectorsForeignAffiliationPercentage
            ),
            sheet(13, 3, SurveyReportDto::getCooperationReviewers,
                    CooperationReviewerReportDto::getJournalId,
                    CooperationReviewerReportDto::getFullName,
                    CooperationReviewerReportDto::getAffiliation,
                    CooperationReviewerReportDto::getCountry,
                    CooperationReviewerReportDto::getEmail
            ),
            sheet(14, 3, SurveyReportDto::getCooperationScientificCouncil,
                    CooperationScientificCouncilReportDto::getJournalId,
                    CooperationScientificCouncilReportDto::getFullName,
                    CooperationScientificCouncilReportDto::getAffiliation,
                    CooperationScientificCouncilReportDto::getCountry,
                    CooperationScientificCouncilReportDto::getEmail
            )
    );

    @SafeVarargs
    private static <T> SheetDefinition<T> sheet(
            int sheetIndex,
            int rowIndex,
            Function<SurveyReportDto, List<T>> rows,
            Function<T, ?>... columns
    ) {
        return new SheetDefinition<>(sheetIndex, rowIndex, rows, List.of(columns));
    }

    @Override
    public void generate(SurveyReportDto surveyReport, Integer year, String language, OutputStream output) {
        Objects.requireNonNull(surveyReport, "surveyReport must not be null");
        Objects.requireNonNull(output, "output must not be null");
        String templatePath = properties.templatePath(year, language)
                .orElseThrow(() -> new IllegalStateException(
                        "Survey report template is not configured for year " + year + " and language " + language
                ));

        try (
                InputStream template = new ClassPathResource(templatePath).getInputStream();
                XSSFWorkbook templateWorkbook = new XSSFWorkbook(template)
        ) {
            List<PreparedSheet<?>> preparedSheets = prepareSheets(templateWorkbook, surveyReport);
            SXSSFWorkbook workbook = new SXSSFWorkbook(
                    templateWorkbook,
                    ROW_ACCESS_WINDOW_SIZE,
                    true,
                    false
            );
            try (workbook) {
                Map<Short, CellStyle> borderedStyles = new HashMap<>();
                for (PreparedSheet<?> sheet : preparedSheets) {
                    fillSheet(workbook, sheet, language, borderedStyles);
                }
                workbook.write(output);
            } finally {
                workbook.dispose();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate survey XLSX report", e);
        }
    }

    private List<PreparedSheet<?>> prepareSheets(XSSFWorkbook workbook, SurveyReportDto surveyReport) {
        List<PreparedSheet<?>> preparedSheets = new ArrayList<>();
        for (SheetDefinition<?> definition : SHEETS) {
            if (definition.sheetIndex() < workbook.getNumberOfSheets()) {
                preparedSheets.add(prepareSheet(workbook, definition, surveyReport));
            }
        }
        return preparedSheets;
    }

    private <T> PreparedSheet<T> prepareSheet(
            XSSFWorkbook workbook,
            SheetDefinition<T> definition,
            SurveyReportDto surveyReport
    ) {
        XSSFSheet sheet = workbook.getSheetAt(definition.sheetIndex());
        XSSFRow templateRow = sheet.getRow(definition.rowIndex());
        if (templateRow == null) {
            templateRow = sheet.createRow(definition.rowIndex());
        }

        RowTemplate rowTemplate = rowTemplate(templateRow, definition.lastColumnIndex());
        List<T> rows = definition.rows().apply(surveyReport);
        if (rows == null || rows.isEmpty()) {
            clearRow(templateRow);
            return new PreparedSheet<>(definition, List.of(), rowTemplate, List.of());
        }

        int extraRows = rows.size() - 1;
        if (extraRows > 0 && sheet.getLastRowNum() > definition.rowIndex()) {
            sheet.shiftRows(definition.rowIndex() + 1, sheet.getLastRowNum(), extraRows, true, false);
        }

        List<RowSnapshot> trailingRows = captureTrailingRows(sheet, definition.rowIndex());
        removeRows(sheet, definition.rowIndex(), sheet.getLastRowNum());
        return new PreparedSheet<>(definition, rows, rowTemplate, trailingRows);
    }

    private <T> void fillSheet(
            SXSSFWorkbook workbook,
            PreparedSheet<T> preparedSheet,
            String language,
            Map<Short, CellStyle> borderedStyles
    ) {
        if (preparedSheet.rows().isEmpty()) {
            return;
        }

        SheetDefinition<T> definition = preparedSheet.definition();
        Sheet sheet = workbook.getSheetAt(definition.sheetIndex());
        for (int i = 0; i < preparedSheet.rows().size(); i++) {
            Row row = sheet.createRow(definition.rowIndex() + i);
            applyRowTemplate(workbook, row, preparedSheet.rowTemplate());
            writeRow(row, preparedSheet.rows().get(i), definition.columns(), language, borderedStyles);
        }
        for (RowSnapshot trailingRow : preparedSheet.trailingRows()) {
            restoreRow(workbook, sheet, trailingRow);
        }
    }

    private <T> void writeRow(
            Row row,
            T source,
            List<Function<T, ?>> columns,
            String language,
            Map<Short, CellStyle> borderedStyles
    ) {
        for (int i = 0; i < columns.size(); i++) {
            writeCell(
                    row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK),
                    columns.get(i).apply(source),
                    language,
                    borderedStyles
            );
        }
    }

    private void writeCell(Cell cell, Object value, String language, Map<Short, CellStyle> borderedStyles) {
        applyThinBlackBorder(cell, borderedStyles);
        if (value == null) {
            cell.setCellValue(EMPTY_CELL_VALUE);
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Collection<?> collection) {
            String joined = collection.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(java.util.stream.Collectors.joining("; "));
            cell.setCellValue(joined.isBlank() ? EMPTY_CELL_VALUE : joined);
            return;
        }
        String text = value.toString();
        cell.setCellValue(text.isBlank() ? EMPTY_CELL_VALUE : text);
    }

    private void applyThinBlackBorder(Cell cell, Map<Short, CellStyle> borderedStyles) {
        CellStyle currentStyle = cell.getCellStyle();
        if (hasThinBlackBorder(currentStyle)) {
            return;
        }

        CellStyle borderedStyle = borderedStyles.computeIfAbsent(currentStyle.getIndex(), ignored -> {
            CellStyle style = cell.getSheet().getWorkbook().createCellStyle();
            style.cloneStyleFrom(currentStyle);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setTopBorderColor(IndexedColors.BLACK.getIndex());
            style.setRightBorderColor(IndexedColors.BLACK.getIndex());
            style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
            style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
            return style;
        });

        cell.setCellStyle(borderedStyle);
    }

    private boolean hasThinBlackBorder(CellStyle style) {
        short black = IndexedColors.BLACK.getIndex();
        return style.getBorderTop() == BorderStyle.THIN
                && style.getBorderRight() == BorderStyle.THIN
                && style.getBorderBottom() == BorderStyle.THIN
                && style.getBorderLeft() == BorderStyle.THIN
                && style.getTopBorderColor() == black
                && style.getRightBorderColor() == black
                && style.getBottomBorderColor() == black
                && style.getLeftBorderColor() == black;
    }

    private void clearRow(Row row) {
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return;
        }
        for (int i = 0; i < lastCellNum; i++) {
            row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setBlank();
        }
    }

    private RowTemplate rowTemplate(Row row, int lastColumn) {
        List<Short> cellStyleIndexes = new ArrayList<>(lastColumn + 1);
        for (int i = 0; i <= lastColumn; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            cellStyleIndexes.add(cell.getCellStyle().getIndex());
        }
        CellStyle rowStyle = row.getRowStyle();
        return new RowTemplate(
                row.getHeight(),
                row.getZeroHeight(),
                rowStyle == null ? null : rowStyle.getIndex(),
                cellStyleIndexes
        );
    }

    private List<RowSnapshot> captureTrailingRows(XSSFSheet sheet, int dataRowIndex) {
        List<RowSnapshot> rows = new ArrayList<>();
        for (int rowIndex = dataRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            XSSFRow row = sheet.getRow(rowIndex);
            if (row != null) {
                rows.add(rowSnapshot(row));
            }
        }
        return rows;
    }

    private RowSnapshot rowSnapshot(Row row) {
        List<CellSnapshot> cells = new ArrayList<>();
        short lastCellNum = row.getLastCellNum();
        for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null) {
                cells.add(cellSnapshot(cell));
            }
        }
        CellStyle rowStyle = row.getRowStyle();
        return new RowSnapshot(
                row.getRowNum(),
                row.getHeight(),
                row.getZeroHeight(),
                rowStyle == null ? null : rowStyle.getIndex(),
                cells
        );
    }

    private CellSnapshot cellSnapshot(Cell cell) {
        Object value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            case ERROR -> cell.getErrorCellValue();
            case BLANK, _NONE -> null;
        };
        return new CellSnapshot(
                cell.getColumnIndex(),
                cell.getCellStyle().getIndex(),
                cell.getCellType(),
                value
        );
    }

    private void removeRows(XSSFSheet sheet, int firstRow, int lastRow) {
        for (int rowIndex = lastRow; rowIndex >= firstRow; rowIndex--) {
            XSSFRow row = sheet.getRow(rowIndex);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }

    private void applyRowTemplate(SXSSFWorkbook workbook, Row row, RowTemplate template) {
        row.setHeight(template.height());
        row.setZeroHeight(template.zeroHeight());
        if (template.rowStyleIndex() != null) {
            row.setRowStyle(workbook.getCellStyleAt(template.rowStyleIndex()));
        }
        for (int columnIndex = 0; columnIndex < template.cellStyleIndexes().size(); columnIndex++) {
            Cell cell = row.createCell(columnIndex);
            cell.setCellStyle(workbook.getCellStyleAt(template.cellStyleIndexes().get(columnIndex)));
        }
    }

    private void restoreRow(SXSSFWorkbook workbook, Sheet sheet, RowSnapshot snapshot) {
        Row row = sheet.createRow(snapshot.rowIndex());
        row.setHeight(snapshot.height());
        row.setZeroHeight(snapshot.zeroHeight());
        if (snapshot.rowStyleIndex() != null) {
            row.setRowStyle(workbook.getCellStyleAt(snapshot.rowStyleIndex()));
        }
        for (CellSnapshot snapshotCell : snapshot.cells()) {
            Cell cell = row.createCell(snapshotCell.columnIndex());
            cell.setCellStyle(workbook.getCellStyleAt(snapshotCell.styleIndex()));
            restoreCellValue(cell, snapshotCell);
        }
    }

    private void restoreCellValue(Cell cell, CellSnapshot snapshot) {
        switch (snapshot.type()) {
            case STRING -> cell.setCellValue((String) snapshot.value());
            case NUMERIC -> cell.setCellValue((Double) snapshot.value());
            case BOOLEAN -> cell.setCellValue((Boolean) snapshot.value());
            case FORMULA -> cell.setCellFormula((String) snapshot.value());
            case ERROR -> cell.setCellErrorValue((Byte) snapshot.value());
            case BLANK, _NONE -> cell.setBlank();
        }
    }

    private record SheetDefinition<T>(
            int sheetIndex,
            int rowIndex,
            Function<SurveyReportDto, List<T>> rows,
            List<Function<T, ?>> columns
    ) {
        private int lastColumnIndex() {
            return columns.size() - 1;
        }
    }

    private record PreparedSheet<T>(
            SheetDefinition<T> definition,
            List<T> rows,
            RowTemplate rowTemplate,
            List<RowSnapshot> trailingRows
    ) {
    }

    private record RowTemplate(
            short height,
            boolean zeroHeight,
            Short rowStyleIndex,
            List<Short> cellStyleIndexes
    ) {
    }

    private record RowSnapshot(
            int rowIndex,
            short height,
            boolean zeroHeight,
            Short rowStyleIndex,
            List<CellSnapshot> cells
    ) {
    }

    private record CellSnapshot(int columnIndex, short styleIndex, CellType type, Object value) {
    }
}
