package com.osatum.wbn.application.user.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserCreateResultDto {
    private UserCreateStatus status;
    private UserViewDto user;
    private UserMembershipDto membership;
}
