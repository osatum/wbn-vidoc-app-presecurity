package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.system.dto.NavigationVisitActivityDto;
import com.osatum.wbn.domain.model.auth.*;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditVisit;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditVisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigationAuditVisitServiceTest {
    private final Instant now = Instant.parse("2026-10-03T10:00:00Z");
    private NavigationAuditVisitRepository repository;
    private NavigationAuditVisitService service;
    private LoginSession session;
    private NavigationAuditVisit visit;

    @BeforeEach
    void setup() {
        repository = mock(NavigationAuditVisitRepository.class);
        var actor = mock(CurrentActorProvider.class);
        when(actor.currentActor()).thenReturn(new UserActor(1L, "test@example.test", null, null, null, 7L));
        service = new NavigationAuditVisitService(repository, actor, Clock.fixed(now, ZoneOffset.UTC));
        session = LoginSession.builder().actorType(LoginActorType.USER).startedAt(now.minusSeconds(3600))
                .expiresAt(now.plusSeconds(86400)).status(LoginSessionStatus.ACTIVE).build();
        session.setId(7L);
        when(repository.lockSession(7L)).thenReturn(Optional.of(session));
        visit = new NavigationAuditVisit();
        visit.setId(1L);
        visit.setLoginSessionId(7L);
        visit.setStartedAt(now.minusSeconds(3600));
        visit.setLastActivityAt(now.minusSeconds(1799));
        when(repository.findLatest(7L)).thenReturn(Optional.of(visit));
        when(repository.findOpen(7L)).thenReturn(Optional.of(visit));
    }

    @Test
    void activityBeforeThirtyMinutesUpdatesSameVisit() {
        service.acceptActivity(activity(now));
        assertThat(visit.getLastActivityAt()).isEqualTo(now);
        assertThat(visit.getEndedAt()).isNull();
        verify(repository).save(visit);
    }

    @Test
    void exactlyThirtyMinutesStartsNewVisitWithoutAddingIdleTime() {
        visit.setLastActivityAt(now.minusSeconds(1800));
        service.acceptActivity(activity(now));
        assertThat(visit.getEndedAt()).isEqualTo(now.minusSeconds(1800));
        var saved = ArgumentCaptor.forClass(NavigationAuditVisit.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().getLast().getStartedAt()).isEqualTo(now);
        assertThat(saved.getAllValues().getLast().getLastActivityAt()).isEqualTo(now);
    }

    @Test
    void delayedActivityCanReopenVisitClosedByScanner() {
        visit.setLastActivityAt(now.minusSeconds(1800));
        visit.setEndedAt(visit.getLastActivityAt());
        visit.setEndReason("INACTIVITY");
        service.acceptActivity(activity(now.minusSeconds(5)));
        assertThat(visit.getEndedAt()).isNull();
        assertThat(visit.getEndReason()).isNull();
        assertThat(visit.getLastActivityAt()).isEqualTo(now.minusSeconds(5));
    }

    @Test
    void repeatedEventDoesNotChangeVisit() {
        var event = activity(now);
        when(repository.hasActivity(7L, event.eventId())).thenReturn(true);
        service.acceptActivity(event);
        verify(repository, never()).save(any());
        verify(repository, never()).recordActivity(any(), any(), any());
    }

    @Test
    void outOfOrderActivityDoesNotMoveLastActivityBackwards() {
        visit.setLastActivityAt(now);
        service.acceptActivity(activity(now.minusSeconds(10)));
        assertThat(visit.getLastActivityAt()).isEqualTo(now);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsOldAndFutureEventsAndExpiredSession() {
        assertThatThrownBy(() -> service.acceptActivity(activity(now.minusSeconds(91)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.acceptActivity(activity(now.plusSeconds(31)))).isInstanceOf(IllegalArgumentException.class);
        session.setExpiresAt(now);
        assertThatThrownBy(() -> service.acceptActivity(activity(now))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scanEndsVisitAtLastActivityNotScanTime() {
        visit.setLastActivityAt(now.minusSeconds(1800));
        when(repository.findInactiveSessionIds(now.minusSeconds(1800), 500)).thenReturn(List.of(7L));
        service.closeInactiveVisits();
        assertThat(visit.getEndedAt()).isEqualTo(visit.getLastActivityAt());
        assertThat(visit.getEndReason()).isEqualTo("INACTIVITY");
    }

    @Test
    void logoutEndsVisitWithoutExtendingDuration() {
        service.finish(7L, "LOGOUT");
        assertThat(visit.getEndedAt()).isEqualTo(visit.getLastActivityAt());
        assertThat(visit.getEndReason()).isEqualTo("LOGOUT");
    }

    @Test
    void loginCreatesZeroDurationVisit() {
        service.start(session);
        var saved = ArgumentCaptor.forClass(NavigationAuditVisit.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLastActivityAt()).isEqualTo(saved.getValue().getStartedAt());
    }

    private NavigationVisitActivityDto activity(Instant time) {
        return new NavigationVisitActivityDto(UUID.randomUUID(), time);
    }
}
