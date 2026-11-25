package com.osatum.wbn.application.institution.dto;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstitutionMembershipListDto {
    /* id memebership */
    private Long id;
    /* email user */
    private String email;
    /* first name user */
    private String firstName;
    /* last name user */
    private String lastName;
    /* ORCID user */
    private String orcid;
    /* state user */
    private EntityState userState;
    /* state membership */
    private EntityState state;
    /* role membership */
    private InstitutionRole role;
}
