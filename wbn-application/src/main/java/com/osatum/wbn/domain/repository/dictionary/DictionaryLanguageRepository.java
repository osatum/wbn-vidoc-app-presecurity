package com.osatum.wbn.domain.repository.dictionary;

import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;

import java.util.List;

public interface DictionaryLanguageRepository extends DictionaryItemsRepository<DictionaryLanguage> {
    void saveAll(List<DictionaryLanguage> items);
}
