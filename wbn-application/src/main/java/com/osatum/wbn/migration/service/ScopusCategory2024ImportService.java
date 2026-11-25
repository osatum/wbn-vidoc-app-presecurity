package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryAsjcCategoryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;
import com.osatum.wbn.domain.model.survey.SupplementaryData;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScopusCategory2024ImportService extends AbstractCategory2024ImportService<DictionaryAsjcCategory> {
    private final DictionaryAsjcCategoryService dictionaryAsjcCategoryService;

    public ScopusCategory2024ImportService(
            QuestionnaireSpringDataRepository questionnaireRepository,
            DictionaryAsjcCategoryService dictionaryAsjcCategoryService
    ) {
        super(questionnaireRepository, "Scopus categories 2024");
        this.dictionaryAsjcCategoryService = dictionaryAsjcCategoryService;
    }

    @Override
    protected DictionaryAsjcCategory dictionaryCategory(Long categoryId) {
        return dictionaryAsjcCategoryService.getById(categoryId);
    }

    @Override
    protected void replaceCategories(SupplementaryData supplementaryData, List<Category> categories) {
        supplementaryData.setAsjcCategories(categories);
    }
}
