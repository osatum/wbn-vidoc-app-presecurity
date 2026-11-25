package com.osatum.wbn.application.dashboard.dto;

import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSessionDto {
    private Long id;
    private OffsetDateTime createdAt;
    private LoginActorType actorType;
    private Long userId;
    private String userEmail;
    private Long institutionId;
    private String institutionName;
    private InstitutionType institutionType;
    private InstitutionRole institutionRole;
    private String ipAddress;
    private String browser;
    private String userAgent;
    private Instant startedAt;
    private Instant lastSeenAt;
    private Instant expiresAt;
    private Instant endedAt;
    private LoginSessionStatus status;
    private LoginSessionEndReason endReason;
}
