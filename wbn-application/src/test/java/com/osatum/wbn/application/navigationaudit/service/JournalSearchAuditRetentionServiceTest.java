package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.system.service.JournalSearchAuditRetentionService;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalSearchAuditRetentionServiceTest {
    private final NavigationAuditEventRepository repository = mock(NavigationAuditEventRepository.class);
    private final Instant now = Instant.parse("2026-10-17T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @Test
    void usesServerReceiptTimeAndABoundedBatch() {
        Instant cutoff = now.minus(Duration.ofDays(365));
        when(repository.deleteJournalSearchEventsBefore(cutoff, 5000)).thenReturn(4);
        assertThat(new JournalSearchAuditRetentionService(repository, clock, 365, 5000, true).purge()).isEqualTo(4);
        verify(repository).deleteJournalSearchEventsBefore(cutoff, 5000);
    }

    @Test
    void canBeExplicitlyDisabledWithoutChangingCollectionOrOtherAuditPolicies() {
        assertThat(new JournalSearchAuditRetentionService(repository, clock, 365, 5000, false).purge()).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    void failsFastForInvalidConfiguration() {
        assertThatThrownBy(() -> new JournalSearchAuditRetentionService(repository, clock, 0, 5000, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JournalSearchAuditRetentionService(repository, clock, 365, 0, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JournalSearchAuditRetentionService(repository, clock, 3651, 5000, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
