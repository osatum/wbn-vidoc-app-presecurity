package com.osatum.wbn.domain.repository.user;

import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequestStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserDataDeletionRequestRepository {
    UserDataDeletionRequest save(UserDataDeletionRequest request);

    Optional<UserDataDeletionRequest> findByTokenHash(String tokenHash);

    List<UserDataDeletionRequest> findByUserIdAndStatusIn(Long userId, Collection<UserDataDeletionRequestStatus> statuses);

    long countRecentByUserIdOrIpAddress(Long userId, String ipAddress, Instant from);

    void anonymizeRequestsForUser(Long userId, String email);
}
