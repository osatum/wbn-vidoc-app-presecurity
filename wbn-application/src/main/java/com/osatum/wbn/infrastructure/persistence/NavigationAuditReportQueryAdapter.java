package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.system.dto.NavigationAuditReportType;
import com.osatum.wbn.application.system.query.NavigationAuditReportQuery;
import com.osatum.wbn.application.system.query.NavigationAuditReportRow;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NavigationAuditReportQueryAdapter implements NavigationAuditReportQuery {
    private final EntityManager entityManager;
    private static final Map<String, String> PAGE_NAMES = Map.of(
            "system.admin.dashboard", "Panel administratora systemu",
            "institution.admin.dashboard", "Panel administratora instytucji",
            "institution.user.dashboard", "Panel użytkownika instytucji",
            "institution.journals.list", "Lista czasopism",
            "institution.journals.details", "Szczegóły czasopisma",
            "institution.journals.decision", "Karta decyzji czasopisma"
    );

    @Override
    public List<NavigationAuditReportRow> fetch(NavigationAuditReportType type, Instant from, Instant until,
                                               NavigationAuditReportRow cursor, int size) {
        boolean visits = type == NavigationAuditReportType.VISITS;
        String time = visits ? "record.startedAt" : "record.occurredAt";
        String select = visits
                ? "record.lastActivityAt, record.endReason"
                : "record.pageKey, record.resourceId, record.target, record.eventId, record.routeTemplate";
        String entity = visits ? "NavigationAuditVisit" : "NavigationAuditEvent";
        boolean serviceEntries = type == NavigationAuditReportType.SERVICE_ENTRIES;
        String filter = visits ? "" : " and record.eventType = :eventType";
        if (!visits && !serviceEntries) {
            filter += " and record.actorType <> :anonymous";
        }
        if (type == NavigationAuditReportType.FILE_DOWNLOADS) {
            filter += " and record.success = true and (lower(record.target) like '%.xls' or lower(record.target) like '%.xlsx')";
        }
        String after = cursor == null ? "" : " and (" + time + " > :cursorTime or (" + time
                + " = :cursorTime and record.id > :cursorId))";
        var query = entityManager.createQuery("select record.id, " + time
                + ", institution.name, actor.email, record.actorType, " + select
                + " from " + entity + " record left join record.institution institution left join record.user actor"
                + " where " + time + " >= :from and " + time + " < :until" + filter + after
                + " order by " + time + ", record.id", Object[].class)
                .setParameter("from", from).setParameter("until", until).setMaxResults(size);
        if (!visits) {
            if (!serviceEntries) {
                query.setParameter("anonymous", NavigationAuditActorType.ANONYMOUS);
            }
            query.setParameter("eventType", switch (type) {
                case PAGE_VIEWS -> NavigationAuditEventType.PAGE_VIEW;
                case FILE_DOWNLOADS -> NavigationAuditEventType.FILE_DOWNLOAD;
                case SERVICE_ENTRIES -> NavigationAuditEventType.SERVICE_ENTRY;
                case VISITS -> throw new IllegalArgumentException("Visits do not query audit events.");
            });
        }
        if (cursor != null) {
            query.setParameter("cursorTime", cursor.occurredAt()).setParameter("cursorId", cursor.id());
        }
        return query.getResultList().stream().map(result -> new NavigationAuditReportRow(
                (Long) result[0], (Instant) result[1], (String) result[2], (String) result[3],
                (NavigationAuditActorType) result[4], visits ? (Instant) result[5] : null,
                visits ? null : description(type, result),
                visits ? (String) result[6] : null,
                visits ? null : (UUID) result[8],
                visits ? null : (String) result[5],
                visits ? null : (String) result[9]
        )).toList();
    }

    @Override
    public Optional<Instant> firstVisitAt() {
        return Optional.ofNullable(entityManager.createQuery(
                "select min(visit.startedAt) from NavigationAuditVisit visit", Instant.class).getSingleResult());
    }

    private String description(NavigationAuditReportType type, Object[] result) {
        if (type == NavigationAuditReportType.SERVICE_ENTRIES) {
            return "Wejście do serwisu";
        }
        if (type == NavigationAuditReportType.FILE_DOWNLOADS) {
            return (String) result[7];
        }
        String pageKey = (String) result[5];
        String page = PAGE_NAMES.getOrDefault(pageKey, pageKey);
        return result[6] == null ? page : page + " (ID: " + result[6] + ")";
    }
}
