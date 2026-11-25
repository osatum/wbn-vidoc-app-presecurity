package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.institution.Institution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InstitutionSpringDataRepository  extends JpaRepository<Institution, Long>, JpaSpecificationExecutor<Institution> {
}
