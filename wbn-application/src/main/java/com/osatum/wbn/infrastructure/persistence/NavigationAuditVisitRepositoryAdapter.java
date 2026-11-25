package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditVisit;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditVisitActivity;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditVisitRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NavigationAuditVisitRepositoryAdapter implements NavigationAuditVisitRepository {
    private final EntityManager entityManager;

    @Override
    public Optional<LoginSession> lockSession(Long sessionId) {
        return Optional.ofNullable(entityManager.find(LoginSession.class, sessionId, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public Optional<NavigationAuditVisit> findOpen(Long sessionId) {
        return entityManager.createQuery("""
                select visit from NavigationAuditVisit visit
                where visit.loginSessionId = :sessionId and visit.endedAt is null
                """, NavigationAuditVisit.class)
                .setParameter("sessionId", sessionId).setMaxResults(1).getResultList().stream().findFirst();
    }

    @Override
    public Optional<NavigationAuditVisit> findLatest(Long sessionId) {
        return entityManager.createQuery("""
                select visit from NavigationAuditVisit visit where visit.loginSessionId = :sessionId
                order by visit.startedAt desc, visit.id desc
                """, NavigationAuditVisit.class)
                .setParameter("sessionId", sessionId).setMaxResults(1).getResultList().stream().findFirst();
    }

    @Override
    public void save(NavigationAuditVisit visit) {
        if (visit.getId() == null) {
            entityManager.persist(visit);
        }
        entityManager.flush();
    }

    @Override
    public boolean hasActivity(Long sessionId, UUID eventId) {
        return entityManager.find(NavigationAuditVisitActivity.class,
                new NavigationAuditVisitActivity.ActivityId(sessionId, eventId)) != null;
    }

    @Override
    public void recordActivity(Long sessionId, UUID eventId, Instant receivedAt) {
        entityManager.persist(new NavigationAuditVisitActivity(sessionId, eventId, receivedAt));
    }

    @Override
    public List<Long> findInactiveSessionIds(Instant cutoff, int limit) {
        return entityManager.createQuery("""
                select visit.loginSessionId from NavigationAuditVisit visit
                where visit.endedAt is null and visit.lastActivityAt <= :cutoff
                  and visit.loginSessionId is not null
                order by visit.loginSessionId
                """, Long.class).setParameter("cutoff", cutoff).setMaxResults(limit).getResultList();
    }

    @Override
    public void deleteActivitiesBefore(Instant cutoff) {
        entityManager.createQuery("delete from NavigationAuditVisitActivity activity where activity.receivedAt < :cutoff")
                .setParameter("cutoff", cutoff).executeUpdate();
    }
}
