package com.osatum.wbn.infrastructure.security.principal;

import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class UserPrincipal {
    private Long userId;
    private Long membershipId;
    private String email;
    private String firstName;
    private String lastName;
    private String orcid;
    private String name;
    private String key;
    private Long institutionId;
    private InstitutionType institutionType;
    private InstitutionRole institutionRole;
    private String institutionName;
    private Long loginSessionId;
    private List<MembershipAccess> accesses;
}
