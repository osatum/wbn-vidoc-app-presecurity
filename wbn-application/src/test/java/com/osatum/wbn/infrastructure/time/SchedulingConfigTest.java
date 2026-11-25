package com.osatum.wbn.infrastructure.time;

import com.osatum.wbn.application.shared.async.AsyncExecutorNames;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class SchedulingConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(SchedulingConfig.class, TestConfiguration.class)
            .withPropertyValues("spring.task.execution.mode=force", "spring.threads.virtual.enabled=false",
                    "spring.task.execution.thread-name-prefix=application-");

    @Test
    void startsWithBootForcedExecutorAndKeepsJournalFailuresAndDefaultTasksSeparate() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SchedulingConfig.class);
            assertThat(context.getBean(AsyncExecutorNames.JOURNAL_EVENT_EXECUTOR))
                    .isInstanceOf(ThreadPoolTaskExecutor.class)
                    .isNotSameAs(context.getBean("applicationTaskExecutor"));
            var probe = context.getBean(AsyncProbe.class);
            assertThat(probe.defaultTask().get(5, TimeUnit.SECONDS)).startsWith("application-");
            probe.journalTask();
            assertThatThrownBy(() -> context.getBean(JournalEventTaskCoordinator.class).awaitIdle(Duration.ofSeconds(5)))
                    .isInstanceOf(IllegalStateException.class).hasRootCauseMessage("index unavailable");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfiguration {
        @Bean JournalEventTaskCoordinator coordinator() { return new JournalEventTaskCoordinator(); }
        @Bean AsyncProbe asyncProbe() { return new AsyncProbe(); }
    }

    public static class AsyncProbe {
        @Async
        public CompletableFuture<String> defaultTask() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }

        @Async(AsyncExecutorNames.JOURNAL_EVENT_EXECUTOR)
        public void journalTask() { throw new IllegalStateException("index unavailable"); }
    }
}
