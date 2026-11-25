package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.LoginSessionSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LoginSessionRepositoryAdapter implements LoginSessionRepository {

    private final LoginSessionSpringDataRepository springData;

    @Override
    public LoginSession save(LoginSession session) {
        return springData.save(session);
    }

    @Override
    public Optional<LoginSession> findById(Long id) {
        return springData.findById(id);
    }

    @Override
    public List<LoginSession> findExpiredActive(Instant now) {
        return springData.findByStatusAndExpiresAtLessThanEqual(LoginSessionStatus.ACTIVE, now);
    }

    @Override
    public List<LoginSession> findActiveByUserId(Long userId, Instant now) {
        return springData.findByUserIdAndStatusAndExpiresAtAfterAndEndedAtIsNull(userId, LoginSessionStatus.ACTIVE, now);
    }

    @Override
    public List<LoginSession> findActiveByInstitutionId(Long institutionId, Instant now) {
        return springData.findByInstitutionIdAndStatusAndExpiresAtAfterAndEndedAtIsNull(
                institutionId,
                LoginSessionStatus.ACTIVE,
                now
        );
    }
}
