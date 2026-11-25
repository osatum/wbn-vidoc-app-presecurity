package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Digitalization;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.JournalIndicator;
import com.osatum.wbn.domain.model.survey.model.ScientificQuality;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScopusIndicatorImportService {
    private static final int SURVEY_YEAR = 2024;

    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<ScopusIndicatorRecord> readRecords(String sourcePath) throws Exception {
        List<File> files = resolveExcelFiles(sourcePath);
        Map<Long, ScopusIndicatorRecord> records = new LinkedHashMap<>();

        for (File file : files) {
            readRecords(file).forEach(record -> records.put(record.journalId(), record));
        }

        return List.copyOf(records.values());
    }

    @Transactional
    public void updatePrevious(ScopusIndicatorRecord record) {
        update(record, IndicatorTarget.PREVIOUS);
    }

    @Transactional
    public void updateCurrent(ScopusIndicatorRecord record) {
        update(record, IndicatorTarget.CURRENT);
    }

    private List<File> resolveExcelFiles(String sourcePath) {
        File source = Path.of(sourcePath).toFile();
        if (!source.exists()) {
            throw new IllegalStateException("Cannot find Scopus import path: " + sourcePath);
        }
        if (source.isFile()) {
            if (!isExcelFile(source)) {
                throw new IllegalStateException("Scopus import path is not an Excel file: " + sourcePath);
            }
            return List.of(source);
        }

        File[] files = source.listFiles(file -> file.isFile() && isExcelFile(file));
        if (files == null) {
            throw new IllegalStateException("Cannot read Scopus import directory: " + sourcePath);
        }
        return Arrays.stream(files)
                .sorted(Comparator.comparing(File::getName))
                .toList();
    }

    private List<ScopusIndicatorRecord> readRecords(File file) throws Exception {
        Map<Long, ScopusIndicatorRecord> records = new LinkedHashMap<>();
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
            Double sjr = readDouble(row.value(columns[0].sjr()));
            Double totalCites = readTotalCites(row.value(columns[0].totalCites()));
            Double citeScore = readDouble(row.value(columns[0].citeScore()));
            Double snip = readDouble(row.value(columns[0].snip()));
            records.put(journalId, new ScopusIndicatorRecord(
                    journalId,
                    sjr,
                    totalCites,
                    citeScore,
                    snip,
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    private HeaderColumns headerColumns(
            MigrationExcelReader.RowData header,
            File file
    ) {
        Integer journalId = null;
        Integer sjr = null;
        Integer totalCites = null;
        Integer citeScore = null;
        Integer snip = null;

        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            String value = normalizedHeader(cell.getValue());
            if ("journalid".equals(value)) {
                journalId = cellIndex;
            } else if ("sjr".equals(value)) {
                sjr = cellIndex;
            } else if ("totalcites".equals(value)) {
                totalCites = cellIndex;
            } else if ("citescore".equals(value)) {
                citeScore = cellIndex;
            } else if ("snip".equals(value)) {
                snip = cellIndex;
            }
        }

        if (journalId == null || sjr == null || totalCites == null || citeScore == null || snip == null) {
            throw new IllegalStateException("Scopus file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, sjr, totalCites, citeScore, snip);
    }

    private void update(ScopusIndicatorRecord record, IndicatorTarget target) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.isEmpty()) {
            log.debug("Skipping Scopus indicators for journal {} without {} questionnaire", record.journalId(), SURVEY_YEAR);
            return;
        }

        Questionnaire questionnaire = questionnaires.get(0);
        SurveyData surveyData = questionnaire.getSurveyData();
        if (surveyData == null) {
            surveyData = new SurveyData();
            questionnaire.setSurveyData(surveyData);
        }
        Survey survey = surveyData.getSurvey();
        if (survey == null) {
            survey = new Survey();
            surveyData.setSurvey(survey);
        }

        ScientificQuality quality = scientificQuality(survey);

        setValue(indicatorSJR(quality), target, record.sjr());
        setValue(indicatorTC(quality), target, record.totalCites());
        setValue(indicatorCS(quality), target, record.citeScore());
        setValue(indicatorSNIP(quality), target, record.snip());

        questionnaireRepository.save(questionnaire);
    }

    private ScientificQuality scientificQuality(Survey survey) {
        Digitalization digitalization = survey.getDigitalization();
        if (digitalization == null) {
            digitalization = new Digitalization();
            survey.setDigitalization(digitalization);
        }
        ScientificQuality quality = digitalization.getScientificQuality();
        if (quality == null) {
            quality = new ScientificQuality();
            digitalization.setScientificQuality(quality);
        }
        return quality;
    }

    private JournalIndicator indicatorSJR(ScientificQuality quality) {
        JournalIndicator indicator = quality.getIndicatorSJR();
        if (indicator == null) {
            indicator = new JournalIndicator();
            quality.setIndicatorSJR(indicator);
        }
        return indicator;
    }

    private JournalIndicator indicatorTC(ScientificQuality quality) {
        JournalIndicator indicator = quality.getIndicatorTC();
        if (indicator == null) {
            indicator = new JournalIndicator();
            quality.setIndicatorTC(indicator);
        }
        return indicator;
    }

    private JournalIndicator indicatorCS(ScientificQuality quality) {
        JournalIndicator indicator = quality.getIndicatorCS();
        if (indicator == null) {
            indicator = new JournalIndicator();
            quality.setIndicatorCS(indicator);
        }
        return indicator;
    }

    private JournalIndicator indicatorSNIP(ScientificQuality quality) {
        JournalIndicator indicator = quality.getIndicatorSNIP();
        if (indicator == null) {
            indicator = new JournalIndicator();
            quality.setIndicatorSNIP(indicator);
        }
        return indicator;
    }

    private void setValue(JournalIndicator indicator, IndicatorTarget target, Double value) {
        if (target == IndicatorTarget.PREVIOUS) {
            indicator.setPrevious(value);
            return;
        }
        indicator.setCurrent(value);
    }

    private Long readJournalId(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalizeDecimal(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid journal_id value: " + value, e);
        }
    }

    private Double readDouble(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(normalizeDecimal(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Scopus indicator value: " + value, e);
        }
    }

    private Double readTotalCites(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(normalizeInteger(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Scopus Total Cites value: " + value, e);
        }
    }

    private String normalizeDecimal(String value) {
        return value.trim()
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(',', '.');
    }

    private String normalizeInteger(String value) {
        String normalized = value.trim()
                .replace("\u00A0", "")
                .replace(" ", "");
        int commaIndex = normalized.lastIndexOf(',');
        int dotIndex = normalized.lastIndexOf('.');
        int decimalIndex = Math.max(commaIndex, dotIndex);
        if (decimalIndex >= 0 && normalized.substring(decimalIndex + 1).matches("0+")) {
            normalized = normalized.substring(0, decimalIndex);
        }
        return normalized.replace(",", "").replace(".", "");
    }

    private String normalizedHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean isExcelFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".xls") || name.endsWith(".xlsx");
    }

    private enum IndicatorTarget {
        PREVIOUS,
        CURRENT
    }

    public record ScopusIndicatorRecord(
            Long journalId,
            Double sjr,
            Double totalCites,
            Double citeScore,
            Double snip,
            String sourceFile,
            int rowNumber
    ) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    private record HeaderColumns(int journalId, int sjr, int totalCites, int citeScore, int snip) {
    }
}
