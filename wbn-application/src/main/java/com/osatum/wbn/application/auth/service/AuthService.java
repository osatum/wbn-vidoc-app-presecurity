package com.osatum.wbn.application.auth.service;

import com.osatum.wbn.application.auth.dto.*;
import com.osatum.wbn.application.auth.port.AccessTokenService;
import com.osatum.wbn.application.auth.port.PreAuthTokenService;
import com.osatum.wbn.application.institution.mapper.InstitutionAccessDtoMapper;
import com.osatum.wbn.application.institution.mapper.InstitutionMembershipAccessDtoMapper;
import com.osatum.wbn.application.institution.dto.InstitutionAccessDto;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.model.institution.*;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final PreAuthTokenService preAuthTokenService;
    private final CurrentActorProvider currentActorProvider;
    private final InstitutionRepository institutionRepository;
    private final InstitutionMembershipRepository membershipRepository;
    private final InstitutionIpMatcherService ipMatcherService;
    private final InstitutionAccessDtoMapper institutionAccessDtoMapper;
    private final InstitutionMembershipAccessDtoMapper institutionMembershipAccessDtoMapper;
    private final LoginSessionService loginSessionService;

    @Value("${app.security.jwt.refresh-ttl-days}")
    private long refreshTtlDays;

    /**
     * Autoryzuje instytucję na podstawie adresu IP i wystawia tokeny dla sesji instytucjonalnej.
     */
    @Transactional
    public AuthResult autorizeIp(@Valid AutorizeIpRequest req, ClientRequestMetadata requestMetadata) {
        if (req == null || req.getId() == null) {
            throw new IllegalArgumentException("Invalid request");
        }
        Long institutionId = req.getId();
        String clientIp = requestMetadata.ipAddress();

        boolean ok = ipMatcherService.matchesInstitution(institutionId, clientIp);
        if (!ok) {
            throw new IllegalArgumentException("IP not allowed");
        }

        Optional<Institution> oInstitution = institutionRepository.findById(institutionId);
        if (oInstitution.isEmpty()) {
            throw new IllegalArgumentException("Invalid institution id = " + institutionId);
        }

        Institution institution = oInstitution.get();
        if (!EntityState.ACTIVE.equals(institution.getState())) {
            throw new IllegalArgumentException("Institution not active");
        }
        if (AuthMode.AUTH_REQUIRED == institution.getAuthMode()) {
            throw new IllegalArgumentException("Institution requires authentication");
        }

        InstitutionRole role = InstitutionRole.INSTITUTION_VIEWER;
        var expiresAt = refreshExpiresAt();
        var session = loginSessionService.startInstitutionSession(institution, role, requestMetadata, expiresAt);
        var refreshToken = issueInstitutionRefreshToken(institution, session, expiresAt);
        var response = AuthResponse.builder()
                .status(AuthStatus.OK)
                .institutionType(institution.getInstitutionType())
                .institutionRole(role)
                .accessToken(accessTokenService.createInstitutionAccessToken(institution, institution.getInstitutionType(), role, session.getId()))
                .build();
        return AuthResult.withRefreshToken(response, refreshToken);

    }

    /**
     * Weryfikuje dane logowania użytkownika i zwraca wynik logowania albo wymóg wyboru instytucji lub roli.
     */
    @Transactional
    public AuthResult login(LoginRequest req, ClientRequestMetadata requestMetadata) {
        var user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        validateLoginUserCanAuthenticate(user);
        Long institutionId = resolveInstitutionId(req);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        log.debug("Authenticating user in institution context: {}", institutionId);
        return getAuthResult(requestMetadata, user, institutionId, null);
    }

    /**
     * Kończy preautoryzowane logowanie użytkownika po wyborze instytucji oraz powiązania.
     */
    @Transactional
    public AuthResult selectInstitution(
            @Valid SelectInstitutionAndRoleRequest req,
            String authCookie,
            ClientRequestMetadata requestMetadata
    ) {
        long userId = resolveSelectingUserId(authCookie);
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        validateUserCanAuthenticate(user);
        long institutionId = req.institutionId();
        long membershipId = req.membershipId();

        return getAuthResult(requestMetadata, user, institutionId, membershipId);
    }

    private long resolveSelectingUserId(String authCookie) {
        return currentActorProvider.findCurrentActor()
                .filter(UserActor.class::isInstance)
                .map(UserActor.class::cast)
                .map(UserActor::userId)
                .orElseGet(() -> preAuthTokenService.requireUserIdFromPreAuthToken(authCookie));
    }

    private @NonNull AuthResult getAuthResult(
            ClientRequestMetadata requestMetadata,
            AppUser user,
            Long institutionId,
            Long membershipId
    ) {


        validateUserCanAuthenticate(user);

        if (institutionId != null) {
            Long finalInstitutionId = institutionId;
            List<InstitutionMembership> memberships = user.getMemberships().stream()
                    .filter(x -> membershipId != null ? membershipId.equals(x.getId()) : true)
                    .filter(x -> finalInstitutionId.equals(x.getInstitution().getId()))
                    .filter(x -> EntityState.ACTIVE.equals(x.getState()))
                    .filter(x -> canRoleAuthenticate(x.getRole()))
                    .filter(x -> EntityState.ACTIVE.equals(x.getInstitution().getState()))
                    .filter(x -> isMembershipAllowedFromIp(x, requestMetadata.ipAddress()))
                    .toList();
            if (memberships.isEmpty()) {
                throw new IllegalArgumentException("User not assigned to institution");
            }
            if (memberships.size() == 1) {
                var membership = memberships.getFirst();
                var expiresAt = refreshExpiresAt();
                var session = loginSessionService.startUserSession(user, membership.getInstitution(), membership.getRole(), requestMetadata, expiresAt);
                var refreshToken = issueUserRefreshToken(user.getId(), membership, session, expiresAt);
                var response = AuthResponse.builder()
                        .status(AuthStatus.OK)
                        .institutionType(membership.getInstitution().getInstitutionType())
                        .institutionRole(membership.getRole())
                        .accessToken(accessTokenService.createUserAccessToken(user, institutionId, membership.getInstitution().getInstitutionType(), membership.getRole(), session.getId()))
                        .build();
                return AuthResult.withRefreshToken(response, refreshToken);
            }

            Optional<Institution> oInstitution = institutionRepository.findById(finalInstitutionId);
            if (oInstitution.isEmpty()) {
                throw new IllegalArgumentException("Invalid institution id = " + finalInstitutionId);
            }

            InstitutionAccessDto institutionDto = institutionAccessDtoMapper.toDto(oInstitution.get());
            institutionDto.setMemberships(memberships.stream().map(institutionMembershipAccessDtoMapper::toDto).toList());

            String preAuthJwt = preAuthTokenService.createPreAuthToken(user.getId(), finalInstitutionId);
            var response = AuthResponse.builder()
                    .status(AuthStatus.MEMBERSHIP_REQUIRED)
                    .institutions(List.of(institutionDto))
                    .build();
            return AuthResult.withPreAuthToken(response, preAuthJwt);
        }

        List<InstitutionMembership> memberships = user.getMemberships().stream()
                .filter(x -> EntityState.ACTIVE.equals(x.getState()))
                .filter(x -> canRoleAuthenticate(x.getRole()))
                .filter(x -> EntityState.ACTIVE.equals(x.getInstitution().getState()))
                .filter(x -> isMembershipAllowedFromIp(x, requestMetadata.ipAddress()))
                .toList();

        if (memberships.isEmpty()) {
            throw new IllegalArgumentException("User not assigned to institution");
        }
        if (memberships.size() == 1) {
            InstitutionMembership membership = memberships.getFirst();
            var expiresAt = refreshExpiresAt();
            var session = loginSessionService.startUserSession(user, membership.getInstitution(), membership.getRole(), requestMetadata, expiresAt);
            var refreshToken = issueUserRefreshToken(user.getId(), membership, session, expiresAt);
            var response = AuthResponse.builder()
                    .status(AuthStatus.OK)
                    .institutionType(membership.getInstitution().getInstitutionType())
                    .institutionRole(membership.getRole())
                    .accessToken(accessTokenService.createUserAccessToken(user, membership.getInstitution().getId(), membership.getInstitution().getInstitutionType(), membership.getRole(), session.getId()))
                    .build();
            return AuthResult.withRefreshToken(response, refreshToken);

        }
        List<Long> institutionIds = memberships.stream()
                .map(x -> x.getInstitution().getId()).distinct()
                .toList();
        if (institutionIds.isEmpty()) {
            throw new IllegalArgumentException("User not assigned to institution");
        }
        if (institutionIds.size() > 1) {
            String preAuthJwt = preAuthTokenService.createPreAuthToken(user.getId(), null);
            List<InstitutionAccessDto> institutions = institutionIds.stream()
                    .map(x -> {


                        Optional<Institution> oInstitution = institutionRepository.findById(x);
                        if (oInstitution.isEmpty()) {
                            throw new IllegalArgumentException("Invalid institution id = " + x);
                        }

                        InstitutionAccessDto institutionDto = institutionAccessDtoMapper.toDto(oInstitution.get());
                        institutionDto.setMemberships(
                                memberships.stream()
                                        .filter(y -> x.equals(y.getInstitution().getId()))
                                        .map(institutionMembershipAccessDtoMapper::toDto)
                                        .toList());

                        return institutionDto;
                    })
                    .filter(x -> x != null)
                    .sorted(Comparator.comparing(InstitutionAccessDto::getName))
                    .toList();
            var response = AuthResponse.builder()
                    .status(AuthStatus.INSTITUTION_REQUIRED)
                    .institutions(institutions)
                    .build();
            return AuthResult.withPreAuthToken(response, preAuthJwt);
        }

        Long finalInstitutionId = institutionIds.getFirst();

        List<InstitutionMembership> finalMemberships = memberships.stream()
                .filter(x -> x.getInstitution().getId().equals(memberships.getFirst().getInstitution().getId())).toList();
        if (finalMemberships.isEmpty()) {
            throw new IllegalArgumentException("User not assigned to institution");
        }
        // Uzytkownik zalogowany (jedna instytuycja, jedna rola)
        if (finalMemberships.size() == 1) {
            var membership = finalMemberships.getFirst();
            var expiresAt = refreshExpiresAt();
            var session = loginSessionService.startUserSession(user, membership.getInstitution(), membership.getRole(), requestMetadata, expiresAt);
            var refreshToken = issueUserRefreshToken(user.getId(), membership, session, expiresAt);

            var response = AuthResponse.builder()
                    .status(AuthStatus.OK)
                    .institutionType(membership.getInstitution().getInstitutionType())
                    .institutionRole(membership.getRole())
                    .accessToken(accessTokenService.createUserAccessToken(user, membership.getInstitution().getId(), membership.getInstitution().getInstitutionType(), membership.getRole(), session.getId()))
                    .build();
            return AuthResult.withRefreshToken(response, refreshToken);
        }

        // Uzytkownik przekierowany na wybor memebership (rola) (jedna instytuycja, wiele rol)
        Optional<Institution> oInstitution = institutionRepository.findById(finalInstitutionId);
        if (oInstitution.isEmpty()) {
            throw new IllegalArgumentException("Invalid institution id = " + finalInstitutionId);
        }

        InstitutionAccessDto institutionDto = institutionAccessDtoMapper.toDto(oInstitution.get());
        institutionDto.setMemberships(memberships.stream().map(institutionMembershipAccessDtoMapper::toDto).toList());

        String preAuthJwt = preAuthTokenService.createPreAuthToken(user.getId(), finalInstitutionId);
        var response = AuthResponse.builder()
                .status(AuthStatus.MEMBERSHIP_REQUIRED)
                .institutions(List.of(institutionDto))
                .build();
        return AuthResult.withPreAuthToken(response, preAuthJwt);
    }

    /**
     * Weryfikuje refresh token, rotuje go i wystawia nowy access token dla aktywnej sesji.
     */
    @Transactional
    public AuthResult refresh(String refreshCookie, ClientRequestMetadata requestMetadata) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new IllegalArgumentException("No refresh token");
        }

        String hash = sha256Base64(refreshCookie);



        var rt = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) {
            if (rt.getExpiresAt().isBefore(Instant.now())) {
                loginSessionService.finish(rt.getLoginSession(), LoginSessionEndReason.EXPIRED);
            }
            throw new IllegalArgumentException("Invalid refresh token");
        }

        Institution institution = rt.getInstitution();


        if (institution == null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        String clientIp = requestMetadata.ipAddress();

        if (rt.getUser() != null) {
            validateUserCanAuthenticate(rt.getUser());

            InstitutionMembership institutionMembership = rt.getInstitutionMembership();

            if (institutionMembership == null) {
                throw new IllegalArgumentException("Invalid refresh token");
            }

            if (!rt.getInstitutionMembership().getInstitution().equals(rt.getInstitution())) {
                throw new IllegalArgumentException("Invalid refresh token");
            }
            validateInstitutionCanAuthenticate(institution);
            validateMembershipCanAuthenticate(institutionMembership);



            // ponownie weryfikujemy ograniczenie IP (żeby nie utrzymywać sesji spoza instytucji)
            var membership = membershipRepository.findByUserIdAndInstitutionIdAndRole(rt.getUser().getId(), institution.getId(), institutionMembership.getRole())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

            validateMembershipCanAuthenticate(membership);


            if (!membership.isLoginAllowedFromAnyIp()) {
                boolean ok = ipMatcherService.matchesInstitution(institution.getId(), clientIp);
                if (!ok) {
                    revokeIpRestrictedSession(rt);
                    throw new IllegalArgumentException("Refresh not allowed from this IP");
                }
            }

            // rotacja
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);

            var user = rt.getUser();
            var expiresAt = refreshExpiresAt();
            loginSessionService.touch(rt.getLoginSession(), expiresAt);
            var refreshToken = issueUserRefreshToken(user.getId(), membership, rt.getLoginSession(), expiresAt);

            var response = AuthResponse.builder()
                    .accessToken(accessTokenService.createUserAccessToken(user, institution.getId(), institution.getInstitutionType(), membership.getRole(), rt.getLoginSession().getId()))
                    .status(AuthStatus.OK)
                    .build();
            return AuthResult.withRefreshToken(response, refreshToken);
        } else {
            validateInstitutionCanAuthenticate(institution);

            boolean ok = ipMatcherService.matchesInstitution(institution.getId(), clientIp);
            if (!ok) {
                revokeIpRestrictedSession(rt);
                throw new IllegalArgumentException("Refresh not allowed from this IP");
            }
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);

            var expiresAt = refreshExpiresAt();
            loginSessionService.touch(rt.getLoginSession(), expiresAt);
            var refreshToken = issueInstitutionRefreshToken(institution, rt.getLoginSession(), expiresAt);

            var response = AuthResponse.builder()
                    .accessToken(accessTokenService.createInstitutionAccessToken(institution, institution.getInstitutionType(), InstitutionRole.INSTITUTION_VIEWER, rt.getLoginSession().getId()))
                    .status(AuthStatus.OK)
                    .build();
            return AuthResult.withRefreshToken(response, refreshToken);
        }

    }

    /**
     * Unieważnia refresh token powiązany z cookie, kończy sesję i czyści cookie uwierzytelniające.
     */
    @Transactional
    public void logout(String refreshCookie) {
        if (refreshCookie != null && !refreshCookie.isBlank()) {
            String hash = sha256Base64(refreshCookie);
            refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
                rt.setRevoked(true);
                loginSessionService.finish(rt.getLoginSession(), LoginSessionEndReason.LOGOUT);
                refreshTokenRepository.save(rt);
            });
        }
    }

    private String issueInstitutionRefreshToken(Institution institution, LoginSession session, Instant expiresAt) {


        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        String hash = sha256Base64(raw);

        var token = RefreshToken.builder()
                .institution(institution)
                .loginSession(session)
                .tokenHash(hash)
                .expiresAt(expiresAt)
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(token);
        return raw;
    }

    private String issueUserRefreshToken(Long userId, InstitutionMembership membership, LoginSession session, Instant expiresAt) {
        var user = userRepository.findById(userId).orElseThrow();

        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        String hash = sha256Base64(raw);

        var token = RefreshToken.builder()
                .user(user)
                .institution(membership.getInstitution())
                .institutionMembership(membership)
                .loginSession(session)
                .tokenHash(hash)
                .expiresAt(expiresAt)
                .revoked(false)
                .createdAt(Instant.now())
                .build();
        refreshTokenRepository.save(token);
        return raw;
    }

    private static Long resolveInstitutionId(LoginRequest req) {
        if (req.institutionId() != null) {
            return req.institutionId();
        }
        return null;
    }

    private static void validateUserCanAuthenticate(AppUser user) {
        if (!EntityState.ACTIVE.equals(user.getState())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
    }

    private static void validateLoginUserCanAuthenticate(AppUser user) {
        if (!EntityState.ACTIVE.equals(user.getState())) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    private static void validateInstitutionCanAuthenticate(Institution institution) {
        if (!EntityState.ACTIVE.equals(institution.getState())) {
            throw new IllegalArgumentException("Institution not active");
        }
    }

    private static void validateMembershipCanAuthenticate(InstitutionMembership membership) {
        if (!EntityState.ACTIVE.equals(membership.getState())) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        if (!canRoleAuthenticate(membership.getRole())) {
            throw new IllegalArgumentException("User pending activation");
        }
        validateInstitutionCanAuthenticate(membership.getInstitution());
    }

    private static boolean canRoleAuthenticate(InstitutionRole role) {
        return role != InstitutionRole.INSTITUTION_PENDING
                && role != InstitutionRole.INSTITUTION_TO_CONFIRM;
    }

    private boolean isMembershipAllowedFromIp(InstitutionMembership membership, String clientIp) {
        return membership.isLoginAllowedFromAnyIp()
                || ipMatcherService.matchesInstitution(membership.getInstitution().getId(), clientIp);
    }

    private void revokeIpRestrictedSession(RefreshToken refreshToken) {
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        loginSessionService.finish(refreshToken.getLoginSession(), LoginSessionEndReason.REVOKED);
    }

    private Instant refreshExpiresAt() {
        return Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);
    }

        // spójnie z issueRefreshCookie (path/samesite/secure)

    private static String sha256Base64(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
