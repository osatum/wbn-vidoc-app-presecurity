package com.osatum.wbn.application.auth.dto;

import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;

import java.time.Instant;

public record LoginSessionViewDto(
        Long id,
        LoginActorType actorType,
        Long userId,
        String userEmail,
        Long institutionId,
        String institutionName,
        InstitutionType institutionType,
        InstitutionRole institutionRole,
        String ipAddress,
        String browser,
        String userAgent,
        Instant startedAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant endedAt,
        LoginSessionStatus status,
        LoginSessionEndReason endReason
) {
}
