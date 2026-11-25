package com.osatum.wbn.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "p1.flyway.postgres.url", matches = ".+")
class InstitutionOperatingProfilePostgresTest {
    @Test
    void migratesAllVersionsAndPersistsTheProfileAcrossConnections() throws Exception {
        String url = System.getProperty("p1.flyway.postgres.url");
        assertThat(url).matches("jdbc:postgresql://localhost:[0-9]+/wbn_profiles_test_[0-9a-f]{32}");
        String username = System.getenv("P1_POSTGRES_USERNAME");
        String password = System.getenv("P1_POSTGRES_PASSWORD");
        try (var connection = DriverManager.getConnection(url, username, password)) {
                var source = new SingleConnectionDataSource(connection, true);
                var flyway = Flyway.configure().dataSource(source)
                        .locations("classpath:db/migration").target("8").load();
                assertThat(flyway.migrate().success).isTrue();
                var jdbc = new JdbcTemplate(source);
                jdbc.update("""
                        INSERT INTO institution (name, institution_type, auth_mode, allow_registration,
                            allow_other_ip, allow_other_email_domain, auto_activate_new_accounts, state)
                        VALUES ('Profile migration fixture', 'RESEARCH_UNIT', 'AUTH_NOT_REQUIRED', false, false, false, false, 'ACTIVE')
                        """);
                var latest = Flyway.configure().dataSource(source)
                        .locations("classpath:db/migration").target("9").load();
                assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
                assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("9");
                Long unitId = jdbc.queryForObject("SELECT id FROM institution WHERE name = 'Profile migration fixture'", Long.class);
                assertThat(jdbc.queryForObject("SELECT operating_profile FROM institution WHERE id = ?", String.class, unitId)).isNull();
                jdbc.update("UPDATE institution SET operating_profile = 'BASIC' WHERE id = ?", unitId);
                try (var restarted = DriverManager.getConnection(url, username, password)) {
                    var persisted = new JdbcTemplate(new SingleConnectionDataSource(restarted, true));
                    assertThat(persisted.queryForObject("SELECT operating_profile FROM institution WHERE id = ?", String.class, unitId))
                            .isEqualTo("BASIC");
                    assertThatThrownBy(() -> persisted.update("UPDATE institution SET operating_profile = 'INVALID' WHERE id = ?", unitId))
                            .isInstanceOf(DataIntegrityViolationException.class);
                }
                assertThat(latest.validateWithResult().validationSuccessful).isTrue();
        }
    }
}
