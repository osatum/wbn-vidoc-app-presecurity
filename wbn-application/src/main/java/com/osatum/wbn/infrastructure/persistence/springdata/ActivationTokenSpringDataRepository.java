package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.registration.ActivationTokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActivationTokenSpringDataRepository extends JpaRepository<ActivationToken, Long> {
    Optional<ActivationToken> findByTokenHash(String tokenHash);

    List<ActivationToken> findByMembershipIdAndStatus(Long membershipId, ActivationTokenStatus status);
}
