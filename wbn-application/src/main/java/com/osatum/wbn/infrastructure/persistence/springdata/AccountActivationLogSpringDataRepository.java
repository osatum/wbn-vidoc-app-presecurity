package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountActivationLogSpringDataRepository extends JpaRepository<AccountActivationLog, Long>, JpaSpecificationExecutor<AccountActivationLog> {
}
