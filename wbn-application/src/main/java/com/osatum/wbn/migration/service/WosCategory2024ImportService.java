package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.dictionary.service.DictionaryTRCategoryService;
import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import com.osatum.wbn.domain.model.survey.SupplementaryData;
import com.osatum.wbn.domain.model.survey.model.Category;
import com.osatum.wbn.infrastructure.persistence.springdata.QuestionnaireSpringDataRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WosCategory2024ImportService extends AbstractCategory2024ImportService<DictionaryTRCategory> {
    private final DictionaryTRCategoryService dictionaryTRCategoryService;

    public WosCategory2024ImportService(
            QuestionnaireSpringDataRepository questionnaireRepository,
            DictionaryTRCategoryService dictionaryTRCategoryService
    ) {
        super(questionnaireRepository, "WoS categories 2024");
        this.dictionaryTRCategoryService = dictionaryTRCategoryService;
    }

    @Override
    protected DictionaryTRCategory dictionaryCategory(Long categoryId) {
        return dictionaryTRCategoryService.getById(categoryId);
    }

    @Override
    protected void replaceCategories(SupplementaryData supplementaryData, List<Category> categories) {
        supplementaryData.setTrCategories(categories);
    }
}
