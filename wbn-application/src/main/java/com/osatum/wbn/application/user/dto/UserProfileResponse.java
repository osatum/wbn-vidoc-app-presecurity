package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class UserProfileResponse {
    private String name;
    private String email;
    private String firstName;
    private String lastName;
    private String orcid;
    private String key;
    private Long userId;
    private Long institutionId;
    private String institutionName;
    private InstitutionType institutionType;
    private InstitutionRole institutionRole;
    private List<ProfileAccess> institutionAccess;
}
