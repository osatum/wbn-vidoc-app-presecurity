package com.osatum.wbn.web.journal.controller;

import com.osatum.wbn.application.journal.service.*;
import com.osatum.wbn.application.journal.repository.DecisionAnalyticsRepository.TimeAxis;
import com.osatum.wbn.application.journal.view.DecisionBenchmarksView.Axis;
import com.osatum.wbn.application.journal.view.JournalDecisionView;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.stream.IntStream;

import static com.osatum.wbn.application.journal.service.DecisionMetric.*;
import static com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport.RUN;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JournalDecisionBenchmarksContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"NORMAL", "ALL_EQUAL", "SMALL", "LOCAL_GAP"})
    void servesTypedComparisonsWithOneYearAndBuildWithoutParticipantPayloads(String scenario) throws Exception {
        var own = new EnumMap<DecisionMetric, Double>(DecisionMetric.class);
        for (var metric : DecisionMetric.values()) own.put(metric, 20.0);
        own.put(ICV, 81.125);
        var population = IntStream.rangeClosed(1, scenario.equals("SMALL") ? 30 : 31).mapToObj(id -> {
            var values = new EnumMap<>(own);
            values.put(ICV, scenario.equals("ALL_EQUAL") ? 81.125 : id % 2 == 0 ? 81.124 : 81.125);
            if (scenario.equals("LOCAL_GAP") && id > 1) values.remove(REVIEW);
            values.put(FOREIGN_COUNCIL, 0.5);
            values.put(SUBMISSION_FEE, id % 2 == 0 ? 1.0 : 0.0);
            return new DecisionObservation((long) id, Set.of(1008L), values);
        }).toList();
        var axes = List.of(INITIAL_ASSESSMENT, REVIEW, PUBLICATION).stream()
                .map(metric -> new TimeAxis(2024, 1008L, metric, new Axis(19, 21), RUN)).toList();
        var result = new DecisionBenchmarkCalculator().calculate(2024, RUN, 1L, 1008L, own, population, axes);
        var service = mock(JournalDecisionService.class);
        when(service.getDecision(1L, 2024)).thenReturn(new JournalDecisionView(2024, JournalDecisionView.AnnualStatus.AVAILABLE,
                List.of(2024), null, 81.125, null, null, List.of(), null, result, true));
        var response = standaloneSetup(new JournalDecisionController(service)).build()
                .perform(get("/api/journal/journal/1/decision").param("surveyYear", "2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("surveyYear").value(2024))
                .andExpect(jsonPath("icv").value(81.125))
                .andExpect(jsonPath("benchmarksVisible").value(true))
                .andExpect(jsonPath("benchmarks.surveyYear").value(2024))
                .andExpect(jsonPath("benchmarks.importId").value(RUN.toString()))
                .andExpect(jsonPath("benchmarks.groupId").value(1008))
                .andExpect(jsonPath("benchmarks.fees", hasSize(5)))
                .andExpect(content().string(not(containsString("journalId"))))
                .andExpect(content().string(not(containsString("population"))))
                .andExpect(content().string(not(containsString("surveyData"))));
        if (scenario.equals("SMALL")) {
            response.andExpect(jsonPath("benchmarks.icv.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("benchmarks.times.review.status").value("UNAVAILABLE"));
        } else {
            response.andExpect(jsonPath("benchmarks.icv.status").value("AVAILABLE"))
                    .andExpect(jsonPath("benchmarks.icv.value.allEqual").value(scenario.equals("ALL_EQUAL")))
                    .andExpect(jsonPath("benchmarks.international.scientificCouncil.value.median").value(.5))
                    .andExpect(jsonPath("benchmarks.fees[0].comparison.value.chargedPercent").value(50.0))
                    .andExpect(jsonPath("benchmarks.times.publication.value.axis.minimum").value(19.0))
                    .andExpect(jsonPath("benchmarks.times.publication.value.axis.maximum").value(21.0))
                    .andExpect(jsonPath("benchmarks.times.review.status").value(scenario.equals("LOCAL_GAP") ? "UNAVAILABLE" : "AVAILABLE"));
        }
    }
}
