package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.auth.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenSpringDataRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    List<RefreshToken> findByLoginSessionIdAndRevokedFalse(Long loginSessionId);
}
