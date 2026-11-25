package com.osatum.wbn.application.notification.event;

public record EmailDeliveryRequestedEvent(
        Long emailMessageLogId,
        String recipient,
        String subject,
        String textBody,
        String htmlBody
) {
}
