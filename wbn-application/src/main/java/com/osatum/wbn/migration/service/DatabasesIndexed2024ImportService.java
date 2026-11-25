package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Digitalization;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ExternalRepositories;
import com.osatum.wbn.domain.model.survey.model.Repository;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabasesIndexed2024ImportService {
    private static final int SURVEY_YEAR = 2024;

    private final QuestionnaireSpringDataRepository questionnaireRepository;
    private final DictionaryRepositoryService dictionaryRepositoryService;

    public List<DatabasesIndexed2024Record> readRecords(String sourcePath) throws Exception {
        File file = Path.of(sourcePath).toFile();
        if (!file.isFile()) {
            throw new IllegalStateException("Cannot find databases indexed 2024 Excel file: " + sourcePath);
        }

        Map<Long, DatabasesIndexed2024Record> records = new LinkedHashMap<>();
        HeaderColumns[] columns = new HeaderColumns[1];
        MigrationExcelReader.readFirstSheet(file, row -> {
            if (columns[0] == null) {
                columns[0] = headerColumns(row, file);
                return;
            }
            Long journalId = readLong(row.value(columns[0].journalId()), "journal_id");
            if (journalId == null) {
                return;
            }
            List<Long> repositoryIds = columns[0].repositoryIds().entrySet().stream()
                    .filter(entry -> isSelected(row.value(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .toList();
            records.put(journalId, new DatabasesIndexed2024Record(
                    journalId,
                    repositoryIds,
                    file.getName(),
                    row.rowIndex() + 1
            ));
        });
        return List.copyOf(records.values());
    }

    @Transactional
    public void updateRepositories(DatabasesIndexed2024Record record) {
        List<Questionnaire> questionnaires = questionnaireRepository.findByYearAndJournalId(SURVEY_YEAR, record.journalId());
        if (questionnaires.isEmpty()) {
            log.debug("Skipping databases indexed 2024 for journal {} without {} questionnaire", record.journalId(), SURVEY_YEAR);
            return;
        }

        List<Repository> repositories = record.repositoryIds().stream()
                .map(this::repository)
                .toList();

        Questionnaire questionnaire = questionnaires.getFirst();
        ExternalRepositories externalRepositories = externalRepositories(questionnaire);
        externalRepositories.setGeneral(new ArrayList<>(repositories));
        questionnaireRepository.save(questionnaire);
    }

    private HeaderColumns headerColumns(
            MigrationExcelReader.RowData header,
            File file
    ) {
        Integer journalId = null;
        Map<Integer, Long> repositoryIds = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> cell : header.values().entrySet()) {
            int cellIndex = cell.getKey();
            String value = cell.getValue();
            if ("journalid".equals(normalizedHeader(value))) {
                journalId = cellIndex;
                continue;
            }

            Long repositoryId = readLong(value, "repository id");
            if (repositoryId != null) {
                repositoryIds.put(cellIndex, repositoryId);
            }
        }

        if (journalId == null || repositoryIds.isEmpty()) {
            throw new IllegalStateException("Databases indexed 2024 file has invalid header: " + file.getName());
        }
        return new HeaderColumns(journalId, Collections.unmodifiableMap(new LinkedHashMap<>(repositoryIds)));
    }

    private Repository repository(Long repositoryId) {
        var dictionaryRepository = dictionaryRepositoryService.getById(repositoryId);
        if (dictionaryRepository == null) {
            throw new IllegalStateException("Cannot find repository dictionary entry: " + repositoryId);
        }
        return new Repository(dictionaryRepository.getId(), dictionaryRepository.getName());
    }

    private ExternalRepositories externalRepositories(Questionnaire questionnaire) {
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
        Digitalization digitalization = survey.getDigitalization();
        if (digitalization == null) {
            digitalization = new Digitalization();
            survey.setDigitalization(digitalization);
        }
        ExternalRepositories externalRepositories = digitalization.getExternalRepositories();
        if (externalRepositories == null) {
            externalRepositories = new ExternalRepositories();
            digitalization.setExternalRepositories(externalRepositories);
        }
        return externalRepositories;
    }

    private Long readLong(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replace("\u00A0", "").replace(" ", "")).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + " value: " + value, e);
        }
    }

    private boolean isSelected(String value) {
        if (value == null || value.isBlank() || "0".equals(value) || "false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return true;
        }
        throw new IllegalArgumentException("Invalid database indexing marker: " + value);
    }

    private String normalizedHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public record DatabasesIndexed2024Record(
            Long journalId,
            List<Long> repositoryIds,
            String sourceFile,
            int rowNumber
    ) {
        public String itemName() {
            return sourceFile + ":" + rowNumber + ":journal-" + journalId;
        }
    }

    private record HeaderColumns(int journalId, Map<Integer, Long> repositoryIds) {
    }
}
