package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequestStatus;
import com.osatum.wbn.domain.repository.passwordreset.PasswordResetRequestRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.PasswordResetRequestSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetRequestRepositoryAdapter implements PasswordResetRequestRepository {
    private final PasswordResetRequestSpringDataRepository springDataRepository;

    @Override
    public PasswordResetRequest save(PasswordResetRequest request) {
        return springDataRepository.save(request);
    }

    @Override
    public Optional<PasswordResetRequest> findByTokenHash(String tokenHash) {
        return springDataRepository.findByTokenHash(tokenHash);
    }

    @Override
    public List<PasswordResetRequest> findByUserIdAndStatusIn(Long userId, Collection<PasswordResetRequestStatus> statuses) {
        return springDataRepository.findByUserIdAndStatusIn(userId, statuses);
    }

    @Override
    public long countRecentByEmailOrIpAddress(String email, String ipAddress, Instant from) {
        return springDataRepository.countRecentByEmailOrIpAddress(email, ipAddress, from);
    }
}
