package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.dictionary.DictionaryTRCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryTRCategorySpringDataRepository extends JpaRepository<DictionaryTRCategory, Long> {
}
