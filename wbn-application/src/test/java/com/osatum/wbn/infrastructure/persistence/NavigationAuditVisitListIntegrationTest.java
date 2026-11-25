package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.system.dto.*;
import com.osatum.wbn.application.system.query.NavigationAuditVisitListQuery;
import com.osatum.wbn.application.system.service.NavigationAuditVisitQueryService;
import com.osatum.wbn.domain.model.institution.*;
import com.osatum.wbn.domain.model.navigationaudit.*;
import com.osatum.wbn.domain.model.user.AppUser;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NavigationAuditVisitListIntegrationTest.Configuration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=${audit.test.jdbc-url:jdbc:h2:mem:visit-list;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE}",
        "spring.datasource.username=${audit.test.username:sa}", "spring.datasource.password=${audit.test.password:}",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
class NavigationAuditVisitListIntegrationTest {
    @TestConfiguration
    @EnableAutoConfiguration
    @EntityScan("com.osatum.wbn.domain.model")
    @Import({NavigationAuditVisitQueryService.class, NavigationAuditVisitListQueryHandlerImpl.class,
            NavigationAuditReportQueryAdapter.class, UserPersonalDataDeletionRepositoryAdapter.class})
    static class Configuration {}

    @Autowired EntityManager entityManager;
    @Autowired NavigationAuditVisitQueryService service;
    @Autowired NavigationAuditReportQueryAdapter reports;
    @Autowired UserPersonalDataDeletionRepositoryAdapter privacy;
    private final Instant start = Instant.parse("2026-10-03T10:00:00Z");

    @Test
    void pagesInStableDescendingOrderAndUsesOnlyTwoQueries() {
        var first = visit(start, null, user(), institution());
        var second = visit(start, "LOGOUT", user(), institution());
        var third = visit(start.plusSeconds(1), null, user(), institution());
        entityManager.flush(); entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        var query = new NavigationAuditVisitListQuery(); query.setSize(2);
        var result = service.findVisits(query);
        assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.data()).extracting(NavigationAuditVisitListDto::id).containsExactly(third.getId(), second.getId());
        query.setPage(1); query.setFirst(0);
        assertThat(service.findVisits(query).data()).extracting(NavigationAuditVisitListDto::id).containsExactly(first.getId());
        query.setPage(2);
        assertThat(service.findVisits(query).data()).isEmpty();
    }

    @Test
    void filtersActorsNamesIdsStatusAndReasonWithoutLosingNullRelations() {
        var institution = institution(); var user = user();
        var open = visit(start, null, user, institution);
        var closed = visit(start, "INACTIVITY", null, institution);
        var anonymous = visit(start, "ANONYMIZED", null, null);
        var query = new NavigationAuditVisitListQuery();
        assertThat(service.findVisits(query).totalElements()).isEqualTo(3);
        query.setInstitution("  RESEARCH  "); query.setUser("ADA");
        assertThat(service.findVisits(query).data()).extracting(NavigationAuditVisitListDto::id).containsExactly(open.getId());
        query.setUser(user.getId().toString()); query.setInstitution(institution.getId().toString());
        assertThat(service.findVisits(query).totalElements()).isEqualTo(1);
        query.setUser(null); query.setInstitution(null); query.setStatus(NavigationAuditVisitStatus.CLOSED);
        assertThat(service.findVisits(query).data()).extracting(NavigationAuditVisitListDto::id).containsExactly(anonymous.getId(), closed.getId());
        query.setEndReason(NavigationAuditVisitEndReason.INACTIVITY);
        assertThat(service.findVisits(query).totalElements()).isEqualTo(1);
        query.setStatus(NavigationAuditVisitStatus.OPEN);
        assertThat(service.findVisits(query).data()).isEmpty();
    }

    @Test
    void treatsWildcardsAsLiteralSearchText() {
        var institution = institution(); institution.setName("Research 100%_!");
        visit(start, null, user(), institution);
        visit(start, null, user(), institution());
        var query = new NavigationAuditVisitListQuery(); query.setInstitution("%_!");
        assertThat(service.findVisits(query).totalElements()).isEqualTo(1);
        query.setInstitution("missing");
        assertThat(service.findVisits(query).totalElements()).isZero();
    }

    @Test
    void dayBoundariesMatchReportAndDoNotClipDuration() {
        var from = Instant.parse("2026-03-28T23:00:00Z");
        var until = Instant.parse("2026-03-29T22:00:00Z");
        visit(from.minusNanos(1000), null, null, null);
        var inside = visit(from, null, null, null);
        inside.setLastActivityAt(until.plusSeconds(1200));
        visit(until.minusNanos(1000), "LOGOUT", null, null);
        visit(until, null, null, null);
        var query = new NavigationAuditVisitListQuery();
        query.setDateFrom(LocalDate.parse("2026-03-29")); query.setDateTo(query.getDateFrom());
        var page = service.findVisits(query);
        assertThat(page.totalElements()).isEqualTo(2);
        var reported = reports.fetch(NavigationAuditReportType.VISITS, from, until, null, 100);
        assertThat(page.data()).extracting(NavigationAuditVisitListDto::id)
                .containsExactlyInAnyOrderElementsOf(reported.stream().map(row -> row.id()).toList());
        var dto = page.data().stream().filter(row -> row.id().equals(inside.getId())).findFirst().orElseThrow();
        assertThat(dto.durationSeconds()).isEqualTo(23 * 3600 + 1200);
        assertThat(dto.status()).isEqualTo(NavigationAuditVisitStatus.OPEN);
        assertThat(dto.endedAt()).isNull();
        assertThat(inside.getLastActivityAt()).isEqualTo(until.plusSeconds(1200));
    }

    @Test
    void anonymizedUsersRemainVisibleWithoutPersonalData() {
        var user = user(); var visit = visit(start, null, user, institution());
        entityManager.flush();
        privacy.anonymizeNavigationAuditVisitsForUser(user.getId()); entityManager.clear();
        var dto = service.findVisits(new NavigationAuditVisitListQuery()).data().getFirst();
        assertThat(dto.id()).isEqualTo(visit.getId());
        assertThat(dto.userId()).isNull(); assertThat(dto.userEmail()).isNull();
        assertThat(dto.status()).isEqualTo(NavigationAuditVisitStatus.CLOSED);
        assertThat(dto.endReason()).isEqualTo("ANONYMIZED");
        assertThat(dto.durationSeconds()).isEqualTo(1200);
    }

    private Institution institution() {
        var institution = new Institution(); institution.setName("Research");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT); institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        entityManager.persist(institution); return institution;
    }

    private AppUser user() {
        var user = new AppUser(); user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test"); user.setRoles("USER"); user.setFirstName("Ada"); user.setLastName("Tester");
        entityManager.persist(user); return user;
    }

    private NavigationAuditVisit visit(Instant timestamp, String reason, AppUser user, Institution institution) {
        var visit = new NavigationAuditVisit(); visit.setStartedAt(timestamp); visit.setLastActivityAt(timestamp.plusSeconds(1200));
        visit.setUser(user); visit.setInstitution(institution);
        visit.setActorType(user == null && !"ANONYMIZED".equals(reason) ? NavigationAuditActorType.INSTITUTION : NavigationAuditActorType.USER);
        if (reason != null) { visit.setEndedAt(visit.getLastActivityAt()); visit.setEndReason(reason); }
        entityManager.persist(visit); return visit;
    }
}
