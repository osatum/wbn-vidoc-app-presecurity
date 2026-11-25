package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.registration.AccountActivationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AccountActivationLogListDto {
    private Long id;
    private Long userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private Long institutionId;
    private String institutionName;
    private Long membershipId;
    private Long activationTokenId;
    private Instant attemptedAt;
    private AccountActivationStatus status;
    private String ipAddress;
    private String userAgent;
    private String browser;
    private String operatingSystem;
    private String errorMessage;
    private Boolean termsAndPrivacyPolicyAccepted;
    private Boolean personalDataProcessingAcknowledged;
}
