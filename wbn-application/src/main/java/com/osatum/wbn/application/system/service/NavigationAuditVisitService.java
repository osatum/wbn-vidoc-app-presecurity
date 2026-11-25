package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.system.dto.NavigationVisitActivityDto;
import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditVisit;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Zarządza wizytami audytowymi niezależnie od ważności sesji logowania.
 * Przerwa w aktywności wynosząca co najmniej skonfigurowany limit (domyślnie 30 minut) rozdziela wizyty.
 * Koniec wizyty przypada na ostatnią odnotowaną aktywność, a nie na moment wykrycia bezczynności.
 * Zamknięcie wizyty nie wylogowuje aktora ani nie skraca ważności jego sesji logowania.
 */
@Service
@RequiredArgsConstructor
public class NavigationAuditVisitService {
    private final NavigationAuditVisitRepository repository;
    private final CurrentActorProvider currentActorProvider;
    private final Clock clock;

    @Value("${app.navigation-audit.enabled:true}")
    private boolean enabled = true;

    @Value("${app.navigation-audit.visits.idle-minutes:30}")
    private long idleMinutes = 30;

    /**
     * Rozpoczyna pierwszą wizytę w chwili rozpoczęcia sesji logowania, jeśli audyt jest włączony.
     * Początek wizyty jest zarazem jej pierwszą aktywnością, więc początkowy czas trwania wynosi zero.
     */
    @Transactional
    public void start(LoginSession session) {
        if (enabled) {
            createVisit(session, session.getStartedAt());
        }
    }

    /**
     * Przyjmuje aktywność bieżącego aktora, sprawdzając ważność sesji i powtórzenia identyfikatora zdarzenia.
     * Dopuszcza znacznik czasu do 90 sekund wstecz i do 30 sekund naprzód względem zegara serwera.
     * Aktualizuje ostatnią aktywność wizyty albo rozpoczyna nową po przerwie osiągającej limit bezczynności.
     *
     * @param activity identyfikator i czas aktywności zarejestrowanej przez frontend
     * @throws IllegalArgumentException gdy brakuje ważnej sesji lub czas aktywności wykracza poza dozwolone okno
     */
    @Transactional
    public void acceptActivity(NavigationVisitActivityDto activity) {
        if (!enabled) {
            return;
        }
        var actor = currentActorProvider.currentActor();
        Long sessionId = actor.loginSessionId();
        if (sessionId == null) {
            throw new IllegalArgumentException("An active login session is required.");
        }
        // Blokada sesji porządkuje równoległe zgłoszenia aktywności oraz zamykanie wizyt przez skaner.
        var session = repository.lockSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("An active login session is required."));
        var now = clock.instant();
        if (session.getStatus() != LoginSessionStatus.ACTIVE || session.getEndedAt() != null
                || !session.getExpiresAt().isAfter(now)) {
            throw new IllegalArgumentException("An active login session is required.");
        }
        if (repository.hasActivity(sessionId, activity.eventId())) {
            return;
        }
        if (activity.occurredAt().isBefore(now.minusSeconds(90))
                || activity.occurredAt().isAfter(now.plusSeconds(30))) {
            throw new IllegalArgumentException("Activity timestamp is outside the allowed time window.");
        }
        // Tolerancja zegara klienta nie może przesunąć aktywności w przyszłość ani przed początek logowania.
        var occurredAt = activity.occurredAt().isAfter(now) ? now : activity.occurredAt();
        if (occurredAt.isBefore(session.getStartedAt())) {
            occurredAt = session.getStartedAt();
        }
        repository.recordActivity(sessionId, activity.eventId(), now);
        var visit = repository.findLatest(sessionId).orElse(null);
        if (visit != null && occurredAt.isBefore(visit.getStartedAt())) {
            return;
        }
        if (visit != null && !visit.getLastActivityAt().plus(idleTimeout()).isAfter(occurredAt)) {
            if (visit.getEndedAt() == null) {
                close(visit, "INACTIVITY");
            }
            visit = null;
        }
        if (visit == null) {
            createVisit(session, occurredAt);
        } else if (occurredAt.isAfter(visit.getLastActivityAt())) {
            if (visit.getEndedAt() != null && !"INACTIVITY".equals(visit.getEndReason())) {
                return;
            }
            // Opóźnione zgłoszenie może cofnąć zamknięcie przez skaner, jeśli faktyczna przerwa była krótsza od limitu.
            visit.setEndedAt(null);
            visit.setEndReason(null);
            visit.setLastActivityAt(occurredAt);
            repository.save(visit);
        }
    }

    /**
     * Zamyka otwartą wizytę danej sesji z przekazaną przyczyną, np. podczas wylogowania.
     * Brak identyfikatora sesji lub otwartej wizyty nie powoduje utworzenia nowego wpisu.
     */
    @Transactional
    public void finish(Long sessionId, String reason) {
        if (sessionId != null) {
            repository.lockSession(sessionId).ifPresent(session ->
                    repository.findOpen(sessionId).ifPresent(visit -> close(visit, reason)));
        }
    }

    /**
     * Okresowo zamyka wizyty bez aktywności przez skonfigurowany czas, przetwarzając najwyżej 500 sesji na przebieg.
     * Po zablokowaniu sesji ponownie sprawdza bezczynność, aby nie zamknąć wizyty właśnie zaktualizowanej.
     * Usuwa też starsze niż dwa dni wpisy używane do rozpoznawania powtórzonych zgłoszeń aktywności.
     */
    @Scheduled(fixedDelayString = "${app.navigation-audit.visits.scan-delay-ms:60000}")
    @Transactional
    public void closeInactiveVisits() {
        if (!enabled) {
            return;
        }
        var now = clock.instant();
        var cutoff = now.minus(idleTimeout());
        for (Long sessionId : repository.findInactiveSessionIds(cutoff, 500)) {
            repository.lockSession(sessionId).ifPresent(session ->
                    repository.findOpen(sessionId)
                            .filter(visit -> !visit.getLastActivityAt().isAfter(cutoff))
                            .ifPresent(visit -> close(visit, "INACTIVITY")));
        }
        repository.deleteActivitiesBefore(now.minus(Duration.ofDays(2)));
    }

    /**
     * Tworzy wizytę powiązaną z sesją logowania, użytkownikiem i instytucją ustalonymi po stronie serwera.
     */
    private void createVisit(LoginSession session, Instant startedAt) {
        var visit = new NavigationAuditVisit();
        visit.setLoginSessionId(session.getId());
        visit.setActorType(session.getActorType() == LoginActorType.USER
                ? NavigationAuditActorType.USER : NavigationAuditActorType.INSTITUTION);
        visit.setUser(session.getUser());
        visit.setInstitution(session.getInstitution());
        visit.setStartedAt(startedAt);
        visit.setLastActivityAt(startedAt);
        repository.save(visit);
    }

    /**
     * Kończy wizytę na ostatniej aktywności, aby czas bezczynności nie powiększał jej raportowanego czasu trwania.
     */
    private void close(NavigationAuditVisit visit, String reason) {
        visit.setEndedAt(visit.getLastActivityAt());
        visit.setEndReason(reason);
        repository.save(visit);
    }

    private Duration idleTimeout() {
        if (idleMinutes < 1) {
            throw new IllegalArgumentException("Visit idle timeout must be positive.");
        }
        return Duration.ofMinutes(idleMinutes);
    }
}
