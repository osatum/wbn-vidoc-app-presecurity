package com.osatum.wbn.domain.repository.dictionary;

import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;

import java.util.List;
import java.util.Optional;

public interface DictionaryRepositoryRepository  extends DictionaryItemsRepository<DictionaryRepository>{
    void saveAll(List<DictionaryRepository> dictionaries);

    Optional<DictionaryRepository> findById(Long id);
}
