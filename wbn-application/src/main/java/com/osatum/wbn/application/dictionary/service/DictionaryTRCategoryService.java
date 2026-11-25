package com.osatum.wbn.application.dictionary.service;


import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import com.osatum.wbn.domain.repository.dictionary.DictionaryTRCategoryRepository;
import org.springframework.stereotype.Service;

/**
 * TR oznaczają zwykle kategorie Thomson Reuters, czyli dawną nazwę klasyfikacji związanej z Web of Science / Journal Citation Reports. Obecnie właścicielem WoS jest Clarivate, a kategorie nazywa się Web of Science Categories (WC).
 */
@Service
public class DictionaryTRCategoryService extends AbstractDictionaryIdItemCacheService<DictionaryTRCategory> {
    public DictionaryTRCategoryService(DictionaryTRCategoryRepository repository) {
        super(repository);
    }
}
