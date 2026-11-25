package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.port.UserNotificationPort;
import com.osatum.wbn.application.user.dto.PasswordResetConfirmDto;
import com.osatum.wbn.application.user.dto.PasswordResetRequestAcceptedDto;
import com.osatum.wbn.application.user.dto.PasswordResetRequestDto;
import com.osatum.wbn.application.user.dto.PasswordResetTokenStatus;
import com.osatum.wbn.application.user.dto.PasswordResetTokenStatusDto;
import com.osatum.wbn.application.user.mapper.PasswordResetRequestMapper;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.auth.RefreshToken;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequestStatus;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import com.osatum.wbn.domain.repository.auth.RefreshTokenRepository;
import com.osatum.wbn.domain.repository.passwordreset.PasswordResetRequestRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final List<PasswordResetRequestStatus> ACTIVE_REQUEST_STATUSES = List.of(
            PasswordResetRequestStatus.EMAIL_QUEUED,
            PasswordResetRequestStatus.LINK_CLICKED
    );

    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserNotificationPort emailNotificationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final PasswordResetRequestMapper mapper;
    private final Clock clock;

    @Value("${app.password-reset.token-validity-minutes:60}")
    private long tokenValidityMinutes;

    @Value("${app.password-reset.rate-limit-window-minutes:15}")
    private long rateLimitWindowMinutes;

    @Value("${app.password-reset.rate-limit-max-requests:3}")
    private long rateLimitMaxRequests;

    /**
     * Przyjmuje zgłoszenie resetu hasła, stosuje limit żądań i kolejkuje wiadomość z tokenem, jeśli konto może zostać obsłużone.
     */
    @Transactional
    public PasswordResetRequestAcceptedDto requestReset(
            PasswordResetRequestDto dto,
            String clientIp,
            String userAgent
    ) {
        String email = normalizeEmail(dto.getEmail());
        Instant now = clock.instant();

        PasswordResetRequest request = baseRequest(email, clientIp, userAgent, now);

        if (isRateLimited(email, clientIp, now)) {
            request.setStatus(PasswordResetRequestStatus.RATE_LIMITED);
            passwordResetRequestRepository.save(request);
            return accepted();
        }

        Optional<AppUser> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            request.setStatus(PasswordResetRequestStatus.USER_NOT_FOUND);
            passwordResetRequestRepository.save(request);
            return accepted();
        }

        AppUser user = userOptional.get();
        request.setUser(user);
        if (!EntityState.ACTIVE.equals(user.getState())) {
            request.setStatus(PasswordResetRequestStatus.USER_NOT_ACTIVE);
            passwordResetRequestRepository.save(request);
            return accepted();
        }

        invalidateActiveRequests(user, now);

        String rawToken = generateRawToken();
        request.setTokenHash(tokenHash(rawToken));
        request.setExpiresAt(now.plus(tokenValidityMinutes, ChronoUnit.MINUTES));
        request.setStatus(PasswordResetRequestStatus.EMAIL_QUEUED);
        PasswordResetRequest saved = passwordResetRequestRepository.save(request);

        try {
            EmailMessageLog emailLog = emailNotificationService.queuePasswordResetMessage(user, saved, rawToken);
            saved.setEmailMessageLogId(emailLog.getId());
            passwordResetRequestRepository.save(saved);
        } catch (Exception e) {
            saved.setStatus(PasswordResetRequestStatus.FAILED);
            saved.setTokenHash(null);
            saved.setExpiresAt(null);
            saved.setErrorMessage(truncate(e.getMessage(), 4000));
            passwordResetRequestRepository.save(saved);
        }

        return accepted();
    }

    /**
     * Weryfikuje status tokenu resetu hasła i zapisuje metadane kliknięcia linku.
     */
    @Transactional
    public PasswordResetTokenStatusDto verifyToken(String rawToken, String clientIp, String userAgent) {
        return new PasswordResetTokenStatusDto(resolveToken(rawToken, clientIp, userAgent, false).status());
    }

    /**
     * Potwierdza reset hasła dla ważnego tokenu, zapisuje nowe hasło i unieważnia aktywne sesje użytkownika.
     */
    @Transactional
    public PasswordResetTokenStatusDto confirmReset(
            String rawToken,
            PasswordResetConfirmDto dto,
            String clientIp,
            String userAgent
    ) {
        if (!Objects.equals(dto.getPassword(), dto.getPasswordRepeat())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }

        TokenResolution resolution = resolveToken(rawToken, clientIp, userAgent, true);
        if (!PasswordResetTokenStatus.VALID.equals(resolution.status())) {
            return new PasswordResetTokenStatusDto(resolution.status());
        }

        PasswordResetRequest request = resolution.request();
        AppUser user = request.getUser();
        Instant now = clock.instant();

        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        userRepository.save(user);

        request.setStatus(PasswordResetRequestStatus.COMPLETED);
        request.setClickedAt(request.getClickedAt() == null ? now : request.getClickedAt());
        request.setCompletedAt(now);
        passwordResetRequestRepository.save(request);

        revokeUserSessions(user.getId(), now);

        return new PasswordResetTokenStatusDto(PasswordResetTokenStatus.COMPLETED);
    }

    private TokenResolution resolveToken(String rawToken, String clientIp, String userAgent, boolean markClicked) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenResolution.invalid();
        }

        Optional<PasswordResetRequest> requestOptional = passwordResetRequestRepository.findByTokenHash(tokenHash(rawToken));
        if (requestOptional.isEmpty()) {
            return TokenResolution.invalid();
        }

        PasswordResetRequest request = requestOptional.get();
        boolean clickRecorded = recordClick(request, clientIp, userAgent);
        PasswordResetTokenStatus status = validateToken(request);
        if (!PasswordResetTokenStatus.VALID.equals(status)) {
            if (clickRecorded) {
                passwordResetRequestRepository.save(request);
            }
            return new TokenResolution(status, request);
        }

        if (markClicked || clickRecorded || !PasswordResetRequestStatus.LINK_CLICKED.equals(request.getStatus())) {
            request.setStatus(PasswordResetRequestStatus.LINK_CLICKED);
            passwordResetRequestRepository.save(request);
        }

        return new TokenResolution(PasswordResetTokenStatus.VALID, request);
    }

    private boolean recordClick(PasswordResetRequest request, String clientIp, String userAgent) {
        boolean changed = false;
        if (request.getClickedAt() == null) {
            request.setClickedAt(clock.instant());
            changed = true;
        }
        if (request.getIpAddress() == null) {
            request.setIpAddress(truncate(clientIp, 64));
            changed = true;
        }
        if (request.getUserAgent() == null) {
            request.setUserAgent(truncate(userAgent, 1024));
            changed = true;
        }
        if (request.getBrowser() == null) {
            request.setBrowser(truncate(mapper.resolveBrowser(userAgent), 128));
            changed = true;
        }
        return changed;
    }

    private PasswordResetTokenStatus validateToken(PasswordResetRequest request) {
        if (PasswordResetRequestStatus.COMPLETED.equals(request.getStatus())) {
            return PasswordResetTokenStatus.USED;
        }
        if (request.getCompletedAt() != null) {
            return PasswordResetTokenStatus.USED;
        }
        if (PasswordResetRequestStatus.INVALIDATED.equals(request.getStatus())
                || PasswordResetRequestStatus.FAILED.equals(request.getStatus())
                || request.getTokenHash() == null) {
            return PasswordResetTokenStatus.INVALID;
        }
        Instant now = clock.instant();
        if (request.getExpiresAt() == null || request.getExpiresAt().isBefore(now)) {
            request.setStatus(PasswordResetRequestStatus.EXPIRED);
            passwordResetRequestRepository.save(request);
            return PasswordResetTokenStatus.EXPIRED;
        }
        AppUser user = request.getUser();
        if (user == null || !EntityState.ACTIVE.equals(user.getState())) {
            return PasswordResetTokenStatus.INVALID;
        }

        return PasswordResetTokenStatus.VALID;
    }

    private PasswordResetRequest baseRequest(String email, String clientIp, String userAgent, Instant now) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setEmail(email);
        request.setRequestedAt(now);
        request.setIpAddress(truncate(clientIp, 64));
        request.setUserAgent(truncate(userAgent, 1024));
        request.setBrowser(truncate(mapper.resolveBrowser(userAgent), 128));
        request.setState(EntityState.ACTIVE);
        request.setCreatedAt(OffsetDateTime.now(clock));
        return request;
    }

    private boolean isRateLimited(String email, String clientIp, Instant now) {
        if (rateLimitMaxRequests < 1 || rateLimitWindowMinutes < 1) {
            return false;
        }

        Instant from = now.minus(rateLimitWindowMinutes, ChronoUnit.MINUTES);
        return passwordResetRequestRepository.countRecentByEmailOrIpAddress(email, clientIp, from) >= rateLimitMaxRequests;
    }

    private void invalidateActiveRequests(AppUser user, Instant now) {
        passwordResetRequestRepository.findByUserIdAndStatusIn(user.getId(), ACTIVE_REQUEST_STATUSES)
                .forEach(request -> {
                    request.setStatus(PasswordResetRequestStatus.INVALIDATED);
                    request.setInvalidatedAt(now);
                    passwordResetRequestRepository.save(request);
                });
    }

    private void revokeUserSessions(Long userId, Instant now) {
        refreshTokenRepository.findByUserIdAndRevokedFalse(userId)
                .forEach(token -> revokeRefreshToken(token));

        loginSessionRepository.findActiveByUserId(userId, now)
                .forEach(session -> revokeLoginSession(session, now));
    }

    private void revokeRefreshToken(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    private void revokeLoginSession(LoginSession session, Instant now) {
        session.setStatus(LoginSessionStatus.REVOKED);
        session.setEndReason(LoginSessionEndReason.REVOKED);
        session.setEndedAt(now);
        loginSessionRepository.save(session);
    }

    private PasswordResetRequestAcceptedDto accepted() {
        return new PasswordResetRequestAcceptedDto(true);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String tokenHash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record TokenResolution(PasswordResetTokenStatus status, PasswordResetRequest request) {
        private static TokenResolution invalid() {
            return new TokenResolution(PasswordResetTokenStatus.INVALID, null);
        }
    }
}
