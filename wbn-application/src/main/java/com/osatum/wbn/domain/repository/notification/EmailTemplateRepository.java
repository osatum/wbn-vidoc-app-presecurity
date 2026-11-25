package com.osatum.wbn.domain.repository.notification;

import com.osatum.wbn.domain.model.notification.EmailTemplate;
import com.osatum.wbn.domain.model.notification.EmailType;

import java.util.List;
import java.util.Optional;

public interface EmailTemplateRepository {
    List<EmailTemplate> findAllOrderByEmailTypeAndLanguage();

    Optional<EmailTemplate> findById(Long id);

    Optional<EmailTemplate> findByEmailTypeAndLanguage(EmailType emailType, String language);

    Optional<EmailTemplate> findActiveByEmailTypeAndLanguage(EmailType emailType, String language);

    EmailTemplate save(EmailTemplate template);
}
