package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.domain.repository.dictionary.DictionaryMniswDisciplineRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryMniswDisciplineSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class DictionaryMniswDisciplineRepositoryAdapter implements DictionaryMniswDisciplineRepository {
    private final DictionaryMniswDisciplineSpringDataRepository repository;

    @Override
    public void saveAll(List<DictionaryMniswDiscipline> items) {
        repository.saveAll(items);
    }

    @Override
    public void deleteAll() {
        repository.deleteAllInBatch();
    }

    @Override
    public List<DictionaryMniswDiscipline> findAll() {
        return repository.findAll();
    }
}
