package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.port.UserNotificationPort;
import com.osatum.wbn.application.user.dto.UserDataDeletionRequestAcceptedDto;
import com.osatum.wbn.application.user.dto.UserDataDeletionRequestDto;
import com.osatum.wbn.application.user.dto.UserDataDeletionTokenStatus;
import com.osatum.wbn.application.user.dto.UserDataDeletionTokenStatusDto;
import com.osatum.wbn.application.user.mapper.UserDataDeletionRequestMapper;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequestStatus;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.user.UserDataDeletionRequestRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
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
public class UserDataDeletionService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String ANONYMIZED_EMAIL = "anonymous@deleted.invalid";
    private static final List<UserDataDeletionRequestStatus> ACTIVE_REQUEST_STATUSES = List.of(
            UserDataDeletionRequestStatus.EMAIL_QUEUED,
            UserDataDeletionRequestStatus.LINK_CLICKED
    );

    private final UserDataDeletionRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final InstitutionRepository institutionRepository;
    private final UserNotificationPort emailNotificationService;
    private final UserService userService;
    private final UserDataDeletionRequestMapper mapper;
    private final Clock clock;

    @Value("${app.user-data-deletion.token-validity-minutes:60}")
    private long tokenValidityMinutes;

    @Value("${app.user-data-deletion.rate-limit-window-minutes:15}")
    private long rateLimitWindowMinutes;

    @Value("${app.user-data-deletion.rate-limit-max-requests:3}")
    private long rateLimitMaxRequests;

    /**
     * Przyjmuje zgłoszenie usunięcia danych dla zalogowanego użytkownika i kolejkuje wiadomość z tokenem potwierdzającym.
     */
    @Transactional
    public UserDataDeletionRequestAcceptedDto requestDeletion(
            UserDataDeletionRequestDto dto,
            String clientIp,
            String userAgent
    ) {
        requireResearchUnitUser(dto);

        String email = normalizeEmail(dto.getEmail());
        Instant now = clock.instant();
        AppUser currentUser = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidlowe konto."));
        Institution institution = institutionRepository.findById(dto.getInstitutionId())
                .orElseThrow(() -> new IllegalArgumentException("Nieprawidlowa instytucja."));

        UserDataDeletionRequest request = baseRequest(email, currentUser, institution, clientIp, userAgent, now);

        Optional<AppUser> requestedUser = userRepository.findByEmail(email);
        if (requestedUser.isEmpty() || !Objects.equals(requestedUser.get().getId(), currentUser.getId())) {
            request.setStatus(UserDataDeletionRequestStatus.REJECTED);
            requestRepository.save(request);
            throw new IllegalArgumentException("Nieprawidlowe konto.");
        }

        if (!normalizeEmail(currentUser.getEmail()).equals(email)) {
            request.setStatus(UserDataDeletionRequestStatus.REJECTED);
            requestRepository.save(request);
            throw new IllegalArgumentException("Nieprawidlowe konto.");
        }

        if (isRateLimited(currentUser.getId(), clientIp, now)) {
            request.setStatus(UserDataDeletionRequestStatus.REJECTED);
            request.setErrorMessage("Rate limit exceeded.");
            requestRepository.save(request);
            throw new IllegalArgumentException("Zbyt wiele zgloszen. Sprobuj ponownie pozniej.");
        }

        invalidateActiveRequests(currentUser.getId(), null, now);

        String rawToken = generateRawToken();
        request.setTokenHash(tokenHash(rawToken));
        request.setExpiresAt(now.plus(tokenValidityMinutes, ChronoUnit.MINUTES));
        request.setStatus(UserDataDeletionRequestStatus.EMAIL_QUEUED);
        UserDataDeletionRequest saved = requestRepository.save(request);

        try {
            EmailMessageLog emailLog = emailNotificationService.queueUserDataDeletionConfirmationMessage(
                    currentUser,
                    institution,
                    saved,
                    rawToken
            );
            saved.setEmailMessageLogId(emailLog.getId());
            requestRepository.save(saved);
        } catch (Exception e) {
            saved.setStatus(UserDataDeletionRequestStatus.FAILED);
            saved.setTokenHash(null);
            saved.setExpiresAt(null);
            saved.setErrorMessage(truncate(e.getMessage(), 4000));
            requestRepository.save(saved);
        }

        return new UserDataDeletionRequestAcceptedDto(true);
    }

    /**
     * Weryfikuje token usunięcia danych użytkownika i zapisuje metadane kliknięcia linku.
     */
    @Transactional
    public UserDataDeletionTokenStatusDto verifyToken(String rawToken, String clientIp, String userAgent) {
        return new UserDataDeletionTokenStatusDto(resolveToken(rawToken, clientIp, userAgent, true).status());
    }

    /**
     * Potwierdza usunięcie danych dla ważnego tokenu, anonimizuje zgłoszenie i usuwa konto zgodnie z RODO.
     */
    @Transactional
    public UserDataDeletionTokenStatusDto confirmDeletion(String rawToken, String clientIp, String userAgent) {
        TokenResolution resolution = resolveToken(rawToken, clientIp, userAgent, true);
        if (!UserDataDeletionTokenStatus.VALID.equals(resolution.status())) {
            return new UserDataDeletionTokenStatusDto(resolution.status());
        }

        UserDataDeletionRequest request = resolution.request();
        AppUser user = request.getUser();
        String email = user.getEmail();
        Long userId = user.getId();
        Instant now = clock.instant();

        invalidateActiveRequests(userId, request.getId(), now);

        request.setStatus(UserDataDeletionRequestStatus.COMPLETED);
        request.setClickedAt(request.getClickedAt() == null ? now : request.getClickedAt());
        request.setCompletedAt(now);
        anonymizeRequest(request);
        requestRepository.save(request);
        requestRepository.anonymizeRequestsForUser(userId, email);

        userService.deleteForGdprAfterVerification(userId);

        return new UserDataDeletionTokenStatusDto(UserDataDeletionTokenStatus.COMPLETED);
    }

    private TokenResolution resolveToken(String rawToken, String clientIp, String userAgent, boolean markClicked) {
        if (rawToken == null || rawToken.isBlank()) {
            return TokenResolution.invalid();
        }

        Optional<UserDataDeletionRequest> requestOptional = requestRepository.findByTokenHash(tokenHash(rawToken));
        if (requestOptional.isEmpty()) {
            return TokenResolution.invalid();
        }

        UserDataDeletionRequest request = requestOptional.get();
        boolean clickRecorded = recordClick(request, clientIp, userAgent);
        UserDataDeletionTokenStatus status = validateToken(request);
        if (!UserDataDeletionTokenStatus.VALID.equals(status)) {
            if (clickRecorded) {
                requestRepository.save(request);
            }
            return new TokenResolution(status, request);
        }

        if (markClicked || clickRecorded || !UserDataDeletionRequestStatus.LINK_CLICKED.equals(request.getStatus())) {
            request.setStatus(UserDataDeletionRequestStatus.LINK_CLICKED);
            requestRepository.save(request);
        }

        return new TokenResolution(UserDataDeletionTokenStatus.VALID, request);
    }

    private UserDataDeletionTokenStatus validateToken(UserDataDeletionRequest request) {
        if (UserDataDeletionRequestStatus.COMPLETED.equals(request.getStatus()) || request.getCompletedAt() != null) {
            return UserDataDeletionTokenStatus.USED;
        }
        if (UserDataDeletionRequestStatus.INVALIDATED.equals(request.getStatus())
                || UserDataDeletionRequestStatus.FAILED.equals(request.getStatus())
                || UserDataDeletionRequestStatus.REJECTED.equals(request.getStatus())
                || request.getTokenHash() == null) {
            return UserDataDeletionTokenStatus.INVALID;
        }
        Instant now = clock.instant();
        if (request.getExpiresAt() == null || request.getExpiresAt().isBefore(now)) {
            request.setStatus(UserDataDeletionRequestStatus.EXPIRED);
            requestRepository.save(request);
            return UserDataDeletionTokenStatus.EXPIRED;
        }

        AppUser user = request.getUser();
        if (user == null || !EntityState.ACTIVE.equals(user.getState())) {
            return UserDataDeletionTokenStatus.INVALID;
        }

        return UserDataDeletionTokenStatus.VALID;
    }

    private boolean recordClick(UserDataDeletionRequest request, String clientIp, String userAgent) {
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

    private UserDataDeletionRequest baseRequest(
            String email,
            AppUser user,
            Institution institution,
            String clientIp,
            String userAgent,
            Instant now
    ) {
        UserDataDeletionRequest request = new UserDataDeletionRequest();
        request.setEmail(email);
        request.setUser(user);
        request.setInstitution(institution);
        request.setRequestedAt(now);
        request.setIpAddress(truncate(clientIp, 64));
        request.setUserAgent(truncate(userAgent, 1024));
        request.setBrowser(truncate(mapper.resolveBrowser(userAgent), 128));
        request.setState(EntityState.ACTIVE);
        request.setCreatedAt(OffsetDateTime.now(clock));
        return request;
    }

    private void requireResearchUnitUser(UserDataDeletionRequestDto dto) {
        if (dto == null || dto.getUserId() == null || dto.getInstitutionId() == null) {
            throw new AccessDeniedException("User account is required.");
        }
        if (!InstitutionType.RESEARCH_UNIT.equals(dto.getInstitutionType())) {
            throw new AccessDeniedException("Operation is available only for research unit users.");
        }
    }

    private boolean isRateLimited(Long userId, String clientIp, Instant now) {
        if (rateLimitMaxRequests < 1 || rateLimitWindowMinutes < 1) {
            return false;
        }

        Instant from = now.minus(rateLimitWindowMinutes, ChronoUnit.MINUTES);
        return requestRepository.countRecentByUserIdOrIpAddress(userId, clientIp, from) >= rateLimitMaxRequests;
    }

    private void invalidateActiveRequests(Long userId, Long excludedRequestId, Instant now) {
        requestRepository.findByUserIdAndStatusIn(userId, ACTIVE_REQUEST_STATUSES)
                .stream()
                .filter(request -> excludedRequestId == null || !Objects.equals(request.getId(), excludedRequestId))
                .forEach(request -> {
                    request.setStatus(UserDataDeletionRequestStatus.INVALIDATED);
                    request.setInvalidatedAt(now);
                    request.setTokenHash(null);
                    requestRepository.save(request);
                });
    }

    private void anonymizeRequest(UserDataDeletionRequest request) {
        request.setUser(null);
        request.setEmail(ANONYMIZED_EMAIL);
        request.setTokenHash(null);
        request.setIpAddress(null);
        request.setUserAgent(null);
        request.setBrowser(null);
        request.setErrorMessage(null);
        request.setCreatedBy("anonymous");
        request.setEditedBy("anonymous");
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private record TokenResolution(UserDataDeletionTokenStatus status, UserDataDeletionRequest request) {
        private static TokenResolution invalid() {
            return new TokenResolution(UserDataDeletionTokenStatus.INVALID, null);
        }
    }
}
