package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import com.osatum.wbn.infrastructure.persistence.springdata.NavigationAuditEventSpringDataRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfSystemProperty(named = "p1.postgres.url", matches = ".+")
class JournalSearchAuditPostgresTest {
    @Test
    void migrationConcurrentRetriesAndBoundedRetentionWorkInAnIsolatedSchema() throws Exception {
        String url = System.getProperty("p1.postgres.url");
        String username = System.getenv("P1_POSTGRES_USERNAME");
        String password = System.getenv("P1_POSTGRES_PASSWORD");
        String schema = "wbn_p1_audit_test_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try (var connection = DriverManager.getConnection(url, username, password)) {
            try {
                connection.createStatement().execute("create schema " + schema);
                created = true;
                connection.setSchema(schema);
                String baseline = new ClassPathResource("db/migration/V1__initial_schema.sql").getContentAsString(StandardCharsets.UTF_8);
                var table = Pattern.compile("CREATE TABLE IF NOT EXISTS public\\.navigation_audit_event\\s*\\(.*?\\n\\s*\\);", Pattern.DOTALL).matcher(baseline);
                assertThat(table.find()).isTrue();
                connection.createStatement().execute(table.group().replace("public.navigation_audit_event", "navigation_audit_event"));
                connection.createStatement().execute("create unique index uk_event_id on navigation_audit_event(event_id)");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V8__add_journal_search_audit_retention_index.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V11__add_decision_audit_retention_index.sql"));
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V14__add_journal_issues_audit_retention_index.sql"));
                var source = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, username, password);
                var jdbc = new JdbcTemplate(source);
                var factory = new LocalContainerEntityManagerFactoryBean();
                factory.setDataSource(source);
                factory.setPackagesToScan("com.osatum.wbn.domain.model");
                factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none", "hibernate.default_schema", schema));
                factory.afterPropertiesSet();
                try {
                    var entityManagerFactory = factory.getObject();
                    Instant cutoff = Instant.parse("2025-10-17T10:00:00Z");
                    var expired = event(cutoff.minusSeconds(1));
                    var first = CompletableFuture.runAsync(() -> save(entityManagerFactory, expired));
                    var second = CompletableFuture.runAsync(() -> save(entityManagerFactory, expired));
                    CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);
                    var boundary = event(cutoff);
                    save(entityManagerFactory, boundary);
                    var decision = event(cutoff.minusSeconds(1));
                    decision.setEventType(NavigationAuditEventType.JOURNAL_DECISION_ACTION);
                    decision.setPageKey("institution.journals.decision");
                    decision.setRouteTemplate("/institution/user/journals/:id/decision");
                    decision.setAction("E01");
                    var p2First = CompletableFuture.runAsync(() -> save(entityManagerFactory, decision));
                    var p2Second = CompletableFuture.runAsync(() -> save(entityManagerFactory, decision));
                    CompletableFuture.allOf(p2First, p2Second).get(30, TimeUnit.SECONDS);
                    var issues = event(cutoff.minusSeconds(1));
                    issues.setEventType(NavigationAuditEventType.JOURNAL_ISSUES_CLICK);
                    save(entityManagerFactory, issues);
                    assertThat(jdbc.queryForObject("select indexdef from pg_indexes where schemaname = ? and indexname = ?", String.class,
                            schema, "idx_navigation_audit_journal_search_retention")).contains("JOURNAL_DECISION_ACTION", "JOURNAL_ISSUES_CLICK");
                    jdbc.update("""
                            insert into navigation_audit_event
                                (event_id, event_type, actor_type, occurred_at, received_at, page_key, route_template)
                            values (?, 'FILE_DOWNLOAD', 'USER', ?, ?, 'institution.journals.list', '/institution/user/journals')
                            """, UUID.randomUUID(), Timestamp.from(cutoff.minusSeconds(1)), Timestamp.from(cutoff.minusSeconds(1)));
                    assertThat(jdbc.queryForObject("select count(*) from navigation_audit_event", Integer.class)).isEqualTo(5);
                    assertThat(jdbc.queryForObject("select properties_json from navigation_audit_event where event_id = ?", String.class, expired.getEventId()))
                            .isEqualTo(expired.getPropertiesJson()).hasSizeGreaterThan(4000);
                    try (var entityManager = entityManagerFactory.createEntityManager()) {
                        entityManager.getTransaction().begin();
                        var adapter = new NavigationAuditEventRepositoryAdapter(mock(NavigationAuditEventSpringDataRepository.class), entityManager);
                        assertThat(adapter.deleteJournalSearchEventsBefore(cutoff, 1)).isEqualTo(1);
                        assertThat(adapter.deleteJournalSearchEventsBefore(cutoff, 1)).isEqualTo(1);
                        assertThat(adapter.deleteJournalSearchEventsBefore(cutoff, 1)).isEqualTo(1);
                        assertThat(adapter.deleteJournalSearchEventsBefore(cutoff, 1)).isZero();
                        entityManager.getTransaction().commit();
                    }
                    assertThat(jdbc.queryForObject("select count(*) from navigation_audit_event", Integer.class)).isEqualTo(2);
                } finally {
                    factory.destroy();
                }
            } finally {
                if (created) {
                    assertThat(schema).matches("wbn_p1_audit_test_[0-9a-f]{32}");
                    connection.setSchema("public");
                    connection.createStatement().execute("drop schema " + schema + " cascade");
                }
            }
        }
    }

    private static void save(EntityManagerFactory factory, NavigationAuditEvent event) {
        try (var entityManager = factory.createEntityManager()) {
            entityManager.getTransaction().begin();
            new NavigationAuditEventRepositoryAdapter(mock(NavigationAuditEventSpringDataRepository.class), entityManager)
                    .saveIfAbsent(event, null, null);
            entityManager.getTransaction().commit();
        }
    }

    private NavigationAuditEvent event(Instant receivedAt) {
        return NavigationAuditEvent.builder().eventId(UUID.randomUUID()).eventType(NavigationAuditEventType.JOURNAL_SEARCH)
                .actorType(NavigationAuditActorType.INSTITUTION).occurredAt(Instant.parse("1990-01-01T00:00:00Z"))
                .receivedAt(receivedAt).pageKey("institution.journals.list").routeTemplate("/institution/user/journals")
                .propertiesJson("{\"criteria\":{\"journalTitle\":\"" + "Long bibliographic title ".repeat(300) + "\"}}").build();
    }
}
