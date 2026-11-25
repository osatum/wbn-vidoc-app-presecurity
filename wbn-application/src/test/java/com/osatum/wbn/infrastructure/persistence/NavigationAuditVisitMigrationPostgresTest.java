package com.osatum.wbn.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "audit.postgres.url", matches = ".+")
class NavigationAuditVisitMigrationPostgresTest {
    @Test
    void appliesAllMigrationsAndEnforcesSingleOpenVisit() throws Exception {
        String url = System.getProperty("audit.postgres.url");
        var flyway = Flyway.configure().dataSource(url, "postgres", "").locations("classpath:db/migration").load();
        var result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(flyway.info().applied()).extracting(MigrationInfo::getScript).endsWith(
                "V6__add_mnisw_discipline_dictionary.sql", "V7__add_exchange_rates.sql",
                "V8__add_journal_search_audit_retention_index.sql", "V9__add_institution_operating_profile.sql",
                "V10__add_decision_analytics.sql", "V11__add_decision_audit_retention_index.sql",
                "V12__persist_decision_benchmarks.sql", "V13__invalidate_p2_fee_benchmarks.sql");
        try (var connection = DriverManager.getConnection(url, "postgres", "");
             var statement = connection.createStatement()) {
            var session = statement.executeQuery("""
                    insert into login_session(actor_type, ip_address, started_at, last_seen_at, expires_at, status, state)
                    values('INSTITUTION', '127.0.0.1', now(), now(), now() + interval '1 day', 'ACTIVE', 'ACTIVE') returning id
                    """);
            session.next();
            long sessionId = session.getLong(1);
            String insert = "insert into navigation_audit_visit(login_session_id, actor_type, started_at, last_activity_at) "
                    + "values(" + sessionId + ", 'INSTITUTION', now(), now())";
            statement.executeUpdate(insert);
            assertThatThrownBy(() -> statement.executeUpdate(insert)).isInstanceOf(java.sql.SQLException.class);
            statement.executeUpdate("update navigation_audit_visit set ended_at=last_activity_at, end_reason='INACTIVITY' where login_session_id=" + sessionId);
            statement.executeUpdate(insert);
            var visits = statement.executeQuery("select count(*) from navigation_audit_visit where login_session_id=" + sessionId);
            visits.next();
            assertThat(visits.getLong(1)).isEqualTo(2L);
        }
    }
}
