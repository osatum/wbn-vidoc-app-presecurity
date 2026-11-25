package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryTRCategoryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.AbstractCategory2024ImportService.Category2024Item;
import com.osatum.wbn.migration.service.AbstractCategory2024ImportService.Category2024Record;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WosCategory2024ImportServiceTest {
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final DictionaryTRCategoryService dictionaryTRCategoryService = mock();
    private final WosCategory2024ImportService service = new WosCategory2024ImportService(
            questionnaireRepository,
            dictionaryTRCategoryService
    );

    @TempDir
    Path tempDir;

    @Test
    void readsMainAndOtherCategoriesAndKeepsEmptyJournalRecord() throws Exception {
        Path file = tempDir.resolve("wos-categories-2024.xlsx");
        createCategoryFile(file);

        List<Category2024Record> records = service.readRecords(file.toString());

        assertThat(records).hasSize(2);
        assertThat(records.getFirst().journalId()).isEqualTo(123L);
        assertThat(records.getFirst().categories())
                .extracting(Category2024Item::categoryId, Category2024Item::main)
                .containsExactly(
                        tuple(10L, true),
                        tuple(11L, false),
                        tuple(12L, false)
                );
        assertThat(records.getLast().journalId()).isEqualTo(456L);
        assertThat(records.getLast().categories()).isEmpty();
    }

    @Test
    void replacesAndClearsTrCategoriesWithoutChangingAsjcCategories() {
        Questionnaire questionnaire = questionnaire(123L);
        Category existingAsjc = new Category(90L, null, "ASJC PL", "ASJC EN", null, true);
        questionnaire.getSurveyData().getSurvey().getSupplementaryData().setAsjcCategories(List.of(existingAsjc));

        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryTRCategoryService.getById(10L)).thenReturn(trCategory(10L, "TR 10 PL", "TR 10 EN"));
        when(dictionaryTRCategoryService.getById(11L)).thenReturn(trCategory(11L, "TR 11 PL", "TR 11 EN"));

        service.updateCategories(record(123L, List.of(
                new Category2024Item(10L, true),
                new Category2024Item(11L, false)
        )));

        var supplementaryData = questionnaire.getSurveyData().getSurvey().getSupplementaryData();
        assertThat(supplementaryData.getTrCategories())
                .extracting(Category::getId, Category::getNamePl, Category::getNameEn, Category::getMain)
                .containsExactly(
                        tuple(10L, "TR 10 PL", "TR 10 EN", true),
                        tuple(11L, "TR 11 PL", "TR 11 EN", false)
                );
        assertThat(supplementaryData.getAsjcCategories()).containsExactly(existingAsjc);
        verify(questionnaireRepository).save(questionnaire);

        service.updateCategories(record(123L, List.of()));

        assertThat(supplementaryData.getTrCategories()).isEmpty();
        assertThat(supplementaryData.getAsjcCategories()).containsExactly(existingAsjc);
        verify(questionnaireRepository, times(2)).save(questionnaire);
    }

    @Test
    void skipsJournalWithoutQuestionnaireFor2024() {
        when(questionnaireRepository.findByYearAndJournalId(2024, 999L)).thenReturn(List.of());

        service.updateCategories(record(999L, List.of(new Category2024Item(10L, true))));

        verify(dictionaryTRCategoryService, never()).getById(10L);
        verify(questionnaireRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private void createCategoryFile(Path file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("categories");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("journal_id");
            header.createCell(1).setCellValue("main");
            header.createCell(2).setCellValue("other");

            var first = sheet.createRow(1);
            first.createCell(0).setCellValue(123);
            first.createCell(1).setCellValue(10);
            first.createCell(2).setCellValue("11, 12, 10");

            var second = sheet.createRow(2);
            second.createCell(0).setCellValue(456);

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

    private DictionaryTRCategory trCategory(Long id, String namePl, String nameEn) {
        DictionaryTRCategory category = new DictionaryTRCategory();
        category.setId(id);
        category.setNamePl(namePl);
        category.setNameEn(nameEn);
        return category;
    }

    private Category2024Record record(Long journalId, List<Category2024Item> categories) {
        return new Category2024Record(journalId, categories, "wos-categories-2024.xlsx", 2);
    }
}
