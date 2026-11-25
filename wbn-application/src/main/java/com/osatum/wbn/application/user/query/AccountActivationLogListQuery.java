package com.osatum.wbn.application.user.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.domain.model.registration.AccountActivationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AccountActivationLogListQuery extends PageRequest {
    private Long userId;
    private Long institutionId;
    private String name;
    private String email;
    private String ipAddress;
    private String userAgent;
    private AccountActivationStatus status;
    private Instant attemptedFrom;
    private Instant attemptedTo;
}
