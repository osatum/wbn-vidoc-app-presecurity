package com.osatum.wbn.application.dictionary.service;

import com.osatum.wbn.domain.model.dictionary.DictionaryIdItem;
import com.osatum.wbn.domain.repository.dictionary.DictionaryItemsRepository;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractDictionaryIdItemCacheService<T extends DictionaryIdItem> {
    private volatile Map<Long, T> cache = Collections.emptyMap();

    private final DictionaryItemsRepository<T> dictionaryRepository;

    public AbstractDictionaryIdItemCacheService(DictionaryItemsRepository dictionaryRepository) {
        this.dictionaryRepository = dictionaryRepository;
    }

    /**
     * Inicjalizuje pamięć podręczną słownika po utworzeniu beana.
     */
    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * Odświeża pamięć podręczną słownika, pobierając aktualne elementy z repozytorium.
     */
    public void reload() {
        List<T> items = dictionaryRepository.findAll();

        Map<Long, T> newCache = items.stream()
                .collect(Collectors.toMap(DictionaryIdItem::getId, item -> item));

        cache = Collections.unmodifiableMap(newCache);
    }

    /**
     * Zwraca wszystkie elementy słownika, ładując cache przy pierwszym użyciu.
     */
    public List<T> getAll() {
        if (cache.isEmpty()) {
            reload();
        }

        return new ArrayList<>(cache.values());
    }

    /**
     * Zwraca element słownika identyfikowany kluczem liczbowym, ładując cache przy pierwszym użyciu.
     */
    public T getById(Long id) {
        if (cache.isEmpty()) {
            reload();
        }
        return cache.get(id);
    }
}
