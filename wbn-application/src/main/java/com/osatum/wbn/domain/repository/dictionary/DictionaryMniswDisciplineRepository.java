package com.osatum.wbn.domain.repository.dictionary;

import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;

import java.util.List;

public interface DictionaryMniswDisciplineRepository extends DictionaryItemsRepository<DictionaryMniswDiscipline> {
    void saveAll(List<DictionaryMniswDiscipline> items);
}
