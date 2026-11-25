package com.osatum.wbn.infrastructure.persistence;


import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import com.osatum.wbn.domain.repository.dictionary.DictionaryTRCategoryRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryTRCategorySpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DictionaryTRCategoryRepositoryAdapter implements DictionaryTRCategoryRepository {
    private final DictionaryTRCategorySpringDataRepository repository;

    @Override
    public void saveAll(List<DictionaryTRCategory> dictionaries) {
        repository.saveAll(dictionaries);
    }

    @Override
    public Optional<DictionaryTRCategory> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    @Override
    public List<DictionaryTRCategory> findAll() {
        return repository.findAll();
    }
}
