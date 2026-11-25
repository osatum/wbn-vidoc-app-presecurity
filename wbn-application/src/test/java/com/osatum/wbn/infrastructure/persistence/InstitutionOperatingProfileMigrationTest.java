package com.osatum.wbn.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.*;

class InstitutionOperatingProfileMigrationTest {
    @Test
    void upgradesExistingUnitsToInheritanceWithoutOverwritingTheirData() throws Exception {
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try (var connection = database.getConnection()) {
            var dataSource = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE institution (id bigint PRIMARY KEY, institution_type varchar(32), name varchar(255))");
            jdbc.update("INSERT INTO institution VALUES (1, 'RESEARCH_UNIT', 'Existing unit'), (2, 'SYSTEM_OPERATOR', 'Operator')");
            var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .baselineOnMigrate(true).baselineVersion("8").target("9").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT operating_profile FROM institution WHERE id = 1", String.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT name FROM institution WHERE id = 1", String.class)).isEqualTo("Existing unit");
            for (String profile : new String[]{"BASIC", "ADVANCED", "BOTH"}) {
                jdbc.update("UPDATE institution SET operating_profile = ? WHERE id = 1", profile);
                assertThat(jdbc.queryForObject("SELECT operating_profile FROM institution WHERE id = 1", String.class)).isEqualTo(profile);
            }
            assertThatThrownBy(() -> jdbc.update("UPDATE institution SET operating_profile = 'INVALID' WHERE id = 1"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE institution SET operating_profile = 'BOTH' WHERE id = 2"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("UPDATE institution SET operating_profile = NULL WHERE id = 1");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        } finally {
            database.shutdown();
        }
    }
}
