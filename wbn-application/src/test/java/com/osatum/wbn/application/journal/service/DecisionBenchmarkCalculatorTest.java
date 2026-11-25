package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.TimeAxis;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Axis;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Availability;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static com.osatum.wbn.application.journal.service.DecisionMetric.*;
import static com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.RUN;
import static org.assertj.core.api.Assertions.*;

class DecisionBenchmarkCalculatorTest {
    private final DecisionBenchmarkCalculator calculator = new DecisionBenchmarkCalculator();

    @Test
    void thresholdCountsThirtyOtherDistinctJournalsAndValidValuesPerParameter() {
        assertThat(calculate(population(29), values()).icv().status()).isEqualTo(Availability.UNAVAILABLE);
        var population = population(30);
        population.set(1, with(population.get(1), REVIEW, null));
        var result = calculate(population, values());
        assertThat(result.icv().status()).isEqualTo(Availability.AVAILABLE);
        assertThat(result.times().review().status()).isEqualTo(Availability.UNAVAILABLE);
        assertThat(result.times().initialAssessment().value().q1()).isEqualTo(20);
        assertThat(result.international().authors().value().median()).isEqualTo(50);
    }

    @Test
    void ownJournalIsExcludedBeforePercentilesAndCounts() {
        var population = population(30);
        var own = new EnumMap<>(values()); own.put(ICV, 999.0); own.put(REVIEW, 999.0);
        population.set(0, new DecisionObservation(1L, Set.of(1008L), own));
        var result = calculate(population, own);
        assertThat(result.icv().value().lowerPercent()).isEqualTo(100);
        assertThat(result.icv().value().equalPercent()).isZero();
        assertThat(result.times().review().value().q3()).isEqualTo(20);
        assertThat(result.times().review().value().axis()).isEqualTo(new Axis(19, 21));
    }

    @Test
    void tiesAreComparedWithTheOwnRawValueRatherThanWithEachOther() {
        var population = population(30);
        var own = new EnumMap<>(values()); own.put(ICV, 81.125);
        for (int i = 1; i < population.size(); i++) population.set(i, with(population.get(i), ICV, 81.124));
        var lower = calculate(population, own).icv().value();
        assertThat(lower.lowerPercent()).isEqualTo(100); assertThat(lower.allEqual()).isFalse();
        for (int i = 1; i < population.size(); i++) population.set(i, with(population.get(i), ICV, 81.125));
        var equal = calculate(population, own).icv().value();
        assertThat(equal.equalPercent()).isEqualTo(100); assertThat(equal.allEqual()).isTrue();
        population.set(1, with(population.get(1), ICV, 81.124));
        var mixed = calculate(population, own).icv().value();
        assertThat(mixed.lowerPercent()).isCloseTo(100.0 / 30, within(1e-12));
        assertThat(mixed.allEqual()).isFalse();
    }

    @Test
    void preservesFractionalQuartilesAndThreeIndependentForeignMedians() {
        var population = population(30);
        for (int i = 0; i < 31; i++) {
            var map = new EnumMap<>(values());
            map.put(REVIEW, (double) (i + 10)); map.put(REJECTION_RATE, (double) i);
            map.put(FOREIGN_AUTHORS, 0.5); map.put(FOREIGN_REVIEWERS, 0.0); map.put(FOREIGN_COUNCIL, 100.0);
            population.set(i, new DecisionObservation((long) i + 1, Set.of(1008L, 2000L), map));
        }
        var axes = axes(); axes.set(1, new TimeAxis(2024, 1008L, REVIEW, new Axis(13, 37), RUN));
        var result = calculator.calculate(2024, RUN, 1L, 1008L, population.getFirst().values(), population, axes);
        assertThat(result.times().review().value().q1()).isEqualTo(18.25);
        assertThat(result.times().review().value().q3()).isEqualTo(32.75);
        assertThat(result.rejectionRate().value().q1()).isEqualTo(8.25);
        assertThat(result.rejectionRate().value().q3()).isEqualTo(22.75);
        assertThat(result.international().authors().value().median()).isEqualTo(.5);
        assertThat(result.international().reviewers().value().median()).isZero();
        assertThat(result.international().scientificCouncil().value().median()).isEqualTo(100);
    }

