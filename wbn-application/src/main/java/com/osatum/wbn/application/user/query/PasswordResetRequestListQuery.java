package com.osatum.wbn.application.user.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequestStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PasswordResetRequestListQuery extends PageRequest {
    private Long userId;
    private String name;
    private String email;
    private String ipAddress;
    private String userAgent;
    private PasswordResetRequestStatus status;
    private Instant requestedFrom;
    private Instant requestedTo;
}
