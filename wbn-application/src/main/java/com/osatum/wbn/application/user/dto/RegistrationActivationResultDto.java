package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationActivationResultDto {
    private Long userId;
    private Long institutionId;
    private Long membershipId;
    private InstitutionRole role;
    private RegistrationActivationStatus status;
}
