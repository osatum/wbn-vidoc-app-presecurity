package com.osatum.wbn.domain.repository.dictionary;

import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;

import java.util.List;

public interface DictionaryCountryRepository extends DictionaryItemsRepository<DictionaryCountry> {
    void saveAll(List<DictionaryCountry> items);
}
