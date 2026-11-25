package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.configuration.dto.OperatingContextDto;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.application.system.dto.NavigationAuditBatchDto;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.system.service.*;
import com.osatum.wbn.domain.model.institution.*;
import com.osatum.wbn.domain.model.navigationaudit.*;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NavigationAuditFileDownloadIntegrationTest.AuditConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:p2-audit-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "app.navigation-audit.enabled=true"
})
@Transactional
class JournalDecisionAuditIntegrationTest {
    @Autowired private EntityManager em;
    @Autowired private NavigationAuditEventService service;
    @Autowired private NavigationAuditEventQueryService queries;
    @Autowired private NavigationAuditEventRepository repository;
    @Autowired private tools.jackson.databind.ObjectMapper mapper;
    @MockitoBean private CurrentActorProvider actor;
    @MockitoBean private OperatingProfileService profiles;
    private final ClientRequestMetadata metadata = new ClientRequestMetadata("127.0.0.1", "not-saved");

    @BeforeEach
    void context() {
        when(profiles.decisionAuditContext()).thenReturn(new OperatingContextDto(10L, null, OperatingProfile.BOTH,
                OperatingProfile.BOTH, OperatingProfile.BOTH.getAvailableFeatures(), SearchView.ADVANCED));
        when(actor.findCurrentActor()).thenReturn(Optional.of(new UserActor(null, "test@example.test", null,
                InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, 42L)));
    }

    @Test
    void storesEveryActionOnceOnTransportRetriesAndExposesTheMinimalContextInExistingRead() {
        var user = new AppUser();
        user.setEmail("p2-audit@example.test"); user.setFirstName("Test"); user.setLastName("Audit");
        user.setPasswordHash("test-hash"); user.setRoles("ROLE_USER"); em.persist(user);
        when(actor.findCurrentActor()).thenReturn(Optional.of(new UserActor(user.getId(), user.getEmail(), null,
                InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, 42L)));
        var batch = new NavigationAuditBatchDto(JournalDecisionAuditProperties.ACTIONS.stream()
                .map(code -> JournalDecisionAuditPropertiesTest.event(code, JournalDecisionAuditPropertiesTest.properties())).toList());
        service.accept(mapper.readValue(mapper.writeValueAsString(batch), NavigationAuditBatchDto.class), metadata);
        service.accept(batch, metadata);
        em.flush(); em.clear();
        var query = new NavigationAuditEventListQuery();
        query.setPageKey("institution.journals.decision");
        query.setEventType(NavigationAuditEventType.JOURNAL_DECISION_ACTION);
        query.setJournalId("123"); query.setSize(100);
        var result = queries.findEvents(query);
        assertThat(result.totalElements()).isEqualTo(14);
        assertThat(result.data()).extracting("action").containsExactlyInAnyOrderElementsOf(JournalDecisionAuditProperties.ACTIONS);
        for (var event : result.data()) {
            assertThat(event.getUserId()).isEqualTo(user.getId());
            assertThat(event.getLoginSessionId()).isEqualTo(42L);
            assertThat(event.getPropertiesJson()).contains("\"surveyYear\":2024", "\"sourceSurveyYear\":2025", "\"effectiveProfile\":\"BOTH\"");
            assertThat(event.getIpAddress()).isNull(); assertThat(event.getUserAgent()).isNull();
            assertThat(event.getRoutePath()).isEqualTo("/institution/user/journals/123/decision");
            assertThat(event.getTarget()).isEqualTo(switch (event.getAction()) {
                case "E07" -> "journal.fullData"; case "E13" -> "journal.website";
                case "E14" -> "https://journals.indexcopernicus.com/search/journal/issue?issueId=all&journalId=123";
                default -> null;
            });
        }
        assertThat(em.createQuery("from NavigationAuditEvent", NavigationAuditEvent.class).getResultList())
                .allSatisfy(event -> { assertThat(event.getRouteParamsJson()).isNull(); assertThat(event.getDurationMs()).isNull(); });
        verify(profiles, times(2)).decisionAuditContext(); // Jedno rozstrzygnięcie dla całej paczki, także po ponowieniu.
    }

