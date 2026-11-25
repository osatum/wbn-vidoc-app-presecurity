package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.EntityState;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class UserListViewDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String orcid;
    private OffsetDateTime createdAt;
    private String createdBy;
    private OffsetDateTime editedAt;
    private String editedBy;
    private EntityState state;
}
