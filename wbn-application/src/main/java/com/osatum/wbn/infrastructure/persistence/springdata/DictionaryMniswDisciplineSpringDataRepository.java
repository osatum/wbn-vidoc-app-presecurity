package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryMniswDisciplineSpringDataRepository extends JpaRepository<DictionaryMniswDiscipline, String> {
}
