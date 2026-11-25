package com.osatum.wbn.application.analytics.listener;

import com.osatum.wbn.application.analytics.assembler.ToJournalAnalyticsAssembler;
import com.osatum.wbn.application.analytics.assembler.ToJournalAnalyticsParameters;
import com.osatum.wbn.application.analytics.repository.AnalyticsJournalRepository;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

import static com.osatum.wbn.application.shared.async.AsyncExecutorNames.JOURNAL_EVENT_EXECUTOR;

@Component
@RequiredArgsConstructor
public class JournalAnalyticsListener {

    private final JournalRepository journalRepository;
    private final AnalyticsJournalRepository analyticsJournalRepository;
    private final ToJournalAnalyticsAssembler assembler = new ToJournalAnalyticsAssembler();
    private final ConfigurationService  configurationService;

    @Async(JOURNAL_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public void on(JournalSavedEvent event) {

        Optional<Journal> oJournal = journalRepository.findById(event.journalId());
        if (oJournal.isEmpty()) {
            return;
        }
        analyticsJournalRepository.deleteForJournalId(oJournal.get().getId());

        ToJournalAnalyticsParameters parameters = new ToJournalAnalyticsParameters(oJournal.get(), null, configurationService.getCurrentRatingYear());
        List<JournalAnalytics> analytics = assembler.toDto(parameters);

        analyticsJournalRepository.saveAll(analytics);
    }
}
