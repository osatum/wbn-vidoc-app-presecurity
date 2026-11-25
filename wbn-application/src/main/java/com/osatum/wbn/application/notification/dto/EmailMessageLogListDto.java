package com.osatum.wbn.application.notification.dto;

import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.model.notification.EmailType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;

@Getter
@Setter
public class EmailMessageLogListDto {
    private Long id;
    private EmailType emailType;
    private String recipient;
    private Long userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private Long institutionId;
    private String institutionName;
    private Long membershipId;
    private EmailMessageStatus status;
    private OffsetDateTime createdAt;
    private Instant sentAt;
    private int attemptCount;
    private String errorMessage;
    private String templateIdentifier;
}