    @Test
    void fiveFeeDenominatorsIncludeFreeDeclarationsButExcludeUnresolvedOnesLocally() {
        var population = population(30);
        for (int i = 1; i < 31; i++) population.set(i, with(population.get(i), SUBMISSION_FEE, i % 2 == 0 ? 1.0 : 0.0));
        var result = calculate(population, values());
        assertThat(result.fees().getFirst().comparison().value().chargedPercent()).isEqualTo(50);
        assertThat(result.fees().get(1).comparison().value().chargedPercent()).isZero();
        assertThat(result.fees()).extracting(value -> value.category().name())
                .containsExactly("SUBMISSION", "PUBLICATION", "OPEN_ACCESS", "RAPID_REVIEW", "OTHER");
        population.set(1, with(population.get(1), SUBMISSION_FEE, null));
        var partial = calculate(population, values());
        assertThat(partial.fees().getFirst().comparison().status()).isEqualTo(Availability.UNAVAILABLE);
        assertThat(partial.fees().get(1).comparison().status()).isEqualTo(Availability.AVAILABLE);
    }

    @Test
    void missingOwnValueAndGroupDoNotBecomeZerosOrBorrowAnotherGroup() {
        var own = new EnumMap<>(values()); own.remove(FOREIGN_COUNCIL);
        var result = calculate(population(30), own);
        assertThat(result.international().scientificCouncil().status()).isEqualTo(Availability.UNAVAILABLE);
        assertThat(result.international().authors().status()).isEqualTo(Availability.AVAILABLE);
        var absent = calculator.calculate(2024, RUN, 1L, null, own, population(30), List.of());
        assertThat(absent.icv().status()).isEqualTo(Availability.UNAVAILABLE);
        var otherGroup = calculator.calculate(2024, RUN, 1L, 2000L, own, population(30), List.of());
        assertThat(otherGroup.icv().status()).isEqualTo(Availability.UNAVAILABLE);
    }

    @Test
    void aMissingOrForeignPublishedAxisIsAnIntegrityErrorRatherThanNormalUnavailability() {
        assertThatThrownBy(() -> calculator.calculate(2024, RUN, 1L, 1008L, values(), population(30), List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> calculator.calculate(2024, RUN, 1L, 1008L, values(), population(30),
                List.of(new TimeAxis(2025, 1008L, REVIEW, new Axis(19, 21), UUID.randomUUID()))))
                .isInstanceOf(IllegalStateException.class);
    }

    private DecisionBenchmarksView calculate(List<DecisionObservation> population, Map<DecisionMetric, Double> own) {
        return calculator.calculate(2024, RUN, 1L, 1008L, own, population, axes());
    }
    private List<TimeAxis> axes() {
        return new ArrayList<>(List.of(new TimeAxis(2024, 1008L, INITIAL_ASSESSMENT, new Axis(19, 21), RUN),
                new TimeAxis(2024, 1008L, REVIEW, new Axis(19, 21), RUN), new TimeAxis(2024, 1008L, PUBLICATION, new Axis(19, 21), RUN)));
    }
    private List<DecisionObservation> population(int others) {
        return new ArrayList<>(IntStream.rangeClosed(1, others + 1)
                .mapToObj(id -> new DecisionObservation((long) id, Set.of(1008L), values())).toList());
    }
    private Map<DecisionMetric, Double> values() {
        var values = new EnumMap<DecisionMetric, Double>(DecisionMetric.class);
        for (var metric : DecisionMetric.values()) values.put(metric, switch (metric) {
            case ICV -> 70.0;
            case FOREIGN_AUTHORS, FOREIGN_REVIEWERS, FOREIGN_COUNCIL -> 50.0;
            case SUBMISSION_FEE, PUBLICATION_FEE, OPEN_ACCESS_FEE, RAPID_REVIEW_FEE, OTHER_FEE -> 0.0;
            default -> 20.0;
        });
        return values;
    }
    private DecisionObservation with(DecisionObservation source, DecisionMetric metric, Double value) {
        var values = new EnumMap<>(source.values());
        if (value == null) values.remove(metric); else values.put(metric, value);
        return new DecisionObservation(source.journalId(), source.groups(), values);
    }
}
