package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.dictionary.service.DictionaryMniswDisciplineService;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.dictionary.DictionaryMniswDiscipline;
import com.osatum.wbn.infrastructure.persistence.springdata.DictionaryMniswDisciplineSpringDataRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictionaryMniswDisciplinePersistenceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void migrateExistingDatabase() {
        database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        jdbcTemplate.execute("""
                CREATE TABLE journal_mnisw_discipline (
                    id bigint PRIMARY KEY,
                    discipline_code varchar(20)
                )
                """);
        jdbcTemplate.execute("""
                INSERT INTO journal_mnisw_discipline (id, discipline_code)
                VALUES (1, '2021'), (2, 'historical-code')
                """);

        var flyway = Flyway.configure()
                .dataSource(database)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("5")
                .target("6")
                .load();
        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(flyway.info().current().getScript()).isEqualTo("V6__add_mnisw_discipline_dictionary.sql");
        assertThat(flyway.info().all()).filteredOn(migration -> migration.getVersion() != null
                        && migration.getVersion().compareTo(MigrationVersion.fromVersion("6")) > 0)
                .extracting(MigrationInfo::getScript).containsExactly(
                        "V7__add_exchange_rates.sql", "V8__add_journal_search_audit_retention_index.sql",
                        "V9__add_institution_operating_profile.sql", "V10__add_decision_analytics.sql",
                        "V11__add_decision_audit_retention_index.sql", "V12__persist_decision_benchmarks.sql",
                        "V13__invalidate_p2_fee_benchmarks.sql", "V14__add_journal_issues_audit_retention_index.sql");
    }

    @AfterEach
    void closeDatabase() {
        if (database != null) {
            database.shutdown();
        }
    }

    @Test
    void readsSeededDictionaryThroughRepositoryAndCache() {
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(DictionaryMniswDiscipline.class);
        configuration.setPhysicalNamingStrategy(new PhysicalNamingStrategySnakeCaseImpl());
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        configuration.getProperties().put("hibernate.connection.datasource", database);

        try (var sessionFactory = configuration.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            var springDataRepository = new JpaRepositoryFactory(session)
                    .getRepository(DictionaryMniswDisciplineSpringDataRepository.class);
            var repository = new DictionaryMniswDisciplineRepositoryAdapter(springDataRepository);
            var service = new DictionaryMniswDisciplineService(repository);
            service.init();

            assertThat(service.getAll()).hasSize(52);
            assertThat(service.getAll()).allSatisfy(dictionaryItem -> {
                assertThat(dictionaryItem.getNameEn()).isNotBlank();
                assertThat(dictionaryItem.getNameRu()).isNotBlank();
            });
            DictionaryMniswDiscipline discipline = service.getByCode("2021");
            assertThat(discipline.getNamePl())
                    .isEqualTo("automatyka, elektronika, elektrotechnika i technologie kosmiczne");
            assertThat(discipline.getNameEn())
                    .isEqualTo("automation, electronics, electrical engineering and space technologies");
            assertThat(discipline.getNameRu())
                    .isEqualTo("автоматика, электроника, электротехника и космические технологии");
            assertThat(discipline.getState()).isEqualTo(EntityState.ACTIVE);
            assertThat(discipline.getCreatedAt()).isNotNull();
            assertThat(service.getByCode("101").getNamePl()).isEqualTo("archeologia");
            assertThat(service.getByCode("2061").getNamePl())
                    .isEqualTo("inżynieria lądowa, geodezja i transport");
            assertThat(service.getByCode("1001").getNamePl()).isEqualTo("weterynaria");
            assertThat(service.getByCode("historical-code")).isNull();
        }
    }

    @Test
    void preservesExistingDisciplineCodesOutsideTheNewDictionary() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT discipline_code FROM journal_mnisw_discipline ORDER BY id
                """, String.class)).containsExactly("2021", "historical-code");
    }

    @Test
    void enforcesUniqueCodesAndRequiredPolishNames() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO dictionary_mnisw_discipline (code, name_pl) VALUES ('101', 'duplicate')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO dictionary_mnisw_discipline (code) VALUES ('missing-name')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
