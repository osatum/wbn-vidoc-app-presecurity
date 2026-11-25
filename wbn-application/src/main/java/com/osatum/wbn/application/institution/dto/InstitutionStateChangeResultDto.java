package com.osatum.wbn.application.institution.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class InstitutionStateChangeResultDto {
    private InstitutionStateChangeStatus status;
    private InstitutionViewDto institution;
    private List<InstitutionMembershipListDto> memberships;
}
