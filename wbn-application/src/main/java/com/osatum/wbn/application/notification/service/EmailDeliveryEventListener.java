package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.event.EmailDeliveryRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmailDeliveryEventListener {
    private final EmailDeliveryService emailDeliveryService;

    /**
     * Po zatwierdzeniu transakcji przekazuje zdarzenie do asynchronicznej dostawy wiadomości e-mail.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailDeliveryRequested(EmailDeliveryRequestedEvent event) {
        emailDeliveryService.deliver(event);
    }
}
