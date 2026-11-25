package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailMessageLogSpringDataRepository extends JpaRepository<EmailMessageLog, Long>, JpaSpecificationExecutor<EmailMessageLog> {
}
