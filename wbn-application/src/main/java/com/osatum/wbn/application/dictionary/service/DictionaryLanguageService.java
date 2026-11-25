package com.osatum.wbn.application.dictionary.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import com.osatum.wbn.domain.repository.dictionary.DictionaryLanguageRepository;
import org.springframework.stereotype.Service;

/**
 * Udostępnia pamięć podręczną języków identyfikowanych kodem, wspólną dla importu i słownika filtrów.
 */
@Service
public class DictionaryLanguageService extends AbstractDictionaryCodeItemCacheService<DictionaryLanguage> {
    public DictionaryLanguageService(DictionaryLanguageRepository dictionaryLanguageRepository) {
        super(dictionaryLanguageRepository);
    }
}
