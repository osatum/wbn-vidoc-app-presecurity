package com.osatum.wbn.application.auth.port;

import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;

public interface AccessTokenService {

    String createInstitutionAccessToken(
            Institution institution,
            InstitutionType institutionType,
            InstitutionRole institutionRole,
            Long loginSessionId
    );

    String createUserAccessToken(
            AppUser user,
            Long institutionId,
            InstitutionType institutionType,
            InstitutionRole institutionRole,
            Long loginSessionId
    );
}
