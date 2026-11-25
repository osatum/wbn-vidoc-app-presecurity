package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailMessageLogListDto;
import com.osatum.wbn.application.notification.dto.EmailMessageResendStatus;
import com.osatum.wbn.application.notification.mapper.EmailMessageLogMapper;
import com.osatum.wbn.application.notification.query.EmailMessageLogListQuery;
import com.osatum.wbn.application.user.service.AccountActivationService;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.notification.EmailMessageLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailMessageLogServiceTest {
    private EmailMessageLogRepository repository;
    private EmailMessageLogListQueryHandler queryHandler;
    private EmailNotificationService emailNotificationService;
    private AccountActivationService accountActivationService;
    private EmailMessageLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailMessageLogRepository.class);
        queryHandler = mock(EmailMessageLogListQueryHandler.class);
        emailNotificationService = mock(EmailNotificationService.class);
        accountActivationService = mock(AccountActivationService.class);
        service = new EmailMessageLogService(
                repository,
                queryHandler,
                emailNotificationService,
                accountActivationService,
                new EmailMessageLogMapper()
        );
    }

    @Test
    void normalizesEmptyQueryBeforeSearch() {
        PageResult<EmailMessageLogListDto> expected = new PageResult<>(List.of(), 0, 0, 20);
        when(queryHandler.execute(any(EmailMessageLogListQuery.class))).thenReturn(expected);

        PageResult<EmailMessageLogListDto> result = service.findEmailMessages(null);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<EmailMessageLogListQuery> queryCaptor = ArgumentCaptor.forClass(EmailMessageLogListQuery.class);
        verify(queryHandler).execute(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getPage()).isZero();
        assertThat(queryCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void requeuesInformationalEmailMessage() {
        EmailMessageLog source = emailLog(EmailType.ACCOUNT_ACTIVATED, EmailMessageStatus.FAILED, InstitutionRole.INSTITUTION_USER);
        EmailMessageLog queued = emailLog(EmailType.ACCOUNT_ACTIVATED, EmailMessageStatus.PENDING, InstitutionRole.INSTITUTION_USER);
        queued.setId(200L);
        when(repository.findById(100L)).thenReturn(Optional.of(source));
        when(emailNotificationService.requeueMessage(source)).thenReturn(queued);

        var result = service.resendEmailMessage(100L);

        assertThat(result.getStatus()).isEqualTo(EmailMessageResendStatus.EMAIL_RESEND_QUEUED);
        assertThat(result.getQueuedMessage().getId()).isEqualTo(200L);
        verify(emailNotificationService).requeueMessage(source);
        verify(accountActivationService, never()).reissueActivationLink(any(), any(), any());
    }

    @Test
    void regeneratesActivationTokenWhenResendingActivationLink() {
        EmailMessageLog source = emailLog(EmailType.ACCOUNT_ACTIVATION_LINK, EmailMessageStatus.FAILED, InstitutionRole.INSTITUTION_PENDING);
        EmailMessageLog queued = emailLog(EmailType.ACTIVATION_LINK_REGENERATED, EmailMessageStatus.PENDING, InstitutionRole.INSTITUTION_PENDING);
        queued.setId(201L);
        when(repository.findById(100L)).thenReturn(Optional.of(source));
        when(accountActivationService.reissueActivationLink(source.getUser(), source.getInstitution(), source.getMembership()))
                .thenReturn(queued);

        var result = service.resendEmailMessage(100L);

        assertThat(result.getStatus()).isEqualTo(EmailMessageResendStatus.EMAIL_RESEND_QUEUED);
        assertThat(result.getQueuedMessage().getEmailType()).isEqualTo(EmailType.ACTIVATION_LINK_REGENERATED);
        verify(accountActivationService).reissueActivationLink(
                same(source.getUser()),
                same(source.getInstitution()),
                same(source.getMembership())
        );
        verify(emailNotificationService, never()).requeueMessage(any());
    }

    @Test
    void rejectsMessageAlreadyWaitingForDelivery() {
        EmailMessageLog source = emailLog(EmailType.ACCOUNT_ACTIVATED, EmailMessageStatus.PENDING, InstitutionRole.INSTITUTION_USER);
        when(repository.findById(100L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.resendEmailMessage(100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already waiting");

        verify(emailNotificationService, never()).requeueMessage(any());
        verify(accountActivationService, never()).reissueActivationLink(any(), any(), any());
    }

    @Test
    void rejectsActivationLinkForMembershipThatIsNotPending() {
        EmailMessageLog source = emailLog(EmailType.ACCOUNT_ACTIVATION_LINK, EmailMessageStatus.FAILED, InstitutionRole.INSTITUTION_USER);
        when(repository.findById(100L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.resendEmailMessage(100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending membership");

        verify(accountActivationService, never()).reissueActivationLink(any(), any(), any());
    }

    private EmailMessageLog emailLog(EmailType emailType, EmailMessageStatus status, InstitutionRole role) {
        AppUser user = new AppUser();
        user.setId(10L);
        user.setEmail("jan@example.edu");
        user.setFirstName("Jan");
        user.setLastName("Kowalski");

        Institution institution = new Institution();
        institution.setId(20L);
        institution.setName("Research Unit");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);

        InstitutionMembership membership = new InstitutionMembership();
        membership.setId(30L);
        membership.setUser(user);
        membership.setInstitution(institution);
        membership.setRole(role);
        membership.setState(EntityState.ACTIVE);

        EmailMessageLog log = new EmailMessageLog();
        log.setId(100L);
        log.setEmailType(emailType);
        log.setRecipient("jan@example.edu");
        log.setUser(user);
        log.setInstitution(institution);
        log.setMembership(membership);
        log.setStatus(status);
        log.setAttemptCount(1);
        log.setTemplateIdentifier(emailType.name());
        log.setContextData("institutionId=20;membershipId=30;activationTokenId=40");
        return log;
    }
}
