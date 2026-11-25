package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Repository;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabasesIndexed2024ImportServiceTest {
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final DictionaryRepositoryService dictionaryRepositoryService = mock();
    private final DatabasesIndexed2024ImportService service = new DatabasesIndexed2024ImportService(
            questionnaireRepository,
            dictionaryRepositoryService
    );

    @TempDir
    Path tempDir;

    @Test
    void readsSelectedRepositoryIdsFromExcelFile() throws Exception {
        Path file = tempDir.resolve("databases-indexed-2024.xlsx");
        createDatabasesIndexedFile(file);

        List<DatabasesIndexed2024ImportService.DatabasesIndexed2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().repositoryIds()).containsExactly(1L, 8L);
        assertThat(records.getLast().journalId()).isEqualTo(456L);
        assertThat(records.getLast().repositoryIds()).containsExactly(3L);
    }

    @Test
    void replacesGeneralRepositoriesWithDictionaryIdsAndNames() {
        Questionnaire questionnaire = questionnaire(123L);
        questionnaire.getSurveyData().getSurvey().getDigitalization().getExternalRepositories()
                .setGeneral(List.of(new Repository(99L, "Old repository")));
        questionnaire.getSurveyData().getSurvey().getDigitalization().getExternalRepositories()
                .setOthers(List.of("Other repository"));

        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryRepositoryService.getById(1L)).thenReturn(dictionaryRepository(1L, "Repository one"));
        when(dictionaryRepositoryService.getById(8L)).thenReturn(dictionaryRepository(8L, "Repository eight"));

        service.updateRepositories(new DatabasesIndexed2024ImportService.DatabasesIndexed2024Record(
                123L,
                List.of(1L, 8L),
                "databases-indexed-2024.xlsx",
                2
        ));

        var externalRepositories = questionnaire.getSurveyData().getSurvey().getDigitalization().getExternalRepositories();
        assertThat(externalRepositories.getGeneral())
                .extracting(Repository::getId, Repository::getName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "Repository one"),
                        org.assertj.core.groups.Tuple.tuple(8L, "Repository eight")
                );
        assertThat(externalRepositories.getOthers()).containsExactly("Other repository");
        verify(questionnaireRepository).save(questionnaire);
    }

    @Test
    void skipsRecordWhenQuestionnaireDoesNotExist() {
        service.updateRepositories(new DatabasesIndexed2024ImportService.DatabasesIndexed2024Record(
                999L,
                List.of(1L),
                "databases-indexed-2024.xlsx",
                2
        ));

        verify(questionnaireRepository).findByYearAndJournalId(2024, 999L);
        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createDatabasesIndexedFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("databases");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("1");
            header.createCell(2).setCellValue("3");
            header.createCell(3).setCellValue("8");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue(true);
            first.createCell(2).setCellValue(false);
            first.createCell(3).setCellValue(true);

            var second = sheet.createRow(2);
            second.createCell(0).setCellValue("456");
            second.createCell(1).setCellValue(0);
            second.createCell(2).setCellValue(1);
            second.createCell(3).setCellValue(0);

            try (OutputStream outputStream = Files.newOutputStream(file)) {
                workbook.write(outputStream);
            }
        }
    }

    private Questionnaire questionnaire(Long journalId) {
        Journal journal = new Journal();
        journal.setId(journalId);

        SurveyData surveyData = new SurveyData();
        surveyData.setSurvey(new Survey());

        Questionnaire questionnaire = new Questionnaire();
        questionnaire.setJournal(journal);
        questionnaire.setYear(2024);
        questionnaire.setSurveyData(surveyData);
        return questionnaire;
    }

    private DictionaryRepository dictionaryRepository(Long id, String name) {
        DictionaryRepository repository = new DictionaryRepository();
        repository.setId(id);
        repository.setName(name);
        return repository;
    }
}
