package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.dictionary.DictionaryCountry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DictionaryCountrySpringDataRepository extends JpaRepository<DictionaryCountry, String> {
}
