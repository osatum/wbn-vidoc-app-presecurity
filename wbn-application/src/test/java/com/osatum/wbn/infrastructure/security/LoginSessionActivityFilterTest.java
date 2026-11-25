package com.osatum.wbn.infrastructure.security;

import com.osatum.wbn.application.auth.service.LoginSessionService;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.infrastructure.security.principal.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class LoginSessionActivityFilterTest {
    private LoginSessionService loginSessionService;
    private LoginSessionActivityFilter filter;

    @BeforeEach
    void setUp() {
        loginSessionService = mock(LoginSessionService.class);
        filter = new LoginSessionActivityFilter(loginSessionService);
        ReflectionTestUtils.setField(filter, "lastSeenThrottleSeconds", 60L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void touchesAuthenticatedUserSession() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userPrincipal(7L),
                null,
                List.of()
        ));

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/user/profile"),
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        verify(loginSessionService).touchIfDue(7L, Duration.ofSeconds(60));
    }

    @Test
    void skipsAuthEndpoints() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userPrincipal(7L),
                null,
                List.of()
        ));

        filter.doFilter(
                new MockHttpServletRequest("POST", "/api/auth/refresh"),
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        verify(loginSessionService, never()).touchIfDue(7L, Duration.ofSeconds(60));
    }

    private UserPrincipal userPrincipal(Long loginSessionId) {
        return UserPrincipal.builder()
                .userId(1L)
                .email("jan@example.org")
                .institutionId(2L)
                .institutionType(InstitutionType.RESEARCH_UNIT)
                .institutionRole(InstitutionRole.INSTITUTION_ADMIN)
                .loginSessionId(loginSessionId)
                .build();
    }
}
