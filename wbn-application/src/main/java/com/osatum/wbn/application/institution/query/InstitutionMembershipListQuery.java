package com.osatum.wbn.application.institution.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstitutionMembershipListQuery extends PageRequest {
    private Long institutionId;
    private String email;
    private String name;
}
