package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.survey.Standards;
import com.osatum.wbn.domain.model.survey.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DecisionFeeReaderTest {
    private final DecisionFeeReader reader = new DecisionFeeReader();

    @Test
    void keepsMixedCurrenciesWithoutConversionOrInventingOneRange() {
        var standards = new Standards();
        standards.setSubmissionFee(fee(FeeType.VARIOUS_FEE, detail(5, 20, "eur"), detail(10, 40, "USD")));
        var result = reader.read(standards).getFirst();
        assertThat(result.status()).isEqualTo(State.AVAILABLE);
        assertThat(result.charged()).isTrue();
        assertThat(result.minimum()).isNull(); assertThat(result.maximum()).isNull(); assertThat(result.currency()).isNull();
        assertThat(result.sourceRanges()).extracting(r -> r.currency()).containsExactly("EUR", "USD");
        assertThat(result.sourceRanges().getFirst().maximum()).isEqualByComparingTo("20");
        assertThat(result.exchangeRates()).isEmpty();
        assertThat(reader.classify(standards)).isEqualTo(reader.read(standards));
    }

    @Test
    void singleCurrencyUsesBothBoundsAndZeroRemainsValid() {
        var standards = new Standards();
        standards.setSubmissionFee(fee(FeeType.VARIOUS_FEE, detail(0, 10, "EUR"), detail(20, 30, "EUR")));
        var result = reader.read(standards).getFirst();
        assertThat(result.minimum()).isZero(); assertThat(result.maximum()).isEqualByComparingTo("30");
        assertThat(result.currency()).isEqualTo("EUR");
        standards.setSubmissionFee(fee(FeeType.ONE_FEE, detail(0, 0, null)));
        assertThat(reader.read(standards).getFirst().charged()).isFalse();
        standards.setSubmissionFee(fee(FeeType.NO_FEE));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.AVAILABLE);
        assertThat(reader.read(standards).getFirst().charged()).isFalse();
    }

    @ParameterizedTest @CsvSource({"0,0,false", "0,100,true", "1500,0,true", "1500,350,true"})
    void considersBothCountryVariantsIncludingAChargeForOnlyOneGroup(int domestic, int foreign, boolean charged) {
        var source = country(domestic, foreign);
        source.setFeeSelectionStatus(FeeSelectionStatus.LEGACY);
        var selection = new CountrySpecificFeeSelection(); selection.setAuthorCountryCode("PL");
        selection.setEffectiveVariant(FeeAuthorVariant.INTERNAL_AUTHORS); source.setCountrySpecificFeeSelection(selection);
        var standards = new Standards(); standards.setSubmissionFee(source);
        var result = reader.read(standards).getFirst();
        assertThat(result.status()).isEqualTo(State.AVAILABLE); assertThat(result.charged()).isEqualTo(charged);
        assertThat(result.effectiveVariant()).isNull(); assertThat(result.selectionStatus()).isEqualTo(FeeSelectionStatus.LEGACY);
        assertThat(result.variants()).extracting(v -> v.variant()).containsExactly(FeeAuthorVariant.INTERNAL_AUTHORS, FeeAuthorVariant.EXTERNAL_AUTHORS);
        assertThat(result.variants().getFirst().minimum()).isEqualByComparingTo(Integer.toString(domestic));
        assertThat(result.variants().get(1).maximum()).isEqualByComparingTo(Integer.toString(foreign));
        selection.setEffectiveVariant(FeeAuthorVariant.EXTERNAL_AUTHORS);
        source.setFeeSelectionStatus(FeeSelectionStatus.INVALID);
        assertThat(reader.read(standards).getFirst().charged()).isEqualTo(charged);
        assertThat(reader.read(standards).getFirst().variants()).isEqualTo(result.variants());
    }

    @ParameterizedTest @EnumSource(FeeCategory.class)
    void allFiveCategoriesUseTheSameCountryClassificationAndOtherNeverExposesAmounts(FeeCategory category) {
        var standards = new Standards(); var source = country(0, 100);
        switch (category) {
            case SUBMISSION -> standards.setSubmissionFee(source); case PUBLICATION -> standards.setPublicationFee(source);
            case OPEN_ACCESS -> standards.setOpenAccessFee(source); case RAPID_REVIEW -> standards.setRapidReview(source);
            case OTHER -> standards.setApc(source);
        }
        var result = reader.read(standards).stream().filter(f -> f.category() == category).findFirst().orElseThrow();
        assertThat(result.charged()).isTrue(); assertThat(result.status()).isEqualTo(State.AVAILABLE);
        if (category == FeeCategory.OTHER) {
            assertThat(result.variants()).isEmpty(); assertThat(result.sourceRanges()).isEmpty();
            assertThat(result.minimum()).isNull(); assertThat(result.maximum()).isNull();
        } else assertThat(result.variants()).hasSize(2);
    }

    @Test
    void missingDuplicateUnknownAndInvalidCountryDetailsNeverBecomeFree() {
        var standards = new Standards(); var internal = detail(0, 0, "PLN"); internal.setDescription("INTERNAL_AUTHORS");
        standards.setSubmissionFee(fee(FeeType.COUNTRY_SPECIFIC_FEE, internal));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.MISSING);
        assertThat(reader.read(standards).getFirst().charged()).isNull();
        standards.setSubmissionFee(fee(FeeType.COUNTRY_SPECIFIC_FEE, internal, internal));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.INVALID);
        standards.setSubmissionFee(fee(FeeType.COUNTRY_SPECIFIC_FEE, internal, detail(0, 100, "EUR")));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.INVALID);
        var source = country(0, 100); source.getFeeDetails().get(1).setMaxValue(-1);
        standards.setSubmissionFee(source);
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.INVALID);
        assertThat(reader.read(standards).getFirst().charged()).isNull();
    }

    @Test
    void incompleteAndInvalidRangesNeverUseAPartialSubset() {
        var standards = new Standards(); var incomplete = detail(0, 10, "PLN"); incomplete.setMinValue(null);
        standards.setSubmissionFee(fee(FeeType.VARIOUS_FEE, detail(0, 0, "PLN"), incomplete));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.MISSING);
        assertThat(reader.read(standards).getFirst().charged()).isNull();
        standards.setSubmissionFee(fee(FeeType.ONE_FEE, detail(20, 10, "PLN")));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.INVALID);
        standards.setSubmissionFee(fee(FeeType.ONE_FEE, detail(0, 10, null)));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.INVALID);
        standards.setSubmissionFee(fee(FeeType.ONE_FEE, detail(0, 10, "PLN"), detail(0, 10, "PLN")));
        assertThat(reader.read(standards).getFirst().status()).isEqualTo(State.INVALID);
    }

    private AuthorsFee country(int domestic, int foreign) {
        var internal = detail(domestic, domestic, "PLN"); internal.setDescription("INTERNAL_AUTHORS");
        var external = detail(foreign, foreign, "EUR"); external.setDescription("EXTERNAL_AUTHORS");
        return fee(FeeType.COUNTRY_SPECIFIC_FEE, external, internal);
    }
    private AuthorsFee fee(FeeType type, FeeDetail... details) { var result = new AuthorsFee(); result.setFeeType(type); result.setFeeDetails(List.of(details)); return result; }
    private FeeDetail detail(int min, int max, String currency) { var result = new FeeDetail(); result.setMinValue(min); result.setMaxValue(max); result.setCurrencyCode(currency); return result; }
}
