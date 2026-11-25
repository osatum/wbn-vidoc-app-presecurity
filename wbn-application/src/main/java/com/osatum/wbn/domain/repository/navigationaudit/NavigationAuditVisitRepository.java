package com.osatum.wbn.domain.repository.navigationaudit;

import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditVisit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NavigationAuditVisitRepository {
    Optional<LoginSession> lockSession(Long sessionId);
    Optional<NavigationAuditVisit> findOpen(Long sessionId);
    Optional<NavigationAuditVisit> findLatest(Long sessionId);
    void save(NavigationAuditVisit visit);
    boolean hasActivity(Long sessionId, UUID eventId);
    void recordActivity(Long sessionId, UUID eventId, Instant receivedAt);
    List<Long> findInactiveSessionIds(Instant cutoff, int limit);
    void deleteActivitiesBefore(Instant cutoff);
}
