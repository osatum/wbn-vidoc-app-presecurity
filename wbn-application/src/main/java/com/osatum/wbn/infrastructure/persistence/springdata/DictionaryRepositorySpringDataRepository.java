package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.dictionary.DictionaryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryRepositorySpringDataRepository extends JpaRepository<DictionaryRepository, Long> {
}
