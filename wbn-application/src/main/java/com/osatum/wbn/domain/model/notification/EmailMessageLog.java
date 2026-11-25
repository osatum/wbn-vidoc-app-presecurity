package com.osatum.wbn.domain.model.notification;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.user.AppUser;
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
@Table(name = "email_message_log",
        indexes = {
                @Index(name = "idx_email_message_log_user", columnList = "user_id"),
                @Index(name = "idx_email_message_log_institution", columnList = "institution_id"),
                @Index(name = "idx_email_message_log_status", columnList = "status"),
                @Index(name = "idx_email_message_log_type", columnList = "email_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessageLog extends GeneratedIdEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 64)
    private EmailType emailType;

    @Column(nullable = false, length = 256)
    private String recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private InstitutionMembership membership;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailMessageStatus status;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "template_identifier", length = 128)
    private String templateIdentifier;

    @Column(name = "context_data", length = 4000)
    private String contextData;
}
