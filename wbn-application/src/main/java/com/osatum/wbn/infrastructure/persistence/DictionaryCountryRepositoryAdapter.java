package com.osatum.wbn.infrastructure.persistence;


import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.repository.dictionary.DictionaryCountryRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryCountrySpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DictionaryCountryRepositoryAdapter implements DictionaryCountryRepository {
    private final DictionaryCountrySpringDataRepository repository;

    @Override
    public void saveAll(List<DictionaryCountry> items) {
        repository.saveAll(items);
    }

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    @Override
    public List<DictionaryCountry> findAll() {
        return repository.findAll();
    }
}
