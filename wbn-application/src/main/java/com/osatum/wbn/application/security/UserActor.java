package com.osatum.wbn.application.security;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;

public record UserActor(
        Long userId,
        String email,
        Long institutionId,
        InstitutionType institutionType,
        InstitutionRole institutionRole,
        Long loginSessionId
) implements Actor {
}
