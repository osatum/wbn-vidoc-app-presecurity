package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.application.system.dto.NavigationAuditBatchDto;
import com.osatum.wbn.application.system.dto.NavigationAuditEventDto;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.system.service.NavigationAuditEventQueryService;
import com.osatum.wbn.application.system.service.NavigationAuditEventService;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NavigationAuditFileDownloadIntegrationTest.AuditConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:p1-audit-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "app.navigation-audit.enabled=true"
})
@Transactional
class JournalSearchAuditIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private NavigationAuditEventService service;
    @Autowired private NavigationAuditEventQueryService queries;
    @Autowired private NavigationAuditEventRepository repository;
    @MockitoBean private CurrentActorProvider actorProvider;

    @Test
    void deduplicatesBatchRetriesWithoutLosingFollowingEventsAndExposesCompleteCriteria() {
        AppUser user = new AppUser();
        user.setEmail("audit-p1@example.test");
        user.setFirstName("Audit");
        user.setLastName("Test");
        user.setPasswordHash("test-only-hash");
        user.setRoles("ROLE_USER");
        entityManager.persist(user);
        when(actorProvider.findCurrentActor()).thenReturn(Optional.of(new UserActor(user.getId(), user.getEmail(), null,
                InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, 42L)));
        var properties = JournalSearchAuditPropertiesTest.properties(Map.of("journalTitle", "Bibliographic title ".repeat(300), "publicationFeeMaxPln", 0));
        var search = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.JOURNAL_SEARCH, properties, null);
        var card = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.JOURNAL_CARD_OPEN, properties, null);
        var metadata = new ClientRequestMetadata("127.0.0.1", "must-not-be-copied");
        service.accept(new NavigationAuditBatchDto(List.of(search, search, card)), metadata);
        service.accept(new NavigationAuditBatchDto(List.of(search, card)), metadata);
        entityManager.flush();
        entityManager.clear();
        var query = new NavigationAuditEventListQuery();
        query.setEventType(NavigationAuditEventType.JOURNAL_SEARCH);
        var results = queries.findEvents(query);
        assertThat(results.totalElements()).isEqualTo(1);
        var saved = results.data().getFirst();
        assertThat(saved.getActorType()).isEqualTo(NavigationAuditActorType.USER);
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getLoginSessionId()).isEqualTo(42L);
        assertThat(saved.getPropertiesJson()).hasSizeGreaterThan(4000).contains("publicationFeeMaxPln");
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getUserAgent()).isNull();
        assertThat(saved.getTarget()).isNull();
        assertThat(saved.getRoutePath()).isEqualTo("/institution/user/journals");
        assertThat(saved.getResourceId()).isNull();
        query.setEventType(NavigationAuditEventType.JOURNAL_CARD_OPEN);
        query.setJournalId("28032");
        assertThat(queries.findEvents(query).totalElements()).isEqualTo(1);
        var stored = entityManager.createQuery("from NavigationAuditEvent where eventId = :id", NavigationAuditEvent.class)
                .setParameter("id", search.eventId()).getSingleResult();
        assertThat(stored.getRouteParamsJson()).isNull();
    }

    @Test
    void storesIssuesClickOnceWithServerTargetAndAppliedCriteriaSeparatelyFromP2() {
        when(actorProvider.findCurrentActor()).thenReturn(Optional.of(new UserActor(null, "test@example.test", null,
                InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, 42L)));
        var click = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.JOURNAL_ISSUES_CLICK,
                JournalSearchAuditPropertiesTest.properties(Map.of("journalTitle", "Applied title")), null);
        service.accept(new NavigationAuditBatchDto(List.of(click, click)), new ClientRequestMetadata("127.0.0.1", "not-saved"));
        entityManager.flush();
        var query = new NavigationAuditEventListQuery();
        query.setEventType(NavigationAuditEventType.JOURNAL_ISSUES_CLICK);
        var results = queries.findEvents(query);
        assertThat(results.totalElements()).isEqualTo(1);
        var saved = results.data().getFirst();
        assertThat(saved.getResourceId()).isEqualTo("28032");
        assertThat(saved.getPageKey()).isEqualTo("institution.journals.list");
        assertThat(saved.getAction()).isEqualTo("JOURNAL_ISSUES_CLICK");
        assertThat(saved.getTarget()).isEqualTo("https://journals.indexcopernicus.com/search/journal/issue?issueId=all&journalId=28032");
        assertThat(saved.getPropertiesJson()).contains("Applied title");
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getUserAgent()).isNull();
        query.setEventType(NavigationAuditEventType.JOURNAL_DECISION_ACTION);
        assertThat(queries.findEvents(query).totalElements()).isZero();
    }

    @Test
    void usesTheServerInstitutionAndLoginSessionForIpActors() {
        var institution = new Institution();
        institution.setName("P1 test institution");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        entityManager.persist(institution);
        when(actorProvider.findCurrentActor()).thenReturn(Optional.of(new InstitutionActor(institution.getId(),
                InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, "P1 test institution", 84L)));
        var search = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.JOURNAL_SEARCH,
                JournalSearchAuditPropertiesTest.properties(Map.of()), null);
        service.accept(new NavigationAuditBatchDto(List.of(search)), new ClientRequestMetadata("127.0.0.1", "not-saved"));
        var event = queries.findEvents(new NavigationAuditEventListQuery()).data().getFirst();
        assertThat(event.getActorType()).isEqualTo(NavigationAuditActorType.INSTITUTION);
        assertThat(event.getInstitutionId()).isEqualTo(institution.getId());
        assertThat(event.getUserId()).isNull();
        assertThat(event.getLoginSessionId()).isEqualTo(84L);
        assertThat(event.getIpAddress()).isNull();
    }

    @Test
    void neverSavesAnonymousOrWrongRouteP1Events() {
        when(actorProvider.findCurrentActor()).thenReturn(Optional.empty());
        var search = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.JOURNAL_SEARCH,
                JournalSearchAuditPropertiesTest.properties(Map.of()), null);
        service.accept(new NavigationAuditBatchDto(List.of(search)), new ClientRequestMetadata("127.0.0.1", "test"));
        assertThat(queries.findEvents(new NavigationAuditEventListQuery()).totalElements()).isZero();
    }

    @Test
    void retentionDeletesOnlyExpiredP1RowsWithAStrictServerTimeBoundaryAndBatchLimit() {
        Instant now = Instant.parse("2026-10-17T10:00:00Z");
        Instant cutoff = now.minus(Duration.ofDays(365));
        for (int index = 0; index < 3; index++) {
            repository.saveIfAbsent(stored(NavigationAuditEventType.JOURNAL_SEARCH, cutoff.minusSeconds(1)), null, null);
        }
        repository.saveIfAbsent(stored(NavigationAuditEventType.JOURNAL_CARD_OPEN, cutoff), null, null);
        repository.saveIfAbsent(stored(NavigationAuditEventType.JOURNAL_SEARCH_RESULT, now), null, null);
        repository.saveIfAbsent(stored(NavigationAuditEventType.FILE_DOWNLOAD, cutoff.minusSeconds(1)), null, null);
        entityManager.flush();
        assertThat(repository.deleteJournalSearchEventsBefore(cutoff, 2)).isEqualTo(2);
        assertThat(repository.deleteJournalSearchEventsBefore(cutoff, 2)).isEqualTo(1);
        assertThat(repository.deleteJournalSearchEventsBefore(cutoff, 2)).isZero();
        entityManager.clear();
        assertThat(queries.findEvents(new NavigationAuditEventListQuery()).totalElements()).isEqualTo(3);
    }

    @Test
    void rejectsAnInvalidP1RecordWithoutTruncationOrPoisoningOtherAuditEventsInTheBatch() {
        when(actorProvider.findCurrentActor()).thenReturn(Optional.of(new UserActor(null, "test@example.test", null,
                InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, 42L)));
        NavigationAuditEventDto invalid = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.JOURNAL_SEARCH,
                JournalSearchAuditPropertiesTest.properties(Map.of("journalTitle", "ą".repeat(65536))), null);
        NavigationAuditEventDto valid = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.JOURNAL_SEARCH,
                JournalSearchAuditPropertiesTest.properties(Map.of("countryCodes", List.of("pl"))), null);
        NavigationAuditEventDto file = JournalSearchAuditPropertiesTest.event(NavigationAuditEventType.FILE_DOWNLOAD, Map.of("year", 2024), true);
        service.accept(new NavigationAuditBatchDto(List.of(invalid, valid, file)), new ClientRequestMetadata("127.0.0.1", "test"));
        entityManager.flush();
        var query = new NavigationAuditEventListQuery();
        assertThat(queries.findEvents(query).totalElements()).isEqualTo(2);
        query.setEventType(NavigationAuditEventType.JOURNAL_SEARCH);
        assertThat(queries.findEvents(query).data().getFirst().getPropertiesJson()).contains("countryCodes").doesNotContain("ą");
        query.setEventType(NavigationAuditEventType.FILE_DOWNLOAD);
        assertThat(queries.findEvents(query).totalElements()).isEqualTo(1);
    }

    private NavigationAuditEvent stored(NavigationAuditEventType type, Instant receivedAt) {
        return NavigationAuditEvent.builder().eventId(UUID.randomUUID()).eventType(type)
                .receivedAt(receivedAt).occurredAt(Instant.parse("1990-01-01T00:00:00Z"))
                .actorType(NavigationAuditActorType.USER).pageKey("institution.journals.list")
                .routeTemplate("/institution/user/journals").propertiesJson("{}").build();
    }
}