    @Test
    void skipsUnknownCodeAndSensitivePayloadWithoutPoisoningFollowingValidEvents() {
        var invalid = JournalDecisionAuditPropertiesTest.properties(); invalid.put("survey", Map.of("editor", "not-saved"));
        service.accept(new NavigationAuditBatchDto(List.of(
                JournalDecisionAuditPropertiesTest.event("E15", JournalDecisionAuditPropertiesTest.properties()),
                JournalDecisionAuditPropertiesTest.event("E01", invalid),
                JournalDecisionAuditPropertiesTest.event("E03", JournalDecisionAuditPropertiesTest.properties()))), metadata);
        assertThat(queries.findEvents(new NavigationAuditEventListQuery()).data()).singleElement()
                .satisfies(event -> assertThat(event.getAction()).isEqualTo("E03"));
    }

    @Test
    void rejectsAnonymousWrongPageAndUnavailableDecisionCard() {
        var dto = JournalDecisionAuditPropertiesTest.event("E01", JournalDecisionAuditPropertiesTest.properties());
        when(actor.findCurrentActor()).thenReturn(Optional.empty());
        service.accept(new NavigationAuditBatchDto(List.of(dto)), metadata);
        verifyNoInteractions(profiles);
        assertThat(new NavigationAuditPageRegistry().isAllowed("institution.journals.list", "/institution/user/journals",
                NavigationAuditEventType.JOURNAL_DECISION_ACTION, NavigationAuditActorType.USER)).isFalse();
        context();
        when(profiles.decisionAuditContext()).thenThrow(new AccessDeniedException("Unavailable"));
        service.accept(new NavigationAuditBatchDto(List.of(dto)), metadata);
        doReturn(new OperatingContextDto(10L, null, OperatingProfile.BASIC,
                OperatingProfile.BASIC, OperatingProfile.BASIC.getAvailableFeatures(), SearchView.BASIC)).when(profiles).decisionAuditContext();
        service.accept(new NavigationAuditBatchDto(List.of(dto)), metadata);
        assertThat(queries.findEvents(new NavigationAuditEventListQuery()).totalElements()).isZero();
    }

    @Test
    void resolvesInstitutionActorOnServerAndAppliesExistingRetentionBoundaryAndLimitToP2() {
        var institution = new Institution(); institution.setName("P2 test");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT); institution.setAuthMode(AuthMode.AUTH_REQUIRED); em.persist(institution);
        when(actor.findCurrentActor()).thenReturn(Optional.of(new InstitutionActor(institution.getId(),
                InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, "P2 test", 84L)));
        service.accept(new NavigationAuditBatchDto(List.of(JournalDecisionAuditPropertiesTest.event("E01", JournalDecisionAuditPropertiesTest.properties()))), metadata);
        var saved = queries.findEvents(new NavigationAuditEventListQuery()).data().getFirst();
        assertThat(saved.getActorType()).isEqualTo(NavigationAuditActorType.INSTITUTION);
        assertThat(saved.getUserId()).isNull(); assertThat(saved.getInstitutionId()).isEqualTo(institution.getId());
        assertThat(saved.getLoginSessionId()).isEqualTo(84L);
        Instant cutoff = Instant.parse("2025-11-02T00:00:00Z");
        for (Instant time : List.of(cutoff.minusSeconds(1), cutoff.minusSeconds(1), cutoff)) {
            repository.saveIfAbsent(NavigationAuditEvent.builder().eventId(UUID.randomUUID())
                    .eventType(NavigationAuditEventType.JOURNAL_DECISION_ACTION).actorType(NavigationAuditActorType.USER)
                    .pageKey("institution.journals.decision").routeTemplate("/institution/user/journals/:id/decision")
                    .occurredAt(Instant.EPOCH).receivedAt(time).build(), null, null);
        }
        assertThat(repository.deleteJournalSearchEventsBefore(cutoff, 1)).isEqualTo(1);
        assertThat(repository.deleteJournalSearchEventsBefore(cutoff, 1)).isEqualTo(1);
        assertThat(repository.deleteJournalSearchEventsBefore(cutoff, 1)).isZero();
        assertThat(queries.findEvents(new NavigationAuditEventListQuery()).totalElements()).isEqualTo(2);
    }
}
