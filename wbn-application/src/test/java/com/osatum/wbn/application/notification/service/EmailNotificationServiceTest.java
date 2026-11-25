package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.event.EmailDeliveryRequestedEvent;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.notification.EmailMessageLogRepository;
import com.osatum.wbn.domain.repository.notification.EmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationServiceTest {
    private EmailMessageLogRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailMessageLogRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        EmailTemplateRepository emailTemplateRepository = mock(EmailTemplateRepository.class);
        when(emailTemplateRepository.findActiveByEmailTypeAndLanguage(any(), any())).thenReturn(Optional.empty());
        RegistrationEmailContentFactory contentFactory = new RegistrationEmailContentFactory(emailTemplateRepository);
        ReflectionTestUtils.setField(
                contentFactory,
                "activationUrlTemplate",
                "https://app.example.org/registration/activation/{token}"
        );
        ReflectionTestUtils.setField(contentFactory, "templateLanguage", "pl");
        service = new EmailNotificationService(repository, contentFactory, eventPublisher);
        when(repository.save(any(EmailMessageLog.class))).thenAnswer(invocation -> {
            EmailMessageLog log = invocation.getArgument(0);
            log.setId(100L);
            return log;
        });
    }

    @Test
    void queuesActivationEmailWithoutPersistingRawToken() {
        String rawToken = "raw-token";

        service.queueActivationMessage(
                EmailType.ACCOUNT_ACTIVATION_LINK,
                user(),
                institution(),
                membership(),
                activationToken(),
                rawToken
        );

        ArgumentCaptor<EmailMessageLog> logCaptor = ArgumentCaptor.forClass(EmailMessageLog.class);
        verify(repository).save(logCaptor.capture());
        EmailMessageLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getStatus()).isEqualTo(EmailMessageStatus.PENDING);
        assertThat(savedLog.getContextData()).contains("activationTokenId=30");
        assertThat(savedLog.getContextData()).doesNotContain(rawToken);

        ArgumentCaptor<EmailDeliveryRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailDeliveryRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        EmailDeliveryRequestedEvent event = eventCaptor.getValue();
        assertThat(event.emailMessageLogId()).isEqualTo(100L);
        assertThat(event.recipient()).isEqualTo("jan@example.edu");
        assertThat(event.htmlBody()).contains("https://app.example.org/registration/activation/raw-token");
    }

    @Test
    void requeuesInformationalEmailFromExistingLog() {
        EmailMessageLog source = new EmailMessageLog();
        source.setId(55L);
        source.setEmailType(EmailType.ACCOUNT_ACTIVATED);
        source.setRecipient("jan@example.edu");
        source.setUser(user());
        source.setInstitution(institution());
        source.setMembership(membership());
        source.setTemplateIdentifier(EmailType.ACCOUNT_ACTIVATED.name());
        source.setContextData("institutionId=1;membershipId=20;activationTokenId=30");

        service.requeueMessage(source);

        ArgumentCaptor<EmailMessageLog> logCaptor = ArgumentCaptor.forClass(EmailMessageLog.class);
        verify(repository).save(logCaptor.capture());
        EmailMessageLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getStatus()).isEqualTo(EmailMessageStatus.PENDING);
        assertThat(savedLog.getAttemptCount()).isZero();
        assertThat(savedLog.getContextData()).contains("resendOfEmailMessageLogId=55");

        ArgumentCaptor<EmailDeliveryRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(EmailDeliveryRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().recipient()).isEqualTo("jan@example.edu");
        assertThat(eventCaptor.getValue().subject()).isEqualTo("Your account is active");
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setEmail("jan@example.edu");
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        return user;
    }

    private Institution institution() {
        Institution institution = new Institution();
        institution.setId(1L);
        institution.setName("Research Unit");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        return institution;
    }

    private InstitutionMembership membership() {
        InstitutionMembership membership = new InstitutionMembership();
        membership.setId(20L);
        return membership;
    }

    private ActivationToken activationToken() {
        ActivationToken activationToken = new ActivationToken();
        activationToken.setId(30L);
        return activationToken;
    }
}
