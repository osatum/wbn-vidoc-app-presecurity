package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.journal.JournalKind;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.SupplementaryData;
import com.osatum.wbn.domain.model.survey.Survey;
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
public class JournalKind2024ImportService {
    private static final int SURVEY_YEAR = 2024;

    private final JournalRepository journalRepository;
    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<JournalKind2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find journal kind 2024 Excel file: " + sourcePath);
        }

        Map<Long, JournalKind2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                columns[0] = headerColumns(row, file);
                return;
            }

            Long journalId = readLong(row.value(columns[0].journalId()));
            JournalKind kind = readKind(row.value(columns[0].kind()));
            if (journalId == null || kind == null) {
                return;
            }

            records.put(journalId, new JournalKind2024Record(
                    journalId,
                    kind,
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    @Transactional
    public void updateKind(JournalKind2024Record record) {
        if (record.kind() == null) {
            return;
        }

        Journal journal = journalRepository.findById(record.journalId()).orElse(null);
        if (journal == null) {
            log.debug("Skipping journal kind 2024 for missing journal {}", record.journalId());
            return;
        }

        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(
                SURVEY_YEAR,
                journal.getId()
        );
        if (questionnaires.isEmpty()) {
            log.debug(
                    "Skipping journal kind 2024 for journal {} without {} questionnaire",
                    journal.getId(),
                    SURVEY_YEAR
            );
            return;
        }

        Questionnaire questionnaire = questionnaires.getFirst();
        supplementaryData(questionnaire).setKind(record.kind());
        questionnaireRepository.save(questionnaire);
    }

    private SupplementaryData supplementaryData(Questionnaire questionnaire) {
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

        SupplementaryData supplementaryData = survey.getSupplementaryData();
        if (supplementaryData == null) {
            supplementaryData = new SupplementaryData();
            survey.setSupplementaryData(supplementaryData);
        }
        return supplementaryData;
    }

    private HeaderColumns headerColumns(MigrationExcelReader.RowData header, File file) {
        Integer journalId = null;
        Integer kind = null;
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            switch (normalizedHeader(cell.getValue())) {
                case "journalid" -> journalId = cellIndex;
                case "kind" -> kind = cellIndex;
                default -> {
                }
            }
        }

        if (journalId == null || kind == null) {
            throw new IllegalStateException("Journal kind 2024 file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, kind);
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

    private JournalKind readKind(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalizedValue = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        for (JournalKind kind : JournalKind.values()) {
            if (kind.name().equals(normalizedValue) || kind.getCode().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Invalid kind value: " + value);
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

    public record JournalKind2024Record(
            Long journalId,
            JournalKind kind,
            String sourceFile,
            int rowNumber
    ) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    private record HeaderColumns(int journalId, int kind) {
    }
}
