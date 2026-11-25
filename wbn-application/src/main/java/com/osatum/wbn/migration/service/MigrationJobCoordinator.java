package com.osatum.wbn.migration.service;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Wyklucza równoległy import, eksport raportów i odbudowę P2 w tej instancji aplikacji. */
@Service
public class MigrationJobCoordinator {
    private final AtomicReference<Job> activeJob = new AtomicReference<>();

    public boolean tryStart(Job job) {
        return activeJob.compareAndSet(null, Objects.requireNonNull(job));
    }

    public boolean isActive(Job job) {
        return activeJob.get() == job;
    }

    public void finish(Job job) {
        activeJob.compareAndSet(job, null);
    }

    public enum Job {
        IMPORT,
        QUESTIONNAIRE_REPORT_2024,
        DECISION_ANALYTICS
    }
}
