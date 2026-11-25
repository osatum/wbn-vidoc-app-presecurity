package com.osatum.wbn.application.institution.dto;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionMembershipAccessDto {
    private Long id;
    private InstitutionRole role;
}
