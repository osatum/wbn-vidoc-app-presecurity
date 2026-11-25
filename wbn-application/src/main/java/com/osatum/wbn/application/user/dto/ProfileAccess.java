package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProfileAccess {
    private Long membershipId;
    private Long institutionId;
    private String name;
    private InstitutionRole institutionRole;
    private InstitutionType institutionType;
}
