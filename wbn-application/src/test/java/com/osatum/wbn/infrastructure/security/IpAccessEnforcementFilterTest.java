package com.osatum.wbn.infrastructure.security;

import com.osatum.wbn.application.auth.service.LoginSessionService;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.application.shared.request.ClientRequestMetadataResolutionService;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionMembershipSpringDataRepository;
import com.osatum.wbn.infrastructure.security.principal.InstitutionPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IpAccessEnforcementFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInstitutionSessionOutsideAllowedNetwork() throws Exception {
        var requestMetadataService = mock(ClientRequestMetadataResolutionService.class);
        var matcher = mock(InstitutionIpMatcherService.class);
        var membershipRepository = mock(InstitutionMembershipSpringDataRepository.class);
        var loginSessionService = mock(LoginSessionService.class);
        var refreshTokenRepository = mock(RefreshTokenRepository.class);
        var filter = new IpAccessEnforcementFilter(
                requestMetadataService,
                matcher,
                membershipRepository,
                loginSessionService,
                refreshTokenRepository
        );

        var principal = new InstitutionPrincipal(
                7L,
                "Institution",
                InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_VIEWER,
                "Institution",
                11L
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
        when(requestMetadataService.resolve(any())).thenReturn(new ClientRequestMetadata("2001:db8::10", "JUnit"));
        when(loginSessionService.isActive(11L)).thenReturn(true);
        when(matcher.matchesInstitution(7L, "2001:db8::10")).thenReturn(false);
        when(refreshTokenRepository.findByLoginSessionIdAndRevokedFalse(11L)).thenReturn(List.of());

        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/institution/7"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("IP_ACCESS_DENIED");
        verify(loginSessionService).revoke(11L);
    }
}
