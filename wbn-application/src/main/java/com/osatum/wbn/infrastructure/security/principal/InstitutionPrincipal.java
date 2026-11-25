package com.osatum.wbn.infrastructure.security.principal;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;

public record InstitutionPrincipal(Long institutionId,  String institutionName, InstitutionType institutionType, InstitutionRole institutionRole, String name, Long loginSessionId) {
}
