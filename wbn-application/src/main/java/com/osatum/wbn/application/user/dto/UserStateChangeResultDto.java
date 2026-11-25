package com.osatum.wbn.application.user.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserStateChangeResultDto {
    private UserStateChangeStatus status;
    private UserViewDto user;
    private List<UserMembershipDto> memberships;
}
