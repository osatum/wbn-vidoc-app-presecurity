package com.osatum.wbn.application.auth.service;

import com.osatum.wbn.application.auth.dto.AuthStatus;
import com.osatum.wbn.application.auth.dto.SelectInstitutionAndRoleRequest;
import com.osatum.wbn.application.auth.port.AccessTokenService;
import com.osatum.wbn.application.auth.port.PreAuthTokenService;
import com.osatum.wbn.application.institution.mapper.InstitutionAccessDtoMapper;
import com.osatum.wbn.application.institution.mapper.InstitutionMembershipAccessDtoMapper;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final AccessTokenService accessTokenService = mock(AccessTokenService.class);
    private final PreAuthTokenService preAuthTokenService = mock(PreAuthTokenService.class);
    private final CurrentActorProvider currentActorProvider = mock(CurrentActorProvider.class);
    private final LoginSessionService loginSessionService = mock(LoginSessionService.class);
    private final InstitutionIpMatcherService ipMatcherService = mock(InstitutionIpMatcherService.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(
                userRepository,
                refreshTokenRepository,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                accessTokenService,
                preAuthTokenService,
                currentActorProvider,
                mock(InstitutionRepository.class),
                mock(InstitutionMembershipRepository.class),
                ipMatcherService,
                mock(InstitutionAccessDtoMapper.class),
                mock(InstitutionMembershipAccessDtoMapper.class),
                loginSessionService
        );
        ReflectionTestUtils.setField(service, "refreshTtlDays", 7L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void selectsContextUsingAuthenticatedUserWithoutPreAuthCookie() {
        AppUser user = activeUser(10L);
        Institution institution = activeInstitution(20L, InstitutionType.SYSTEM_OPERATOR);
        InstitutionMembership membership = activeMembership(30L, institution, InstitutionRole.INSTITUTION_ADMIN);
        membership.setLoginAllowedFromAnyIp(false);
        user.setMemberships(Set.of(membership));

        LoginSession session = new LoginSession();
        session.setId(40L);
        when(currentActorProvider.findCurrentActor()).thenReturn(Optional.of(new UserActor(
                user.getId(),
                user.getEmail(),
                11L,
                InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_USER,
                12L
        )));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(ipMatcherService.matchesInstitution(institution.getId(), "127.0.0.1")).thenReturn(true);
        when(loginSessionService.startUserSession(
                eq(user),
                eq(institution),
                eq(InstitutionRole.INSTITUTION_ADMIN),
                any(ClientRequestMetadata.class),
                any()
        )).thenReturn(session);
        when(accessTokenService.createUserAccessToken(
                user,
                institution.getId(),
                institution.getInstitutionType(),
                membership.getRole(),
                session.getId()
        )).thenReturn("switched-access-token");

        var result = service.selectInstitution(
                new SelectInstitutionAndRoleRequest(institution.getId(), membership.getId()),
                null,
                new ClientRequestMetadata("127.0.0.1", "JUnit")
        );

        assertThat(result.response().getStatus()).isEqualTo(AuthStatus.OK);
        assertThat(result.response().getAccessToken()).isEqualTo("switched-access-token");
        assertThat(result.response().getInstitutionType()).isEqualTo(InstitutionType.SYSTEM_OPERATOR);
        verify(preAuthTokenService, never()).requireUserIdFromPreAuthToken(any());
        verify(ipMatcherService).matchesInstitution(institution.getId(), "127.0.0.1");
    }

    private AppUser activeUser(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail("user@example.org");
        user.setState(EntityState.ACTIVE);
        return user;
    }

    private Institution activeInstitution(Long id, InstitutionType type) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("System operator");
        institution.setInstitutionType(type);
        institution.setState(EntityState.ACTIVE);
        return institution;
    }

    private InstitutionMembership activeMembership(Long id, Institution institution, InstitutionRole role) {
        InstitutionMembership membership = new InstitutionMembership();
        membership.setId(id);
        membership.setInstitution(institution);
        membership.setRole(role);
        membership.setState(EntityState.ACTIVE);
        return membership;
    }
}
