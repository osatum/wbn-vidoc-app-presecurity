package com.osatum.wbn.application.user.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequestStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class UserDataDeletionRequestListQuery extends PageRequest {
    private Long userId;
    private Long institutionId;
    private String name;
    private String email;
    private String institutionName;
    private String ipAddress;
    private String userAgent;
    private UserDataDeletionRequestStatus status;
    private Instant requestedFrom;
    private Instant requestedTo;
}
