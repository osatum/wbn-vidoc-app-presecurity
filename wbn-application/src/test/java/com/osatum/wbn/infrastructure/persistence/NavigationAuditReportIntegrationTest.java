package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.system.dto.*;
import com.osatum.wbn.application.system.service.NavigationAuditVisitService;
import com.osatum.wbn.application.system.service.NavigationAuditReportService;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportXlsxGenerator;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportXlsGenerator;
import com.osatum.wbn.infrastructure.report.NavigationAuditReportGeneratorRouter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import java.nio.file.Files;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.domain.model.auth.*;
import com.osatum.wbn.domain.model.institution.*;
import com.osatum.wbn.domain.model.navigationaudit.*;
import com.osatum.wbn.domain.model.user.AppUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NavigationAuditReportIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=${audit.test.jdbc-url:jdbc:h2:mem:report-query;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE}",
        "spring.datasource.username=${audit.test.username:sa}",
        "spring.datasource.password=${audit.test.password:}",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class NavigationAuditReportIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @Import({NavigationAuditReportQueryAdapter.class, NavigationAuditVisitRepositoryAdapter.class,
            UserPersonalDataDeletionRepositoryAdapter.class, InstitutionDeletionRepositoryAdapter.class,
            NavigationAuditReportService.class, NavigationAuditReportXlsxGenerator.class,
            NavigationAuditReportXlsGenerator.class, NavigationAuditReportGeneratorRouter.class})
    static class Configuration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
    @Autowired EntityManager entityManager;
    @Autowired NavigationAuditReportQueryAdapter query;
    @Autowired NavigationAuditVisitRepositoryAdapter visits;
    @Autowired UserPersonalDataDeletionRepositoryAdapter privacy;
    @Autowired InstitutionDeletionRepositoryAdapter deletion;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired NavigationAuditReportService reportService;
    private final Instant now = Instant.parse("2026-10-03T10:00:00Z");

    @Test
    void exportsDatabaseDataForAllFourReportTypes() throws Exception {
        visit(session());
        event(NavigationAuditEventType.PAGE_VIEW, NavigationAuditActorType.USER, null, null);
        event(NavigationAuditEventType.FILE_DOWNLOAD, NavigationAuditActorType.USER, "file.xlsx", true);
        event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.ANONYMOUS, null, null);
        entityManager.flush();
        for (var type : NavigationAuditReportType.values()) {
            var date = java.time.LocalDate.parse("2026-10-03");
            try (var report = reportService.export(new NavigationAuditReportRequest(date, date, type));
                 var input = Files.newInputStream(report.path()); var workbook = WorkbookFactory.create(input)) {
                var sheet = workbook.getSheet("Raport");
                assertThat(sheet.getLastRowNum()).isEqualTo(1);
                if (type == NavigationAuditReportType.VISITS) {
                    assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isCloseTo(1200.0 / 86400, within(0.000001));
                } else if (type == NavigationAuditReportType.SERVICE_ENTRIES) {
                    assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Użytkownik anonimowy");
                    assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Wejście do serwisu");
                } else {
                    assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo(type == NavigationAuditReportType.PAGE_VIEWS
                            ? "Szczegóły czasopisma (ID: 123)" : "file.xlsx");
                }
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsAndScannerShareSessionLockAndDoNotDuplicateVisits() throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        Long sessionId = transaction.execute(status -> session().getId());
        var actor = mock(CurrentActorProvider.class);
        when(actor.currentActor()).thenReturn(new UserActor(null, null, null, null, null, sessionId));
        var service = new NavigationAuditVisitService(visits, actor, Clock.fixed(now, ZoneOffset.UTC));
        var activity = new NavigationVisitActivityDto(UUID.randomUUID(), now);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = java.util.stream.IntStream.range(0, 4).<Callable<Void>>mapToObj(index -> () -> {
                transaction.executeWithoutResult(status -> service.acceptActivity(activity));
                return null;
            }).toList();
            for (var result : executor.invokeAll(tasks)) {
                result.get(10, TimeUnit.SECONDS);
            }
        }
        transaction.executeWithoutResult(status -> {
            assertThat(entityManager.createQuery("select count(visit) from NavigationAuditVisit visit where visit.loginSessionId = :id", Long.class)
                    .setParameter("id", sessionId).getSingleResult()).isEqualTo(1L);
            assertThat(entityManager.createQuery("select count(activity) from NavigationAuditVisitActivity activity where activity.loginSessionId = :id", Long.class)
                    .setParameter("id", sessionId).getSingleResult()).isEqualTo(1L);
        });
        var laterService = new NavigationAuditVisitService(visits, actor, Clock.fixed(now.plusSeconds(1800), ZoneOffset.UTC));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.<Callable<Void>>of(
                    () -> { transaction.executeWithoutResult(status -> laterService.closeInactiveVisits()); return null; },
                    () -> { transaction.executeWithoutResult(status -> laterService.acceptActivity(new NavigationVisitActivityDto(UUID.randomUUID(), now.plusSeconds(1800)))); return null; }
            );
            for (var result : executor.invokeAll(tasks)) {
                result.get(10, TimeUnit.SECONDS);
            }
        }
        transaction.executeWithoutResult(status -> {
            assertThat(entityManager.createQuery("select count(visit) from NavigationAuditVisit visit where visit.loginSessionId = :id", Long.class)
                    .setParameter("id", sessionId).getSingleResult()).isEqualTo(2L);
            assertThat(visits.findOpen(sessionId)).isPresent();
            entityManager.createQuery("delete from NavigationAuditVisitActivity activity where activity.loginSessionId = :id").setParameter("id", sessionId).executeUpdate();
            entityManager.createQuery("delete from NavigationAuditVisit visit where visit.loginSessionId = :id").setParameter("id", sessionId).executeUpdate();
        });
    }

    @Test
    void projectionsFilterTechnicalEventsAndReadAllBatchesInStableOrder() {
        event(NavigationAuditEventType.PAGE_VIEW, NavigationAuditActorType.USER, null, null);
        event(NavigationAuditEventType.PAGE_VIEW, NavigationAuditActorType.INSTITUTION, null, null);
        event(NavigationAuditEventType.PAGE_VIEW, NavigationAuditActorType.ANONYMOUS, null, null);
        event(NavigationAuditEventType.PAGE_HEARTBEAT, NavigationAuditActorType.USER, null, null);
        event(NavigationAuditEventType.FILE_DOWNLOAD, NavigationAuditActorType.USER, "file.xls", true);
        event(NavigationAuditEventType.FILE_DOWNLOAD, NavigationAuditActorType.USER, "file.xlsx", true);
        event(NavigationAuditEventType.FILE_DOWNLOAD, NavigationAuditActorType.USER, "file.xlsx", false);
        event(NavigationAuditEventType.API_RESULT, NavigationAuditActorType.USER, "file.xls", true);
        var first = query.fetch(NavigationAuditReportType.PAGE_VIEWS, now, now.plusSeconds(1), null, 1);
        var second = query.fetch(NavigationAuditReportType.PAGE_VIEWS, now, now.plusSeconds(1), first.getLast(), 1);
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().id()).isGreaterThan(first.getFirst().id());
        assertThat(query.fetch(NavigationAuditReportType.PAGE_VIEWS, now, now.plusSeconds(1), second.getLast(), 1)).isEmpty();
        assertThat(query.fetch(NavigationAuditReportType.FILE_DOWNLOADS, now, now.plusSeconds(1), null, 10))
                .extracting("description").containsExactly("file.xls", "file.xlsx");
        assertThat(query.fetch(NavigationAuditReportType.PAGE_VIEWS, now.minusSeconds(1), now, null, 10)).isEmpty();
    }

    @Test
    void privacyClosesVisitsAndRemovesUserSessionAndReceipts() {
        var session = session();
        var visit = visit(session);
        visits.recordActivity(session.getId(), UUID.randomUUID(), now);
        entityManager.flush();
        privacy.anonymizeNavigationAuditVisitsForUser(session.getUser().getId());
        entityManager.clear();
        var anonymized = entityManager.find(NavigationAuditVisit.class, visit.getId());
        assertThat(anonymized.getUser()).isNull();
        assertThat(anonymized.getLoginSessionId()).isNull();
        assertThat(anonymized.getEndReason()).isEqualTo("ANONYMIZED");
        assertThat(anonymized.getEndedAt()).isEqualTo(anonymized.getLastActivityAt());
        assertThat(entityManager.createQuery("select count(activity) from NavigationAuditVisitActivity activity", Long.class).getSingleResult()).isZero();
        privacy.anonymizeLoginSessionsForUser(session.getUser().getId(), session.getUser().getEmail());
        entityManager.createQuery("delete from AppUser user where user.id = :id").setParameter("id", session.getUser().getId()).executeUpdate();
    }

    @Test
    void institutionDeletionRemovesVisitsBeforeSessions() {
        var session = session();
        visit(session);
        visits.recordActivity(session.getId(), UUID.randomUUID(), now);
        entityManager.flush();
        deletion.deleteDirectDataForInstitution(session.getInstitution().getId());
        entityManager.clear();
        assertThat(entityManager.createQuery("select count(visit) from NavigationAuditVisit visit", Long.class).getSingleResult()).isZero();
        assertThat(entityManager.createQuery("select count(activity) from NavigationAuditVisitActivity activity", Long.class).getSingleResult()).isZero();
    }

    private LoginSession session() {
        var institution = new Institution();
        institution.setName("Test"); institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED); entityManager.persist(institution);
        var user = new AppUser();
        user.setEmail(UUID.randomUUID() + "@example.test"); user.setPasswordHash("test"); user.setRoles("USER");
        user.setFirstName("Test"); user.setLastName("Test"); entityManager.persist(user);
        var session = LoginSession.builder().actorType(LoginActorType.USER).user(user).institution(institution)
                .startedAt(now).lastSeenAt(now).expiresAt(now.plusSeconds(86400)).ipAddress("127.0.0.1")
                .status(LoginSessionStatus.ACTIVE).build();
        entityManager.persist(session);
        return session;
    }

    private NavigationAuditVisit visit(LoginSession session) {
        var visit = new NavigationAuditVisit();
        visit.setUser(session.getUser()); visit.setInstitution(session.getInstitution());
        visit.setActorType(NavigationAuditActorType.USER); visit.setLoginSessionId(session.getId());
        visit.setStartedAt(now); visit.setLastActivityAt(now.plusSeconds(1200));
        visits.save(visit);
        return visit;
    }

    @Test
    void serviceEntriesIncludeAllActorsNullRelationsAndSuccessValuesWithoutGroupingUsers() {
        var session = session();
        var first = event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.USER, null, null);
        first.setUser(session.getUser());
        first.setInstitution(session.getInstitution());
        var second = event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.USER, null, false);
        second.setUser(session.getUser());
        second.setInstitution(session.getInstitution());
        var anonymous = event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.ANONYMOUS, null, null);
        anonymous.setPageKey("public.home");
        anonymous.setRouteTemplate("/");
        var institution = event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.INSTITUTION, null, true);
        institution.setInstitution(session.getInstitution());
        var anonymized = event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.USER, null, null);
        for (var type : NavigationAuditEventType.values()) {
            if (type != NavigationAuditEventType.SERVICE_ENTRY) {
                event(type, NavigationAuditActorType.USER, "other.xls", true);
            }
        }
        event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.USER, null, null).setOccurredAt(now.minusSeconds(1));
        event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.USER, null, null).setOccurredAt(now.plusSeconds(1));
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        var rows = query.fetch(NavigationAuditReportType.SERVICE_ENTRIES, now, now.plusSeconds(1), null, 1000);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityFetchCount()).isZero();
        assertThat(rows).extracting("eventId").containsExactly(first.getEventId(), second.getEventId(),
                anonymous.getEventId(), institution.getEventId(), anonymized.getEventId());
        assertThat(rows).extracting("actorType").containsExactly(NavigationAuditActorType.USER, NavigationAuditActorType.USER,
                NavigationAuditActorType.ANONYMOUS, NavigationAuditActorType.INSTITUTION, NavigationAuditActorType.USER);
        assertThat(rows.get(0).user()).isEqualTo(session.getUser().getEmail());
        assertThat(rows.get(1).user()).isEqualTo(rows.get(0).user());
        assertThat(rows.get(2).institution()).isNull();
        assertThat(rows.get(2).user()).isNull();
        assertThat(rows.get(2).pageKey()).isEqualTo("public.home");
        assertThat(rows.get(2).routeTemplate()).isEqualTo("/");
        assertThat(rows.get(3).user()).isNull();
        assertThat(rows.get(3).institution()).isEqualTo("Test");
        assertThat(rows.get(4).user()).isNull();
    }

    @Test
    void serviceEntriesReadAllDatabaseBatchesWithEqualTimestampsInStableOrder() {
        var expected = java.util.stream.IntStream.range(0, 1205)
                .mapToObj(index -> event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.ANONYMOUS, null, null).getEventId()).toList();
        entityManager.flush();
        entityManager.clear();
        var first = query.fetch(NavigationAuditReportType.SERVICE_ENTRIES, now, now.plusSeconds(1), null, 1000);
        var second = query.fetch(NavigationAuditReportType.SERVICE_ENTRIES, now, now.plusSeconds(1), first.getLast(), 1000);
        assertThat(first).hasSize(1000);
        assertThat(second).hasSize(205);
        assertThat(java.util.stream.Stream.concat(first.stream(), second.stream()).map(row -> row.eventId()).toList())
                .containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(query.fetch(NavigationAuditReportType.SERVICE_ENTRIES, now, now.plusSeconds(1), second.getLast(), 1000)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29,2026-03-28T23:00:00Z,2026-03-29T22:00:00Z",
            "2026-10-25,2026-10-24T22:00:00Z,2026-10-25T23:00:00Z"})
    void serviceEntryExportsApplyDstBoundariesToOccurrenceNotReceipt(String date, String fromText, String untilText) throws Exception {
        var from = Instant.parse(fromText);
        var until = Instant.parse(untilText);
        event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.ANONYMOUS, null, null).setOccurredAt(from.minusSeconds(1));
        var first = event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.ANONYMOUS, null, null);
        first.setOccurredAt(from);
        var last = event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.ANONYMOUS, null, null);
        last.setOccurredAt(until.minusSeconds(1));
        event(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditActorType.ANONYMOUS, null, null).setOccurredAt(until);
        entityManager.flush();
        var day = java.time.LocalDate.parse(date);
        try (var report = reportService.export(new NavigationAuditReportRequest(day, day, NavigationAuditReportType.SERVICE_ENTRIES));
             var input = Files.newInputStream(report.path()); var workbook = WorkbookFactory.create(input)) {
            var sheet = workbook.getSheet("Raport");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo(first.getEventId().toString());
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue()).isEqualTo(last.getEventId().toString());
        }
    }

    private NavigationAuditEvent event(NavigationAuditEventType type, NavigationAuditActorType actor, String target, Boolean success) {
        var event = NavigationAuditEvent.builder().eventId(UUID.randomUUID()).eventType(type).actorType(actor)
                .occurredAt(now).receivedAt(now).pageKey("institution.journals.details").routeTemplate("/institution/user/journals/:id")
                .resourceId("123").target(target).success(success).build();
        entityManager.persist(event);
        return event;
    }
}
