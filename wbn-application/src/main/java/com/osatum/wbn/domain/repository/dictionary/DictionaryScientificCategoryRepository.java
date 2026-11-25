package com.osatum.wbn.domain.repository.dictionary;

import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;

import java.util.List;
import java.util.Optional;

public interface DictionaryScientificCategoryRepository extends DictionaryItemsRepository<DictionaryScientificCategory> {
    void saveAll(List<DictionaryScientificCategory> dictionaries);

    Optional<DictionaryScientificCategory> findById(Long id);
}
