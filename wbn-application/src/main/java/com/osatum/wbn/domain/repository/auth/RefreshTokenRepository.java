package com.osatum.wbn.domain.repository.auth;

import com.osatum.wbn.domain.model.auth.RefreshToken;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);
    List<RefreshToken> findByLoginSessionIdAndRevokedFalse(Long loginSessionId);
    RefreshToken save(RefreshToken token);
}
