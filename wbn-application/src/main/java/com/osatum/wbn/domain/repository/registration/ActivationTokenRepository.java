package com.osatum.wbn.domain.repository.registration;

import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.registration.ActivationTokenStatus;

import java.util.List;
import java.util.Optional;

public interface ActivationTokenRepository {
    Optional<ActivationToken> findByTokenHash(String tokenHash);

    List<ActivationToken> findByMembershipIdAndStatus(Long membershipId, ActivationTokenStatus status);

    ActivationToken save(ActivationToken token);
}
