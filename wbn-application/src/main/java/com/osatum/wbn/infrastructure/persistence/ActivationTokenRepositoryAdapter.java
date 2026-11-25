package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.registration.ActivationTokenStatus;
import com.osatum.wbn.domain.repository.registration.ActivationTokenRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.ActivationTokenSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ActivationTokenRepositoryAdapter implements ActivationTokenRepository {
    private final ActivationTokenSpringDataRepository springDataRepository;

    @Override
    public Optional<ActivationToken> findByTokenHash(String tokenHash) {
        return springDataRepository.findByTokenHash(tokenHash);
    }

    @Override
    public List<ActivationToken> findByMembershipIdAndStatus(Long membershipId, ActivationTokenStatus status) {
        return springDataRepository.findByMembershipIdAndStatus(membershipId, status);
    }

    @Override
    public ActivationToken save(ActivationToken token) {
        return springDataRepository.save(token);
    }
}
