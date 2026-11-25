package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.journal.service.ExchangeRateService;
import com.osatum.wbn.domain.model.journal.ExchangeRate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "p1.postgres.url", matches = ".+")
class ExchangeRateMigrationPostgresTest {
    @Test
    void migrationPersistsExactRatesAcrossServiceRestartAndRollsBackTheWholeFailedBatch() throws Exception {
        String url = System.getProperty("p1.postgres.url");
        String username = System.getenv("P1_POSTGRES_USERNAME");
        String password = System.getenv("P1_POSTGRES_PASSWORD");
        String schema = "wbn_p1_test_" + UUID.randomUUID().toString().replace("-", "");
        boolean created = false;
        try (var connection = DriverManager.getConnection(url, username, password)) {
            try {
                connection.createStatement().execute("create schema " + schema);
                created = true;
                connection.setSchema(schema);
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V7__add_exchange_rates.sql"));
                var source = new SingleConnectionDataSource(connection, true);
                var jdbc = new JdbcTemplate(source);
                var service = new ExchangeRateService(new ExchangeRateRepositoryAdapter(jdbc));
                var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
                var initial = List.of(rate("USD", "4.1234", "A", 18), rate("THB", "0.112345", "B", 16));
                transaction.executeWithoutResult(status -> service.update(initial));
                try (var restarted = DriverManager.getConnection(url, username, password)) {
                    restarted.setSchema(schema);
                    var newService = new ExchangeRateService(new ExchangeRateRepositoryAdapter(new JdbcTemplate(new SingleConnectionDataSource(restarted, true))));
                    assertThat(newService.snapshot().rates().get("USD")).isEqualByComparingTo("4.1234");
                    assertThat(newService.snapshot().quotations().get("THB").effectiveDate()).isEqualTo(LocalDate.of(2026, 9, 16));
                }
                transaction.executeWithoutResult(status -> service.update(List.of(rate("USD", "2", "A", 10), rate("THB", "0.05", "B", 9))));
                assertThat(service.snapshot().rates().get("USD")).isEqualByComparingTo("4.1234");
                assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    service.update(List.of(rate("USD", "8", "A", 21), rate("THB", "0.2", "B", 21)));
                    jdbc.update("insert into exchange_rate values ('XXX', -1, 'A', 'invalid', current_date)");
                })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
                assertThat(service.snapshot().rates().get("USD")).isEqualByComparingTo("4.1234");
                assertThat(service.snapshot().rates().get("THB")).isEqualByComparingTo("0.112345");
                assertThatThrownBy(() -> jdbc.update("insert into exchange_rate values ('PLN', 1, 'A', 'invalid', current_date)"))
                        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            } finally {
                if (created) {
                    assertThat(schema).matches("wbn_p1_test_[0-9a-f]{32}");
                    connection.setSchema("public");
                    connection.createStatement().execute("drop schema " + schema + " cascade");
                }
            }
        }
    }

    private ExchangeRate rate(String code, String mid, String table, int day) {
        return new ExchangeRate(code, new BigDecimal(mid), table, "test/" + table, LocalDate.of(2026, 9, day));
    }
}
