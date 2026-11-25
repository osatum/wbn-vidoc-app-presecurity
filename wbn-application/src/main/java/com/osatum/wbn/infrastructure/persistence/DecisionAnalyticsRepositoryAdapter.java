package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository;
import com.osatum.wbn.application.journal.service.DecisionBenchmarkResult;
import com.osatum.wbn.application.journal.service.DecisionMetric;
import com.osatum.wbn.application.journal.service.DecisionPreparedData.Population;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Axis;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utrwala generacje benchmarków w relacyjnych tabelach oraz migawki wejść i wyników w JSON.
 * Osobne transakcje zapisu pozostawiają przygotowywany zbiór niedostępny aż do końcowej publikacji.
 * Blokada wiersza stanu, identyfikator przebiegu i rewizja chronią przed publikacją zastąpionych wyników.
 * Kontrola liczebności obejmuje również niedostępne metryki, które muszą pozostać obecne w zapisanym komplecie.
 */
@Repository
@RequiredArgsConstructor
public class DecisionAnalyticsRepositoryAdapter implements DecisionAnalyticsRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public Snapshot snapshot() { return read(false); }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Snapshot freshSnapshot() { return read(false); }

    private Snapshot read(boolean lock) {
        return jdbc.queryForObject("select status, import_id, ready_years, updated_at, source_revision from decision_analytics_state where id = 1"
                        + (lock ? " for update" : ""), (row, index) -> {
                    String id = row.getString("import_id");
                    return new Snapshot(Status.valueOf(row.getString("status")), id == null ? null : UUID.fromString(id),
                            years(row.getString("ready_years")), row.getTimestamp("updated_at").toInstant(), row.getLong("source_revision"));
                });
    }

    @Override
    public Generation generation(UUID id) {
        return jdbc.queryForObject("select source_years, benchmark_years, source_revision, prepared_at from decision_analytics_generation where import_id = ?",
                (row, index) -> new Generation(years(row.getString("source_years")), years(row.getString("benchmark_years")),
                        row.getObject("source_revision", Long.class), row.getTimestamp("prepared_at") == null ? null
                        : row.getTimestamp("prepared_at").toInstant()), id.toString());
    }

    @Override
    public List<DecisionBenchmarkResult> findResults(int year, Long journalId, UUID id) {
        return jdbc.query("select result_json from decision_benchmark_result where import_id = ? and survey_year = ? and journal_id = ? order by metric",
                (row, index) -> json.readValue(row.getString("result_json"), DecisionBenchmarkResult.class), id.toString(), year, journalId);
    }

    @Override
    public List<TimeAxis> findAxes(int year, Long groupId, UUID importId) {
        return jdbc.query("""
                select metric, minimum_days, maximum_days from decision_time_axis
                where survey_year = ? and group_id = ? and import_id = ?
                """, (row, index) -> new TimeAxis(year, groupId, DecisionMetric.valueOf(row.getString("metric")),
                new Axis(row.getDouble("minimum_days"), row.getDouble("maximum_days")), importId), year, groupId, importId.toString());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void start(UUID id, Set<Integer> sourceYears, Set<Integer> benchmarkYears) {
        // Nowy jawny przebieg może zastąpić osierocone BUILDING po restarcie; stary identyfikator traci prawo publikacji.
        read(true);
        if (sourceYears.isEmpty() || benchmarkYears.isEmpty() || !sourceYears.containsAll(benchmarkYears)) {
            throw new IllegalArgumentException("Invalid P2 preparation years");
        }
        jdbc.update("insert into decision_analytics_generation(import_id, source_years, benchmark_years, created_at) values (?, ?, ?, ?)",
                id.toString(), join(sourceYears), join(benchmarkYears), now());
        jdbc.update("update decision_analytics_state set status = 'BUILDING', import_id = ?, ready_years = '', updated_at = ? where id = 1",
                id.toString(), now());
    }

    /** Usuwa poprzednie generacje i osie przed zmianą źródeł; nie jest archiwizacją historycznych wyników. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(UUID id) {
        requireBuilding(id);
        jdbc.update("delete from decision_time_axis");
        jdbc.update("delete from decision_analytics_generation where import_id <> ?", id.toString());
        jdbc.update("delete from decision_analytics_year where import_id = ?", id.toString());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startPreparation(UUID id, long revision) {
        requireRevision(id, revision);
        jdbc.update("delete from decision_time_axis where import_id = ?", id.toString());
        jdbc.update("delete from decision_analytics_year where import_id = ?", id.toString());
        jdbc.update("update decision_analytics_generation set source_revision = ?, prepared_at = null where import_id = ?", revision, id.toString());
    }

    /** Zapis rocznika jest atomowy i może zostać powtórzony przed zakończeniem przygotowania bez mnożenia rekordów. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveYear(UUID id, int year, Population population, List<DecisionBenchmarkResult> results,
                         List<TimeAxis> axes, long revision) {
        requireRevision(id, revision);
        var generation = generation(id);
        if (!Objects.equals(generation.sourceRevision(), revision) || !generation.benchmarkYears().contains(year)
                || generation.preparedAt() != null || axes.stream().anyMatch(axis -> axis.surveyYear() != year
                || !axis.importId().equals(id) || !axis.metric().isTime())) throw new IllegalArgumentException("Inconsistent P2 year");
        int expected = Math.multiplyExact(population.inputs().size(), DecisionMetric.values().length);
        if (results.size() != expected) throw new IllegalArgumentException("Incomplete P2 year results");
        jdbc.update("delete from decision_time_axis where import_id = ? and survey_year = ?", id.toString(), year);
        jdbc.update("delete from decision_analytics_year where import_id = ? and survey_year = ?", id.toString(), year);
        jdbc.update("""
                insert into decision_analytics_year(import_id, survey_year, input_count, population_count, available_count, axis_count)
                values (?, ?, ?, ?, ?, ?)
                """, id.toString(), year, population.inputs().size(), population.observations().size(),
                results.stream().filter(DecisionBenchmarkResult::available).count(), axes.size());
        jdbc.batchUpdate("insert into decision_benchmark_input(import_id, survey_year, journal_id, included, input_json) values (?, ?, ?, ?, ?)",
                population.inputs(), 256, (statement, input) -> {
                    statement.setString(1, id.toString()); statement.setInt(2, year); statement.setLong(3, input.journalId());
                    statement.setBoolean(4, input.included()); statement.setString(5, json.writeValueAsString(input));
                });
        jdbc.batchUpdate("""
                insert into decision_benchmark_result(import_id, survey_year, journal_id, metric, group_id,
                    group_size, valid_count, own_value, unavailable_reason, result_json) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, results, 512, (statement, result) -> {
                    statement.setString(1, id.toString()); statement.setInt(2, year); statement.setLong(3, result.journalId());
                    statement.setString(4, result.metric().name()); statement.setObject(5, result.groupId());
                    statement.setInt(6, result.groupSize()); statement.setInt(7, result.validCount());
                    statement.setObject(8, result.ownValue()); statement.setString(9, result.unavailableReason());
                    statement.setString(10, json.writeValueAsString(result));
                });
        jdbc.batchUpdate("""
                insert into decision_time_axis(survey_year, group_id, metric, minimum_days, maximum_days, import_id)
                values (?, ?, ?, ?, ?, ?)
                """, axes, 256, (statement, axis) -> {
                    statement.setInt(1, year); statement.setLong(2, axis.groupId()); statement.setString(3, axis.metric().name());
                    statement.setDouble(4, axis.axis().minimum()); statement.setDouble(5, axis.axis().maximum()); statement.setString(6, id.toString());
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishPreparation(UUID id, long revision) {
        requireRevision(id, revision);
        validate(id);
        jdbc.update("update decision_analytics_generation set prepared_at = ? where import_id = ?", now(), id.toString());
    }

    /** Dopiero pod blokadą i po kontroli kompletności udostępnia jednocześnie wejścia, wyniki i osie tej generacji. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(UUID id) {
        var generation = generation(id);
        if (generation.sourceRevision() == null || generation.preparedAt() == null) throw new IllegalStateException("P2 generation is not prepared");
        requireRevision(id, generation.sourceRevision());
        validate(id);
        jdbc.update("update decision_analytics_generation set published_at = ? where import_id = ?", now(), id.toString());
        jdbc.update("update decision_analytics_state set status = 'READY', ready_years = ?, updated_at = ? where id = 1",
                join(generation.sourceYears()), now());
    }

    private void validate(UUID id) {
        var generation = generation(id);
        var prepared = new HashSet<>(jdbc.queryForList("select survey_year from decision_analytics_year where import_id = ?", Integer.class, id.toString()));
        if (!prepared.equals(generation.benchmarkYears())) throw new IllegalStateException("Incomplete P2 generation years");
        for (int year : prepared) {
            int invalid = jdbc.queryForObject("""
                    select count(*) from decision_analytics_year y where y.import_id = ? and y.survey_year = ? and (
                        y.input_count <> (select count(*) from decision_benchmark_input i where i.import_id = y.import_id and i.survey_year = y.survey_year)
                        or y.population_count <> (select count(*) from decision_benchmark_input i where i.import_id = y.import_id and i.survey_year = y.survey_year and i.included = true)
                        or y.input_count * 13 <> (select count(*) from decision_benchmark_result r where r.import_id = y.import_id and r.survey_year = y.survey_year)
                        or y.available_count <> (select count(*) from decision_benchmark_result r where r.import_id = y.import_id and r.survey_year = y.survey_year and r.unavailable_reason is null)
                        or y.axis_count <> (select count(*) from decision_time_axis a where a.import_id = y.import_id and a.survey_year = y.survey_year))
                    """, Integer.class, id.toString(), year);
            if (invalid != 0) throw new IllegalStateException("Incomplete P2 generation records");
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id) {
        jdbc.update("update decision_analytics_state set status = 'FAILED', ready_years = '', updated_at = ? where id = 1 and import_id = ?",
                now(), id.toString());
    }

    @Override
    @Transactional
    public void invalidate() {
        jdbc.update("""
                update decision_analytics_state set
                    status = case when status = 'READY' then 'NOT_READY' else status end,
                    ready_years = case when status = 'READY' then '' else ready_years end,
                    source_revision = source_revision + 1, updated_at = ? where id = 1
                """, now());
    }

    private Snapshot requireBuilding(UUID id) {
        var state = read(true);
        if (state.status() != Status.BUILDING || !id.equals(state.importId())) throw new IllegalStateException("P2 build was superseded or is no longer running");
        return state;
    }
    private void requireRevision(UUID id, long revision) {
        if (requireBuilding(id).sourceRevision() != revision) throw new IllegalStateException("P2 source data changed during preparation");
    }
    private Set<Integer> years(String text) {
        return text.isBlank() ? Set.of() : Arrays.stream(text.split(",")).map(Integer::valueOf).collect(Collectors.toUnmodifiableSet());
    }
    private String join(Set<Integer> years) { return years.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")); }
    private OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC); }
}
