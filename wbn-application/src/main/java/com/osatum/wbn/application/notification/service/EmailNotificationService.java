package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailContent;
import com.osatum.wbn.application.notification.event.EmailDeliveryRequestedEvent;
import com.osatum.wbn.application.user.port.UserNotificationPort;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.domain.model.registration.ActivationToken;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.domain.repository.notification.EmailMessageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class EmailNotificationService implements UserNotificationPort {
    private final EmailMessageLogRepository emailMessageLogRepository;
    private final RegistrationEmailContentFactory contentFactory;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Kolejkuje wiadomość związaną z aktywacją konta i publikuje zdarzenie jej dostarczenia.
     */
    @Override
    public EmailMessageLog queueActivationMessage(
            EmailType emailType,
            AppUser user,
            Institution institution,
            InstitutionMembership membership,
            ActivationToken activationToken,
            String rawActivationToken
    ) {
        EmailContent content = contentFactory.create(emailType, user, institution, rawActivationToken);
        EmailMessageLog log = new EmailMessageLog();
        log.setEmailType(emailType);
        log.setRecipient(user.getEmail());
        log.setUser(user);
        log.setInstitution(institution);
        log.setMembership(membership);
        log.setStatus(EmailMessageStatus.PENDING);
        log.setAttemptCount(0);
        log.setTemplateIdentifier(content.templateIdentifier());
        log.setContextData(contextData(institution, membership, activationToken));
        log.setState(EntityState.ACTIVE);
        log.setCreatedAt(OffsetDateTime.now());
        EmailMessageLog saved = emailMessageLogRepository.save(log);
        eventPublisher.publishEvent(new EmailDeliveryRequestedEvent(
                saved.getId(),
                saved.getRecipient(),
                content.subject(),
                content.textBody(),
                content.htmlBody()
        ));
        return saved;
    }

    /**
     * Kolejkuje wiadomość z linkiem resetu hasła i publikuje zdarzenie jej dostarczenia.
     */
    @Override
    public EmailMessageLog queuePasswordResetMessage(
            AppUser user,
            PasswordResetRequest passwordResetRequest,
            String rawPasswordResetToken
    ) {
        EmailContent content = contentFactory.create(EmailType.PASSWORD_RESET_LINK, user, null, rawPasswordResetToken);
        EmailMessageLog log = new EmailMessageLog();
        log.setEmailType(EmailType.PASSWORD_RESET_LINK);
        log.setRecipient(user.getEmail());
        log.setUser(user);
        log.setStatus(EmailMessageStatus.PENDING);
        log.setAttemptCount(0);
        log.setTemplateIdentifier(content.templateIdentifier());
        log.setContextData(passwordResetContextData(passwordResetRequest));
        log.setState(EntityState.ACTIVE);
        log.setCreatedAt(OffsetDateTime.now());
        EmailMessageLog saved = emailMessageLogRepository.save(log);
        eventPublisher.publishEvent(new EmailDeliveryRequestedEvent(
                saved.getId(),
                saved.getRecipient(),
                content.subject(),
                content.textBody(),
                content.htmlBody()
        ));
        return saved;
    }

    /**
     * Kolejkuje wiadomość potwierdzającą usunięcie danych użytkownika i publikuje zdarzenie jej dostarczenia.
     */
    @Override
    public EmailMessageLog queueUserDataDeletionConfirmationMessage(
            AppUser user,
            Institution institution,
            UserDataDeletionRequest userDataDeletionRequest,
            String rawUserDataDeletionToken
    ) {
        EmailContent content = contentFactory.create(EmailType.USER_DATA_DELETION_CONFIRMATION, user, institution, rawUserDataDeletionToken);
        EmailMessageLog log = new EmailMessageLog();
        log.setEmailType(EmailType.USER_DATA_DELETION_CONFIRMATION);
        log.setRecipient(user.getEmail());
        log.setUser(user);
        log.setInstitution(institution);
        log.setStatus(EmailMessageStatus.PENDING);
        log.setAttemptCount(0);
        log.setTemplateIdentifier(content.templateIdentifier());
        log.setContextData(userDataDeletionContextData(userDataDeletionRequest));
        log.setState(EntityState.ACTIVE);
        log.setCreatedAt(OffsetDateTime.now());
        EmailMessageLog saved = emailMessageLogRepository.save(log);
        eventPublisher.publishEvent(new EmailDeliveryRequestedEvent(
                saved.getId(),
                saved.getRecipient(),
                content.subject(),
                content.textBody(),
                content.htmlBody()
        ));
        return saved;
    }

    /**
     * Tworzy nowy log i ponownie kolejkuje wiadomość na podstawie istniejącej wysyłki.
     */
    public EmailMessageLog requeueMessage(EmailMessageLog sourceLog) {
        EmailContent content = contentFactory.create(
                sourceLog.getEmailType(),
                sourceLog.getUser(),
                sourceLog.getInstitution(),
                null
        );

        EmailMessageLog log = new EmailMessageLog();
        log.setEmailType(sourceLog.getEmailType());
        log.setRecipient(sourceLog.getRecipient());
        log.setUser(sourceLog.getUser());
        log.setInstitution(sourceLog.getInstitution());
        log.setMembership(sourceLog.getMembership());
        log.setStatus(EmailMessageStatus.PENDING);
        log.setAttemptCount(0);
        log.setTemplateIdentifier(content.templateIdentifier());
        log.setContextData(resendContextData(sourceLog));
        log.setState(EntityState.ACTIVE);
        log.setCreatedAt(OffsetDateTime.now());
        EmailMessageLog saved = emailMessageLogRepository.save(log);
        eventPublisher.publishEvent(new EmailDeliveryRequestedEvent(
                saved.getId(),
                saved.getRecipient(),
                content.subject(),
                content.textBody(),
                content.htmlBody()
        ));
        return saved;
    }

    private String contextData(Institution institution, InstitutionMembership membership, ActivationToken activationToken) {
        return "institutionId=" + institution.getId()
                + ";membershipId=" + membership.getId()
                + ";activationTokenId=" + activationToken.getId();
    }

    private String passwordResetContextData(PasswordResetRequest passwordResetRequest) {
        return "passwordResetRequestId=" + passwordResetRequest.getId();
    }

    private String userDataDeletionContextData(UserDataDeletionRequest userDataDeletionRequest) {
        return "userDataDeletionRequestId=" + userDataDeletionRequest.getId();
    }

    private String resendContextData(EmailMessageLog sourceLog) {
        String contextData = sourceLog.getContextData();
        String resendData = "resendOfEmailMessageLogId=" + sourceLog.getId();
        if (contextData == null || contextData.isBlank()) {
            return resendData;
        }
        return contextData + ";" + resendData;
    }
}
