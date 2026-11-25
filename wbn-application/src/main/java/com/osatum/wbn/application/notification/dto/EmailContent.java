package com.osatum.wbn.application.notification.dto;

public record EmailContent(
        String subject,
        String textBody,
        String htmlBody,
        String templateIdentifier
) {
    public EmailContent(String subject, String textBody, String htmlBody) {
        this(subject, textBody, htmlBody, null);
    }
}
