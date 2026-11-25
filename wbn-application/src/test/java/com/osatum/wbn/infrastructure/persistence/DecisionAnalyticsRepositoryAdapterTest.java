package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.journal.event.JournalSavedEvent;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.*;
import com.osatum.wbn.application.journal.service.*;
import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Axis;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static com.osatum.wbn.application.journal.service.DecisionPreparedTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionAnalyticsRepositoryAdapterTest {
    @Configuration @EnableTransactionManagement static class Transactions { }

    @Test
    void feeRulesMigrationInvalidatesOldResultsWithoutDeletingControlSnapshots() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var repository = context.getBean(DecisionAnalyticsRepository.class);
            var id = begin(repository, Set.of(2024));
            save(repository, id, 2024, 31, 20);
            repository.finishPreparation(id, 0); repository.publish(id);
            new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                    new org.springframework.core.io.ClassPathResource("db/migration/V13__invalidate_p2_fee_benchmarks.sql"))
                    .execute(database.source);
            assertThat(repository.snapshot().status()).isEqualTo(Status.NOT_READY);
            assertThat(repository.snapshot().ready(2024)).isFalse();
            assertThat(repository.findResults(2024, 1L, id)).hasSize(13);
            assertThat(database.jdbc.queryForObject("select count(*) from decision_benchmark_input", Integer.class)).isEqualTo(31);
        }
    }

    @Test
    void preparesBeforePublishingAndSurvivesRestartWithCompleteThirteenRowsAndPrivateDataFreeInputs() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var repository = context.getBean(DecisionAnalyticsRepository.class);
            assertThat(repository.snapshot().status()).isEqualTo(Status.NOT_READY);
            var id = begin(repository, Set.of(2024));
            save(repository, id, 2024, 31, 20);
            assertThat(repository.snapshot().ready(2024)).isFalse();
            assertThatThrownBy(() -> repository.publish(id)).isInstanceOf(IllegalStateException.class);
            repository.finishPreparation(id, 0);
            assertThat(repository.snapshot().status()).isEqualTo(Status.BUILDING);
            repository.publish(id);
            var restarted = new DecisionAnalyticsRepositoryAdapter(database.jdbc);
            assertThat(restarted.snapshot().ready(2024)).isTrue();
            assertThat(restarted.snapshot().ready(2025)).isTrue();
            assertThat(restarted.generation(id).benchmarkYears()).containsExactly(2024);
            assertThat(restarted.findResults(2024, 1L, id)).hasSize(13).allMatch(DecisionBenchmarkResult::available);
            assertThat(restarted.findResults(2025, 1L, id)).isEmpty();
            assertThat(database.jdbc.queryForObject("select count(*) from decision_benchmark_result", Integer.class)).isEqualTo(403);
            String input = database.jdbc.queryForObject("select input_json from decision_benchmark_input where journal_id = 1", String.class);
            assertThat(input).contains("questionnaireId", "PUBLISHED", "foreignAuthors", "0.5", "NO_FEE", "received", "rejected")
                    .doesNotContain("userData", "email", "reviewerList", "editorialStaff");
        }
    }

    @Test
    void atomicYearWriteRejectsDuplicatesAndCompletenessCheckRejectsMissingRows() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var repository = context.getBean(DecisionAnalyticsRepository.class);
            var id = begin(repository, Set.of(2024));
            var data = population(2024, 1, 20);
            var results = results(data);
            var duplicate = new ArrayList<>(results); duplicate.set(12, duplicate.getFirst());
            assertThatThrownBy(() -> repository.saveYear(id, 2024, data, duplicate, List.of(), 0))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(repository.findResults(2024, 1L, id)).isEmpty();
            assertThat(database.jdbc.queryForObject("select count(*) from decision_analytics_year", Integer.class)).isZero();
            repository.saveYear(id, 2024, data, results, List.of(), 0);
            // Powtórzenie zapisu tego samego rocznika nie zwiększa liczebności.
            repository.saveYear(id, 2024, data, results, List.of(), 0);
            database.jdbc.update("delete from decision_benchmark_result where metric = 'ICV'");
            assertThatThrownBy(() -> repository.finishPreparation(id, 0)).isInstanceOf(IllegalStateException.class);
            assertThat(repository.snapshot().ready(2024)).isFalse();
        }
    }

    @Test
    void everyYearHasSeparatePersistedResultsAndCardReadsStayConstantWithoutCalculation() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var repository = context.getBean(DecisionAnalyticsRepository.class);
            var id = begin(repository, Set.of(2024, 2025));
            save(repository, id, 2024, 31, 20);
            assertThatThrownBy(() -> repository.finishPreparation(id, 0)).isInstanceOf(IllegalStateException.class);
            save(repository, id, 2025, 31, 77);
            repository.finishPreparation(id, 0); repository.publish(id);
            var reader = mock(DecisionPopulationReader.class);
            var calculator = mock(DecisionBenchmarkCalculator.class);
            var service = new DecisionAnalyticsService(repository, new ConfigurationService(), reader, calculator);
            var snapshot = repository.snapshot();
            var annual2024 = annual(2024, 20); var annual2025 = annual(2025, 77);
            database.statements.set(0);
            long started = System.nanoTime();
            for (int i = 0; i < 20; i++) {
                int year = i % 2 == 0 ? 2024 : 2025;
                var card = service.benchmarks(snapshot, year, 1L, year == 2024 ? annual2024 : annual2025, 81.125);
                assertThat(card.times().review().value().q1()).isEqualTo(year == 2024 ? 20 : 77);
                assertThat(card.importId()).isEqualTo(id);
            }
            assertThat(database.statements.get()).isEqualTo(60);
            verifyNoInteractions(reader, calculator);
            System.out.printf("P2 persisted card measurement: reads=20, queries=%d, elapsedMs=%.3f%n",
                    database.statements.get(), (System.nanoTime() - started) / 1_000_000.0);
        }
    }

    @Test
    void sourceChangesBeforeOrAfterPreparationBlockPublicationAndRollbackDoesNotInvalidate() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var repository = context.getBean(DecisionAnalyticsRepository.class);
            var id = begin(repository, Set.of(2024));
            save(repository, id, 2024, 1, 20);
            repository.finishPreparation(id, 0);
            var tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
            tx.executeWithoutResult(status -> { context.publishEvent(new JournalSavedEvent(1L)); status.setRollbackOnly(); });
            assertThat(repository.snapshot().sourceRevision()).isZero();
            tx.executeWithoutResult(status -> context.publishEvent(new JournalSavedEvent(1L)));
            assertThatThrownBy(() -> repository.publish(id)).isInstanceOf(IllegalStateException.class).hasMessageContaining("source data changed");
            assertThatThrownBy(() -> repository.saveYear(id, 2024, population(2024, 1, 20), results(population(2024, 1, 20)), List.of(), 0))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(repository.snapshot().status()).isEqualTo(Status.BUILDING);
            repository.fail(id);
            var next = begin(repository, Set.of(2024));
            repository.saveYear(next, 2024, population(2024, 1, 20), results(population(2024, 1, 20)), List.of(), 1);
            repository.finishPreparation(next, 1); repository.publish(next);
            tx.executeWithoutResult(status -> context.publishEvent(new JournalSavedEvent(1L)));
            assertThat(repository.snapshot().status()).isEqualTo(Status.NOT_READY);
        }
    }

    @Test
    void restartCanReplaceAbandonedBuildAndOldBuildCannotPublishOrFailNewGeneration() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var repository = context.getBean(DecisionAnalyticsRepository.class);
            var first = begin(repository, Set.of(2024));
            save(repository, first, 2024, 1, 20); repository.finishPreparation(first, 0);
            var second = begin(repository, Set.of(2024));
            assertThat(repository.findResults(2024, 1L, first)).isEmpty();
            assertThatThrownBy(() -> repository.saveYear(first, 2024, population(2024, 1, 20), results(population(2024, 1, 20)), List.of(), 0))
                    .isInstanceOf(IllegalStateException.class);
            repository.fail(first);
            assertThat(repository.snapshot().status()).isEqualTo(Status.BUILDING);
            assertThat(repository.snapshot().importId()).isEqualTo(second);
            repository.fail(second);
            assertThat(new DecisionAnalyticsRepositoryAdapter(database.jdbc).snapshot().status()).isEqualTo(Status.FAILED);
        }
    }

    @Test
    void freshReadDetectsImportStartedDuringRepeatableReadCard() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var repository = context.getBean(DecisionAnalyticsRepository.class);
            var first = begin(repository, Set.of(2024));
            save(repository, first, 2024, 1, 20); repository.finishPreparation(first, 0); repository.publish(first);
            var tx = new TransactionTemplate(context.getBean(DataSourceTransactionManager.class));
            tx.setReadOnly(true); tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            tx.executeWithoutResult(status -> {
                var before = repository.snapshot();
                try { CompletableFuture.runAsync(() -> begin(repository, Set.of(2024))).get(5, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new IllegalStateException(exception); }
                assertThat(repository.snapshot()).isEqualTo(before);
                assertThat(repository.freshSnapshot().status()).isEqualTo(Status.BUILDING);
            });
        }
    }

    @Test
    void preparesFullReferenceSizedFixtureThroughTransactionalServiceAndPublishesOnlyAtFinalStep() {
        try (var database = new DecisionTestDatabase(); var context = context(database)) {
            var source = population(2024, 4827, 20);
            var observations = source.observations().stream().map(item -> new DecisionObservation(item.journalId(),
                    Set.of(1008L + item.journalId() % 27), item.values())).toList();
            var inputs = source.inputs().stream().map(item -> {
                long group = 1008L + item.journalId() % 27;
                return new DecisionPreparedData.Input(item.journalId(), item.sources(), group, "AVAILABLE",
                        List.of(new com.osatum.wbn.application.journal.view.JournalDecisionAnnualView.Group(group, "Grupa", "Group", null)),
                        item.metrics(), null);
            }).toList();
            var reader = context.getBean(DecisionPopulationReader.class);
            when(reader.read(2024)).thenReturn(new DecisionPreparedData.Population(observations, inputs));
            var service = context.getBean(DecisionAnalyticsService.class);
            var id = service.begin();
            database.statements.set(0);
            long started = System.nanoTime();
            service.prepare(id, progress -> { });
            double elapsedMs = (System.nanoTime() - started) / 1_000_000.0;
            assertThat(service.snapshot().status()).isEqualTo(Status.BUILDING);
            assertThat(database.jdbc.queryForObject("select count(*) from decision_benchmark_result", Integer.class)).isEqualTo(62751);
            assertThat(database.jdbc.queryForObject("select count(*) from decision_time_axis", Integer.class)).isEqualTo(81);
            assertThat(database.jdbc.queryForObject("select count(*) from decision_benchmark_input where included = true", Integer.class)).isEqualTo(4827);
            service.publish(id);
            assertThat(service.snapshot().ready(2024)).isTrue();
            verify(reader).read(2024); verify(reader, never()).read(2025);
            System.out.printf("P2 complete fixture preparation: journals=4827, results=62751, axes=81, elapsedMs=%.3f%n", elapsedMs);
        }
    }

    private UUID begin(DecisionAnalyticsRepository repository, Set<Integer> years) {
        var id = UUID.randomUUID();
        repository.start(id, Set.of(2024, 2025), years); repository.clear(id);
        repository.startPreparation(id, repository.snapshot().sourceRevision());
        return id;
    }
    private void save(DecisionAnalyticsRepository repository, UUID id, int year, int count, int days) {
        var data = population(year, count, days);
        var axes = count < 31 ? List.<TimeAxis>of() : List.of(DecisionMetric.INITIAL_ASSESSMENT, DecisionMetric.REVIEW, DecisionMetric.PUBLICATION).stream()
                .map(metric -> new TimeAxis(year, 1008L, metric, new Axis(days - 1, days + 1), id)).toList();
        repository.saveYear(id, year, data, results(data), axes, repository.snapshot().sourceRevision());
    }
    private List<DecisionBenchmarkResult> results(DecisionPreparedData.Population data) {
        var calculator = new DecisionBenchmarkCalculator();
        return data.observations().stream().flatMap(item -> calculator.results(item.journalId(), 1008L, item.values(), data.observations()).stream()).toList();
    }
    private AnnotationConfigApplicationContext context(DecisionTestDatabase database) {
        var context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean(DataSourceTransactionManager.class, () -> new DataSourceTransactionManager(database.source));
        context.registerBean(DecisionAnalyticsRepository.class, () -> new DecisionAnalyticsRepositoryAdapter(database.jdbc));
        context.registerBean(DecisionAnalyticsInvalidationListener.class);
        context.registerBean(ConfigurationService.class);
        context.registerBean(DecisionPopulationReader.class, () -> mock(DecisionPopulationReader.class));
        context.registerBean(DecisionBenchmarkCalculator.class);
        context.registerBean(DecisionAnalyticsService.class);
        context.refresh(); return context;
    }
}
