package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.shared.exception.ActivationTokenAlreadyUsedException;
import com.osatum.wbn.application.user.port.UserNotificationPort;
import com.osatum.wbn.application.user.dto.RegistrationActivationStatus;
import com.osatum.wbn.application.user.service.AccountActivationService;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import com.osatum.wbn.domain.model.registration.AccountActivationStatus;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.registration.ActivationTokenStatus;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.registration.AccountActivationLogRepository;
import com.osatum.wbn.domain.repository.registration.ActivationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivationServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-12T10:00:00Z");
    private static final String RAW_TOKEN = "activation-token";

    private ActivationTokenRepository activationTokenRepository;
    private AccountActivationLogRepository activationLogRepository;
    private UserNotificationPort emailNotificationService;
    private AccountActivationService service;

    @BeforeEach
    void setUp() {
        activationTokenRepository = mock(ActivationTokenRepository.class);
        activationLogRepository = mock(AccountActivationLogRepository.class);
        emailNotificationService = mock(UserNotificationPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccountActivationService(
                activationTokenRepository,
                activationLogRepository,
                emailNotificationService,
                clock
        );
        ReflectionTestUtils.setField(service, "activationTokenValidityDays", 7L);
        when(activationTokenRepository.save(any(ActivationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationLogRepository.save(any(AccountActivationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void activatesAccountAutomaticallyWhenInstitutionAllowsIt() {
        ActivationToken token = activeToken(true);
        when(activationTokenRepository.findByTokenHash(AccountActivationService.tokenHash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        var result = service.activate(RAW_TOKEN, "192.0.2.10", "JUnit");

        assertThat(result.getStatus()).isEqualTo(RegistrationActivationStatus.ACCOUNT_ACTIVATED);
        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_USER);
        assertThat(token.getStatus()).isEqualTo(ActivationTokenStatus.USED);
        assertThat(token.getUsedAt()).isEqualTo(NOW);
        assertThat(token.getMembership().getRole()).isEqualTo(InstitutionRole.INSTITUTION_USER);
        assertThat(token.getMembership().isLoginAllowedFromAnyIp()).isTrue();
        assertThat(token.getMembership().getActivatedAt()).isEqualTo(NOW);

        verify(emailNotificationService).queueActivationMessage(
                EmailType.ACCOUNT_ACTIVATED,
                token.getUser(),
                token.getInstitution(),
                token.getMembership(),
                token,
                null
        );
        assertActivationLog(AccountActivationStatus.SUCCESS_AUTO_ACTIVATED);
    }

    @Test
    void marksAccountAsWaitingForConfirmationWhenInstitutionRequiresIt() {
        ActivationToken token = activeToken(false);
        when(activationTokenRepository.findByTokenHash(AccountActivationService.tokenHash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        var result = service.activate(RAW_TOKEN, "192.0.2.10", "JUnit");

        assertThat(result.getStatus()).isEqualTo(RegistrationActivationStatus.WAITING_FOR_CONFIRMATION);
        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_TO_CONFIRM);
        assertThat(token.getMembership().getRole()).isEqualTo(InstitutionRole.INSTITUTION_TO_CONFIRM);
        assertThat(token.getMembership().isLoginAllowedFromAnyIp()).isTrue();
        verify(emailNotificationService).queueActivationMessage(
                EmailType.ACCOUNT_WAITING_FOR_CONFIRMATION,
                token.getUser(),
                token.getInstitution(),
                token.getMembership(),
                token,
                null
        );
        assertActivationLog(AccountActivationStatus.SUCCESS_WAITING_FOR_CONFIRMATION);
    }

    @Test
    void generatesNewTokenWhenActivationTokenExpired() {
        ActivationToken token = activeToken(true);
        token.setExpiresAt(NOW.minusSeconds(1));
        when(activationTokenRepository.findByTokenHash(AccountActivationService.tokenHash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));
        when(activationTokenRepository.findByMembershipIdAndStatus(20L, ActivationTokenStatus.ACTIVE))
                .thenReturn(List.of());

        var result = service.activate(RAW_TOKEN, "192.0.2.10", "JUnit");

        assertThat(result.getStatus()).isEqualTo(RegistrationActivationStatus.ACTIVATION_LINK_EXPIRED_NEW_LINK_SENT);
        assertThat(result.getRole()).isEqualTo(InstitutionRole.INSTITUTION_PENDING);
        assertThat(token.getStatus()).isEqualTo(ActivationTokenStatus.EXPIRED);
        assertThat(token.getMembership().getRole()).isEqualTo(InstitutionRole.INSTITUTION_PENDING);
        ArgumentCaptor<ActivationToken> newTokenCaptor = ArgumentCaptor.forClass(ActivationToken.class);
        verify(emailNotificationService).queueActivationMessage(
                eq(EmailType.ACTIVATION_LINK_REGENERATED),
                same(token.getUser()),
                same(token.getInstitution()),
                same(token.getMembership()),
                newTokenCaptor.capture(),
                notNull()
        );
        assertThat(newTokenCaptor.getValue()).isNotSameAs(token);
        assertThat(newTokenCaptor.getValue().getStatus()).isEqualTo(ActivationTokenStatus.ACTIVE);
        assertActivationLogs(AccountActivationStatus.FAILED_TOKEN_EXPIRED, AccountActivationStatus.NEW_TOKEN_GENERATED);
    }

    @Test
    void blocksAlreadyUsedActivationToken() {
        ActivationToken token = activeToken(true);
        token.setStatus(ActivationTokenStatus.USED);
        token.setUsedAt(NOW.minusSeconds(60));
        when(activationTokenRepository.findByTokenHash(AccountActivationService.tokenHash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.activate(RAW_TOKEN, "192.0.2.10", "JUnit"))
                .isInstanceOf(ActivationTokenAlreadyUsedException.class)
                .hasMessageContaining("already used");

        assertActivationLog(AccountActivationStatus.FAILED_TOKEN_ALREADY_USED);
    }

    @Test
    void logsRegistrationSubmit() {
        ActivationToken token = activeToken(true);

        service.logRegistrationSubmit(token, true, true, "192.0.2.10", "JUnit");

        ArgumentCaptor<AccountActivationLog> captor = ArgumentCaptor.forClass(AccountActivationLog.class);
        verify(activationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AccountActivationStatus.REGISTRATION_SUBMITTED);
        assertThat(captor.getValue().getUser()).isSameAs(token.getUser());
        assertThat(captor.getValue().getInstitution()).isSameAs(token.getInstitution());
        assertThat(captor.getValue().getMembership()).isSameAs(token.getMembership());
        assertThat(captor.getValue().getActivationToken()).isSameAs(token);
        assertThat(captor.getValue().getIpAddress()).isEqualTo("192.0.2.10");
        assertThat(captor.getValue().getUserAgent()).isEqualTo("JUnit");
        assertThat(captor.getValue().getAttemptedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getTermsAndPrivacyPolicyAccepted()).isTrue();
        assertThat(captor.getValue().getPersonalDataProcessingAcknowledged()).isTrue();
    }

    private void assertActivationLog(AccountActivationStatus status) {
        ArgumentCaptor<AccountActivationLog> captor = ArgumentCaptor.forClass(AccountActivationLog.class);
        verify(activationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(status);
        assertThat(captor.getValue().getIpAddress()).isEqualTo("192.0.2.10");
        assertThat(captor.getValue().getUserAgent()).isEqualTo("JUnit");
        assertThat(captor.getValue().getAttemptedAt()).isEqualTo(NOW);
    }

    private void assertActivationLogs(AccountActivationStatus first, AccountActivationStatus second) {
        ArgumentCaptor<AccountActivationLog> captor = ArgumentCaptor.forClass(AccountActivationLog.class);
        verify(activationLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AccountActivationLog::getStatus)
                .containsExactly(first, second);
    }

    private ActivationToken activeToken(boolean autoActivate) {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setEmail("jan@example.edu");
        user.setState(EntityState.ACTIVE);

        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Research Unit");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        institution.setAutoActivateNewAccounts(autoActivate);
        institution.setState(EntityState.ACTIVE);

        InstitutionMembership membership = new InstitutionMembership();
        membership.setId(20L);
        membership.setUser(user);
        membership.setInstitution(institution);
        membership.setRole(InstitutionRole.INSTITUTION_PENDING);
        membership.setLoginAllowedFromAnyIp(false);
        membership.setState(EntityState.ACTIVE);

        ActivationToken token = new ActivationToken();
        token.setId(30L);
        token.setUser(user);
        token.setInstitution(institution);
        token.setMembership(membership);
        token.setTokenHash(AccountActivationService.tokenHash(RAW_TOKEN));
        token.setExpiresAt(NOW.plusSeconds(60));
        token.setStatus(ActivationTokenStatus.ACTIVE);
        token.setState(EntityState.ACTIVE);
        return token;
    }
}
