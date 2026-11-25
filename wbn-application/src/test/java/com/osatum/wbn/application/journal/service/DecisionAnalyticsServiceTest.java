package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import static com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.RUN;
import static com.osatum.wbn.application.journal.service.DecisionPreparedTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionAnalyticsServiceTest {
    private final DecisionAnalyticsRepository repository = mock();
    private final DecisionPopulationReader population = mock();
    private final DecisionBenchmarkCalculator calculator = spy(new DecisionBenchmarkCalculator());
    private final ConfigurationService configuration = new ConfigurationService();
    private final DecisionAnalyticsService service = new DecisionAnalyticsService(repository, configuration, population, calculator);

    @Test
    void cardReadsStoredResultsWithoutReadingPopulationOrCalculatingAndKeepsUnprepared2025AvailableAsAnnualData() {
        var data = population(2024, 31, 20);
        var results = new DecisionBenchmarkCalculator().results(1L, 1008L, data.observations().getFirst().values(), data.observations());
        var axes = List.of(DecisionMetric.INITIAL_ASSESSMENT, DecisionMetric.REVIEW, DecisionMetric.PUBLICATION).stream()
                .map(metric -> new TimeAxis(2024, 1008L, metric, new com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Axis(19, 21), RUN)).toList();
        var snapshot = new Snapshot(Status.READY, RUN, Set.of(2024, 2025), Instant.EPOCH, 0);
        when(repository.generation(RUN)).thenReturn(new Generation(Set.of(2024, 2025), Set.of(2024), 0L, Instant.EPOCH));
        when(repository.findResults(2024, 1L, RUN)).thenReturn(results);
        when(repository.findAxes(2024, 1008L, RUN)).thenReturn(axes);
        var result = service.benchmarks(snapshot, 2024, 1L, annual(2024, 20), 81.125);
        assertThat(result.icv().value().allEqual()).isTrue();
        assertThat(result.times().review().value().q1()).isEqualTo(20);
        assertThat(result.rejectionRate().value().q3()).isEqualTo(25);
        assertThat(result.international().authors().value().median()).isEqualTo(.5);
        assertThat(result.fees()).hasSize(5).allSatisfy(fee -> assertThat(fee.comparison().value().chargedPercent()).isZero());
        assertThat(service.benchmarks(snapshot, 2025, 1L, annual(2025, 77), 81.125)).isNull();
        assertThat(snapshot.ready(2025)).isTrue();
        verifyNoInteractions(population, calculator);
        when(repository.findResults(2024, 1L, RUN)).thenReturn(List.of());
        assertThatThrownBy(() -> service.benchmarks(snapshot, 2024, 1L, annual(2024, 20), 81.125))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Incomplete");
    }

    @Test
    void beginFreezes2024ScopeIndependentlyOfBothSearchYearsAndVisibility() {
        var id = service.begin();
        verify(repository).start(id, Set.of(2024, 2025), Set.of(2024));
    }

    @Test
    void prepareReadsOnlyFrozen2024OnceAndDoesNotPublishUntilExplicitFinalization() {
        building(Set.of(2024));
        doReturn(population(2024, 31, 20)).when(population).read(2024);
        var progress = new ArrayList<DecisionAnalyticsService.Progress>();
        service.prepare(RUN, progress::add);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<DecisionBenchmarkResult>> results = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<TimeAxis>> axes = ArgumentCaptor.forClass(List.class);
        verify(repository).saveYear(eq(RUN), eq(2024), any(), results.capture(), axes.capture(), eq(0L));
        assertThat(results.getValue()).hasSize(31 * 13).allMatch(DecisionBenchmarkResult::available);
        assertThat(axes.getValue()).hasSize(3);
        verify(population).read(2024);
        verify(population, never()).read(2025);
        verify(repository).finishPreparation(RUN, 0);
        verify(repository, never()).publish(any());
        assertThat(progress.getLast().processed()).isEqualTo(31);
        assertThat(progress.getLast().message()).contains("dostępne=403");
        service.publish(RUN);
        verify(repository).publish(RUN);
    }

    @Test
    void manualRebuildUsesBothExplicitYearsWithoutMixingTheirInputsAndPublishesAfterPreparation() {
        building(Set.of(2024, 2025));
        doReturn(population(2024, 31, 20)).when(population).read(2024);
        doReturn(population(2025, 31, 77)).when(population).read(2025);
        service.rebuild(RUN);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<DecisionBenchmarkResult>> results = ArgumentCaptor.forClass(List.class);
        verify(repository).saveYear(eq(RUN), eq(2024), any(), results.capture(), any(), eq(0L));
        assertThat(results.getValue()).filteredOn(value -> value.metric() == DecisionMetric.REVIEW).allSatisfy(value -> {
            assertThat(value.ownValue()).isEqualTo(20); assertThat(value.q1()).isEqualTo(20);
        });
        verify(repository).saveYear(eq(RUN), eq(2025), any(), results.capture(), any(), eq(0L));
        assertThat(results.getValue()).filteredOn(value -> value.metric() == DecisionMetric.REVIEW).allSatisfy(value -> {
            assertThat(value.ownValue()).isEqualTo(77); assertThat(value.q1()).isEqualTo(77);
        });
        var order = inOrder(repository);
        order.verify(repository).finishPreparation(RUN, 0);
        order.verify(repository).publish(RUN);
        verify(population).read(2024); verify(population).read(2025);
    }

    @Test
    void missingMainGroupStillGetsThirteenUnavailableRowsAndDoesNotBorrowAnotherGroup() {
        building(Set.of(2024));
        var data = population(2024, 1, 20);
        var item = data.inputs().getFirst();
        var input = new DecisionPreparedData.Input(item.journalId(), item.sources(), null, "INVALID", item.groups(), item.metrics(), null);
        when(population.read(2024)).thenReturn(new DecisionPreparedData.Population(data.observations(), List.of(input)));
        service.prepare(RUN, progress -> { });
        @SuppressWarnings("unchecked") ArgumentCaptor<List<DecisionBenchmarkResult>> results = ArgumentCaptor.forClass(List.class);
        verify(repository).saveYear(eq(RUN), eq(2024), any(), results.capture(), any(), eq(0L));
        assertThat(results.getValue()).hasSize(13).allSatisfy(value -> {
            assertThat(value.available()).isFalse(); assertThat(value.groupId()).isNull();
            assertThat(value.unavailableReason()).isEqualTo("MISSING_OR_INVALID_MAIN_GROUP");
        });
    }

    @Test
    void cleanupFailureLeavesPersistedUnavailableStateWithoutReadingSources() {
        doThrow(new IllegalStateException("cleanup failed")).when(repository).clear(any());
        assertThatThrownBy(service::begin).isInstanceOf(IllegalStateException.class);
        var order = inOrder(repository);
        order.verify(repository).start(any(), any(), any()); order.verify(repository).clear(any()); order.verify(repository).fail(any());
        verifyNoInteractions(population);
    }

    @Test
    void readOrWriteFailureNeverFinishesOrPublishesGeneration() {
        building(Set.of(2024));
        when(population.read(2024)).thenThrow(new IllegalStateException("read failed"));
        assertThatThrownBy(() -> service.rebuild(RUN)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).finishPreparation(any(), anyLong());
        verify(repository, never()).publish(any());
        doReturn(population(2024, 1, 20)).when(population).read(2024);
        doThrow(new IllegalStateException("write failed")).when(repository).saveYear(any(), anyInt(), any(), any(), any(), anyLong());
        assertThatThrownBy(() -> service.rebuild(RUN)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).publish(any());
    }

    @Test
    void supersededBuildCannotPrepareOrPublish() {
        when(repository.snapshot()).thenReturn(new Snapshot(Status.BUILDING, UUID.randomUUID(), Set.of(), Instant.EPOCH, 0));
        assertThatThrownBy(() -> service.rebuild(RUN)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(population);
    }

    private void building(Set<Integer> years) {
        when(repository.snapshot()).thenReturn(new Snapshot(Status.BUILDING, RUN, Set.of(), Instant.EPOCH, 0));
        when(repository.generation(RUN)).thenReturn(new Generation(Set.of(2024, 2025), years, null, null));
    }
}
