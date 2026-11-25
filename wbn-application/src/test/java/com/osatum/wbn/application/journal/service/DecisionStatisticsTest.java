package com.osatum.wbn.application.journal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class DecisionStatisticsTest {
    @ParameterizedTest
    @CsvSource({"0,1", "0.25,1.75", "0.5,2.5", "0.75,3.25", "1,4"})
    void interpolatesPercentileIncForEvenSamples(double p, double expected) {
        assertThat(DecisionStatistics.percentile(new double[]{1, 2, 3, 4}, p)).isEqualTo(expected);
    }

    @Test
    void handlesOddSingletonAndConstantSamplesWithoutRounding() {
        assertThat(DecisionStatistics.percentile(new double[]{1, 2, 3, 4, 5}, .5)).isEqualTo(3);
        assertThat(DecisionStatistics.percentile(new double[]{.5}, .25)).isEqualTo(.5);
        assertThat(DecisionStatistics.percentile(new double[]{81.125, 81.125, 81.125}, .75)).isEqualTo(81.125);
        var source = List.of(3.0, 1.0, 2.0);
        assertThat(DecisionStatistics.sorted(source)).containsExactly(1, 2, 3);
        assertThat(source).containsExactly(3.0, 1.0, 2.0);
    }

    @Test
    void buildsTheSameAxisForEveryPossibleExclusionInTheReferenceExample() {
        double[] sample = IntStream.rangeClosed(10, 40).asDoubleStream().toArray();
        var axis = DecisionStatistics.timeAxis(sample);
        assertThat(axis.minimum()).isEqualTo(13);
        assertThat(axis.maximum()).isEqualTo(37);
        for (int removed = 0; removed < sample.length; removed++) {
            final int index = removed;
            var others = IntStream.range(0, sample.length).filter(i -> i != index).mapToDouble(i -> sample[i]).toArray();
            assertThat(DecisionStatistics.percentile(others, .25)).isBetween(axis.minimum(), axis.maximum());
            assertThat(DecisionStatistics.percentile(others, .75)).isBetween(axis.minimum(), axis.maximum());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0,1", "20,19,21"})
    void constantSamplesKeepAMinimumOneDayMargin(double value, double min, double max) {
        var axis = DecisionStatistics.timeAxis(IntStream.range(0, 31).mapToDouble(i -> value).toArray());
        assertThat(axis.minimum()).isEqualTo(min);
        assertThat(axis.maximum()).isEqualTo(max);
    }

    @Test
    void anOutlierDoesNotStretchTheAxisToTheOwnTime() {
        var values = new double[31]; values[30] = 1_000_000;
        var axis = DecisionStatistics.timeAxis(values);
        assertThat(axis.minimum()).isZero(); assertThat(axis.maximum()).isEqualTo(1);
        assertThatThrownBy(() -> DecisionStatistics.timeAxis(new double[30])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisionStatistics.sorted(List.of(Double.NaN))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisionStatistics.percentile(new double[0], .5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DecisionStatistics.percentile(new double[]{1}, 1.1)).isInstanceOf(IllegalArgumentException.class);
    }
}
