package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.questionnaire.SurveyJsonConverter;
import com.osatum.wbn.domain.model.survey.Survey;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public final class DecisionTestDatabase implements AutoCloseable {
    private final EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
    private final SingleConnectionDataSource migrationSource = migrationSource();
    public final AtomicInteger statements = new AtomicInteger();
    public final DataSource source = new DelegatingDataSource(database) {
        @Override public Connection getConnection() throws SQLException { return counted(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException { return counted(super.getConnection(user, password)); }
    };
    public final JdbcTemplate jdbc = new JdbcTemplate(source);

    public DecisionTestDatabase() {
        jdbc.execute("create table journal(id bigint primary key, state varchar(32))");
        jdbc.execute("create table questionnaire_survey(id bigint primary key, survey text, state varchar(32))");
        jdbc.execute("create table questionnaire(id bigint primary key, journal_id bigint, questionnaire_year integer, survey_data_id bigint, state varchar(32))");
        jdbc.execute("alter table questionnaire add rating_id bigint");
        jdbc.execute("create table questionnaire_rating_icv(id bigint primary key, rating_status varchar(32), icv double precision)");
        jdbc.execute("create table journal_rating(id bigint primary key, journal_id bigint, rating_year integer, rating_code varchar(20), rating_status varchar(32), value_num double precision, state varchar(32))");
        var result = Flyway.configure().dataSource(migrationSource).locations("classpath:db/migration").baselineOnMigrate(true)
                .baselineVersion("9").target("10").load().migrate();
        if (result.migrationsExecuted != 1) throw new IllegalStateException("V10 was not applied");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V12__persist_decision_benchmarks.sql")).execute(migrationSource);
        statements.set(0);
    }

    public void add(long journalId, int year, String status, Double icv, Survey survey) {
        long rowId = journalId * 10 + year - 2020;
        jdbc.update("merge into journal key(id) values (?, 'ACTIVE')", journalId);
        jdbc.update("insert into questionnaire_survey values (?, ?, 'ACTIVE')", rowId, new SurveyJsonConverter().convertToDatabaseColumn(survey));
        jdbc.update("insert into questionnaire_rating_icv values (?, ?, ?)", rowId, "INDEXED".equals(status) ? "PUBLISHED" : status, icv);
        jdbc.update("insert into questionnaire values (?, ?, ?, ?, 'ACTIVE', ?)", rowId, journalId, year, rowId, rowId);
        jdbc.update("insert into journal_rating values (?, ?, ?, 'ICV', ?, ?, 'ACTIVE')", rowId, journalId, year, status, icv);
    }

    private Connection counted(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement") || method.getName().equals("createStatement")) statements.incrementAndGet();
            try { return method.invoke(connection, args); }
            catch (InvocationTargetException exception) { throw exception.getCause(); }
        });
    }

    private SingleConnectionDataSource migrationSource() {
        try { return new SingleConnectionDataSource(database.getConnection(), true); }
        catch (SQLException exception) { throw new IllegalStateException(exception); }
    }

    @Override public void close() { migrationSource.destroy(); database.shutdown(); }
}
