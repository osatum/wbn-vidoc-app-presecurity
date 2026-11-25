package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;
import com.osatum.wbn.domain.repository.dictionary.DictionaryRepositoryRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryRepositorySpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DictionaryRepositoryRepositoryAdapter implements DictionaryRepositoryRepository {
    private final DictionaryRepositorySpringDataRepository repository;

    @Override
    public void saveAll(List<DictionaryRepository> dictionaries) {
        repository.saveAll(dictionaries);
    }

    @Override
    public Optional<DictionaryRepository> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    @Override
    public List<DictionaryRepository> findAll() {
        return repository.findAll();
    }
}
