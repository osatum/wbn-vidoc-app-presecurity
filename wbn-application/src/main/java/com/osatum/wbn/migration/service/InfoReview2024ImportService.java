package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Standards;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.BooleanDouble;
import com.osatum.wbn.domain.model.survey.model.InfoReviewProcedure;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class InfoReview2024ImportService {
    private static final int SURVEY_YEAR = 2024;

    private final JournalRepository journalRepository;
    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<InfoReview2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find info review procedure 2024 Excel file: " + sourcePath);
        }

        Map<Long, InfoReview2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                columns[0] = headerColumns(row, file);
                return;
            }

            Long journalId = readLong(row.value(columns[0].journalId()));
            if (journalId == null) {
                return;
            }

            Boolean confirm = readConfirm(row.value(columns[0].confirm()));
            Double value = readDouble(row.value(columns[0].value()));
            records.put(journalId, new InfoReview2024Record(
                    journalId,
                    confirm,
                    value,
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    @Transactional
    public void updateDoubleBlindReview(InfoReview2024Record record) {
        Journal journal = journalRepository.findById(record.journalId()).orElse(null);
        if (journal == null) {
            log.debug("Skipping info review procedure 2024 for missing journal {}", record.journalId());
            return;
        }

        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(
                SURVEY_YEAR,
                journal.getId()
        );
        if (questionnaires.isEmpty()) {
            log.debug(
                    "Skipping info review procedure 2024 for journal {} without {} questionnaire",
                    journal.getId(),
                    SURVEY_YEAR
            );
            return;
        }

        Questionnaire questionnaire = questionnaires.getFirst();
        InfoReviewProcedure infoReviewProcedure = infoReviewProcedure(questionnaire);
        infoReviewProcedure.setDoubleBlindReview(new BooleanDouble(record.confirm(), record.value()));
        questionnaireRepository.save(questionnaire);
    }

    private InfoReviewProcedure infoReviewProcedure(Questionnaire questionnaire) {
        Standards standards = standards(questionnaire);
        InfoReviewProcedure infoReviewProcedure = standards.getInfoReviewProcedure();
        if (infoReviewProcedure == null) {
            infoReviewProcedure = new InfoReviewProcedure();
            standards.setInfoReviewProcedure(infoReviewProcedure);
        }
        return infoReviewProcedure;
    }

    private Standards standards(Questionnaire questionnaire) {
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

        Standards standards = survey.getStandards();
        if (standards == null) {
            standards = new Standards();
            survey.setStandards(standards);
        }
        return standards;
    }

    private HeaderColumns headerColumns(MigrationExcelReader.RowData header, File file) {
        Integer journalId = null;
        Integer confirm = null;
        Integer value = null;
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            switch (normalizedHeader(cell.getValue())) {
                case "journalid" -> journalId = cellIndex;
                case "confirm" -> confirm = cellIndex;
                case "value" -> value = cellIndex;
                default -> {
                }
            }
        }

        if (journalId == null || confirm == null || value == null) {
            throw new IllegalStateException("Info review procedure 2024 file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, confirm, value);
    }

    private Long readLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalizeNumber(value)).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid journal_id value: " + value, e);
        }
    }

    private Boolean readConfirm(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing confirm value");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "tak" -> true;
            case "nie" -> false;
            default -> throw new IllegalArgumentException("Invalid confirm value: " + value);
        };
    }

    private Double readDouble(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing numeric value");
        }
        try {
            double number = Double.parseDouble(normalizeNumber(value));
            if (!Double.isFinite(number)) {
                throw new NumberFormatException("Not a finite number");
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value: " + value, e);
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

    public record InfoReview2024Record(
            Long journalId,
            boolean confirm,
            Double value,
            String sourceFile,
            int rowNumber
    ) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    private record HeaderColumns(int journalId, int confirm, int value) {
    }
}
