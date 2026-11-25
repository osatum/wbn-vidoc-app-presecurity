package com.osatum.wbn.application.dictionary.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.domain.repository.dictionary.DictionaryMniswDisciplineRepository;
import org.springframework.stereotype.Service;

@Service
public class DictionaryMniswDisciplineService extends AbstractDictionaryCodeItemCacheService<DictionaryMniswDiscipline> {
    public DictionaryMniswDisciplineService(DictionaryMniswDisciplineRepository dictionaryRepository) {
        super(dictionaryRepository);
    }
}
