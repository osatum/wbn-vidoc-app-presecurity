package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryAsjcCategoryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.questionnaire.Questionnaire;
import com.osatum.wbn.domain.model.questionnaire.SurveyData;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import com.osatum.wbn.migration.service.AbstractCategory2024ImportService.Category2024Item;
import com.osatum.wbn.migration.service.AbstractCategory2024ImportService.Category2024Record;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScopusCategory2024ImportServiceTest {
    private final QuestionnaireSpringDataRepository questionnaireRepository = mock();
    private final DictionaryAsjcCategoryService dictionaryAsjcCategoryService = mock();
    private final ScopusCategory2024ImportService service = new ScopusCategory2024ImportService(
            questionnaireRepository,
            dictionaryAsjcCategoryService
    );

    @Test
    void replacesAndClearsAsjcCategoriesWithoutChangingTrCategories() {
        Questionnaire questionnaire = questionnaire(123L);
        Category existingTr = new Category(90L, null, "TR PL", "TR EN", null, true);
        questionnaire.getSurveyData().getSurvey().getSupplementaryData().setTrCategories(List.of(existingTr));

        when(questionnaireRepository.findByYearAndJournalId(2024, 123L)).thenReturn(List.of(questionnaire));
        when(dictionaryAsjcCategoryService.getById(20L))
                .thenReturn(asjcCategory(20L, "ASJC 20 PL", "ASJC 20 EN"));
        when(dictionaryAsjcCategoryService.getById(21L))
                .thenReturn(asjcCategory(21L, "ASJC 21 PL", "ASJC 21 EN"));

        service.updateCategories(record(List.of(
                new Category2024Item(20L, true),
                new Category2024Item(21L, false)
        )));

        var supplementaryData = questionnaire.getSurveyData().getSurvey().getSupplementaryData();
        assertThat(supplementaryData.getAsjcCategories())
                .extracting(Category::getId, Category::getNamePl, Category::getNameEn, Category::getMain)
                .containsExactly(
                        tuple(20L, "ASJC 20 PL", "ASJC 20 EN", true),
                        tuple(21L, "ASJC 21 PL", "ASJC 21 EN", false)
                );
        assertThat(supplementaryData.getTrCategories()).containsExactly(existingTr);
        verify(questionnaireRepository).save(questionnaire);

        service.updateCategories(record(List.of()));

        assertThat(supplementaryData.getAsjcCategories()).isEmpty();
        assertThat(supplementaryData.getTrCategories()).containsExactly(existingTr);
        verify(questionnaireRepository, times(2)).save(questionnaire);
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

    private DictionaryAsjcCategory asjcCategory(Long id, String namePl, String nameEn) {
        DictionaryAsjcCategory category = new DictionaryAsjcCategory();
        category.setId(id);
        category.setNamePl(namePl);
        category.setNameEn(nameEn);
        return category;
    }

    private Category2024Record record(List<Category2024Item> categories) {
        return new Category2024Record(123L, categories, "scopus-categories-2024.xlsx", 2);
    }
}
