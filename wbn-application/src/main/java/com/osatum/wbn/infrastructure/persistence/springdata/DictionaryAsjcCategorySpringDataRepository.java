package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.dictionary.DictionaryAsjcCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryAsjcCategorySpringDataRepository extends JpaRepository<DictionaryAsjcCategory, Long> {
}
