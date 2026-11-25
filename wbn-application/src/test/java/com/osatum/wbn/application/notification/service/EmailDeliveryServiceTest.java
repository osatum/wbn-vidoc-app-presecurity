package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.event.EmailDeliveryRequestedEvent;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.repository.notification.EmailMessageLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-12T10:00:00Z");
    private static final String MESSAGE_ID_DOMAIN = "mail.example.org";

    private EmailMessageLogRepository repository;
    private JavaMailSender mailSender;
    private EmailDeliveryService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailMessageLogRepository.class);
        mailSender = mock(JavaMailSender.class);
        service = new EmailDeliveryService(repository, mailSender, Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@example.org");
        ReflectionTestUtils.setField(service, "retryEnabled", true);
        ReflectionTestUtils.setField(service, "retryMaxAttempts", 3);
        ReflectionTestUtils.setField(service, "retryDelayMs", 0L);
        when(repository.save(any(EmailMessageLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsEmailAndMarksLogAsSent() throws Exception {
        EmailMessageLog log = log();
        MimeMessage mimeMessage = mimeMessage();
        when(repository.findById(1L)).thenReturn(Optional.of(log));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doAnswer(invocation -> {
            mimeMessage.saveChanges();
            return null;
        }).when(mailSender).send(mimeMessage);
        ReflectionTestUtils.setField(service, "mailEnabled", true);

        service.deliver(event());

        verify(mailSender).send(mimeMessage);
        assertThat(mimeMessage.getHeader("Message-ID", null)).endsWith("@" + MESSAGE_ID_DOMAIN + ">");
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(EmailMessageStatus.SENT);
        assertThat(log.getSentAt()).isEqualTo(NOW);
        assertThat(log.getErrorMessage()).isNull();
    }

    @Test
    void retriesAndMarksLogAsFailedWhenMailSenderFails() {
        EmailMessageLog log = log();
        MimeMessage mimeMessage = mimeMessage();
        when(repository.findById(1L)).thenReturn(Optional.of(log));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("smtp failed")).when(mailSender).send(mimeMessage);
        ReflectionTestUtils.setField(service, "mailEnabled", true);

        service.deliver(event());

        verify(mailSender, times(3)).send(mimeMessage);
        assertThat(log.getAttemptCount()).isEqualTo(3);
        assertThat(log.getStatus()).isEqualTo(EmailMessageStatus.FAILED);
        assertThat(log.getErrorMessage()).contains("smtp failed");
    }

    @Test
    void usesSingleAttemptWhenRetryIsDisabled() {
        EmailMessageLog log = log();
        MimeMessage mimeMessage = mimeMessage();
        when(repository.findById(1L)).thenReturn(Optional.of(log));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("smtp failed")).when(mailSender).send(mimeMessage);
        ReflectionTestUtils.setField(service, "mailEnabled", true);
        ReflectionTestUtils.setField(service, "retryEnabled", false);

        service.deliver(event());

        verify(mailSender).send(mimeMessage);
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(EmailMessageStatus.FAILED);
    }

    @Test
    void cancelsDeliveryWhenMailIsDisabled() {
        EmailMessageLog log = log();
        when(repository.findById(1L)).thenReturn(Optional.of(log));
        ReflectionTestUtils.setField(service, "mailEnabled", false);

        service.deliver(event());

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(EmailMessageStatus.CANCELLED);
        assertThat(log.getErrorMessage()).contains("disabled");
    }

    private EmailDeliveryRequestedEvent event() {
        return new EmailDeliveryRequestedEvent(
                1L,
                "jan@example.edu",
                "Subject",
                "Text body",
                "<p>Html body</p>"
        );
    }

    private EmailMessageLog log() {
        EmailMessageLog log = new EmailMessageLog();
        log.setId(1L);
        log.setStatus(EmailMessageStatus.PENDING);
        return log;
    }

    private MimeMessage mimeMessage() {
        Properties properties = new Properties();
        properties.setProperty("mail.host", MESSAGE_ID_DOMAIN);
        return new MimeMessage(Session.getInstance(properties));
    }
}
