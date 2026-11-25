package com.osatum.wbn.domain.repository.dictionary;

import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;

import java.util.List;
import java.util.Optional;

public interface DictionaryAsjcCategoryRepository extends DictionaryItemsRepository<DictionaryAsjcCategory> {
    void saveAll(List<DictionaryAsjcCategory> dictionaries);

    Optional<DictionaryAsjcCategory> findById(Long id);
}
