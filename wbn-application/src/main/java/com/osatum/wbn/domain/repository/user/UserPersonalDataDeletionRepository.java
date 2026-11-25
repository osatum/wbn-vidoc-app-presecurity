package com.osatum.wbn.domain.repository.user;

public interface UserPersonalDataDeletionRepository {
    void deleteRefreshTokensForUser(Long userId);

    void anonymizeAccountActivationLogsForUser(Long userId);

    void anonymizePasswordResetRequestsForUser(Long userId, String email);

    void anonymizeUserDataDeletionRequestsForUser(Long userId, String email);

    void anonymizeEmailMessageLogsForUser(Long userId, String email);

    void deleteActivationTokensForUser(Long userId);

    void anonymizeLoginSessionsForUser(Long userId, String email);

    void anonymizeNavigationAuditEventsForUser(Long userId);

    void anonymizeNavigationAuditVisitsForUser(Long userId);

    void clearMembershipActivatedByUser(Long userId);

    void deleteMembershipsForUser(Long userId);
}
