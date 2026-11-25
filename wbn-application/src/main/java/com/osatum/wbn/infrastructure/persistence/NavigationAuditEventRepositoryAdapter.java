package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditInstitutionEntryCount;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.NavigationAuditEventSpringDataRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class NavigationAuditEventRepositoryAdapter implements NavigationAuditEventRepository {

    private final NavigationAuditEventSpringDataRepository springData;
    private final EntityManager entityManager;

    @Override
    public void saveIfAbsent(NavigationAuditEvent event, Long userId, Long institutionId) {
        if (event.getEventType().isJournalAnalyticsEvent()) {
            saveJournalAnalyticsEvent(event, userId, institutionId);
            return;
        }
        if (springData.existsByEventId(event.getEventId())) {
            return;
        }

        try {
            if (userId != null) {
                event.setUser(entityManager.getReference(AppUser.class, userId));
            }
            if (institutionId != null) {
                event.setInstitution(entityManager.getReference(Institution.class, institutionId));
            }
            springData.save(event);
        } catch (DataIntegrityViolationException ignored) {
            // Zdarzenie jest idempotentne po eventId. Duplikat nie powinien przerywać batcha.
        }
    }

    private void saveJournalAnalyticsEvent(NavigationAuditEvent event, Long userId, Long institutionId) {
        var query = entityManager.createNativeQuery("""
                insert into navigation_audit_event
                    (event_id, event_type, occurred_at, received_at, actor_type, user_id, institution_id,
                     institution_type, institution_role, login_session_id, frontend_session_id, view_id,
                     page_key, route_template, route_path, resource_type, resource_id, action, target,
                     duration_ms, success, status_code, properties_json, created_at, edited_at, state)
                values (:eventId, :eventType, :occurredAt, :receivedAt, :actorType, :userId, :institutionId,
                        :institutionType, :institutionRole, :loginSessionId, :sessionId, :viewId,
                        :pageKey, :routeTemplate, :routePath, :resourceType, :resourceId, :action, :target,
                        :durationMs, :success, :statusCode, :properties, :createdAt, :createdAt, 'ACTIVE')
                on conflict do nothing
                """);
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("eventId", event.getEventId());
        parameters.put("eventType", event.getEventType().name());
        parameters.put("occurredAt", event.getOccurredAt());
        parameters.put("receivedAt", event.getReceivedAt());
        parameters.put("actorType", event.getActorType().name());
        parameters.put("userId", userId);
        parameters.put("institutionId", institutionId);
        parameters.put("institutionType", event.getInstitutionType() == null ? null : event.getInstitutionType().name());
        parameters.put("institutionRole", event.getInstitutionRole() == null ? null : event.getInstitutionRole().name());
        parameters.put("loginSessionId", event.getLoginSessionId());
        parameters.put("sessionId", event.getFrontendSessionId());
        parameters.put("viewId", event.getViewId());
        parameters.put("pageKey", event.getPageKey());
        parameters.put("routeTemplate", event.getRouteTemplate());
        parameters.put("routePath", event.getRoutePath());
        parameters.put("resourceType", event.getResourceType());
        parameters.put("resourceId", event.getResourceId());
        parameters.put("action", event.getAction());
        parameters.put("target", event.getTarget());
        parameters.put("durationMs", event.getDurationMs());
        parameters.put("success", event.getSuccess());
        parameters.put("statusCode", event.getStatusCode());
        parameters.put("properties", event.getPropertiesJson());
        parameters.put("createdAt", event.getReceivedAt());
        parameters.forEach(query::setParameter);
        query.executeUpdate();
    }

    @Override
    public int deleteJournalSearchEventsBefore(Instant cutoff, int limit) {
        return entityManager.createNativeQuery("""
                delete from navigation_audit_event where id in (
                    select id from navigation_audit_event
                    where received_at < :cutoff and event_type in
                        ('JOURNAL_SEARCH', 'JOURNAL_FILTERS_CLEAR', 'JOURNAL_SEARCH_RESULT', 'JOURNAL_SEARCH_EMPTY', 'JOURNAL_CARD_OPEN', 'JOURNAL_ISSUES_CLICK', 'JOURNAL_DECISION_ACTION')
                    order by received_at, id limit :limit)
                """)
                .setParameter("cutoff", cutoff)
                .setParameter("limit", limit)
                .executeUpdate();
    }

    @Override
    public long countOccurredAtBetween(Instant from, Instant to) {
        return springData.countByOccurredAtGreaterThanEqualAndOccurredAtLessThan(from, to);
    }

    @Override
    public long countDistinctUsersOccurredAtBetween(Instant from, Instant to) {
        return springData.countDistinctUsersOccurredAtBetween(from, to);
    }

    @Override
    public long countDistinctInstitutionsOccurredAtBetween(Instant from, Instant to) {
        return springData.countDistinctInstitutionsOccurredAtBetween(from, to);
    }

    @Override
    public long countDistinctLoginSessionsOccurredAtBetween(Instant from, Instant to) {
        return springData.countDistinctLoginSessionsOccurredAtBetween(from, to);
    }

    @Override
    public long countAuthenticatedServiceEntriesOccurredAtBetween(Instant from, Instant to) {
        return springData.countAuthenticatedServiceEntriesOccurredAtBetween(from, to);
    }

    @Override
    public long countAuthenticatedServiceEntriesOccurredAtFrom(Instant from) {
        return springData.countAuthenticatedServiceEntriesOccurredAtFrom(from);
    }

    @Override
    public long countDistinctAuthenticatedFrontendSessionsReceivedAtFrom(Instant from) {
        return springData.countDistinctAuthenticatedFrontendSessionsReceivedAtFrom(from);
    }

    @Override
    public List<NavigationAuditInstitutionEntryCount> findTopInstitutionServiceEntryCounts(Instant from, Instant to, int limit) {
        return springData.findTopInstitutionServiceEntryCounts(from, to, PageRequest.of(0, limit));
    }

    @Override
    public List<NavigationAuditEvent> findLatestAuthenticatedServiceEntries(int limit) {
        return springData.findLatestAuthenticatedServiceEntries(PageRequest.of(0, limit));
    }
}
