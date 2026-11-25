package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import com.osatum.wbn.domain.repository.dictionary.DictionaryScientificCategoryRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryScientificCategorySpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DictionaryScientificCategoryRepositoryAdapter implements DictionaryScientificCategoryRepository {
    private final DictionaryScientificCategorySpringDataRepository repository;

    @Override
    public void saveAll(List<DictionaryScientificCategory> dictionaries) {
        repository.saveAll(dictionaries);
    }

    @Override
    public Optional<DictionaryScientificCategory> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    @Override
    public List<DictionaryScientificCategory> findAll() {
        return repository.findAll();
    }
}
