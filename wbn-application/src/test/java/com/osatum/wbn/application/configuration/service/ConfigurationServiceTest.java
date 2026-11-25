package com.osatum.wbn.application.configuration.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.util.List;

class ConfigurationServiceTest {

    @Test
    void benchmarkPreparationYearsAreExplicitAndIndependentOfSearchYearsAndVisibility() {
        var runner = new ApplicationContextRunner().withUserConfiguration(ConfigurationService.class);
        runner.run(context -> assertThat(context.getBean(ConfigurationService.class).getP2BenchmarkYears()).containsExactly(2024));
        runner.withPropertyValues("app.migration.p2-benchmark-years=2024,2025").run(context -> {
            var configuration = context.getBean(ConfigurationService.class);
            assertThat(configuration.getP2BenchmarkYears()).containsExactly(2024, 2025);
            assertThat(configuration.getCurrentRatingYear()).isEqualTo(2024);
            assertThat(configuration.getAvailableSearchYears()).containsExactly(2025, 2024);
            assertThat(configuration.isDecisionBenchmarksVisible()).isFalse();
        });
        for (String invalid : List.of("", "2024,2024", "0", "2026")) {
            runner.withPropertyValues("app.migration.p2-benchmark-years=" + invalid)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void comparisonsAreOffByDefaultAndRequireAnExplicitEnvironmentSetting() {
        var runner = new ApplicationContextRunner().withUserConfiguration(ConfigurationService.class);
        runner.run(context -> assertThat(context.getBean(ConfigurationService.class).isDecisionBenchmarksVisible()).isFalse());
        runner.withPropertyValues("app.decision-card.benchmarks-visible=true").run(context -> {
            var configuration = context.getBean(ConfigurationService.class);
            assertThat(configuration.isDecisionBenchmarksVisible()).isTrue();
            assertThat(configuration.resolveSearchYear(2024)).isEqualTo(2024);
            assertThat(configuration.resolveSearchYear(2025)).isEqualTo(2025);
        });
        runner.withPropertyValues("app.decision-card.benchmarks-visible=false")
                .run(context -> assertThat(context.getBean(ConfigurationService.class).isDecisionBenchmarksVisible()).isFalse());
    }

    @Test
    void buildsIssuesLinksForTheSelectedJournalAndRejectsInvalidIdentifiers() {
        var configuration = new ConfigurationService();
        configuration.validateJournalLinks();
        assertThat(configuration.getJournalIssuesUrl(18215L)).isEqualTo(
                "https://journals.indexcopernicus.com/search/journal/issue?issueId=all&journalId=18215");
        assertThat(configuration.getJournalIssuesUrl(456L)).endsWith("journalId=456");
        assertThatIllegalArgumentException().isThrownBy(() -> configuration.getJournalIssuesUrl(null));
        assertThatIllegalArgumentException().isThrownBy(() -> configuration.getJournalIssuesUrl(0L));
        assertThatIllegalArgumentException().isThrownBy(() -> configuration.getJournalIssuesUrl(-1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://journals.example.test/issues", "javascript:open({journalId})",
            "https://user:password@journals.example.test/issues/{journalId}",
            "https://journals.example.test/{journalId}?id={journalId}",
            "https://journals.example.test/issues/{journalId}#fragment"})
    void rejectsInvalidIssuesLinkTemplates(String template) {
        var configuration = new ConfigurationService();
        ReflectionTestUtils.setField(configuration, "journalIssuesUrlTemplate", template);
        assertThatIllegalStateException().isThrownBy(configuration::validateJournalLinks);
    }

    @Test
    void exposesOnlyPublishedYearsAndUsesCurrentYearWhenOmitted() {
        var configuration = new ConfigurationService();
        configuration.validateSearchYears();
        assertThat(configuration.getAvailableSearchYears()).containsExactly(2025, 2024);
        assertThat(configuration.resolveSearchYear(null)).isEqualTo(2024);
        assertThat(configuration.resolveSearchYear(2024)).isEqualTo(2024);
        assertThat(configuration.resolveSearchYear(2025)).isEqualTo(2025);
        assertThatIllegalArgumentException().isThrownBy(() -> configuration.resolveSearchYear(2023));
    }

    @Test
    void newDefaultRetainsPublishedHistoricalYear() {
        var configuration = spy(new ConfigurationService());
        doReturn(2025).when(configuration).getCurrentRatingYear();
        doReturn(List.of(2025, 2024)).when(configuration).getAvailableSearchYears();
        configuration.validateSearchYears();
        assertThat(configuration.resolveSearchYear(null)).isEqualTo(2025);
        assertThat(configuration.resolveSearchYear(2024)).isEqualTo(2024);
    }

    @Test
    void rejectsMissingDefaultDuplicatedAndUnsortedYears() {
        var configuration = spy(new ConfigurationService());
        for (List<Integer> invalid : List.of(List.<Integer>of(), List.of(2025), List.of(2024, 2024), List.of(2024, 2025))) {
            doReturn(invalid).when(configuration).getAvailableSearchYears();
            assertThatIllegalStateException().isThrownBy(configuration::validateSearchYears);
        }
    }

    @Test
    void currentRatingYearDefaultsTo2024() {
        assertThat(new ConfigurationService().getCurrentRatingYear()).isEqualTo(2024);
    }

    @ParameterizedTest
    @ValueSource(ints = {2024, 2025})
    void frontendRatingYearsFollowTheSharedCurrentRatingYear(int year) {
        var configuration = spy(new ConfigurationService());
        doReturn(year).when(configuration).getCurrentRatingYear();

        var years = configuration.getRatingYears();

        assertThat(years.getCurrentIcvYear()).isEqualTo(year);
        assertThat(years.getPreviousIcvYear()).isEqualTo(year - 1);
        assertThat(configuration.getCurrentMniswYear()).isEqualTo(2024);
    }
}
