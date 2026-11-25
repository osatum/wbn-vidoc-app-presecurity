package com.osatum.wbn.infrastructure.elasticsearch.listener;

import com.osatum.wbn.application.journal.event.JournalRecreateEvent;
import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import com.osatum.wbn.infrastructure.elasticsearch.assembler.simple.JournalSurveyDocumentAssembler;
import com.osatum.wbn.infrastructure.elasticsearch.document.journal.JournalDocument;
import com.osatum.wbn.infrastructure.elasticsearch.service.ElasticsearchIndexRecreator;
import com.osatum.wbn.infrastructure.elasticsearch.service.JournalSearchProjectionWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

import static com.osatum.wbn.application.shared.async.AsyncExecutorNames.JOURNAL_EVENT_EXECUTOR;

@Component
@RequiredArgsConstructor
public class JournalEventsListener {

    private final JournalRepository repository;
    private final JournalSurveyDocumentAssembler assembler;
    private final JournalSearchProjectionWriter projectionWriter;
    private final ElasticsearchIndexRecreator indexRecreator;

    @Async(JOURNAL_EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void on(JournalSavedEvent event) {

        Optional<Journal> journal = repository.findById(event.journalId());
        if (journal.isEmpty()) {
            return;
        }
        var documents = assembler.toDocuments(journal.get());
        projectionWriter.deleteJournalDocuments(event.journalId());
        documents.forEach(projectionWriter::save);
    }
    @EventListener
    @Transactional(readOnly = true)
    public void on(JournalRecreateEvent event) {
        indexRecreator.recreateIndex(JournalDocument.class);
        repository.findAll().stream()
                .flatMap(journal -> assembler.toDocuments(journal).stream())
                .forEach(projectionWriter::save);
        projectionWriter.refresh();
    }


}
