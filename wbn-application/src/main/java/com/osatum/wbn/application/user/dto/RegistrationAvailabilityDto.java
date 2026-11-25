package com.osatum.wbn.application.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationAvailabilityDto {
    private Long institutionId;
    private boolean allowed;
    private RegistrationAvailabilityReason reason;
    private RegistrationInstitutionDto institution;
}
