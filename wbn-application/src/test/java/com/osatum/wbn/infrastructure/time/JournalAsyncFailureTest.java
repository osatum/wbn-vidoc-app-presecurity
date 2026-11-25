package com.osatum.wbn.infrastructure.time;

import com.osatum.wbn.application.shared.async.AsyncExecutorNames;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalAsyncFailureTest {
    public static class FailingListener {
        @Async(AsyncExecutorNames.JOURNAL_EVENT_EXECUTOR)
        public void onSaved() { throw new IllegalStateException("index unavailable"); }
    }

    @Test
    void springVoidAsyncFailuresAreRecordedEvenThoughTheFutureSwallowsThem() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JournalEventTaskCoordinator.class);
            context.registerBean(FailingListener.class);
            context.register(SchedulingConfig.class);
            context.refresh();
            context.getBean(FailingListener.class).onSaved();
            var coordinator = context.getBean(JournalEventTaskCoordinator.class);
            assertThatThrownBy(() -> coordinator.awaitIdle(Duration.ofSeconds(5)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("index unavailable");
        }
    }
}
