package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.dictionary.DictionaryScientificCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryScientificCategorySpringDataRepository  extends JpaRepository<DictionaryScientificCategory, Long> {
}
