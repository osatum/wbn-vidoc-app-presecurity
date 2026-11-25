package com.osatum.wbn.application.dictionary.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;
import com.osatum.wbn.domain.repository.dictionary.DictionaryRepositoryRepository;
import org.springframework.stereotype.Service;

/**
 * Udostępnia pamięć podręczną baz indeksacyjnych według identyfikatorów zachowywanych w danych ankiety.
 */
@Service
public class DictionaryRepositoryService extends AbstractDictionaryIdItemCacheService<DictionaryRepository> {
    public DictionaryRepositoryService(DictionaryRepositoryRepository dictionaryRepository) {
        super(dictionaryRepository);
    }
}
