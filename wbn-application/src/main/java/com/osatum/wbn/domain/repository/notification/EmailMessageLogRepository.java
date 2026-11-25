package com.osatum.wbn.domain.repository.notification;

import com.osatum.wbn.domain.model.notification.EmailMessageLog;

import java.util.Optional;

public interface EmailMessageLogRepository {
    Optional<EmailMessageLog> findById(Long id);

    EmailMessageLog save(EmailMessageLog log);
}
