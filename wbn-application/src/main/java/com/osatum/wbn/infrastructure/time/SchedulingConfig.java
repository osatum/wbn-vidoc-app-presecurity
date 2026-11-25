package com.osatum.wbn.infrastructure.time;

import com.osatum.wbn.application.shared.async.AsyncExecutorNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class SchedulingConfig {
    // Boot opakowuje beany AsyncConfigurer; konfiguracja deklarująca metody @Bean musi pozostać od nich oddzielona.
    @Bean
    AsyncConfigurer journalEventAsyncConfigurer(JournalEventTaskCoordinator coordinator) {
        return new AsyncConfigurer() {
            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                var logger = new SimpleAsyncUncaughtExceptionHandler();
                return (exception, method, arguments) -> {
                    var async = method.getAnnotation(Async.class);
                    if (async != null && AsyncExecutorNames.JOURNAL_EVENT_EXECUTOR.equals(async.value())) {
                        coordinator.recordFailure(exception);
                    }
                    logger.handleUncaughtException(exception, method, arguments);
                };
            }
        };
    }

    @Bean(name = AsyncExecutorNames.JOURNAL_EVENT_EXECUTOR)
    ThreadPoolTaskExecutor journalEventExecutor(
            JournalEventTaskCoordinator taskCoordinator,
            @Value("${app.migration.journal-events.parallelism:4}") int parallelism,
            @Value("${app.migration.journal-events.queue-capacity:100}") int queueCapacity
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("journal-event-");
        executor.setTaskDecorator(taskCoordinator::decorate);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
