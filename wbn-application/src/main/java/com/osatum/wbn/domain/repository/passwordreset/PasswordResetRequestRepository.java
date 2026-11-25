package com.osatum.wbn.domain.repository.passwordreset;

import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequestStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PasswordResetRequestRepository {
    PasswordResetRequest save(PasswordResetRequest request);

    Optional<PasswordResetRequest> findByTokenHash(String tokenHash);

    List<PasswordResetRequest> findByUserIdAndStatusIn(Long userId, Collection<PasswordResetRequestStatus> statuses);

    long countRecentByEmailOrIpAddress(String email, String ipAddress, Instant from);
}
