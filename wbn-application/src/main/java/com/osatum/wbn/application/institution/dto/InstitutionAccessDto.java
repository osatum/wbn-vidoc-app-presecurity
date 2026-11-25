package com.osatum.wbn.application.institution.dto;


import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InstitutionAccessDto {
    Long id;
    String name;
    String description;
    InstitutionType institutionType;

    private List<InstitutionMembershipAccessDto> memberships;

    // "warunki logowania" na poziomie instytucji:
    AuthMode authMode;             // AUTH_REQUIRED / AUTH_NOT_REQUIRED
    boolean allowRegistration;
    boolean autoActivateNewAccounts;
    // czy posiada uzytkownikow
    private boolean hasUsers;
    // czy wymaga lgowania
    private boolean requiresAuth;
}
