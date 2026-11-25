package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.dictionary.service.DictionaryRepositoryService;
import com.osatum.wbn.application.dictionary.service.DictionaryScientificCategoryService;
import com.osatum.wbn.domain.model.survey.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.osatum.wbn.application.journal.service.DecisionMetric.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionMetricReaderTest {
    @Test
    void countryFeesReachAllFiveBenchmarksAndExcludeTheOwnJournal() {
        var reader = new DecisionMetricReader(new DecisionFeeReader());
        var population = new java.util.ArrayList<DecisionObservation>();
        for (int i = 0; i <= 30; i++) {
            var survey = DecisionFixture.survey(2024, 20);
            var internal = new FeeDetail(); internal.setDescription("INTERNAL_AUTHORS"); internal.setCurrencyCode("PLN");
            internal.setMinValue(i % 3 == 1 ? 100 : 0); internal.setMaxValue(internal.getMinValue());
            var external = new FeeDetail(); external.setDescription("EXTERNAL_AUTHORS"); external.setCurrencyCode("EUR");
            external.setMinValue(i % 3 == 2 || i == 0 ? 100 : 0); external.setMaxValue(external.getMinValue());
            var fee = new AuthorsFee(); fee.setFeeType(FeeType.COUNTRY_SPECIFIC_FEE);
            fee.setFeeSelectionStatus(FeeSelectionStatus.LEGACY); fee.setFeeDetails(List.of(internal, external));
            var standards = survey.getStandards();
            standards.setSubmissionFee(fee); standards.setPublicationFee(fee); standards.setOpenAccessFee(fee);
            standards.setRapidReview(fee); standards.setApc(fee);
            population.add(new DecisionObservation((long) i + 1, java.util.Set.of(1008L), reader.read(survey, 2024, 81.125)));
        }
        var results = new DecisionBenchmarkCalculator().results(1L, 1008L, population.getFirst().values(), population);
        assertThat(results.stream().filter(r -> r.metric().isFee()).toList()).hasSize(5).allSatisfy(result -> {
            assertThat(result.available()).isTrue(); assertThat(result.validCount()).isEqualTo(30);
            assertThat(result.chargedCount()).isEqualTo(20);
            assertThat(result.chargedPercent()).isCloseTo(2000.0 / 30, within(1e-10));
        });
    }

    @Test
    void ownAndPopulationMetricsUseTheSameValidationAndFeeSelectionWithoutRequiringRates() {
        var fees = new DecisionFeeReader();
        var reader = new DecisionMetricReader(fees);
        var survey = DecisionFixture.survey(2025, 0);
        survey.getStandards().getAvgManuscriptTimes().setReview(-1);
        survey.getCooperation().getDirectorsMetric().setForeignAffiliationPercentage(101.0);
        var old = new ArticlePublished(); old.setYear(2024); old.setReceivedManuscriptCount(1); old.setRejectedManuscriptCount(1);
        survey.getStability().setArticlePublished(List.of(old, survey.getStability().getArticlePublished().getFirst()));
        var fee = survey.getStandards().getSubmissionFee(); fee.setFeeType(FeeType.VARIOUS_FEE);
        var euro = new FeeDetail(); euro.setMinValue(0); euro.setMaxValue(100); euro.setCurrencyCode("EUR");
        var usd = new FeeDetail(); usd.setMinValue(0); usd.setMaxValue(100); usd.setCurrencyCode("USD");
        fee.setFeeDetails(List.of(euro, usd));
        var metrics = reader.read(survey, 2025, 81.125);
        assertThat(metrics).containsEntry(REJECTION_RATE, 25.0).containsEntry(FOREIGN_AUTHORS, .5)
                .containsEntry(INITIAL_ASSESSMENT, 0.0).containsEntry(SUBMISSION_FEE, 1.0)
                .doesNotContainKeys(REVIEW, FOREIGN_COUNCIL);
        var annual = new DecisionAnnualReader(new DecisionProfileReader(mock(DictionaryScientificCategoryService.class),
                mock(DictionaryRepositoryService.class)), fees).read(survey, 2025);
        assertThat(DecisionMetricReader.own(annual, 81.125)).isEqualTo(metrics);
    }
}
