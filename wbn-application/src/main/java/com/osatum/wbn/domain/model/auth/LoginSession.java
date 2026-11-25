package com.osatum.wbn.domain.model.auth;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "login_session", indexes = {
        @Index(name = "idx_login_session_active", columnList = "status, expires_at"),
        @Index(name = "idx_login_session_user", columnList = "user_id"),
        @Index(name = "idx_login_session_institution", columnList = "institution_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginSession extends GeneratedIdEntity {


    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 32)
    private LoginActorType actorType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(name = "user_email", length = 128)
    private String userEmail;

    @Column(name = "institution_name", length = 512)
    private String institutionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_type", length = 64)
    private InstitutionType institutionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "institution_role", length = 64)
    private InstitutionRole institutionRole;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(name = "browser", length = 128)
    private String browser;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LoginSessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 32)
    private LoginSessionEndReason endReason;
}
