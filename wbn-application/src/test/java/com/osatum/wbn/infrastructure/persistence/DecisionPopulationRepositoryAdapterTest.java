package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.survey.Survey;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DecisionPopulationRepositoryAdapterTest {
    @Test
    void selectsOnlyUnambiguousActiveIndexedJournalYearPairsInOneQuery() {
        try (var database = new DecisionTestDatabase()) {
            database.add(1, 2024, "INDEXED", 0.0, survey(2024));
            database.add(1, 2025, "INDEXED", 81.125, survey(2025));
            database.add(2, 2024, "NOT_INDEXED", 10.0, survey(2024));
            database.add(3, 2024, "NOT_EVALUATED", null, survey(2024));
            database.add(4, 2024, "INDEXED", 90.0, survey(2024));
            database.jdbc.update("update journal set state = 'INACTIVE' where id = 4");
            database.add(5, 2024, "INDEXED", 90.0, survey(2024));
            database.jdbc.update("insert into journal_rating values (999, 5, 2024, 'ICV', 'NOT_INDEXED', null, 'ACTIVE')");
            database.add(6, 2024, "INDEXED", 90.0, survey(2024));
            database.jdbc.update("insert into questionnaire values (999, 6, 2024, 64, 'ACTIVE', 64)");
            var repository = new DecisionPopulationRepositoryAdapter(database.jdbc);
            database.statements.set(0);
            var result = repository.findCandidates(2024);
            assertThat(database.statements.get()).isEqualTo(1);
            assertThat(result.stream().filter(value -> value.exclusionReason() == null)).extracting(value -> value.journalId()).containsExactly(1L);
            assertThat(result).anyMatch(value -> "INACTIVE_JOURNAL".equals(value.exclusionReason())).anyMatch(value -> "AMBIGUOUS_ICV_RATING".equals(value.exclusionReason())).anyMatch(value -> "AMBIGUOUS_QUESTIONNAIRE".equals(value.exclusionReason()));
            assertThat(result.getFirst().icv()).isZero();
            assertThat(result.getFirst().survey().getEvaluationYear()).isEqualTo(2024);
            assertThat(repository.findCandidates(2025)).singleElement().satisfies(value -> {
                assertThat(value.icv()).isEqualTo(81.125);
                assertThat(value.survey().getEvaluationYear()).isEqualTo(2025);
            });
        }
    }

    @Test
    void measuresAThousandSurveyReadsWithAConstantQueryCountAndWithoutLoadingJournalAggregates() {
        try (var database = new DecisionTestDatabase()) {
            var survey = survey(2024);
            for (int i = 1; i <= 1000; i++) database.add(i, 2024, "INDEXED", (double) i, survey);
            var repository = new DecisionPopulationRepositoryAdapter(database.jdbc);
            database.statements.set(0);
            long started = System.nanoTime();
            var result = repository.findCandidates(2024);
            double elapsed = (System.nanoTime() - started) / 1_000_000.0;
            assertThat(result).hasSize(1000);
            assertThat(database.statements.get()).isEqualTo(1);
            System.out.printf("P2 JDBC measurement: journals=%d, queries=%d, elapsedMs=%.3f%n", result.size(), database.statements.get(), elapsed);
        }
    }

    private Survey survey(int year) { var survey = new Survey(); survey.setEvaluationYear(year); return survey; }
}
