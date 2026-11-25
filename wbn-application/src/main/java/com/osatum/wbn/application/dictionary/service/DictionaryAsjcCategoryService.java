package com.osatum.wbn.application.dictionary.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;
import com.osatum.wbn.domain.repository.dictionary.DictionaryAsjcCategoryRepository;
import org.springframework.stereotype.Service;
/* ASJC (All Science Journal Classification Codes) są stosowane w Scopusie.  */
@Service
public class DictionaryAsjcCategoryService extends AbstractDictionaryIdItemCacheService<DictionaryAsjcCategory> {
    public DictionaryAsjcCategoryService(DictionaryAsjcCategoryRepository dictionaryRepository) {
        super(dictionaryRepository);
    }
}
