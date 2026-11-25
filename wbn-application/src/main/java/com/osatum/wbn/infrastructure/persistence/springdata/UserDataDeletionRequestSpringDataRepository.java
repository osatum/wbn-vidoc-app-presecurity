package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserDataDeletionRequestSpringDataRepository extends JpaRepository<UserDataDeletionRequest, Long>, JpaSpecificationExecutor<UserDataDeletionRequest> {
    Optional<UserDataDeletionRequest> findByTokenHash(String tokenHash);

    List<UserDataDeletionRequest> findByUserIdAndStatusIn(Long userId, Collection<UserDataDeletionRequestStatus> statuses);

    @Query("""
            select count(request)
            from UserDataDeletionRequest request
            where request.requestedAt >= :from
                and (
                    request.user.id = :userId
                    or (:ipAddress is not null and request.ipAddress = :ipAddress)
                )
            """)
    long countRecentByUserIdOrIpAddress(
            @Param("userId") Long userId,
            @Param("ipAddress") String ipAddress,
            @Param("from") Instant from
    );
}
