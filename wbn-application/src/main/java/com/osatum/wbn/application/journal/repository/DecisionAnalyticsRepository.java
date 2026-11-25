package com.osatum.wbn.application.journal.repository;

import com.osatum.wbn.application.journal.service.DecisionMetric;
import com.osatum.wbn.application.journal.service.DecisionBenchmarkResult;
import com.osatum.wbn.application.journal.service.DecisionPreparedData.Population;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Axis;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dostęp do trwałych generacji P2: wejść, wyników, osi i gotowości ich publikacji.
 * Operacje zapisu identyfikują przebieg i rewizję źródeł, aby zastąpiony lub nieaktualny zbiór nie stał się gotowy.
 */
public interface DecisionAnalyticsRepository {
    enum Status { NOT_READY, BUILDING, READY, FAILED }
    /** Lata gotowych danych rocznych Karty nie oznaczają automatycznie lat przygotowanych porównań. */
    record Snapshot(Status status, UUID importId, Set<Integer> years, Instant updatedAt, long sourceRevision) {
        public Snapshot { years = Set.copyOf(years); }
        public boolean ready(int year) { return status == Status.READY && years.contains(year); }
    }
    record TimeAxis(int surveyYear, Long groupId, DecisionMetric metric, Axis axis, UUID importId) { }
    /** Zakres roczników jest zamrożony przy rozpoczęciu przebiegu, a rewizja przy odczycie jego końcowych źródeł. */
    record Generation(Set<Integer> sourceYears, Set<Integer> benchmarkYears, Long sourceRevision, Instant preparedAt) {
        public Generation { sourceYears = Set.copyOf(sourceYears); benchmarkYears = Set.copyOf(benchmarkYears); }
    }
    Snapshot snapshot();
    Snapshot freshSnapshot();
    List<TimeAxis> findAxes(int year, Long groupId, UUID importId);
    Generation generation(UUID importId);
    List<DecisionBenchmarkResult> findResults(int year, Long journalId, UUID importId);
    void start(UUID importId, Set<Integer> sourceYears, Set<Integer> benchmarkYears);
    void clear(UUID importId);
    void startPreparation(UUID importId, long sourceRevision);
    void saveYear(UUID importId, int year, Population population, List<DecisionBenchmarkResult> results,
                  List<TimeAxis> axes, long sourceRevision);
    void finishPreparation(UUID importId, long sourceRevision);
    void publish(UUID importId);
    void fail(UUID importId);
    void invalidate();
}
