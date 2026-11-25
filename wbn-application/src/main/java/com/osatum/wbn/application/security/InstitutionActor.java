package com.osatum.wbn.application.security;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;

public record InstitutionActor(
        Long institutionId,
        InstitutionType institutionType,
        InstitutionRole institutionRole,
        String name,
        Long loginSessionId
) implements Actor {
}
