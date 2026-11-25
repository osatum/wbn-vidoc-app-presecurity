package com.osatum.wbn.application.dictionary.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import com.osatum.wbn.domain.repository.dictionary.DictionaryCountryRepository;
import org.springframework.stereotype.Service;

/**
 * Udostępnia pamięć podręczną krajów identyfikowanych kodem; nazwy służą prezentacji, nie filtrowaniu.
 */
@Service
public class DictionaryCountryService  extends AbstractDictionaryCodeItemCacheService<DictionaryCountry> {
    public DictionaryCountryService(DictionaryCountryRepository repository) {
        super(repository);
    }
}
