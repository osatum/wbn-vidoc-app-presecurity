package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.user.dto.ProfileAccess;
import com.osatum.wbn.application.user.dto.UserBasicDataDto;
import com.osatum.wbn.application.user.dto.UserProfileResponse;
import com.osatum.wbn.application.user.mapper.UserMapper;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final CurrentActorProvider currentActorProvider;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginSessionRepository loginSessionRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile() {
        return switch (currentActorProvider.currentActor()) {
            case UserActor userActor -> buildUserProfile(findUser(userActor.userId()), userActor);
            case InstitutionActor institutionActor -> UserProfileResponse.builder()
                    .institutionId(institutionActor.institutionId())
                    .institutionType(institutionActor.institutionType())
                    .institutionRole(institutionActor.institutionRole())
                    .name(institutionActor.name())
                    .build();
        };
    }

    @Transactional(readOnly = true)
    public UserBasicDataDto findOwnBasicData() {
        UserActor actor = requireResearchUnitUser();
        return userMapper.basicDataDto(findUser(actor.userId()));
    }

    @Transactional
    public UserProfileResponse updateOwnBasicData(UserBasicDataDto dto) {
        UserActor actor = requireResearchUnitUser();
        AppUser user = findUser(actor.userId());
        userMapper.updateBasicData(dto, user);
        return buildUserProfile(userRepository.save(user), actor);
    }

    @Transactional
    public void changeOwnPassword(String currentPassword, String password, String passwordRepeat) {
        UserActor actor = requireUser();
        if (actor.loginSessionId() == null) {
            throw new IllegalArgumentException("Current login session is required.");
        }

        AppUser user = findUser(actor.userId());
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!Objects.equals(password, passwordRepeat)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        revokeOtherUserSessions(actor.userId(), actor.loginSessionId());
    }

    private UserProfileResponse buildUserProfile(AppUser user, UserActor actor) {
        String institutionName = memberships(user).stream()
                .filter(membership -> Objects.equals(actor.institutionId(), membership.getInstitution().getId()))
                .map(InstitutionMembership::getInstitution)
                .map(institution -> institution.getName())
                .findFirst()
                .orElse(null);

        return UserProfileResponse.builder()
                .userId(user.getId())
                .institutionId(actor.institutionId())
                .institutionType(actor.institutionType())
                .institutionRole(actor.institutionRole())
                .institutionAccess(findAccess(actor.institutionId(), user))
                .institutionName(institutionName)
                .name(buildName(user))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .orcid(user.getOrcid())
                .key(buildKey(user))
                .email(user.getEmail())
                .build();
    }

    private List<ProfileAccess> findAccess(Long currentInstitutionId, AppUser user) {
        return memberships(user).stream()
                .filter(membership -> !Objects.equals(currentInstitutionId, membership.getInstitution().getId()))
                .filter(membership -> EntityState.ACTIVE.equals(membership.getState()))
                .filter(membership -> EntityState.ACTIVE.equals(membership.getInstitution().getState()))
                .map(membership -> ProfileAccess.builder()
                        .membershipId(membership.getId())
                        .institutionId(membership.getInstitution().getId())
                        .institutionType(membership.getInstitution().getInstitutionType())
                        .institutionRole(membership.getRole())
                        .name(membership.getInstitution().getName())
                        .build())
                .toList();
    }

    private static List<InstitutionMembership> memberships(AppUser user) {
        return user.getMemberships() == null ? List.of() : List.copyOf(user.getMemberships());
    }

    private UserActor requireUser() {
        if (currentActorProvider.currentActor() instanceof UserActor userActor) {
            return userActor;
        }
        throw new AccessDeniedException("User account is required.");
    }

    private UserActor requireResearchUnitUser() {
        UserActor actor = requireUser();
        if (!InstitutionType.RESEARCH_UNIT.equals(actor.institutionType())) {
            throw new AccessDeniedException("Operation requires a research unit user account.");
        }
        return actor;
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void revokeOtherUserSessions(Long userId, Long currentLoginSessionId) {
        refreshTokenRepository.findByUserIdAndRevokedFalse(userId).stream()
                .filter(token -> !belongsToLoginSession(token, currentLoginSessionId))
                .forEach(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });

        Instant now = Instant.now();
        loginSessionRepository.findActiveByUserId(userId, now).stream()
                .filter(session -> !Objects.equals(session.getId(), currentLoginSessionId))
                .forEach(session -> revokeLoginSession(session, now));
    }

    private boolean belongsToLoginSession(RefreshToken token, Long loginSessionId) {
        LoginSession session = token.getLoginSession();
        return session != null && Objects.equals(session.getId(), loginSessionId);
    }

    private void revokeLoginSession(LoginSession session, Instant now) {
        session.setStatus(LoginSessionStatus.REVOKED);
        session.setEndReason(LoginSessionEndReason.REVOKED);
        session.setEndedAt(now);
        loginSessionRepository.save(session);
    }

    private static String buildName(AppUser user) {
        return String.join(" ", nonNull(user.getFirstName()), nonNull(user.getLastName())).trim();
    }

    private static String buildKey(AppUser user) {
        return initial(user.getFirstName()) + " " + initial(user.getLastName());
    }

    private static String initial(String value) {
        return value == null || value.isBlank() ? "" : value.substring(0, 1).toUpperCase();
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
