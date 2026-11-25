package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.user.dto.UserBasicDataDto;
import com.osatum.wbn.application.user.dto.UserCreateDto;
import com.osatum.wbn.application.user.dto.UserCreateResultDto;
import com.osatum.wbn.application.user.dto.UserCreateStatus;
import com.osatum.wbn.application.user.dto.UserViewDto;
import com.osatum.wbn.application.user.mapper.UserMapper;
import com.osatum.wbn.application.user.query.UserListQuery;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserPersonalDataDeletionRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceTest {
    private UserRepository repository;
    private UserPersonalDataDeletionRepository personalDataDeletionRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private LoginSessionRepository loginSessionRepository;
    private InstitutionRepository institutionRepository;
    private InstitutionMembershipRepository membershipRepository;
    private UserMapper mapper;
    private UserUseCaseValidator useCaseValidator;
    private PasswordEncoder passwordEncoder;
    private UserService service;
    private UserProfileService profileService;
    private CurrentActorProvider currentActorProvider;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        personalDataDeletionRepository = mock(UserPersonalDataDeletionRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        loginSessionRepository = mock(LoginSessionRepository.class);
        institutionRepository = mock(InstitutionRepository.class);
        membershipRepository = mock(InstitutionMembershipRepository.class);
        mapper = mock(UserMapper.class);
        useCaseValidator = mock(UserUseCaseValidator.class);
        passwordEncoder = mock(PasswordEncoder.class);
        currentActorProvider = mock(CurrentActorProvider.class);

        service = new UserService(
                mock(UserListQueryHandler.class),
                repository,
                institutionRepository,
                membershipRepository,
                mapper,
                useCaseValidator,
                passwordEncoder,
                personalDataDeletionRepository
        );
        profileService = new UserProfileService(
                currentActorProvider,
                repository,
                mapper,
                passwordEncoder,
                refreshTokenRepository,
                loginSessionRepository
        );
        when(currentActorProvider.currentActor()).thenReturn(userActor(InstitutionType.RESEARCH_UNIT));
    }

    @Test
    void returnsDefaultUserListQuery() {
        UserListQuery query = service.getDefaultUserListQuery();

        assertThat(query.getPage()).isZero();
        assertThat(query.getSize()).isEqualTo(10);
        assertThat(query.getFirst()).isZero();
        assertThat(query.getSortBy()).isEqualTo(UserListQuery.SortBy.SORT_BY_NAME_ASC);
    }

    @Test
    void preparesCreateUserFormWithUserRoleForResearchUnit() {
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution(1L, InstitutionType.RESEARCH_UNIT)));

        UserCreateDto result = service.prepareCreateUserForm(1L);

        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_USER);
        assertThat(result.getRoleOptions()).containsExactly(InstitutionRole.INSTITUTION_USER);
    }

    @Test
    void preparesCreateUserFormWithAdminRoleForSystemOperator() {
        when(institutionRepository.findById(2L)).thenReturn(Optional.of(institution(2L, InstitutionType.SYSTEM_OPERATOR)));

        UserCreateDto result = service.prepareCreateUserForm(2L);

        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_ADMIN);
        assertThat(result.getRoleOptions()).containsExactly(InstitutionRole.INSTITUTION_ADMIN);
    }

    @Test
    void rejectsRoleNotAllowedForInstitutionType() {
        UserCreateDto dto = createDto(1L, InstitutionRole.INSTITUTION_ADMIN);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution(1L, InstitutionType.RESEARCH_UNIT)));
        when(useCaseValidator.findExistingUser("admin@example.org")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role is not allowed");

        verify(repository, never()).save(any(AppUser.class));
        verify(membershipRepository, never()).save(any(InstitutionMembership.class));
    }

    @Test
    void createsSystemOperatorUserWithAdminApplicationRoleAndAdminMembership() {
        Institution institution = institution(2L, InstitutionType.SYSTEM_OPERATOR);
        UserCreateDto dto = createDto(2L, InstitutionRole.INSTITUTION_ADMIN);
        AppUser user = new AppUser();
        InstitutionMembership membership = new InstitutionMembership();
        UserCreateResultDto expected = new UserCreateResultDto();
        expected.setStatus(UserCreateStatus.CREATED);

        when(institutionRepository.findById(2L)).thenReturn(Optional.of(institution));
        when(useCaseValidator.findExistingUser("admin@example.org")).thenReturn(Optional.empty());
        when(useCaseValidator.normalizeEmail("admin@example.org")).thenReturn("admin@example.org");
        when(passwordEncoder.encode("secret123")).thenReturn("encoded");
        when(mapper.createEntity(same(dto), eq("admin@example.org"), eq("encoded"), eq("ADMIN"))).thenReturn(user);
        when(repository.save(user)).thenReturn(user);
        when(mapper.createMembership(user, institution, InstitutionRole.INSTITUTION_ADMIN)).thenReturn(membership);
        when(membershipRepository.save(membership)).thenReturn(membership);
        when(mapper.createResult(UserCreateStatus.CREATED, user, membership)).thenReturn(expected);

        UserCreateResultDto result = service.createUser(dto);

        assertThat(result.getStatus()).isEqualTo(UserCreateStatus.CREATED);
        verify(mapper).createEntity(same(dto), eq("admin@example.org"), eq("encoded"), eq("ADMIN"));
        verify(mapper).createMembership(user, institution, InstitutionRole.INSTITUTION_ADMIN);
    }

    @Test
    void attachesExistingUserToSystemOperatorAndAddsAdminApplicationRole() {
        Institution institution = institution(2L, InstitutionType.SYSTEM_OPERATOR);
        UserCreateDto dto = createDto(2L, InstitutionRole.INSTITUTION_ADMIN);
        dto.setAttachExistingUser(true);
        AppUser user = new AppUser();
        user.setId(10L);
        user.setRoles("USER");
        InstitutionMembership membership = new InstitutionMembership();
        UserCreateResultDto expected = new UserCreateResultDto();
        expected.setStatus(UserCreateStatus.EXISTING_USER_ATTACHED);

        when(institutionRepository.findById(2L)).thenReturn(Optional.of(institution));
        when(useCaseValidator.findExistingUser("admin@example.org")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        when(mapper.createMembership(user, institution, InstitutionRole.INSTITUTION_ADMIN)).thenReturn(membership);
        when(membershipRepository.save(membership)).thenReturn(membership);
        when(mapper.createResult(UserCreateStatus.EXISTING_USER_ATTACHED, user, membership)).thenReturn(expected);

        UserCreateResultDto result = service.createUser(dto);

        assertThat(result.getStatus()).isEqualTo(UserCreateStatus.EXISTING_USER_ATTACHED);
        assertThat(user.getRoles()).isEqualTo("USER,ADMIN");
        verify(repository).save(user);
        verify(mapper).createMembership(user, institution, InstitutionRole.INSTITUTION_ADMIN);
    }

    @Test
    void deletesAccountAndMembershipsButAnonymizesUserActivityInFkSafeOrder() {
        AppUser user = new AppUser();
        user.setId(42L);
        user.setEmail("jan@example.org");
        when(repository.findById(42L)).thenReturn(Optional.of(user));

        service.deleteForGdpr(42L);

        InOrder inOrder = inOrder(personalDataDeletionRepository, repository);
        inOrder.verify(repository).findById(42L);
        inOrder.verify(personalDataDeletionRepository).deleteRefreshTokensForUser(42L);
        inOrder.verify(personalDataDeletionRepository).anonymizeAccountActivationLogsForUser(42L);
        inOrder.verify(personalDataDeletionRepository).anonymizePasswordResetRequestsForUser(42L, "jan@example.org");
        inOrder.verify(personalDataDeletionRepository).anonymizeUserDataDeletionRequestsForUser(42L, "jan@example.org");
        inOrder.verify(personalDataDeletionRepository).anonymizeEmailMessageLogsForUser(42L, "jan@example.org");
        inOrder.verify(personalDataDeletionRepository).anonymizeNavigationAuditVisitsForUser(42L);
        inOrder.verify(personalDataDeletionRepository).anonymizeLoginSessionsForUser(42L, "jan@example.org");
        inOrder.verify(personalDataDeletionRepository).deleteActivationTokensForUser(42L);
        inOrder.verify(personalDataDeletionRepository).clearMembershipActivatedByUser(42L);
        inOrder.verify(personalDataDeletionRepository).deleteMembershipsForUser(42L);
        inOrder.verify(repository).delete(user);
    }

    @Test
    void throwsWhenUserDoesNotExist() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteForGdpr(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found: 404");

        verifyNoInteractions(personalDataDeletionRepository);
    }

    @Test
    void changesOwnPasswordAndRevokesOtherSessionsOnly() {
        AppUser user = new AppUser();
        user.setId(42L);
        user.setPasswordHash("old-hash");

        LoginSession currentSession = activeSession(10L);
        LoginSession otherSession = activeSession(11L);
        RefreshToken currentToken = refreshToken(currentSession);
        RefreshToken otherToken = refreshToken(otherSession);

        when(repository.findById(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(42L)).thenReturn(List.of(currentToken, otherToken));
        when(loginSessionRepository.findActiveByUserId(any(), any(Instant.class))).thenReturn(List.of(currentSession, otherSession));

        profileService.changeOwnPassword("current-password", "new-password", "new-password");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(currentToken.isRevoked()).isFalse();
        assertThat(otherToken.isRevoked()).isTrue();
        assertThat(currentSession.getStatus()).isEqualTo(LoginSessionStatus.ACTIVE);
        assertThat(currentSession.getEndedAt()).isNull();
        assertThat(otherSession.getStatus()).isEqualTo(LoginSessionStatus.REVOKED);
        assertThat(otherSession.getEndReason()).isEqualTo(LoginSessionEndReason.REVOKED);
        assertThat(otherSession.getEndedAt()).isNotNull();

        verify(repository).save(user);
        verify(refreshTokenRepository, never()).save(currentToken);
        verify(refreshTokenRepository).save(otherToken);
        verify(loginSessionRepository, never()).save(currentSession);
        verify(loginSessionRepository).save(otherSession);
    }

    @Test
    void rejectsOwnPasswordChangeWhenCurrentPasswordIsInvalid() {
        AppUser user = new AppUser();
        user.setId(42L);
        user.setPasswordHash("old-hash");
        when(repository.findById(42L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> profileService.changeOwnPassword("bad-password", "new-password", "new-password"))
                .isInstanceOf(BadCredentialsException.class);

        verify(repository, never()).save(user);
        verifyNoInteractions(refreshTokenRepository, loginSessionRepository);
    }

    @Test
    void findsOwnBasicDataForResearchUnitUser() {
        AppUser user = new AppUser();
        user.setId(42L);
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        user.setOrcid("0000-0000-0000-000X");
        UserBasicDataDto dto = new UserBasicDataDto();
        dto.setFirstName("Jan");
        dto.setLastName("Kowalski");
        dto.setOrcid("0000-0000-0000-000X");

        when(repository.findById(42L)).thenReturn(Optional.of(user));
        when(mapper.basicDataDto(user)).thenReturn(dto);

        UserBasicDataDto result = profileService.findOwnBasicData();

        assertThat(result).isSameAs(dto);
        verify(mapper).basicDataDto(user);
    }

    @Test
    void rejectsOwnBasicDataLookupOutsideResearchUnitContext() {
        when(currentActorProvider.currentActor()).thenReturn(userActor(InstitutionType.SYSTEM_OPERATOR));

        assertThatThrownBy(profileService::findOwnBasicData)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("research unit");

        verify(repository, never()).findById(any());
    }

    @Test
    void updatesOwnBasicDataForResearchUnitUser() {
        AppUser user = new AppUser();
        user.setId(42L);
        UserBasicDataDto dto = new UserBasicDataDto();
        dto.setFirstName("Jan");
        dto.setLastName("Kowalski");
        dto.setOrcid("0000-0000-0000-000X");
        when(repository.findById(42L)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        var result = profileService.updateOwnBasicData(dto);

        assertThat(result.getUserId()).isEqualTo(42L);
        verify(mapper).updateBasicData(dto, user);
        verify(repository).save(user);
    }

    @Test
    void rejectsOwnBasicDataUpdateOutsideResearchUnitContext() {
        UserBasicDataDto dto = new UserBasicDataDto();

        when(currentActorProvider.currentActor()).thenReturn(userActor(InstitutionType.SYSTEM_OPERATOR));

        assertThatThrownBy(() -> profileService.updateOwnBasicData(dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("research unit");

        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
    }

    private LoginSession activeSession(Long id) {
        LoginSession session = new LoginSession();
        session.setId(id);
        session.setStatus(LoginSessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        return session;
    }

    private UserActor userActor(InstitutionType institutionType) {
        return new UserActor(
                42L,
                "jan@example.org",
                1L,
                institutionType,
                InstitutionRole.INSTITUTION_USER,
                10L
        );
    }

    private RefreshToken refreshToken(LoginSession session) {
        RefreshToken token = new RefreshToken();
        token.setLoginSession(session);
        token.setRevoked(false);
        return token;
    }

    private UserCreateDto createDto(Long institutionId, InstitutionRole role) {
        UserCreateDto dto = new UserCreateDto();
        dto.setInstitutionId(institutionId);
        dto.setEmail("admin@example.org");
        dto.setFirstName("Admin");
        dto.setLastName("User");
        dto.setPassword("secret123");
        dto.setRole(role);
        return dto;
    }

    private Institution institution(Long id, InstitutionType institutionType) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName("Institution " + id);
        institution.setInstitutionType(institutionType);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        return institution;
    }
}
