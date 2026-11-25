package com.osatum.wbn.migration.service;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Standards;
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
public class LinkToReview2024ImportService {
    private static final int SURVEY_YEAR = 2024;

    private final JournalRepository journalRepository;
    private final QuestionnaireSpringDataRepository questionnaireRepository;

    public List<LinkToReview2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find link to review procedure 2024 Excel file: " + sourcePath);
        }

        Map<Long, LinkToReview2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                columns[0] = headerColumns(row, file);
                return;
            }

            Long journalId = readLong(row.value(columns[0].journalId()));
            String link = row.value(columns[0].link());
            if (journalId == null || link == null || link.isBlank()) {
                return;
            }

            records.put(journalId, new LinkToReview2024Record(
                    journalId,
                    link.trim(),
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    @Transactional
    public void updateLinkToReviewProcedure(LinkToReview2024Record record) {
        if (record.link() == null || record.link().isBlank()) {
            return;
        }

        Journal journal = journalRepository.findById(record.journalId()).orElse(null);
        if (journal == null) {
            log.debug("Skipping link to review procedure 2024 for missing journal {}", record.journalId());
            return;
        }

        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(
                SURVEY_YEAR,
                journal.getId()
        );
        if (questionnaires.isEmpty()) {
            log.debug(
                    "Skipping link to review procedure 2024 for journal {} without {} questionnaire",
                    journal.getId(),
                    SURVEY_YEAR
            );
            return;
        }

        Questionnaire questionnaire = questionnaires.getFirst();
        standards(questionnaire).setLinkToReviewProcedure(record.link().trim());
        questionnaireRepository.save(questionnaire);
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
        Integer link = null;
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            switch (normalizedHeader(cell.getValue())) {
                case "journalid" -> journalId = cellIndex;
                case "link" -> link = cellIndex;
                default -> {
                }
            }
        }

        if (journalId == null || link == null) {
            throw new IllegalStateException("Link to review procedure 2024 file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, link);
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

    private String normalizeNumber(String value) {
        return value.trim()
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(',', '.');
    }

    private String normalizedHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record LinkToReview2024Record(
            Long journalId,
            String link,
            String sourceFile,
            int rowNumber
    ) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    private record HeaderColumns(int journalId, int link) {
    }
}
