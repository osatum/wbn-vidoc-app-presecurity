package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.mockito.Mockito.*;

public final class DecisionAnalyticsTestSupport {
    public static final UUID RUN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private DecisionAnalyticsTestSupport() { }
    public static DecisionAnalyticsService readyAnalytics() {
        var service = mock(DecisionAnalyticsService.class);
        when(service.snapshot()).thenReturn(new DecisionAnalyticsRepository.Snapshot(
                DecisionAnalyticsRepository.Status.READY, RUN, Set.of(2024, 2025), Instant.EPOCH, 0));
        when(service.unchanged(any())).thenReturn(true);
        return service;
    }
    public static DecisionAnalyticsService importAnalytics() {
        var service = mock(DecisionAnalyticsService.class);
        when(service.begin()).thenReturn(RUN);
        return service;
    }
}
