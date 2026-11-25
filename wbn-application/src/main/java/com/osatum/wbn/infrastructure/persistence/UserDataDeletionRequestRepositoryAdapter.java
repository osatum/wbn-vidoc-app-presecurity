package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequestStatus;
import com.osatum.wbn.domain.repository.user.UserDataDeletionRequestRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.UserDataDeletionRequestSpringDataRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserDataDeletionRequestRepositoryAdapter implements UserDataDeletionRequestRepository {
    private static final String ANONYMIZED_EMAIL = "anonymous@deleted.invalid";
    private static final String ANONYMIZED_ACTOR = "anonymous";

    private final UserDataDeletionRequestSpringDataRepository springDataRepository;
    private final EntityManager entityManager;

    @Override
    public UserDataDeletionRequest save(UserDataDeletionRequest request) {
        return springDataRepository.save(request);
    }

    @Override
    public Optional<UserDataDeletionRequest> findByTokenHash(String tokenHash) {
        return springDataRepository.findByTokenHash(tokenHash);
    }

    @Override
    public List<UserDataDeletionRequest> findByUserIdAndStatusIn(Long userId, Collection<UserDataDeletionRequestStatus> statuses) {
        return springDataRepository.findByUserIdAndStatusIn(userId, statuses);
    }

    @Override
    public long countRecentByUserIdOrIpAddress(Long userId, String ipAddress, Instant from) {
        return springDataRepository.countRecentByUserIdOrIpAddress(userId, ipAddress, from);
    }

    @Override
    public void anonymizeRequestsForUser(Long userId, String email) {
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT);

        entityManager.createQuery("""
                update UserDataDeletionRequest request
                set request.user = null,
                    request.email = :anonymizedEmail,
                    request.tokenHash = null,
                    request.ipAddress = null,
                    request.userAgent = null,
                    request.browser = null,
                    request.errorMessage = null,
                    request.createdBy = :anonymizedActor,
                    request.editedBy = :anonymizedActor
                where request.user.id = :userId
                    or lower(request.email) = :email
                """)
                .setParameter("userId", userId)
                .setParameter("email", normalizedEmail)
                .setParameter("anonymizedEmail", ANONYMIZED_EMAIL)
                .setParameter("anonymizedActor", ANONYMIZED_ACTOR)
                .executeUpdate();
    }
}
