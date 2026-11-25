package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.repository.user.UserPersonalDataDeletionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class UserPersonalDataDeletionRepositoryAdapter implements UserPersonalDataDeletionRepository {
    private static final String ANONYMIZED_EMAIL = "anonymous@deleted.invalid";
    private static final String ANONYMIZED_IP_ADDRESS = "0.0.0.0";
    private static final String ANONYMIZED_ACTOR = "anonymous";

    private final EntityManager entityManager;

    @Override
    public void deleteRefreshTokensForUser(Long userId) {
        entityManager.createQuery("""
                delete from RefreshToken token
                where token.user.id = :userId
                    or token.institutionMembership.id in (
                        select membership.id
                        from InstitutionMembership membership
                        where membership.user.id = :userId
                    )
                    or token.loginSession.id in (
                        select session.id
                        from LoginSession session
                        where session.user.id = :userId
                    )
                """)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    public void anonymizeAccountActivationLogsForUser(Long userId) {
        entityManager.createQuery("""
                update AccountActivationLog log
                set log.user = null,
                    log.membership = null,
                    log.activationToken = null,
                    log.ipAddress = null,
                    log.userAgent = null,
                    log.errorMessage = null,
                    log.technicalData = null,
                    log.createdBy = :anonymizedActor,
                    log.editedBy = :anonymizedActor
                where log.user.id = :userId
                    or log.membership.id in (
                        select membership.id
                        from InstitutionMembership membership
                        where membership.user.id = :userId
                    )
                    or log.activationToken.id in (
                        select token.id
                        from ActivationToken token
                        where token.user.id = :userId
                    )
                """)
                .setParameter("userId", userId)
                .setParameter("anonymizedActor", ANONYMIZED_ACTOR)
                .executeUpdate();
    }

    @Override
    public void anonymizePasswordResetRequestsForUser(Long userId, String email) {
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT);

        entityManager.createQuery("""
                update PasswordResetRequest request
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

    @Override
    public void anonymizeUserDataDeletionRequestsForUser(Long userId, String email) {
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

    @Override
    public void anonymizeEmailMessageLogsForUser(Long userId, String email) {
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT);

        entityManager.createQuery("""
                update EmailMessageLog log
                set log.user = null,
                    log.membership = null,
                    log.recipient = :anonymizedEmail,
                    log.errorMessage = null,
                    log.contextData = null,
                    log.createdBy = :anonymizedActor,
                    log.editedBy = :anonymizedActor
                where log.user.id = :userId
                    or lower(log.recipient) = :email
                    or log.membership.id in (
                        select membership.id
                        from InstitutionMembership membership
                        where membership.user.id = :userId
                    )
                """)
                .setParameter("userId", userId)
                .setParameter("email", normalizedEmail)
                .setParameter("anonymizedEmail", ANONYMIZED_EMAIL)
                .setParameter("anonymizedActor", ANONYMIZED_ACTOR)
                .executeUpdate();
    }

    @Override
    public void deleteActivationTokensForUser(Long userId) {
        entityManager.createQuery("""
                delete from ActivationToken token
                where token.user.id = :userId
                    or token.membership.id in (
                        select membership.id
                        from InstitutionMembership membership
                        where membership.user.id = :userId
                    )
                """)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    public void anonymizeLoginSessionsForUser(Long userId, String email) {
        String normalizedEmail = email == null ? "" : email.toLowerCase(Locale.ROOT);

        entityManager.createQuery("""
                update LoginSession session
                set session.user = null,
                    session.userEmail = :anonymizedEmail,
                    session.ipAddress = :anonymizedIpAddress,
                    session.userAgent = null,
                    session.browser = null,
                    session.createdBy = :anonymizedActor,
                    session.editedBy = :anonymizedActor
                where session.user.id = :userId
                    or lower(session.userEmail) = :email
                """)
                .setParameter("userId", userId)
                .setParameter("email", normalizedEmail)
                .setParameter("anonymizedEmail", ANONYMIZED_EMAIL)
                .setParameter("anonymizedIpAddress", ANONYMIZED_IP_ADDRESS)
                .setParameter("anonymizedActor", ANONYMIZED_ACTOR)
                .executeUpdate();
    }

    @Override
    public void anonymizeNavigationAuditEventsForUser(Long userId) {
        entityManager.createQuery("""
                update NavigationAuditEvent event
                set event.user = null,
                    event.loginSessionId = null,
                    event.frontendSessionId = null,
                    event.viewId = null,
                    event.ipAddress = :anonymizedIpAddress,
                    event.userAgent = null,
                    event.createdBy = :anonymizedActor,
                    event.editedBy = :anonymizedActor
                where event.user.id = :userId
                """)
                .setParameter("userId", userId)
                .setParameter("anonymizedIpAddress", ANONYMIZED_IP_ADDRESS)
                .setParameter("anonymizedActor", ANONYMIZED_ACTOR)
                .executeUpdate();
    }

    @Override
    public void anonymizeNavigationAuditVisitsForUser(Long userId) {
        entityManager.createQuery("""
                delete from NavigationAuditVisitActivity activity where activity.loginSessionId in
                (select session.id from LoginSession session where session.user.id = :userId)
                """).setParameter("userId", userId).executeUpdate();
        entityManager.createQuery("""
                update NavigationAuditVisit visit set visit.user = null, visit.loginSessionId = null,
                visit.endedAt = visit.lastActivityAt, visit.endReason = 'ANONYMIZED',
                visit.createdBy = :actor, visit.editedBy = :actor where visit.user.id = :userId
                """).setParameter("userId", userId).setParameter("actor", ANONYMIZED_ACTOR).executeUpdate();
    }

    @Override
    public void clearMembershipActivatedByUser(Long userId) {
        entityManager.createQuery("""
                update InstitutionMembership membership
                set membership.activatedBy = null
                where membership.activatedBy.id = :userId
                """)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    @Override
    public void deleteMembershipsForUser(Long userId) {
        entityManager.createQuery("""
                delete from InstitutionMembership membership
                where membership.user.id = :userId
                """)
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
