package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.Snapshot;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.TimeAxis;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Consumer;
import static com.osatum.wbn.application.journal.service.DecisionMetric.*;

/**
 * Przygotowuje i odczytuje generacje benchmarków P2 dla jawnie skonfigurowanych roczników.
 * Import i ręczna odbudowa utrwalają te same wejścia, wyniki i osie; Karta korzysta z gotowego zbioru.
 * Przygotowanie jest oddzielone od publikacji, aby błąd późniejszego etapu importu nie udostępnił części wyników.
 * Zakres obliczeń pozostaje niezależny od lat wyszukiwarki i globalnej flagi prezentacji porównań.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DecisionAnalyticsService {
    private final DecisionAnalyticsRepository repository;
    private final ConfigurationService configuration;
    private final DecisionPopulationReader population;
    private final DecisionBenchmarkCalculator calculator;

    /** Liczniki dotyczą tytułów bieżącego rocznika, a nie sumy wszystkich lat generacji. */
    public record Progress(int year, int total, int processed, String message) { }
    public Snapshot snapshot() { return repository.snapshot(); }
    /** Osobna transakcja wykrywa zmiany niewidoczne w migawce REPEATABLE_READ Karty. */
    public boolean unchanged(Snapshot snapshot) { return snapshot.equals(repository.freshSnapshot()); }

    /**
     * Utrwala zakres lat i unieważnia gotowość przed usunięciem poprzednich wyników.
     * Rozpoczęcie nowego przebiegu nie usuwa źródłowych ankiet ani ocen czasopism.
     */
    public UUID begin() {
        configuration.validateP2BenchmarkYears();
        UUID id = UUID.randomUUID();
        repository.start(id, Set.copyOf(configuration.getAvailableSearchYears()), Set.copyOf(configuration.getP2BenchmarkYears()));
        try { repository.clear(id); }
        catch (RuntimeException exception) { repository.fail(id); throw exception; }
        return id;
    }

    public void fail(UUID id) { repository.fail(id); }
    /** Publikuje kompletną generację dopiero po ponownym sprawdzeniu rewizji źródeł w repozytorium. */
    public void publish(UUID id) { repository.publish(id); }

    /** Ręczna odbudowa korzysta z tej samej ścieżki co import, a następnie publikuje gotowy zbiór. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void rebuild(UUID id) {
        prepare(id, progress -> { });
        publish(id);
    }

    /**
     * Czyta populację każdego rocznika raz i zapisuje również niedostępności oraz diagnostykę wyłączeń.
     * Dane źródłowe są czytane ze spójnej migawki; zapis odbywa się w osobnych transakcjach repozytorium.
     * Zakres lat pochodzi z rozpoczętej generacji, a import publikuje ją dopiero po pozostałych etapach.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void prepare(UUID id, Consumer<Progress> progress) {
        long started = System.nanoTime();
        var state = repository.snapshot();
        if (state.status() != DecisionAnalyticsRepository.Status.BUILDING || !id.equals(state.importId())) {
            throw new IllegalStateException("P2 preparation requires the current build identifier");
        }
        var generation = repository.generation(id);
        repository.startPreparation(id, state.sourceRevision());
        for (var year : new TreeSet<>(generation.benchmarkYears())) {
            var data = population.read(year);
            var groups = new TreeMap<Long, List<DecisionObservation>>();
            for (var item : data.observations()) for (var group : item.groups()) {
                groups.computeIfAbsent(group, key -> new ArrayList<>()).add(item);
            }
            var axes = new ArrayList<TimeAxis>();
            for (var group : groups.entrySet()) for (var metric : List.of(INITIAL_ASSESSMENT, REVIEW, PUBLICATION)) {
                var values = group.getValue().stream().map(item -> item.values().get(metric)).filter(Objects::nonNull).toList();
                if (values.size() >= 31) axes.add(new TimeAxis(year, group.getKey(), metric,
                        DecisionStatistics.timeAxis(DecisionStatistics.sorted(values)), id));
            }
            var byId = new HashMap<Long, DecisionObservation>();
            data.observations().forEach(item -> byId.put(item.journalId(), item));
            var results = new ArrayList<DecisionBenchmarkResult>();
            int processed = 0;
            progress.accept(new Progress(year, data.inputs().size(), 0, "Przygotowanie benchmarków P2 za " + year + "."));
            for (var input : data.inputs()) {
                var own = byId.get(input.journalId());
                var ownValues = own == null ? Map.<DecisionMetric, Double>of() : own.values();
                Long main = "AVAILABLE".equals(input.mainGroupState()) ? input.mainGroupId() : null;
                var values = calculator.results(input.journalId(), main, ownValues,
                        main == null ? List.of() : groups.getOrDefault(main, List.of()));
                if (!input.included()) values = values.stream().map(value -> new DecisionBenchmarkResult(value.journalId(),
                        value.metric(), value.groupId(), value.groupSize(), value.validCount(), value.ownValue(),
                        "EXCLUDED_FROM_POPULATION", null, null, null, null, null, null, value.chargedCount(), null)).toList();
                // Ta sama walidacja pokrycia osi co w dotychczasowej Karcie, zanim generacja trafi do bazy.
                DecisionBenchmarkResult.view(year, id, input.journalId(), values,
                        axes.stream().filter(axis -> Objects.equals(main, axis.groupId())).toList());
                results.addAll(values);
                progress.accept(new Progress(year, data.inputs().size(), ++processed, "Obliczanie benchmarków P2 za " + year + "."));
            }
            repository.saveYear(id, year, data, results, axes, state.sourceRevision());
            long available = results.stream().filter(DecisionBenchmarkResult::available).count();
            String summary = "P2 " + year + ": wejścia=" + data.inputs().size() + ", populacja=" + data.observations().size()
                    + ", wyłączenia=" + (data.inputs().size() - data.observations().size()) + ", dostępne=" + available
                    + ", niedostępne=" + (results.size() - available) + ", osie=" + axes.size() + ".";
            progress.accept(new Progress(year, data.inputs().size(), processed, summary));
            log.info("{}", summary);
        }
        repository.finishPreparation(id, state.sourceRevision());
        log.info("P2 generation prepared: importId={}, years={}, elapsedMs={}", id, generation.benchmarkYears(),
                (System.nanoTime() - started) / 1_000_000.0);
    }

    /**
     * Odczytuje wyniki jednej Karty bez ponownego pobierania populacji lub liczenia statystyk.
     * Brak przygotowanego rocznika daje brak porównań; niekompletne lub rozbieżne zapisane wyniki są błędem integralności.
     */
    public DecisionBenchmarksView benchmarks(Snapshot snapshot, int year, Long journalId,
                                            JournalDecisionAnnualView annual, Double icv) {
        if (!snapshot.ready(year)) throw new IllegalStateException("P2 source generation is not ready");
        if (!repository.generation(snapshot.importId()).benchmarkYears().contains(year)) return null;
        var results = repository.findResults(year, journalId, snapshot.importId());
        var main = annual.profile().mainGroup();
        Long groupId = main.value() == null ? null : main.value().id();
        var own = DecisionMetricReader.own(annual, icv);
        for (var result : results) {
            if (!Objects.equals(groupId, result.groupId()) || !Objects.equals(own.get(result.metric()), result.ownValue())) {
                throw new IllegalStateException("Stored P2 results do not match current annual data");
            }
        }
        var axes = groupId == null ? List.<TimeAxis>of() : repository.findAxes(year, groupId, snapshot.importId());
        return DecisionBenchmarkResult.view(year, snapshot.importId(), journalId, results, axes);
    }
}
