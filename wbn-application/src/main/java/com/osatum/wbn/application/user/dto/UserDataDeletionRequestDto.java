package com.osatum.wbn.application.user.dto;

import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDataDeletionRequestDto {
    private String email;
    private Long userId;
    private Long institutionId;
    private InstitutionType institutionType;
}
