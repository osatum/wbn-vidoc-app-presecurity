package com.osatum.wbn.infrastructure.security.service;

import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService(
            "1234567890123456789012345678901234567890",
            "test-issuer",
            10
    );

    @Test
    void includesLoginSessionIdInUserAccessToken() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setEmail("jan@example.org");
        user.setRoles("ADMIN");

        String token = jwtService.createUserAccessToken(
                user,
                2L,
                InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_ADMIN,
                44L
        );

        var claims = jwtService.parse(token);

        assertThat(claims.get(ClaimsConstant.CLAIMS_LOGIN_SESSION_ID, Long.class)).isEqualTo(44L);
    }

    @Test
    void includesLoginSessionIdInInstitutionAccessToken() {
        Institution institution = new Institution();
        institution.setId(2L);
        institution.setName("Instytucja");

        String token = jwtService.createInstitutionAccessToken(
                institution,
                InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_VIEWER,
                55L
        );

        var claims = jwtService.parse(token);

        assertThat(claims.get(ClaimsConstant.CLAIMS_LOGIN_SESSION_ID, Long.class)).isEqualTo(55L);
    }
}
