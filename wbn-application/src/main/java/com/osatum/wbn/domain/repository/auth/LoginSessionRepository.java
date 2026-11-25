package com.osatum.wbn.domain.repository.auth;

import com.osatum.wbn.domain.model.auth.LoginSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LoginSessionRepository {
    LoginSession save(LoginSession session);

    Optional<LoginSession> findById(Long id);

    List<LoginSession> findExpiredActive(Instant now);

    List<LoginSession> findActiveByUserId(Long userId, Instant now);

    List<LoginSession> findActiveByInstitutionId(Long institutionId, Instant now);
}
