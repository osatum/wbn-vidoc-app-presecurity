package com.osatum.wbn.application.dictionary.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import com.osatum.wbn.domain.repository.dictionary.DictionaryScientificCategoryRepository;
import org.springframework.stereotype.Service;

/**
 * Udostępnia pamięć podręczną hierarchii kategorii JML według identyfikatorów.
 * Kategorie te nie są słownikiem odłożonych do osobnej realizacji dyscyplin MNiSW.
 */
@Service
public class DictionaryScientificCategoryService  extends AbstractDictionaryIdItemCacheService<DictionaryScientificCategory> {
    public DictionaryScientificCategoryService(DictionaryScientificCategoryRepository repository) {
        super(repository);
    }

}
