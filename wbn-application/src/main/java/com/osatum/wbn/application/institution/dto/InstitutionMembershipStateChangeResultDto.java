package com.osatum.wbn.application.institution.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstitutionMembershipStateChangeResultDto {
    private InstitutionMembershipStateChangeStatus status;
    private InstitutionMembershipListDto membership;
}
