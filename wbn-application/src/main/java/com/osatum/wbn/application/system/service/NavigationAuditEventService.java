package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.configuration.dto.OperatingContextDto;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.application.system.dto.NavigationAuditBatchDto;
import com.osatum.wbn.application.system.dto.NavigationAuditEventDto;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.osatum.wbn.domain.model.institution.SystemFeature;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Zapisuje zdarzenia nawigacji dopuszczone przez rejestr stron, z kontekstem aktora ustalonym na serwerze.
 * Obsługuje również zdarzenia anonimowe i rozpoznaje ponowne wysłanie zdarzenia po jego identyfikatorze.
 * Rejestrowanie zdarzeń jest oddzielone od aktualizacji wizyt obsługiwanej przez NavigationAuditVisitService.
 * P2 dopuszcza tylko zwalidowany kontekst Karty i profil serwerowy; nie przechowuje IP ani User-Agent.
 * E14 oznacza kliknięcie wydań czasopisma; jego publiczny adres docelowy pochodzi z konfiguracji serwera.
 * Powtórzenie identyfikatora zdarzenia nie tworzy dodatkowego wpisu, niezależnie od ponowień transportu.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NavigationAuditEventService {

    private static final int JSON_MAX_LENGTH = 4000;

    private final NavigationAuditEventRepository repository;
    private final NavigationAuditPageRegistry pageRegistry;
    private final CurrentActorProvider currentActorProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OperatingProfileService operatingProfiles;
    private final ConfigurationService configuration;

    @Value("${app.navigation-audit.enabled:true}")
    private boolean enabled;

    /**
     * Przyjmuje paczkę zdarzeń nawigacji, uzupełnia kontekst aktora i zapisuje dozwolone zdarzenia.
     * Zdarzenia spoza rejestru stron pomija, a powtórzone identyfikatory nie tworzą kolejnych wpisów.
     * Po wyłączeniu audytu nie zapisuje paczki.
     *
     * @param batch zdarzenia zarejestrowane przez frontend
     * @param requestMetadata metadane połączenia ustalone na podstawie żądania HTTP
     */
    @Transactional
    public void accept(NavigationAuditBatchDto batch, ClientRequestMetadata requestMetadata) {
        if (!enabled) {
            return;
        }

        ActorContext actor = resolveActorOrAnonymous();
        Instant receivedAt = clock.instant();
        String ipAddress = requestMetadata.ipAddress();
        String userAgent = trimToLength(requestMetadata.userAgent(), 1024);
        OperatingContextDto decisionContext = null;
        boolean decisionContextResolved = false;

        for (NavigationAuditEventDto dto : batch.events()) {
            if (!pageRegistry.isAllowed(dto.pageKey(), dto.routeTemplate(), dto.eventType(), actor.actorType())) {
                continue;
            }

            NavigationAuditEvent event;
            try {
                if (dto.eventType() == NavigationAuditEventType.JOURNAL_DECISION_ACTION) {
                    if (!decisionContextResolved) {
                        decisionContextResolved = true;
                        try {
                            decisionContext = operatingProfiles.decisionAuditContext();
                        } catch (AccessDeniedException ignored) {
                            // Zmiana uprawnień nie może zatruć pozostałych zdarzeń w paczce.
                        }
                    }
                    if (decisionContext == null || !decisionContext.availableFeatures().contains(SystemFeature.DECISION_CARD)) continue;
                }
                event = toEntity(dto, actor, receivedAt, ipAddress, userAgent,
                        decisionContext == null ? null : decisionContext.effectiveProfile());
            } catch (JournalSearchAuditProperties.InvalidPropertiesException | JournalDecisionAuditProperties.InvalidPropertiesException exception) {
                log.warn("Rejected invalid journal analytics event {} of type {}", dto.eventId(), dto.eventType());
                continue;
            }
            repository.saveIfAbsent(event, actor.userId(), actor.institutionId());
        }
    }

    /**
     * Łączy dane zdarzenia z kontekstem aktora i odbioru; identyfikatory użytkownika i instytucji
     * są przekazywane osobno do repozytorium, a nie pobierane z danych przesłanych przez frontend.
     */
    private NavigationAuditEvent toEntity(
            NavigationAuditEventDto dto,
            ActorContext actor,
            Instant receivedAt,
            String ipAddress,
            String userAgent,
            OperatingProfile effectiveProfile
    ) {
        NavigationAuditEvent event = NavigationAuditEvent.builder()
                .eventId(dto.eventId())
                .eventType(dto.eventType())
                .occurredAt(dto.occurredAt())
                .receivedAt(receivedAt)
                .actorType(actor.actorType())
                .institutionType(actor.institutionType())
                .institutionRole(actor.institutionRole())
                .loginSessionId(actor.loginSessionId())
                .frontendSessionId(dto.sessionId())
                .viewId(dto.viewId())
                .pageKey(dto.pageKey())
                .routeTemplate(dto.routeTemplate())
                .routePath(sanitizeRoutePath(dto.url()))
                .routeParamsJson(dto.eventType().isJournalAnalyticsEvent() ? null : toJson(dto.params()))
                .resourceType(dto.resource() == null ? null : dto.resource().type())
                .resourceId(dto.resource() == null ? null : dto.resource().id())
                .action(dto.action())
                .target(dto.target())
                .durationMs(dto.durationMs())
                .success(dto.success())
                .statusCode(dto.statusCode())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .propertiesJson(dto.eventType().isJournalAnalyticsEvent() ? null : toJson(dto.properties()))
                .build();

        if (dto.eventType().isJournalSearchEvent()) {
            // P1 zachowuje komplet zwalidowanych kryteriów, ale pomija IP, User-Agent i parametry adresu.
            event.setPropertiesJson(JournalSearchAuditProperties.serialize(dto, objectMapper));
            event.setRoutePath("/institution/user/journals");
            event.setRouteParamsJson(null);
            event.setIpAddress(null);
            event.setUserAgent(null);
            event.setTarget(null);
            event.setAction(dto.eventType().name());
            if (dto.eventType() == NavigationAuditEventType.JOURNAL_ISSUES_CLICK) {
                event.setTarget(configuration.getJournalIssuesUrl(Long.valueOf(dto.resource().id())));
            } else if (dto.eventType() != NavigationAuditEventType.JOURNAL_CARD_OPEN) {
                event.setResourceType(null);
                event.setResourceId(null);
            }
        }

        if (dto.eventType() == NavigationAuditEventType.JOURNAL_DECISION_ACTION) {
            // Zasób i cel ustala serwer, aby nie zapisywać dowolnych adresów klienta ani treści ankiety.
            event.setPropertiesJson(JournalDecisionAuditProperties.serialize(dto, objectMapper, effectiveProfile));
            event.setRoutePath("/institution/user/journals/" + dto.resource().id() + "/decision");
            event.setIpAddress(null);
            event.setUserAgent(null);
            event.setTarget(switch (dto.action()) {
                case "E07" -> "journal.fullData";
                case "E13" -> "journal.website";
                case "E14" -> configuration.getJournalIssuesUrl(Long.valueOf(dto.resource().id()));
                default -> null;
            });
            event.setDurationMs(null);
            event.setSuccess(null);
            event.setStatusCode(null);
        }

        return event;
    }

    /**
     * Odczytuje kontekst użytkownika lub instytucji z uwierzytelnienia, a przy jego braku zwraca aktora anonimowego.
     */
    private ActorContext resolveActorOrAnonymous() {
        var actor = currentActorProvider.findCurrentActor().orElse(null);
        if (actor instanceof UserActor userActor) {
            return new ActorContext(
                    NavigationAuditActorType.USER,
                    userActor.userId(),
                    userActor.institutionId(),
                    userActor.institutionType(),
                    userActor.institutionRole(),
                    userActor.loginSessionId()
            );
        }

        if (actor instanceof InstitutionActor institutionActor) {
            return new ActorContext(
                    NavigationAuditActorType.INSTITUTION,
                    null,
                    institutionActor.institutionId(),
                    institutionActor.institutionType(),
                    institutionActor.institutionRole(),
                    institutionActor.loginSessionId()
            );
        }

        return new ActorContext(
                NavigationAuditActorType.ANONYMOUS,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Ogranicza długość ścieżki i usuwa część po znaku zapytania oraz fragmencie,
     * aby nie utrwalać parametrów URL, które mogą zawierać dane wrażliwe.
     */
    private String sanitizeRoutePath(String url) {
        String value = trimToLength(url, 500);
        if (value == null) {
            return null;
        }

        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }

        int hashIndex = value.indexOf('#');
        if (hashIndex >= 0) {
            value = value.substring(0, hashIndex);
        }

        return trimToLength(value, 500);
    }

    /**
     * Serializuje opcjonalne metadane z limitem długości; brak danych lub błąd serializacji daje null.
     */
    private String toJson(Map<?, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            return trimToLength(objectMapper.writeValueAsString(value), JSON_MAX_LENGTH);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        return trimmed.substring(0, maxLength);
    }

    private record ActorContext(
            NavigationAuditActorType actorType,
            Long userId,
            Long institutionId,
            InstitutionType institutionType,
            InstitutionRole institutionRole,
            Long loginSessionId
    ) {
    }
}
