package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LoginSessionSpringDataRepository extends JpaRepository<LoginSession, Long>, JpaSpecificationExecutor<LoginSession> {
    List<LoginSession> findByStatusAndExpiresAtLessThanEqual(LoginSessionStatus status, Instant now);

    List<LoginSession> findByUserIdAndStatusAndExpiresAtAfterAndEndedAtIsNull(Long userId, LoginSessionStatus status, Instant now);

    List<LoginSession> findByInstitutionIdAndStatusAndExpiresAtAfterAndEndedAtIsNull(Long institutionId, LoginSessionStatus status, Instant now);
}
