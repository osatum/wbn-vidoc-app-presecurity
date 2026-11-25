package com.osatum.wbn.infrastructure.time;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Component
public class JournalEventTaskCoordinator {
    private final Object monitor = new Object();
    private int pendingTasks;
    private Throwable failure;

    public Runnable decorate(Runnable runnable) {
        synchronized (monitor) {
            pendingTasks++;
        }
        return () -> {
            try {
                runnable.run();
            } catch (RuntimeException | Error exception) {
                recordFailure(exception);
                throw exception;
            } finally {
                synchronized (monitor) {
                    pendingTasks--;
                    if (pendingTasks == 0) {
                        monitor.notifyAll();
                    }
                }
            }
        };
    }

    public void recordFailure(Throwable exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        synchronized (monitor) {
            if (failure == null) failure = exception;
        }
    }

    public void awaitIdle(Duration timeout) throws InterruptedException {
        synchronized (monitor) {
            waitUntilIdle(timeout);
            if (failure != null) throw new IllegalStateException("Journal event task failed", failure);
        }
    }

    /** Nowy pełny import może odrzucić błędy starego zbioru dopiero po zakończeniu jego zadań. */
    public void beginBatch(Duration timeout) throws InterruptedException {
        synchronized (monitor) {
            waitUntilIdle(timeout);
            failure = null;
        }
    }

    private void waitUntilIdle(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (monitor) {
            while (pendingTasks > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IllegalStateException(
                            "Timed out waiting for " + pendingTasks + " journal event tasks"
                    );
                }
                TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
            }
        }
    }

    int pendingTasks() {
        synchronized (monitor) {
            return pendingTasks;
        }
    }
}
