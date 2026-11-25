package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.repository.institution.InstitutionDeletionRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionDeletionUser;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class InstitutionDeletionRepositoryAdapter implements InstitutionDeletionRepository {

    private static final String ANONYMIZED_ACTOR = "deleted-user";
    private static final Pattern DATABASE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final EntityManager entityManager;

    @Override
    public List<InstitutionDeletionUser> findUsersWithoutOtherInstitution(Long institutionId) {
        return entityManager.createQuery("""
                        select distinct membership.user.id, membership.user.email
                        from InstitutionMembership membership
                        where membership.institution.id = :institutionId
                            and not exists (
                                select otherMembership.id
                                from InstitutionMembership otherMembership
                                where otherMembership.user.id = membership.user.id
                                    and otherMembership.institution.id <> :institutionId
                            )
                        """, Object[].class)
                .setParameter("institutionId", institutionId)
                .getResultList()
                .stream()
                .map(result -> new InstitutionDeletionUser((Long) result[0], (String) result[1]))
                .toList();
    }

    @Override
    public void deleteDirectDataForInstitution(Long institutionId) {
        deleteRefreshTokensForInstitution(institutionId);
        deleteNavigationAuditVisits("institution", institutionId);
        deleteNavigationAuditEventsForInstitution(institutionId);
        deleteAccountActivationLogsForInstitution(institutionId);
        deleteEmailMessageLogsForInstitution(institutionId);
        deleteUserDataDeletionRequestsForInstitution(institutionId);
        deleteActivationTokensForInstitution(institutionId);
        deleteLoginSessionsForInstitution(institutionId);
        deleteInstitutionIpRules(institutionId);
        deleteInstitutionEmailDomains(institutionId);
    }

    @Override
    public void deleteAllDataForUser(InstitutionDeletionUser user) {
        Long userId = user.userId();
        String normalizedEmail = normalizeEmail(user.email());

        deleteRefreshTokensForUser(userId);
        deleteNavigationAuditVisits("user", userId);
        deleteNavigationAuditEventsForUser(userId);
        deleteAccountActivationLogsForUser(userId);
        deleteEmailMessageLogsForUser(userId, normalizedEmail);
        deleteUserDataDeletionRequestsForUser(userId, normalizedEmail);
        deletePasswordResetRequestsForUser(userId, normalizedEmail);
        deleteActivationTokensForUser(userId);
        deleteLoginSessionsForUser(userId, normalizedEmail);
        clearMembershipActivatedByUser(userId);
        deleteMembershipsForUser(userId);
        deleteUser(userId);
        anonymizeAuditActorReferences(normalizedEmail);
    }

    @Override
    public void deleteMembershipsForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from InstitutionMembership membership
                        where membership.institution.id = :institutionId
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    @Override
    public void deleteInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from Institution institution
                        where institution.id = :institutionId
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteRefreshTokensForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from RefreshToken token
                        where token.institution.id = :institutionId
                            or token.institutionMembership.id in (
                                select membership.id
                                from InstitutionMembership membership
                                where membership.institution.id = :institutionId
                            )
                            or token.loginSession.id in (
                                select session.id
                                from LoginSession session
                                where session.institution.id = :institutionId
                            )
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteNavigationAuditVisits(String actorField, Long actorId) {
        entityManager.createQuery("delete from NavigationAuditVisitActivity activity where activity.loginSessionId in "
                        + "(select session.id from LoginSession session where session." + actorField + ".id = :actorId)")
                .setParameter("actorId", actorId).executeUpdate();
        entityManager.createQuery("delete from NavigationAuditVisit visit where visit." + actorField + ".id = :actorId")
                .setParameter("actorId", actorId).executeUpdate();
    }

    private void deleteNavigationAuditEventsForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from NavigationAuditEvent event
                        where event.institution.id = :institutionId
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteAccountActivationLogsForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from AccountActivationLog log
                        where log.institution.id = :institutionId
                            or log.membership.id in (
                                select membership.id
                                from InstitutionMembership membership
                                where membership.institution.id = :institutionId
                            )
                            or log.activationToken.id in (
                                select token.id
                                from ActivationToken token
                                where token.institution.id = :institutionId
                            )
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteEmailMessageLogsForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from EmailMessageLog log
                        where log.institution.id = :institutionId
                            or log.membership.id in (
                                select membership.id
                                from InstitutionMembership membership
                                where membership.institution.id = :institutionId
                            )
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteUserDataDeletionRequestsForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from UserDataDeletionRequest request
                        where request.institution.id = :institutionId
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteActivationTokensForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from ActivationToken token
                        where token.institution.id = :institutionId
                            or token.membership.id in (
                                select membership.id
                                from InstitutionMembership membership
                                where membership.institution.id = :institutionId
                            )
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteLoginSessionsForInstitution(Long institutionId) {
        entityManager.createQuery("""
                        delete from LoginSession session
                        where session.institution.id = :institutionId
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteInstitutionIpRules(Long institutionId) {
        entityManager.createQuery("""
                        delete from InstitutionIpRule rule
                        where rule.institution.id = :institutionId
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteInstitutionEmailDomains(Long institutionId) {
        entityManager.createQuery("""
                        delete from InstitutionEmailDomain domain
                        where domain.institution.id = :institutionId
                        """)
                .setParameter("institutionId", institutionId)
                .executeUpdate();
    }

    private void deleteRefreshTokensForUser(Long userId) {
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

    private void deleteNavigationAuditEventsForUser(Long userId) {
        entityManager.createQuery("""
                        delete from NavigationAuditEvent event
                        where event.user.id = :userId
                            or event.loginSessionId in (
                                select session.id
                                from LoginSession session
                                where session.user.id = :userId
                            )
                        """)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private void deleteAccountActivationLogsForUser(Long userId) {
        entityManager.createQuery("""
                        delete from AccountActivationLog log
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
                .executeUpdate();
    }

    private void deleteEmailMessageLogsForUser(Long userId, String normalizedEmail) {
        entityManager.createQuery("""
                        delete from EmailMessageLog log
                        where log.user.id = :userId
                            or log.membership.id in (
                                select membership.id
                                from InstitutionMembership membership
                                where membership.user.id = :userId
                            )
                            or (:email <> '' and lower(log.recipient) = :email)
                        """)
                .setParameter("userId", userId)
                .setParameter("email", normalizedEmail)
                .executeUpdate();
    }

    private void deleteUserDataDeletionRequestsForUser(Long userId, String normalizedEmail) {
        entityManager.createQuery("""
                        delete from UserDataDeletionRequest request
                        where request.user.id = :userId
                            or (:email <> '' and lower(request.email) = :email)
                        """)
                .setParameter("userId", userId)
                .setParameter("email", normalizedEmail)
                .executeUpdate();
    }

    private void deletePasswordResetRequestsForUser(Long userId, String normalizedEmail) {
        entityManager.createQuery("""
                        delete from PasswordResetRequest request
                        where request.user.id = :userId
                            or (:email <> '' and lower(request.email) = :email)
                        """)
                .setParameter("userId", userId)
                .setParameter("email", normalizedEmail)
                .executeUpdate();
    }

    private void deleteActivationTokensForUser(Long userId) {
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

    private void deleteLoginSessionsForUser(Long userId, String normalizedEmail) {
        entityManager.createQuery("""
                        delete from LoginSession session
                        where session.user.id = :userId
                            or (:email <> '' and lower(session.userEmail) = :email)
                        """)
                .setParameter("userId", userId)
                .setParameter("email", normalizedEmail)
                .executeUpdate();
    }

    private void clearMembershipActivatedByUser(Long userId) {
        entityManager.createQuery("""
                        update InstitutionMembership membership
                        set membership.activatedBy = null
                        where membership.activatedBy.id = :userId
                        """)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private void deleteMembershipsForUser(Long userId) {
        entityManager.createQuery("""
                        delete from InstitutionMembership membership
                        where membership.user.id = :userId
                        """)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private void deleteUser(Long userId) {
        entityManager.createQuery("""
                        delete from AppUser user
                        where user.id = :userId
                        """)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private void anonymizeAuditActorReferences(String normalizedEmail) {
        if (normalizedEmail.isBlank()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<String> tableNames = entityManager.createNativeQuery("""
                        select table_name
                        from information_schema.columns
                        where table_schema = current_schema()
                            and column_name in ('created_by', 'edited_by')
                        group by table_name
                        having count(distinct column_name) = 2
                        """)
                .getResultList();

        tableNames.stream()
                .filter(tableName -> DATABASE_IDENTIFIER.matcher(tableName).matches())
                .forEach(tableName -> entityManager.createNativeQuery("""
                                update %s
                                set created_by = case when lower(created_by) = :email then :anonymizedActor else created_by end,
                                    edited_by = case when lower(edited_by) = :email then :anonymizedActor else edited_by end
                                where lower(created_by) = :email
                                    or lower(edited_by) = :email
                                """.formatted(tableName))
                        .setParameter("email", normalizedEmail)
                        .setParameter("anonymizedActor", ANONYMIZED_ACTOR)
                        .executeUpdate());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
