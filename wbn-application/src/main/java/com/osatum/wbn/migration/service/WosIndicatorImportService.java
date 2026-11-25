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
public class WosIndicatorImportService {
    private static final int SURVEY_YEAR = 2024;

    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<WosIndicatorRecord> readRecords(String sourcePath) throws Exception {
        List<File> files = resolveExcelFiles(sourcePath);
        Map<Long, WosIndicatorRecord> records = new LinkedHashMap<>();

        for (File file : files) {
            readRecords(file).forEach(record -> records.put(record.journalId(), record));
        }

        return List.copyOf(records.values());
    }

    @Transactional
    public void updatePrevious(WosIndicatorRecord record) {
        update(record, IndicatorTarget.PREVIOUS);
    }

    @Transactional
    public void updateCurrent(WosIndicatorRecord record) {
        update(record, IndicatorTarget.CURRENT);
    }

    private List<File> resolveExcelFiles(String sourcePath) {
        File source = Path.of(sourcePath).toFile();
        if (!source.exists()) {
            throw new IllegalStateException("Cannot find WoS import path: " + sourcePath);
        }
        if (source.isFile()) {
            if (!isExcelFile(source)) {
                throw new IllegalStateException("WoS import path is not an Excel file: " + sourcePath);
            }
            return List.of(source);
        }

        File[] files = source.listFiles(file -> file.isFile() && isExcelFile(file));
        if (files == null) {
            throw new IllegalStateException("Cannot read WoS import directory: " + sourcePath);
        }
        return Arrays.stream(files)
                .sorted(Comparator.comparing(File::getName))
                .toList();
    }

    private List<WosIndicatorRecord> readRecords(File file) throws Exception {
        Map<Long, WosIndicatorRecord> records = new LinkedHashMap<>();
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
            Double impactFactor = readDouble(row.value(columns[0].impactFactor()));
            Double articleInfluence = readDouble(row.value(columns[0].articleInfluence()));
            records.put(journalId, new WosIndicatorRecord(
                    journalId,
                    impactFactor,
                    articleInfluence,
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
        Integer impactFactor = null;
        Integer articleInfluence = null;

        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            String value = normalizedHeader(cell.getValue());
            if ("journalid".equals(value)) {
                journalId = cellIndex;
            } else if ("if".equals(value) || "impactfactor".equals(value)) {
                impactFactor = cellIndex;
            } else if ("articleinfluence".equals(value) || "articleinfluencescore".equals(value)) {
                articleInfluence = cellIndex;
            }
        }

        if (journalId == null || impactFactor == null || articleInfluence == null) {
            throw new IllegalStateException("WoS file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, impactFactor, articleInfluence);
    }

    private void update(WosIndicatorRecord record, IndicatorTarget target) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.isEmpty()) {
            log.debug("Skipping WoS indicators for journal {} without {} questionnaire", record.journalId(), SURVEY_YEAR);
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
        setValue(indicatorIF(quality), target, record.impactFactor());
        setValue(indicatorAI(quality), target, record.articleInfluence());
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

    private JournalIndicator indicatorIF(ScientificQuality quality) {
        JournalIndicator indicator = quality.getIndicatorIF();
        if (indicator == null) {
            indicator = new JournalIndicator();
            quality.setIndicatorIF(indicator);
        }
        return indicator;
    }

    private JournalIndicator indicatorAI(ScientificQuality quality) {
        JournalIndicator indicator = quality.getIndicatorAI();
        if (indicator == null) {
            indicator = new JournalIndicator();
            quality.setIndicatorAI(indicator);
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
            return new BigDecimal(normalizeNumber(value)).longValueExact();
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
            return Double.valueOf(normalizeNumber(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid WoS indicator value: " + value, e);
        }
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

    private enum IndicatorTarget {
        PREVIOUS,
        CURRENT
    }

    public record WosIndicatorRecord(
            Long journalId,
            Double impactFactor,
            Double articleInfluence,
            String sourceFile,
            int rowNumber
    ) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    private record HeaderColumns(int journalId, int impactFactor, int articleInfluence) {
    }
}
