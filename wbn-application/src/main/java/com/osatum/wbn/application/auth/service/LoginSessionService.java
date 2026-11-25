package com.osatum.wbn.application.auth.service;

import com.osatum.wbn.application.auth.dto.LoginSessionViewDto;
import com.osatum.wbn.application.auth.query.LoginSessionListQuery;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.application.system.service.NavigationAuditVisitService;
import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionEndReason;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.auth.LoginSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final LoginSessionRepository repository;
    private final LoginSessionListQueryHandler queryHandler;
    private final Clock clock;
    private final NavigationAuditVisitService visitService;

    /**
     * Tworzy aktywną sesję logowania użytkownika w kontekście wskazanej instytucji i roli.
     */
    @Transactional
    public LoginSession startUserSession(
            AppUser user,
            Institution institution,
            InstitutionRole institutionRole,
            ClientRequestMetadata requestMetadata,
            Instant expiresAt
    ) {
        var now = clock.instant();
        var session = LoginSession.builder()
                .actorType(LoginActorType.USER)
                .user(user)
                .institution(institution)
                .userEmail(user.getEmail())
                .institutionName(institution.getName())
                .institutionType(institution.getInstitutionType())
                .institutionRole(institutionRole)
                .ipAddress(requestMetadata.ipAddress())
                .userAgent(requestMetadata.userAgent())
                .browser(resolveBrowser(requestMetadata.userAgent()))
                .startedAt(now)
                .lastSeenAt(now)
                .expiresAt(expiresAt)
                .status(LoginSessionStatus.ACTIVE)
                .build();

        var saved = repository.save(session);
        visitService.start(saved);
        return saved;
    }

    /**
     * Tworzy aktywną sesję logowania instytucji autoryzowanej bez konta użytkownika.
     */
    @Transactional
    public LoginSession startInstitutionSession(
            Institution institution,
            InstitutionRole institutionRole,
            ClientRequestMetadata requestMetadata,
            Instant expiresAt
    ) {
        var now = clock.instant();
        var session = LoginSession.builder()
                .actorType(LoginActorType.INSTITUTION)
                .institution(institution)
                .institutionName(institution.getName())
                .institutionType(institution.getInstitutionType())
                .institutionRole(institutionRole)
                .ipAddress(requestMetadata.ipAddress())
                .userAgent(requestMetadata.userAgent())
                .browser(resolveBrowser(requestMetadata.userAgent()))
                .startedAt(now)
                .lastSeenAt(now)
                .expiresAt(expiresAt)
                .status(LoginSessionStatus.ACTIVE)
                .build();

        var saved = repository.save(session);
        visitService.start(saved);
        return saved;
    }

    /**
     * Aktualizuje czas ostatniej aktywności oraz termin wygaśnięcia wskazanej sesji.
     */
    @Transactional
    public void touch(LoginSession session, Instant expiresAt) {
        if (session == null) {
            return;
        }

        session.setLastSeenAt(clock.instant());
        session.setExpiresAt(expiresAt);
        repository.save(session);
    }

    /**
     * Aktualizuje czas aktywności sesji tylko wtedy, gdy minął wymagany minimalny odstęp.
     */
    @Transactional
    public void touchIfDue(Long sessionId, Duration minInterval) {
        if (sessionId == null) {
            return;
        }

        repository.findById(sessionId).ifPresent(session -> {
            var now = clock.instant();
            if (session.getEndedAt() != null || session.getStatus() != LoginSessionStatus.ACTIVE || !session.getExpiresAt().isAfter(now)) {
                return;
            }

            var lastSeenAt = session.getLastSeenAt();
            if (lastSeenAt != null && lastSeenAt.plus(minInterval).isAfter(now)) {
                return;
            }

            session.setLastSeenAt(now);
            repository.save(session);
        });
    }

    /**
     * Kończy sesję logowania z podanym powodem zakończenia.
     */
    @Transactional
    public void finish(LoginSession session, LoginSessionEndReason reason) {
        if (session == null || session.getEndedAt() != null) {
            return;
        }

        visitService.finish(session.getId(), reason.name());
        session.setEndedAt(clock.instant());
        session.setEndReason(reason);
        session.setStatus(statusFor(reason));
        repository.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long sessionId) {
        if (sessionId == null) {
            return false;
        }
        return repository.findById(sessionId)
                .filter(session -> session.getStatus() == LoginSessionStatus.ACTIVE)
                .filter(session -> session.getEndedAt() == null)
                .filter(session -> session.getExpiresAt().isAfter(clock.instant()))
                .isPresent();
    }

    @Transactional
    public void revoke(Long sessionId) {
        repository.findById(sessionId).ifPresent(session -> finish(session, LoginSessionEndReason.REVOKED));
    }

    /**
     * Wyszukuje aktywne sesje po terminie ważności i oznacza je jako wygasłe.
     */
    @Transactional
    public void markExpiredSessions() {
        var now = clock.instant();
        repository.findExpiredActive(now).forEach(session -> {
            visitService.finish(session.getId(), LoginSessionEndReason.EXPIRED.name());
            session.setEndedAt(session.getExpiresAt());
            session.setEndReason(LoginSessionEndReason.EXPIRED);
            session.setStatus(LoginSessionStatus.EXPIRED);
            repository.save(session);
        });
    }

    /**
     * Zwraca stronicowaną listę wszystkich sesji logowania zgodnie z przekazanymi filtrami.
     */
    @Transactional(readOnly = true)
    public PageResult<LoginSessionViewDto> getLoginSessions(LoginSessionListQuery query) {
        var allSessionsQuery = normalize(query);
        allSessionsQuery.setActiveOnly(false);

        return queryHandler.execute(allSessionsQuery);
    }

    /**
     * Zwraca stronicowaną listę sesji logowania przypisanych do wskazanego użytkownika.
     */
    @Transactional(readOnly = true)
    public PageResult<LoginSessionViewDto> getLoginSessionsForUser(Long id, LoginSessionListQuery query) {
        query.setUserId(id);
        var allSessionsQuery = normalize(query);
        allSessionsQuery.setActiveOnly(false);

        return queryHandler.execute(allSessionsQuery);
    }


    /**
     * Zwraca stronicowaną listę sesji logowania powiązanych ze wskazaną instytucją.
     */
    @Transactional(readOnly = true)
    public PageResult<LoginSessionViewDto> getLoginSessionsForInstitution(Long id, LoginSessionListQuery query) {
        query.setInstitutionId(id);
        var allSessionsQuery = normalize(query);
        allSessionsQuery.setActiveOnly(false);

        return queryHandler.execute(allSessionsQuery);
    }


    /**
     * Oznacza wygasłe sesje i zwraca stronicowaną listę nadal aktywnych sesji logowania.
     */
    @Transactional
    public PageResult<LoginSessionViewDto> getActiveSessions(LoginSessionListQuery query) {
        markExpiredSessions();

        var activeQuery = normalize(query);
        activeQuery.setActiveOnly(true);
        activeQuery.setStatus(null);
        activeQuery.setEndReason(null);

        return queryHandler.execute(activeQuery);
    }

    private static LoginSessionListQuery normalize(LoginSessionListQuery query) {
        var normalized = query == null ? new LoginSessionListQuery() : query;

        if (normalized.getSize() < 1) {
            normalized.setSize(DEFAULT_PAGE_SIZE);
        }

        if (normalized.getPage() < 0) {
            normalized.setPage(0);
        }

        return normalized;
    }

    private static LoginSessionStatus statusFor(LoginSessionEndReason reason) {
        return switch (reason) {
            case LOGOUT -> LoginSessionStatus.LOGGED_OUT;
            case EXPIRED -> LoginSessionStatus.EXPIRED;
            case REVOKED -> LoginSessionStatus.REVOKED;
        };
    }

    private static String resolveBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        var normalized = userAgent.toLowerCase();
        if (normalized.contains("edg/")) {
            return "Microsoft Edge";
        }
        if (normalized.contains("opr/") || normalized.contains("opera")) {
            return "Opera";
        }
        if (normalized.contains("firefox/")) {
            return "Firefox";
        }
        if (normalized.contains("chrome/") || normalized.contains("crios/")) {
            return "Chrome";
        }
        if (normalized.contains("safari/")) {
            return "Safari";
        }

        return "Other";
    }
}
