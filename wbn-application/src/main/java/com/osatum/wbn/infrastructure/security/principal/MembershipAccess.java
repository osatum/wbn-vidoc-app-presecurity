package com.osatum.wbn.infrastructure.security.principal;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MembershipAccess {
    private String name;
    private Long institutionId;
    private Long membershipId;
    private InstitutionRole institutionRole;
    private InstitutionType institutionType;

}
