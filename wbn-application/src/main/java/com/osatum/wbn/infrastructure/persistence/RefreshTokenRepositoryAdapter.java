package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.RefreshTokenSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenSpringDataRepository springData;

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return springData.findByTokenHash(tokenHash);
    }

    @Override
    public List<RefreshToken> findByUserIdAndRevokedFalse(Long userId) {
        return springData.findByUserIdAndRevokedFalse(userId);
    }

    @Override
    public List<RefreshToken> findByLoginSessionIdAndRevokedFalse(Long loginSessionId) {
        return springData.findByLoginSessionIdAndRevokedFalse(loginSessionId);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        return springData.save(token);
    }
}
