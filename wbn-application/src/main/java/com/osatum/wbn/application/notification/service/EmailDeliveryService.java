package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.event.EmailDeliveryRequestedEvent;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.repository.notification.EmailMessageLogRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class EmailDeliveryService {
    private final EmailMessageLogRepository emailMessageLogRepository;
    private final JavaMailSender mailSender;
    private final Clock clock;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.mail.retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${app.mail.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${app.mail.retry.delay-ms:0}")
    private long retryDelayMs;

    /**
     * Dostarcza zakolejkowaną wiadomość e-mail, aktualizując log wysyłki i obsługując ponowienia.
     */
    @Transactional
    public void deliver(EmailDeliveryRequestedEvent event) {
        EmailMessageLog log = emailMessageLogRepository.findById(event.emailMessageLogId())
                .orElse(null);
        if (log == null) {
            return;
        }

        if (!mailEnabled) {
            log.setAttemptCount(log.getAttemptCount() + 1);
            log.setStatus(EmailMessageStatus.CANCELLED);
            log.setErrorMessage("Mail delivery is disabled.");
            emailMessageLogRepository.save(log);
            return;
        }

        int maxAttempts = retryEnabled ? Math.max(1, retryMaxAttempts) : 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (tryDeliver(event, log, attempt < maxAttempts)) {
                return;
            }
        }
    }

    private boolean tryDeliver(EmailDeliveryRequestedEvent event, EmailMessageLog log, boolean canRetry) {
        log.setAttemptCount(log.getAttemptCount() + 1);
        try {
            log.setStatus(EmailMessageStatus.PROCESSING);
            emailMessageLogRepository.save(log);
            send(event);
            log.setStatus(EmailMessageStatus.SENT);
            log.setSentAt(clock.instant());
            log.setErrorMessage(null);
            emailMessageLogRepository.save(log);
            return true;
        } catch (MailException | MessagingException e) {
            log.setErrorMessage(truncate(e.getMessage(), 4000));
            if (canRetry) {
                log.setStatus(EmailMessageStatus.RETRYING);
                emailMessageLogRepository.save(log);
                return !waitBeforeRetry(log);
            }
            log.setStatus(EmailMessageStatus.FAILED);
            emailMessageLogRepository.save(log);
            return true;
        }
    }

    private void send(EmailDeliveryRequestedEvent event) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(mailFrom);
        helper.setTo(event.recipient());
        helper.setSubject(event.subject());
        helper.setText(event.textBody(), event.htmlBody());
        mailSender.send(message);
    }

    private boolean waitBeforeRetry(EmailMessageLog log) {
        if (retryDelayMs <= 0) {
            return true;
        }

        try {
            Thread.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.setStatus(EmailMessageStatus.FAILED);
            log.setErrorMessage("Mail delivery retry interrupted.");
            emailMessageLogRepository.save(log);
            return false;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
