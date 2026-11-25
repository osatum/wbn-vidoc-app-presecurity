package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationInstitutionDto {
    private Long id;
    private String name;
    private String description;
    private InstitutionType institutionType;
    private boolean allowOtherIp;
    private boolean allowOtherEmailDomain;
}
