package com.osatum.wbn.application.auth.service;

import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import com.osatum.wbn.application.system.service.NavigationAuditVisitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-06-14T10:00:00Z");
    private static final Duration THROTTLE = Duration.ofSeconds(60);

    private LoginSessionRepository repository;
    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(LoginSessionRepository.class);
        var queryHandler = mock(LoginSessionListQueryHandler.class);
        service = new LoginSessionService(repository, queryHandler, Clock.fixed(NOW, ZoneOffset.UTC), mock(NavigationAuditVisitService.class));
        when(repository.save(any(LoginSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatesLastSeenAtWhenThrottleElapsed() {
        LoginSession session = activeSession(NOW.minusSeconds(61));
        when(repository.findById(1L)).thenReturn(Optional.of(session));

        service.touchIfDue(1L, THROTTLE);

        assertThat(session.getLastSeenAt()).isEqualTo(NOW);
        verify(repository).save(session);
    }

    @Test
    void doesNotUpdateLastSeenAtWhenThrottleHasNotElapsed() {
        LoginSession session = activeSession(NOW.minusSeconds(30));
        when(repository.findById(1L)).thenReturn(Optional.of(session));

        service.touchIfDue(1L, THROTTLE);

        assertThat(session.getLastSeenAt()).isEqualTo(NOW.minusSeconds(30));
        verify(repository, never()).save(session);
    }

    @Test
    void doesNotUpdateEndedSession() {
        LoginSession session = activeSession(NOW.minusSeconds(120));
        session.setEndedAt(NOW.minusSeconds(1));
        when(repository.findById(1L)).thenReturn(Optional.of(session));

        service.touchIfDue(1L, THROTTLE);

        assertThat(session.getLastSeenAt()).isEqualTo(NOW.minusSeconds(120));
        verify(repository, never()).save(session);
    }

    private LoginSession activeSession(Instant lastSeenAt) {
        LoginSession session = new LoginSession();
        session.setId(1L);
        session.setStatus(LoginSessionStatus.ACTIVE);
        session.setLastSeenAt(lastSeenAt);
        session.setExpiresAt(NOW.plusSeconds(300));
        return session;
    }
}
