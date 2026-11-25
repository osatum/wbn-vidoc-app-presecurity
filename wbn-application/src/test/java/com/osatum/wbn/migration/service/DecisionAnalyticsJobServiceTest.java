package com.osatum.wbn.migration.service;

import com.osatum.wbn.application.journal.service.DecisionAnalyticsService;
import com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport;
import com.osatum.wbn.infrastructure.time.JournalEventTaskCoordinator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionAnalyticsJobServiceTest {
    @Test
    void existingDataPreparationWaitsForEventsAndReleasesTheSharedJobOnSuccessOrFailure() throws Exception {
        for (boolean failed : new boolean[]{false, true}) {
            var analytics = DecisionAnalyticsTestSupport.importAnalytics();
            var ready = DecisionAnalyticsTestSupport.readyAnalytics().snapshot();
            when(analytics.snapshot()).thenReturn(ready);
            var coordinator = new MigrationJobCoordinator();
            var events = mock(JournalEventTaskCoordinator.class);
            if (failed) doThrow(new IllegalStateException("index failed")).when(events).awaitIdle(any());
            var service = new DecisionAnalyticsJobService(analytics, coordinator, events);
            try {
                assertThat(service.start()).isPresent();
                if (failed) {
                    verify(analytics, timeout(2000)).fail(DecisionAnalyticsTestSupport.RUN);
                    verify(analytics, never()).rebuild(any());
                } else {
                    verify(analytics, timeout(2000)).rebuild(DecisionAnalyticsTestSupport.RUN);
                    var order = inOrder(analytics, events);
                    order.verify(analytics).begin();
                    order.verify(events).awaitIdle(any());
                    order.verify(analytics).rebuild(DecisionAnalyticsTestSupport.RUN);
                    verify(analytics, never()).fail(any());
                }
                org.awaitility.Awaitility.await().untilAsserted(() ->
                        assertThat(coordinator.isActive(MigrationJobCoordinator.Job.DECISION_ANALYTICS)).isFalse());
            } finally { service.shutdown(); }
        }
    }

    @Test
    void refusesToOverlapAnImportWithoutTouchingTheReadinessState() {
        var analytics = mock(DecisionAnalyticsService.class);
        var coordinator = new MigrationJobCoordinator();
        coordinator.tryStart(MigrationJobCoordinator.Job.IMPORT);
        var service = new DecisionAnalyticsJobService(analytics, coordinator, mock(JournalEventTaskCoordinator.class));
        try {
            assertThat(service.start()).isEmpty();
            verifyNoInteractions(analytics);
        } finally { service.shutdown(); }
    }
}
