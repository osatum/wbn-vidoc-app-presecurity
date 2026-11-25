package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.application.shared.request.ClientRequestMetadata;
import com.osatum.wbn.application.system.dto.NavigationAuditBatchDto;
import com.osatum.wbn.application.system.dto.NavigationAuditEventDto;
import com.osatum.wbn.application.system.dto.NavigationAuditResourceDto;
import com.osatum.wbn.application.system.mapper.NavigationAuditEventMapper;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.system.service.NavigationAuditEventQueryService;
import com.osatum.wbn.application.system.service.NavigationAuditEventService;
import com.osatum.wbn.application.system.service.NavigationAuditPageRegistry;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.infrastructure.persistence.NavigationAuditEventListQueryHandlerImpl;
import com.osatum.wbn.infrastructure.persistence.NavigationAuditEventRepositoryAdapter;
import com.osatum.wbn.infrastructure.persistence.audit.JpaAuditingConfig;
import com.osatum.wbn.infrastructure.persistence.springdata.NavigationAuditEventSpringDataRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NavigationAuditFileDownloadIntegrationTest.AuditConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:file-download-audit-test;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "app.navigation-audit.enabled=true"
})
@Transactional
class NavigationAuditFileDownloadIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @EnableJpaRepositories(basePackageClasses = NavigationAuditEventSpringDataRepository.class)
    @Import({NavigationAuditEventService.class, NavigationAuditPageRegistry.class, ConfigurationService.class,
            NavigationAuditEventRepositoryAdapter.class, NavigationAuditEventQueryService.class,
            NavigationAuditEventListQueryHandlerImpl.class, NavigationAuditEventMapper.class, JpaAuditingConfig.class})
    static class AuditConfiguration {
        @Bean
        OperatingProfileService operatingProfiles() {
            return org.mockito.Mockito.mock(OperatingProfileService.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NavigationAuditEventService eventService;

    @Autowired
    private NavigationAuditEventQueryService queryService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CurrentActorProvider actorProvider;

    @Test
    void persistsDownloadsAndSearchesThemByTypeAndJournal() throws Exception {
        Institution institution = new Institution();
        institution.setName("Audit test institution");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        entityManager.persist(institution);

        AppUser user = new AppUser();
        user.setEmail("file-download@example.test");
        user.setFirstName("Audit");
        user.setLastName("Test");
        user.setPasswordHash("test-hash");
        user.setRoles("ROLE_USER");
        entityManager.persist(user);

        when(actorProvider.findCurrentActor()).thenReturn(Optional.of(new UserActor(
                user.getId(), user.getEmail(), institution.getId(), InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_USER, 42L
        )));

        NavigationAuditEventDto journalDownload = download(
                "institution.journals.details", "/institution/user/journals/:id",
                "/institution/user/journals/123?language=pl", "questionnaire.report.download",
                "survey-2024-123-pl.xls", new NavigationAuditResourceDto("journal", "123"), "xls"
        );
        NavigationAuditEventDto bulkDownload = download(
                "institution.journals.list", "/institution/user/journals",
                "/institution/user/journals", "questionnaire.report.bulk.download",
                "questionnaire-report-2024-pl.xlsx", new NavigationAuditResourceDto("questionnaire-report", "2024"), "xlsx"
        );
        NavigationAuditEventDto apiResult = new NavigationAuditEventDto(
                UUID.randomUUID(), NavigationAuditEventType.API_RESULT, Instant.now(),
                "download-session", "download-view", journalDownload.pageKey(), journalDownload.routeTemplate(),
                journalDownload.url(), Map.of(), journalDownload.resource(),
                "QuestionnaireService.generateCurrentQuestionnaireReport", null, 50, true, 200, Map.of()
        );
        NavigationAuditBatchDto batch = objectMapper.readValue(
                objectMapper.writeValueAsString(new NavigationAuditBatchDto(List.of(journalDownload, bulkDownload, apiResult))),
                NavigationAuditBatchDto.class
        );
        ClientRequestMetadata metadata = new ClientRequestMetadata("127.0.0.1", "Audit test browser");
        eventService.accept(batch, metadata);
        entityManager.flush();
        eventService.accept(batch, metadata);
        entityManager.flush();
        entityManager.clear();

        NavigationAuditEventListQuery query = new NavigationAuditEventListQuery();
        query.setEventType(NavigationAuditEventType.FILE_DOWNLOAD);
        var downloads = queryService.findEvents(query);
        assertThat(downloads.totalElements()).isEqualTo(2);
        assertThat(downloads.data()).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(NavigationAuditEventType.FILE_DOWNLOAD);
            assertThat(event.getSuccess()).isTrue();
            assertThat(event.getUserEmail()).isEqualTo("file-download@example.test");
            assertThat(event.getInstitutionName()).isEqualTo("Audit test institution");
            assertThat(event.getLoginSessionId()).isEqualTo(42L);
            assertThat(event.getIpAddress()).isEqualTo("127.0.0.1");
        });
        assertThat(downloads.data()).extracting(event -> event.getTarget())
                .containsExactlyInAnyOrder("survey-2024-123-pl.xls", "questionnaire-report-2024-pl.xlsx");

        query.setJournalId("123");
        var journalDownloads = queryService.findEvents(query);
        assertThat(journalDownloads.totalElements()).isEqualTo(1);
        assertThat(journalDownloads.data().getFirst().getAction()).isEqualTo("questionnaire.report.download");
        assertThat(journalDownloads.data().getFirst().getRoutePath()).isEqualTo("/institution/user/journals/123");

        NavigationAuditEvent stored = entityManager.createQuery(
                        "select event from NavigationAuditEvent event where event.eventId = :eventId", NavigationAuditEvent.class)
                .setParameter("eventId", journalDownload.eventId())
                .getSingleResult();
        assertThat(stored.getPropertiesJson()).contains("\"year\":2024", "\"language\":\"pl\"", "\"sizeBytes\":128");
        assertThat(stored.getFrontendSessionId()).isEqualTo("download-session");
        assertThat(stored.getViewId()).isEqualTo("download-view");
    }

    private NavigationAuditEventDto download(
            String pageKey, String routeTemplate, String url, String action,
            String filename, NavigationAuditResourceDto resource, String format
    ) {
        return new NavigationAuditEventDto(
                UUID.randomUUID(), NavigationAuditEventType.FILE_DOWNLOAD, Instant.now(),
                "download-session", "download-view", pageKey, routeTemplate, url,
                Map.of(), resource, action, filename, null, true, null,
                Map.of("year", 2024, "language", "pl", "format", format, "sizeBytes", 128)
        );
    }
}
