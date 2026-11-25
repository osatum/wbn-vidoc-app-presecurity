package com.osatum.wbn.domain.model.user;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.institution.Institution;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_data_deletion_request",
        indexes = {
                @Index(name = "idx_user_data_deletion_request_user", columnList = "user_id"),
                @Index(name = "idx_user_data_deletion_request_institution", columnList = "institution_id"),
                @Index(name = "idx_user_data_deletion_request_email", columnList = "email"),
                @Index(name = "idx_user_data_deletion_request_status", columnList = "status"),
                @Index(name = "idx_user_data_deletion_request_requested_at", columnList = "requested_at"),
                @Index(name = "idx_user_data_deletion_request_token_hash", columnList = "token_hash")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDataDeletionRequest extends GeneratedIdEntity {

    @Column(nullable = false, length = 128)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @Column(name = "token_hash", length = 128)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserDataDeletionRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "clicked_at")
    private Instant clickedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(length = 128)
    private String browser;

    @Column(name = "email_message_log_id")
    private Long emailMessageLogId;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;
}
