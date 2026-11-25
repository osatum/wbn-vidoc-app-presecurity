package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailMessageLogListDto;
import com.osatum.wbn.application.notification.dto.EmailMessageResendResultDto;
import com.osatum.wbn.application.notification.dto.EmailMessageResendStatus;
import com.osatum.wbn.application.notification.mapper.EmailMessageLogMapper;
import com.osatum.wbn.application.notification.query.EmailMessageLogListQuery;
import com.osatum.wbn.application.user.service.AccountActivationService;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.repository.notification.EmailMessageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class EmailMessageLogService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final EnumSet<EmailMessageStatus> BUSY_STATUSES = EnumSet.of(
            EmailMessageStatus.PENDING,
            EmailMessageStatus.PROCESSING,
            EmailMessageStatus.RETRYING
    );
    private static final EnumSet<EmailType> ACTIVATION_LINK_TYPES = EnumSet.of(
            EmailType.ACCOUNT_ACTIVATION_LINK,
            EmailType.ACTIVATION_LINK_REGENERATED
    );

    private final EmailMessageLogRepository repository;
    private final EmailMessageLogListQueryHandler queryHandler;
    private final EmailNotificationService emailNotificationService;
    private final AccountActivationService accountActivationService;
    private final EmailMessageLogMapper mapper;

    /**
     * Zwraca stronicowaną listę logów wysyłki wiadomości e-mail.
     */
    @Transactional(readOnly = true)
    public PageResult<EmailMessageLogListDto> findEmailMessages(EmailMessageLogListQuery query) {
        return queryHandler.execute(normalize(query));
    }

    /**
     * Ponawia wysyłkę wiadomości e-mail na podstawie istniejącego logu, generując nowy link aktywacyjny, gdy jest wymagany.
     */
    @Transactional
    public EmailMessageResendResultDto resendEmailMessage(Long id) {
        EmailMessageLog source = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Email message log not found."));

        validateCanBeResent(source);

        EmailMessageLog queuedMessage = ACTIVATION_LINK_TYPES.contains(source.getEmailType())
                ? accountActivationService.reissueActivationLink(source.getUser(), source.getInstitution(), source.getMembership())
                : emailNotificationService.requeueMessage(source);

        return new EmailMessageResendResultDto(
                EmailMessageResendStatus.EMAIL_RESEND_QUEUED,
                mapper.toListDto(queuedMessage)
        );
    }

    private void validateCanBeResent(EmailMessageLog source) {
        if (BUSY_STATUSES.contains(source.getStatus())) {
            throw new IllegalArgumentException("Email message is already waiting for delivery.");
        }

        if (source.getUser() == null || source.getInstitution() == null || source.getMembership() == null) {
            throw new IllegalArgumentException("Email message cannot be resent because linked account data is incomplete.");
        }

        if (ACTIVATION_LINK_TYPES.contains(source.getEmailType())) {
            InstitutionMembership membership = source.getMembership();
            if (!EntityState.ACTIVE.equals(membership.getState())
                    || !InstitutionRole.INSTITUTION_PENDING.equals(membership.getRole())) {
                throw new IllegalArgumentException("Activation link can be resent only for pending membership.");
            }
        }
    }

    private static EmailMessageLogListQuery normalize(EmailMessageLogListQuery query) {
        EmailMessageLogListQuery normalized = query == null ? new EmailMessageLogListQuery() : query;

        if (normalized.getSize() < 1) {
            normalized.setSize(DEFAULT_PAGE_SIZE);
        }

        if (normalized.getPage() < 0) {
            normalized.setPage(0);
        }

        return normalized;
    }
}
