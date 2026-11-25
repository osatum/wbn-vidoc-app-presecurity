package com.osatum.wbn.application.security;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;

import java.util.Optional;

public sealed interface Actor permits UserActor, InstitutionActor {

    Long institutionId();

    InstitutionType institutionType();

    InstitutionRole institutionRole();

    Long loginSessionId();

    default Optional<Long> institutionIdOptional() {
        return Optional.ofNullable(institutionId());
    }

    default Optional<InstitutionType> institutionTypeOptional() {
        return Optional.ofNullable(institutionType());
    }

    default Optional<InstitutionRole> institutionRoleOptional() {
        return Optional.ofNullable(institutionRole());
    }

    default boolean hasInstitutionContext() {
        return institutionIdOptional().isPresent();
    }
}
