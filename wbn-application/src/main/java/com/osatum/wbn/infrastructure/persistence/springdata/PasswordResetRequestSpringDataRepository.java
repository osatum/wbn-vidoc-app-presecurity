package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetRequestSpringDataRepository extends JpaRepository<PasswordResetRequest, Long>, JpaSpecificationExecutor<PasswordResetRequest> {
    Optional<PasswordResetRequest> findByTokenHash(String tokenHash);

    List<PasswordResetRequest> findByUserIdAndStatusIn(Long userId, Collection<PasswordResetRequestStatus> statuses);

    @Query("""
            select count(request)
            from PasswordResetRequest request
            where request.requestedAt >= :from
                and (
                    request.email = :email
                    or (:ipAddress is not null and request.ipAddress = :ipAddress)
                )
            """)
    long countRecentByEmailOrIpAddress(
            @Param("email") String email,
            @Param("ipAddress") String ipAddress,
            @Param("from") Instant from
    );
}
