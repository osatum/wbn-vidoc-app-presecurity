package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;
import com.osatum.wbn.domain.repository.dictionary.DictionaryAsjcCategoryRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryAsjcCategorySpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DictionaryAsjcCategoryRepositoryAdapter implements DictionaryAsjcCategoryRepository {
    private final DictionaryAsjcCategorySpringDataRepository repository;

    @Override
    public void saveAll(List<DictionaryAsjcCategory> dictionaries) {
        repository.saveAll(dictionaries);
    }

    @Override
    public Optional<DictionaryAsjcCategory> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    @Override
    public List<DictionaryAsjcCategory> findAll() {
        return repository.findAll();
    }
}
