package com.osatum.wbn.application.auth.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginSessionListQuery extends PageRequest {
    private String name;
    private String email;
    private String ipAddress;
    private LoginActorType actorType;
    private LoginSessionStatus status;
    private LoginSessionEndReason endReason;
    private Boolean activeOnly;
    private Long userId;
    private Long institutionId;

}
