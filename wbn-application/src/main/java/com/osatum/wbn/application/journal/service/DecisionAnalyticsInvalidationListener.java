package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Unieważnienie jest częścią zapisu źródła: nie wolno udostępniać starych osi po zatwierdzeniu zmiany. */
@Component
@RequiredArgsConstructor
public class DecisionAnalyticsInvalidationListener {
    private final DecisionAnalyticsRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void changed(JournalSavedEvent event) {
        repository.invalidate();
    }
}
