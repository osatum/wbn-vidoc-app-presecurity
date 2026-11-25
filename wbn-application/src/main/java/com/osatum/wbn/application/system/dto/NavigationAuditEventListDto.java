package com.osatum.wbn.application.system.dto;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class NavigationAuditEventListDto {
    private Long id;
    private UUID eventId;
    private NavigationAuditEventType eventType;
    private Instant occurredAt;
    private Instant receivedAt;
    private NavigationAuditActorType actorType;
    private Long userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private Long institutionId;
    private String institutionName;
    private InstitutionType institutionType;
    private InstitutionRole institutionRole;
    private Long loginSessionId;
    private String viewId;
    private String pageKey;
    private String routeTemplate;
    private String routePath;
    private String resourceType;
    private String resourceId;
    private String action;
    private String target;
    private Integer durationMs;
    private Boolean success;
    private Integer statusCode;
    private String ipAddress;
    private String userAgent;
    private String propertiesJson;
}
