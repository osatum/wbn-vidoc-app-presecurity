package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.shared.exception.ActivationTokenAlreadyUsedException;
import com.osatum.wbn.application.user.port.UserNotificationPort;
import com.osatum.wbn.application.user.dto.RegistrationActivationResultDto;
import com.osatum.wbn.application.user.dto.RegistrationActivationStatus;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import com.osatum.wbn.domain.model.registration.AccountActivationStatus;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.registration.ActivationTokenStatus;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.registration.AccountActivationLogRepository;
import com.osatum.wbn.domain.repository.registration.ActivationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

@Service
@RequiredArgsConstructor
public class AccountActivationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ActivationTokenRepository activationTokenRepository;
    private final AccountActivationLogRepository activationLogRepository;
    private final UserNotificationPort emailNotificationService;
    private final Clock clock;

    @Value("${app.registration.activation-token-validity-days:7}")
    private long activationTokenValidityDays;

    /**
     * Generuje nowy token aktywacyjny dla powiązania i kolejkuje wiadomość e-mail z linkiem aktywacyjnym.
     */
    @Transactional
    public ActivationToken issueActivationToken(
            AppUser user,
            Institution institution,
            InstitutionMembership membership,
            EmailType emailType
    ) {
        invalidateActiveTokens(membership);
        ActivationTokenDraft draft = createActivationToken(user, institution, membership);
        ActivationToken saved = activationTokenRepository.save(draft.token());
        emailNotificationService.queueActivationMessage(emailType, user, institution, membership, saved, draft.rawToken());
        return saved;
    }

    /**
     * Zapisuje log zgłoszenia rejestracji powiązany z tokenem aktywacyjnym i zgodami użytkownika.
     */
    @Transactional
    public void logRegistrationSubmit(
            ActivationToken token,
            boolean termsAndPrivacyPolicyAccepted,
            boolean personalDataProcessingAcknowledged,
            String clientIp,
            String userAgent
    ) {
        logAttempt(
                token,
                AccountActivationStatus.REGISTRATION_SUBMITTED,
                clientIp,
                userAgent,
                null,
                termsAndPrivacyPolicyAccepted,
                personalDataProcessingAcknowledged
        );
    }

    /**
     * Unieważnia poprzednie aktywne tokeny i generuje nowy link aktywacyjny dla istniejącego powiązania.
     */
    @Transactional
    public EmailMessageLog reissueActivationLink(
            AppUser user,
            Institution institution,
            InstitutionMembership membership
    ) {
        invalidateActiveTokens(membership);
        ActivationTokenDraft draft = createActivationToken(user, institution, membership);
        ActivationToken saved = activationTokenRepository.save(draft.token());
        return emailNotificationService.queueActivationMessage(
                EmailType.ACTIVATION_LINK_REGENERATED,
                user,
                institution,
                membership,
                saved,
                draft.rawToken()
        );
    }

    /**
     * Aktywuje konto na podstawie tokenu, obsługując wygaśnięcie tokenu i tryb ręcznego potwierdzenia instytucji.
     */
    @Transactional
    public RegistrationActivationResultDto activate(String rawToken, String clientIp, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            logAttempt(null, AccountActivationStatus.FAILED_TOKEN_NOT_FOUND, clientIp, userAgent, "Activation token is required.");
            throw new IllegalArgumentException("Activation token is required.");
        }

        ActivationToken token = activationTokenRepository.findByTokenHash(tokenHash(rawToken))
                .orElseThrow(() -> {
                    logAttempt(null, AccountActivationStatus.FAILED_TOKEN_NOT_FOUND, clientIp, userAgent, "Activation token not found.");
                    return new IllegalArgumentException("Activation token is invalid.");
                });

        validateTokenReferences(token, clientIp, userAgent);

        if (token.getUsedAt() != null || ActivationTokenStatus.USED.equals(token.getStatus())) {
            logAttempt(token, AccountActivationStatus.FAILED_TOKEN_ALREADY_USED, clientIp, userAgent, "Activation token was already used.");
            throw new ActivationTokenAlreadyUsedException();
        }

        Instant now = clock.instant();
        if (token.getExpiresAt().isBefore(now) || ActivationTokenStatus.EXPIRED.equals(token.getStatus())) {
            token.setStatus(ActivationTokenStatus.EXPIRED);
            activationTokenRepository.save(token);
            logAttempt(token, AccountActivationStatus.FAILED_TOKEN_EXPIRED, clientIp, userAgent, "Activation token expired.");
            ActivationToken newToken = issueActivationToken(
                    token.getUser(),
                    token.getInstitution(),
                    token.getMembership(),
                    EmailType.ACTIVATION_LINK_REGENERATED
            );
            logAttempt(newToken, AccountActivationStatus.NEW_TOKEN_GENERATED, clientIp, userAgent, null);
            return activationResult(token, RegistrationActivationStatus.ACTIVATION_LINK_EXPIRED_NEW_LINK_SENT);
        }

        InstitutionMembership membership = token.getMembership();
        if (!EntityState.ACTIVE.equals(membership.getState())
                || !InstitutionRole.INSTITUTION_PENDING.equals(membership.getRole())) {
            logAttempt(token, AccountActivationStatus.FAILED_MEMBERSHIP_NOT_FOUND, clientIp, userAgent, "Membership is not pending activation.");
            throw new IllegalArgumentException("Activation is not available for this account.");
        }

        boolean autoActivate = token.getInstitution().isAutoActivateNewAccounts();
        InstitutionRole targetRole = autoActivate ? InstitutionRole.INSTITUTION_USER : InstitutionRole.INSTITUTION_TO_CONFIRM;
        membership.setRole(targetRole);
        membership.setLoginAllowedFromAnyIp(true);
        membership.setActivatedAt(now);

        token.setUsedAt(now);
        token.setStatus(ActivationTokenStatus.USED);
        activationTokenRepository.save(token);

        AccountActivationStatus activationStatus = autoActivate
                ? AccountActivationStatus.SUCCESS_AUTO_ACTIVATED
                : AccountActivationStatus.SUCCESS_WAITING_FOR_CONFIRMATION;
        logAttempt(token, activationStatus, clientIp, userAgent, null);

        EmailType emailType = autoActivate
                ? EmailType.ACCOUNT_ACTIVATED
                : EmailType.ACCOUNT_WAITING_FOR_CONFIRMATION;
        emailNotificationService.queueActivationMessage(emailType, token.getUser(), token.getInstitution(), membership, token, null);

        return activationResult(token, autoActivate
                ? RegistrationActivationStatus.ACCOUNT_ACTIVATED
                : RegistrationActivationStatus.WAITING_FOR_CONFIRMATION);
    }

    private void invalidateActiveTokens(InstitutionMembership membership) {
        activationTokenRepository.findByMembershipIdAndStatus(membership.getId(), ActivationTokenStatus.ACTIVE)
                .forEach(token -> {
                    token.setStatus(ActivationTokenStatus.INVALIDATED);
                    activationTokenRepository.save(token);
                });
    }

    private ActivationTokenDraft createActivationToken(AppUser user, Institution institution, InstitutionMembership membership) {
        String rawToken = generateRawToken();
        ActivationToken token = new ActivationToken();
        token.setUser(user);
        token.setInstitution(institution);
        token.setMembership(membership);
        token.setTokenHash(tokenHash(rawToken));
        token.setExpiresAt(clock.instant().plus(activationTokenValidityDays, ChronoUnit.DAYS));
        token.setStatus(ActivationTokenStatus.ACTIVE);
        token.setState(EntityState.ACTIVE);
        token.setCreatedAt(OffsetDateTime.now(clock));
        return new ActivationTokenDraft(token, rawToken);
    }

    private void validateTokenReferences(ActivationToken token, String clientIp, String userAgent) {
        if (token.getUser() == null) {
            logAttempt(token, AccountActivationStatus.FAILED_USER_NOT_FOUND, clientIp, userAgent, "Token user not found.");
            throw new IllegalArgumentException("Activation token is invalid.");
        }
        if (token.getInstitution() == null || token.getMembership() == null) {
            logAttempt(token, AccountActivationStatus.FAILED_MEMBERSHIP_NOT_FOUND, clientIp, userAgent, "Token membership not found.");
            throw new IllegalArgumentException("Activation token is invalid.");
        }
    }

    private RegistrationActivationResultDto activationResult(ActivationToken token, RegistrationActivationStatus status) {
        InstitutionMembership membership = token.getMembership();
        return new RegistrationActivationResultDto(
                token.getUser().getId(),
                token.getInstitution().getId(),
                membership.getId(),
                membership.getRole(),
                status
        );
    }

    private void logAttempt(
            ActivationToken token,
            AccountActivationStatus status,
            String clientIp,
            String userAgent,
            String errorMessage
    ) {
        logAttempt(token, status, clientIp, userAgent, errorMessage, null, null);
    }

    private void logAttempt(
            ActivationToken token,
            AccountActivationStatus status,
            String clientIp,
            String userAgent,
            String errorMessage,
            Boolean termsAndPrivacyPolicyAccepted,
            Boolean personalDataProcessingAcknowledged
    ) {
        AccountActivationLog log = new AccountActivationLog();
        if (token != null) {
            log.setUser(token.getUser());
            log.setInstitution(token.getInstitution());
            log.setMembership(token.getMembership());
            log.setActivationToken(token);
        }
        log.setAttemptedAt(clock.instant());
        log.setStatus(status);
        log.setIpAddress(truncate(clientIp, 64));
        log.setUserAgent(truncate(userAgent, 1024));
        log.setErrorMessage(truncate(errorMessage, 4000));
        log.setTermsAndPrivacyPolicyAccepted(termsAndPrivacyPolicyAccepted);
        log.setPersonalDataProcessingAcknowledged(personalDataProcessingAcknowledged);
        log.setState(EntityState.ACTIVE);
        log.setCreatedAt(OffsetDateTime.now(clock));
        activationLogRepository.save(log);
    }

    /**
     * Wylicza bezpieczny skrót tokenu używany do zapisu i wyszukiwania tokenów aktywacyjnych.
     */
    public static String tokenHash(String rawToken) {
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

    private record ActivationTokenDraft(ActivationToken token, String rawToken) {
    }
}
