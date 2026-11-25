package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.dictionary.DictionaryLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryLanguageSpringDataRepository extends JpaRepository<DictionaryLanguage, String> {
}
