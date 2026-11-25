package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.repository.dictionary.DictionaryLanguageRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryLanguageSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DictionaryLanguageRepositoryAdapter implements DictionaryLanguageRepository {
    private final DictionaryLanguageSpringDataRepository repository;

    @Override
    public void saveAll(List<DictionaryLanguage> items) {
        repository.saveAll(items);
    }

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    @Override
    public List<DictionaryLanguage> findAll() {
        return repository.findAll();
    }
}
