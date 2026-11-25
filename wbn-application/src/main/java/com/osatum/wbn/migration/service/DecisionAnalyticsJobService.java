package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Snapshot;
import com.osatum.wbn.application.journal.service.DecisionAnalyticsService;
import com.osatum.wbn.application.system.authorization.CanSystemManage;
import com.osatum.wbn.infrastructure.time.JournalEventTaskCoordinator;
import com.osatum.wbn.migration.service.MigrationJobCoordinator.Job;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Odbudowuje analitykę bez importu, pod wspólną blokadą zadań migracyjnych tej instancji.
 * Przed obliczeniami czeka na zadania zdarzeń czasopism, aby nie publikować wyników dla nieukończonych zmian.
 * Przygotowuje skonfigurowane roczniki benchmarków; lista lat wyszukiwarki nie rozszerza zakresu obliczeń.
 * Zakończenie zadania nie zmienia globalnej flagi prezentacji benchmarków ani udostępnionego rocznika.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionAnalyticsJobService {
    private final DecisionAnalyticsService analytics;
    private final MigrationJobCoordinator coordinator;
    private final JournalEventTaskCoordinator events;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "decision-analytics-build");
        thread.setDaemon(true);
        return thread;
    });
    @Value("${app.migration.journal-events.await-timeout:10m}")
    private Duration awaitTimeout = Duration.ofMinutes(10);

    /**
     * Uruchamia odbudowę w tle wyłącznie dla operatora i po zajęciu wspólnego koordynatora tej instancji.
     * Zwrócona migawka potwierdza przyjęcie zadania, a jego zakończenie wymaga odczytu statusu.
     */
    @CanSystemManage
    public Optional<Snapshot> start() {
        if (!coordinator.tryStart(Job.DECISION_ANALYTICS)) return Optional.empty();
        UUID id = null;
        try {
            id = analytics.begin();
            UUID run = id;
            executor.submit(() -> rebuild(run));
            return Optional.of(analytics.snapshot());
        } catch (RuntimeException exception) {
            try { if (id != null) analytics.fail(id); }
            finally { coordinator.finish(Job.DECISION_ANALYTICS); }
            throw exception;
        }
    }

    /** Trwały stan analityki jest niezależny od życia wątku wykonującego odbudowę. */
    @CanSystemManage
    public Snapshot status() { return analytics.snapshot(); }

    /** Błąd synchronizacji lub obliczeń pozostawia generację niedostępną i zwalnia lokalny koordynator. */
    private void rebuild(UUID id) {
        try {
            events.awaitIdle(awaitTimeout);
            analytics.rebuild(id);
        } catch (Exception | OutOfMemoryError exception) {
            log.error("P2 analytics preparation failed: importId={}", id, exception);
            analytics.fail(id);
        } finally {
            coordinator.finish(Job.DECISION_ANALYTICS);
        }
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }
}
