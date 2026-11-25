package com.osatum.wbn.domain.repository.dictionary;

import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;

import java.util.List;
import java.util.Optional;

public interface DictionaryTRCategoryRepository extends DictionaryItemsRepository<DictionaryTRCategory> {
    void saveAll(List<DictionaryTRCategory> dictionaries);

    Optional<DictionaryTRCategory> findById(Long id);
}
