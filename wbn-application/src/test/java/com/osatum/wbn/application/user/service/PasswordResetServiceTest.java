package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.port.UserNotificationPort;
import com.osatum.wbn.application.user.dto.PasswordResetConfirmDto;
import com.osatum.wbn.application.user.dto.PasswordResetRequestDto;
import com.osatum.wbn.application.user.dto.PasswordResetTokenStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-14T12:00:00Z");

    @Mock
    private PasswordResetRequestRepository requestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserNotificationPort emailNotificationService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginSessionRepository loginSessionRepository;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(
                requestRepository,
                userRepository,
                passwordEncoder,
                emailNotificationService,
                refreshTokenRepository,
                loginSessionRepository,
                new PasswordResetRequestMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "tokenValidityMinutes", 60L);
        ReflectionTestUtils.setField(service, "rateLimitWindowMinutes", 15L);
        ReflectionTestUtils.setField(service, "rateLimitMaxRequests", 3L);

        AtomicLong sequence = new AtomicLong(1);
        when(requestRepository.save(any(PasswordResetRequest.class))).thenAnswer(invocation -> {
            PasswordResetRequest request = invocation.getArgument(0);
            if (request.getId() == null) {
                request.setId(sequence.getAndIncrement());
            }
            return request;
        });
    }

    @Test
    void queuesResetForActiveUserAndInvalidatesPreviousRequest() {
        AppUser user = activeUser();
        PasswordResetRequest previous = resetRequest(user, PasswordResetRequestStatus.EMAIL_QUEUED, NOW.plus(30, ChronoUnit.MINUTES));
        EmailMessageLog emailLog = new EmailMessageLog();
        emailLog.setId(77L);

        when(requestRepository.countRecentByEmailOrIpAddress("jan@example.edu", "127.0.0.1", NOW.minus(15, ChronoUnit.MINUTES)))
                .thenReturn(0L);
        when(userRepository.findByEmail("jan@example.edu")).thenReturn(Optional.of(user));
        when(requestRepository.findByUserIdAndStatusIn(eq(user.getId()), any())).thenReturn(List.of(previous));
        when(emailNotificationService.queuePasswordResetMessage(eq(user), any(PasswordResetRequest.class), any()))
                .thenReturn(emailLog);

        var result = service.requestReset(requestDto(" Jan@Example.Edu "), "127.0.0.1", "Mozilla/5.0 Chrome/125");

        assertThat(result.accepted()).isTrue();
        assertThat(previous.getStatus()).isEqualTo(PasswordResetRequestStatus.INVALIDATED);
        assertThat(previous.getInvalidatedAt()).isEqualTo(NOW);

        ArgumentCaptor<PasswordResetRequest> requestCaptor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(emailNotificationService).queuePasswordResetMessage(eq(user), requestCaptor.capture(), any());
        PasswordResetRequest saved = requestCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("jan@example.edu");
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(60, ChronoUnit.MINUTES));
        assertThat(saved.getStatus()).isEqualTo(PasswordResetRequestStatus.EMAIL_QUEUED);
        assertThat(saved.getBrowser()).isEqualTo("Chrome");
    }

    @Test
    void doesNotSendEmailForInactiveUser() {
        AppUser user = activeUser();
        user.setState(EntityState.INACTIVE);

        when(requestRepository.countRecentByEmailOrIpAddress("jan@example.edu", "127.0.0.1", NOW.minus(15, ChronoUnit.MINUTES)))
                .thenReturn(0L);
        when(userRepository.findByEmail("jan@example.edu")).thenReturn(Optional.of(user));

        service.requestReset(requestDto("jan@example.edu"), "127.0.0.1", "Mozilla/5.0");

        ArgumentCaptor<PasswordResetRequest> requestCaptor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(PasswordResetRequestStatus.USER_NOT_ACTIVE);
        verify(emailNotificationService, never()).queuePasswordResetMessage(any(), any(), any());
    }

    @Test
    void rateLimitedRequestIsLoggedWithoutUserLookup() {
        when(requestRepository.countRecentByEmailOrIpAddress("jan@example.edu", "127.0.0.1", NOW.minus(15, ChronoUnit.MINUTES)))
                .thenReturn(3L);

        service.requestReset(requestDto("jan@example.edu"), "127.0.0.1", "Mozilla/5.0");

        ArgumentCaptor<PasswordResetRequest> requestCaptor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getStatus()).isEqualTo(PasswordResetRequestStatus.RATE_LIMITED);
        verify(userRepository, never()).findByEmail(any());
        verify(emailNotificationService, never()).queuePasswordResetMessage(any(), any(), any());
    }

    @Test
    void confirmResetChangesPasswordCompletesRequestAndRevokesSessions() {
        AppUser user = activeUser();
        PasswordResetRequest request = resetRequest(user, PasswordResetRequestStatus.EMAIL_QUEUED, NOW.plus(30, ChronoUnit.MINUTES));
        String rawToken = "raw-reset-token";
        request.setTokenHash(tokenHash(rawToken));
        RefreshToken refreshToken = new RefreshToken();
        LoginSession loginSession = new LoginSession();
        loginSession.setStatus(LoginSessionStatus.ACTIVE);

        when(requestRepository.findByTokenHash(tokenHash(rawToken))).thenReturn(Optional.of(request));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");
        when(refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId())).thenReturn(List.of(refreshToken));
        when(loginSessionRepository.findActiveByUserId(user.getId(), NOW)).thenReturn(List.of(loginSession));

        var result = service.confirmReset(rawToken, confirmDto("new-password", "new-password"), "127.0.0.1", "Mozilla/5.0");

        assertThat(result.status()).isEqualTo(PasswordResetTokenStatus.COMPLETED);
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(request.getStatus()).isEqualTo(PasswordResetRequestStatus.COMPLETED);
        assertThat(request.getCompletedAt()).isEqualTo(NOW);
        assertThat(refreshToken.isRevoked()).isTrue();
        assertThat(loginSession.getStatus()).isEqualTo(LoginSessionStatus.REVOKED);
        assertThat(loginSession.getEndReason()).isEqualTo(LoginSessionEndReason.REVOKED);
        assertThat(loginSession.getEndedAt()).isEqualTo(NOW);
        verify(userRepository).save(user);
        verify(refreshTokenRepository).save(refreshToken);
        verify(loginSessionRepository).save(loginSession);
    }

    private PasswordResetRequestDto requestDto(String email) {
        PasswordResetRequestDto dto = new PasswordResetRequestDto();
        dto.setEmail(email);
        return dto;
    }

    private PasswordResetConfirmDto confirmDto(String password, String passwordRepeat) {
        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setPassword(password);
        dto.setPasswordRepeat(passwordRepeat);
        return dto;
    }

    private AppUser activeUser() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setEmail("jan@example.edu");
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        user.setPasswordHash("old-password");
        user.setState(EntityState.ACTIVE);
        return user;
    }

    private PasswordResetRequest resetRequest(AppUser user, PasswordResetRequestStatus status, Instant expiresAt) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setId(20L);
        request.setEmail(user.getEmail());
        request.setUser(user);
        request.setStatus(status);
        request.setRequestedAt(NOW.minus(5, ChronoUnit.MINUTES));
        request.setExpiresAt(expiresAt);
        request.setState(EntityState.ACTIVE);
        return request;
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
}
