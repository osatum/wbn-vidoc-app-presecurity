package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserMembershipDto {
    private Long id;
    private Long institutionId;
    private InstitutionRole role;
    private String institutionName;
    private InstitutionType institutionType;
}
