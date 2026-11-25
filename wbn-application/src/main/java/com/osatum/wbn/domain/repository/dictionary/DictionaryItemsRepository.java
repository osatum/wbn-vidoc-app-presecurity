package com.osatum.wbn.domain.repository.dictionary;

import java.util.List;

public interface DictionaryItemsRepository<T> {
    List<T> findAll();

    void deleteAll();

}
