package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.EntityState;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class UserViewDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String orcid;
    private String roles;
    private List<UserMembershipDto> memberships;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime editedAt;
    private String editedBy;
    private EntityState state;
}
