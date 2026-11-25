package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserCreateDto {
    private Long institutionId;
    private String email;
    private String firstName;
    private String lastName;
    private String orcid;
    private String password;
    private InstitutionRole role;
    private List<InstitutionRole> roleOptions;
    private boolean attachExistingUser;
}
