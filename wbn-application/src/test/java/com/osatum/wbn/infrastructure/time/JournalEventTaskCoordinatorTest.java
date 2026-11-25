package com.osatum.wbn.infrastructure.time;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEventTaskCoordinatorTest {

    @Test
    void waitsUntilAllDecoratedTasksFinish() throws Exception {
        JournalEventTaskCoordinator coordinator = new JournalEventTaskCoordinator();
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        CountDownLatch idleReached = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            executor.submit(coordinator.decorate(() -> {
                taskStarted.countDown();
                try {
                    releaseTask.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(taskStarted.await(5, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> {
                try {
                    coordinator.awaitIdle(Duration.ofSeconds(5));
                    idleReached.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThat(idleReached.await(100, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(coordinator.pendingTasks()).isEqualTo(1);

            releaseTask.countDown();

            assertThat(idleReached.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.pendingTasks()).isZero();
        } finally {
            releaseTask.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failsWhenTasksDoNotFinishBeforeTimeout() {
        JournalEventTaskCoordinator coordinator = new JournalEventTaskCoordinator();
        coordinator.decorate(() -> {
        });

        assertThatThrownBy(() -> coordinator.awaitIdle(Duration.ofMillis(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1 journal event tasks");
    }

    @Test
    void aFailedAsyncTaskCannotBeMistakenForSuccessfullyCompletedIndexing() throws Exception {
        var coordinator = new JournalEventTaskCoordinator();
        var failure = new IllegalStateException("index unavailable");
        assertThatThrownBy(() -> coordinator.decorate(() -> { throw failure; }).run()).isSameAs(failure);
        assertThat(coordinator.pendingTasks()).isZero();
        assertThatThrownBy(() -> coordinator.awaitIdle(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class).hasCause(failure);
        coordinator.beginBatch(Duration.ofSeconds(1));
        coordinator.decorate(() -> { }).run();
        coordinator.awaitIdle(Duration.ofSeconds(1));
    }
}
