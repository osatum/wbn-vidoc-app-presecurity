package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequestStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PasswordResetRequestListDto {
    private Long id;
    private String email;
    private Long userId;
    private String userFirstName;
    private String userLastName;
    private PasswordResetRequestStatus status;
    private Instant requestedAt;
    private Instant expiresAt;
    private Instant clickedAt;
    private Instant completedAt;
    private Instant invalidatedAt;
    private String ipAddress;
    private String userAgent;
    private String browser;
    private String operatingSystem;
    private Long emailMessageLogId;
    private String errorMessage;
}
