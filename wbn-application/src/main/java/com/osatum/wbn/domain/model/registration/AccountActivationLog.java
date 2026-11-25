package com.osatum.wbn.domain.model.registration;

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
@Table(name = "account_activation_log",
        indexes = {
                @Index(name = "idx_account_activation_log_user", columnList = "user_id"),
                @Index(name = "idx_account_activation_log_institution", columnList = "institution_id"),
                @Index(name = "idx_account_activation_log_attempted_at", columnList = "attempted_at"),
                @Index(name = "idx_account_activation_log_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountActivationLog extends GeneratedIdEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private Institution institution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private InstitutionMembership membership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activation_token_id")
    private ActivationToken activationToken;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private AccountActivationStatus status;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 1024)
    private String userAgent;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "technical_data", length = 4000)
    private String technicalData;

    @Column(name = "terms_and_privacy_policy_accepted")
    private Boolean termsAndPrivacyPolicyAccepted;

    @Column(name = "personal_data_processing_acknowledged")
    private Boolean personalDataProcessingAcknowledged;
}
