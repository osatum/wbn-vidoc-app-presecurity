package com.osatum.wbn.domain.model.navigationaudit;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "navigation_audit_event", indexes = {
        @Index(name = "idx_navigation_audit_event_page_time", columnList = "page_key, occurred_at"),
        @Index(name = "idx_navigation_audit_event_type_time", columnList = "event_type, occurred_at"),
        @Index(name = "idx_navigation_audit_event_user_time", columnList = "user_id, occurred_at"),
        @Index(name = "idx_navigation_audit_event_institution_time", columnList = "institution_id, occurred_at"),
        @Index(name = "idx_navigation_audit_event_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_navigation_audit_event_login_session", columnList = "login_session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NavigationAuditEvent extends GeneratedIdEntity {

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private NavigationAuditEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 32)
    private NavigationAuditActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", length = 64)
    private InstitutionType institutionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_role", length = 64)
    private InstitutionRole institutionRole;

    @Column(name = "login_session_id")
    private Long loginSessionId;

    @Column(name = "frontend_session_id", length = 80)
    private String frontendSessionId;

    @Column(name = "view_id", length = 80)
    private String viewId;

    @Column(name = "page_key", nullable = false, length = 120)
    private String pageKey;

    @Column(name = "route_template", nullable = false, length = 250)
    private String routeTemplate;

    @Column(name = "route_path", length = 500)
    private String routePath;

    @Column(name = "route_params_json", columnDefinition = "text")
    private String routeParamsJson;

    @Column(name = "resource_type", length = 80)
    private String resourceType;

    @Column(name = "resource_id", length = 120)
    private String resourceId;

    @Column(name = "action", length = 120)
    private String action;

    @Column(name = "target", length = 120)
    private String target;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(name = "properties_json", columnDefinition = "text")
    private String propertiesJson;
}
