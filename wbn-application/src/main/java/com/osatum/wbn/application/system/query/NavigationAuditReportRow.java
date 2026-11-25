package com.osatum.wbn.application.system.query;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import java.time.Instant;
import java.util.UUID;

public record NavigationAuditReportRow(Long id, Instant occurredAt, String institution, String user,
                                       NavigationAuditActorType actorType, Instant lastActivityAt,
                                       String description, String endReason, UUID eventId,
                                       String pageKey, String routeTemplate) {
    public NavigationAuditReportRow(Long id, Instant occurredAt, String institution, String user,
                                    NavigationAuditActorType actorType, Instant lastActivityAt,
                                    String description, String endReason) {
        this(id, occurredAt, institution, user, actorType, lastActivityAt, description, endReason, null, null, null);
    }
}
