package com.osatum.wbn.application.system.dto;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;

import java.time.Duration;
import java.time.Instant;

public record NavigationAuditVisitListDto(
        Long id, NavigationAuditActorType actorType,
        Long institutionId, String institutionName,
        Long userId, String userEmail, String userFirstName, String userLastName,
        Instant startedAt, Instant lastActivityAt, Instant endedAt, String endReason,
        long durationSeconds, NavigationAuditVisitStatus status
) {
    public NavigationAuditVisitListDto(Long id, NavigationAuditActorType actorType,
                                       Long institutionId, String institutionName,
                                       Long userId, String userEmail, String userFirstName, String userLastName,
                                       Instant startedAt, Instant lastActivityAt, Instant endedAt, String endReason) {
        this(id, actorType, institutionId, institutionName, userId, userEmail, userFirstName, userLastName,
                startedAt, lastActivityAt, endedAt, endReason,
                Duration.between(startedAt, lastActivityAt).getSeconds(),
                endedAt == null ? NavigationAuditVisitStatus.OPEN : NavigationAuditVisitStatus.CLOSED);
    }
}
