package com.osatum.wbn.domain.service.journal;

import com.osatum.wbn.domain.model.survey.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorFeeSelectionPolicyTest {
    private final AuthorFeeSelectionPolicy policy = new AuthorFeeSelectionPolicy();

    @ParameterizedTest
    @CsvSource({
            "PL, , INTERNAL_AUTHORS, JOURNAL_COUNTRY",
            "' pl ', , INTERNAL_AUTHORS, JOURNAL_COUNTRY",
            "DE, , EXTERNAL_AUTHORS, JOURNAL_COUNTRY",
            "PL, EXTERNAL_AUTHORS, EXTERNAL_AUTHORS, JOURNAL_OVERRIDE",
            "DE, INTERNAL_AUTHORS, INTERNAL_AUTHORS, JOURNAL_OVERRIDE",
            ", INTERNAL_AUTHORS, INTERNAL_AUTHORS, JOURNAL_OVERRIDE"
    })
    void selectsByDescriptionRegardlessOfOrder(String country, FeeAuthorVariant override,
                                               FeeAuthorVariant effective, FeeSelectionSource source) {
        AuthorsFee fee = fee(override, effective, source);

        var result = policy.validate(fee, "1.1", country);

        assertThat(result.status()).isEqualTo(FeeSelectionStatus.RESOLVED);
        assertThat(result.selectedFeeDetail().getDescription()).isEqualTo(effective.name());
        assertThat(result.selectedFeeDetail().getMinValue()).isEqualTo(100);
        assertThat(result.selectedFeeDetail().getMaxValue()).isEqualTo(200);
        assertThat(result.selectedFeeDetail().getCurrencyCode()).isEqualTo("PLN");
        assertThat(fee.getFeeDetails()).hasSize(2);
    }

    @Test
    void preservesUnresolvedSelectionWithoutUsingZeroOrAnyDetail() {
        AuthorsFee fee = fee(null, null, FeeSelectionSource.UNRESOLVED);
        assertThat(policy.validate(fee, "1.1", "  ").status()).isEqualTo(FeeSelectionStatus.UNRESOLVED);
        assertThat(policy.validate(fee, "1.1", null).selectedFeeDetail()).isNull();
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INVALID);
        assertThat(fee.getFeeDetails()).hasSize(2);
    }

    @Test
    void distinguishesMissingSelectionInLegacyAndCurrentSchema() {
        AuthorsFee fee = fee(null, null, null);
        fee.setCountrySpecificFeeSelection(null);
        assertThat(policy.validate(fee, "1.0", "PL").status()).isEqualTo(FeeSelectionStatus.LEGACY);
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INCOMPLETE);
        assertThat(policy.resolve(fee).selectedFeeDetail()).isNull();
    }

    @Test
    void rejectsMissingAndDuplicateMatchingDetails() {
        AuthorsFee fee = fee(null, FeeAuthorVariant.INTERNAL_AUTHORS, FeeSelectionSource.JOURNAL_COUNTRY);
        fee.setFeeDetails(List.of(detail(FeeAuthorVariant.EXTERNAL_AUTHORS)));
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INVALID);
        fee.setFeeDetails(List.of(detail(FeeAuthorVariant.INTERNAL_AUTHORS), detail(FeeAuthorVariant.INTERNAL_AUTHORS)));
        assertThat(policy.validate(fee, "1.1", "PL").selectedFeeDetail()).isNull();
        fee.setFeeDetails(null);
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INVALID);
    }

    @ParameterizedTest
    @CsvSource({
            "PL, INTERNAL_AUTHORS, EXTERNAL_AUTHORS, JOURNAL_OVERRIDE",
            "PL, , EXTERNAL_AUTHORS, JOURNAL_OVERRIDE",
            "PL, , EXTERNAL_AUTHORS, JOURNAL_COUNTRY",
            ", , INTERNAL_AUTHORS, JOURNAL_COUNTRY",
            "PL, INTERNAL_AUTHORS, INTERNAL_AUTHORS, JOURNAL_COUNTRY",
            ", INTERNAL_AUTHORS, , UNRESOLVED"
    })
    void rejectsInconsistentSelection(String country, FeeAuthorVariant override,
                                     FeeAuthorVariant effective, FeeSelectionSource source) {
        assertThat(policy.validate(fee(override, effective, source), "1.1", country).status())
                .isEqualTo(FeeSelectionStatus.INVALID);
    }

    @Test
    void doesNotApplySelectionToAuthorsFromOtherCountries() {
        AuthorsFee fee = fee(null, FeeAuthorVariant.INTERNAL_AUTHORS, FeeSelectionSource.JOURNAL_COUNTRY);
        fee.getCountrySpecificFeeSelection().setAuthorCountryCode("DE");
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INVALID);
        fee.setFeeSelectionStatus(FeeSelectionStatus.RESOLVED);
        assertThat(policy.resolve(fee).selectedFeeDetail()).isNull();
    }

    @Test
    void requiresCountrySourceAndEffectiveVariantInNewSelection() {
        AuthorsFee fee = fee(null, FeeAuthorVariant.INTERNAL_AUTHORS, FeeSelectionSource.JOURNAL_COUNTRY);
        fee.getCountrySpecificFeeSelection().setAuthorCountryCode(null);
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INCOMPLETE);
        fee.getCountrySpecificFeeSelection().setAuthorCountryCode("PL");
        fee.getCountrySpecificFeeSelection().setSource(null);
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INCOMPLETE);
        fee.getCountrySpecificFeeSelection().setSource(FeeSelectionSource.JOURNAL_COUNTRY);
        fee.getCountrySpecificFeeSelection().setEffectiveVariant(null);
        assertThat(policy.validate(fee, "1.1", "PL").status()).isEqualTo(FeeSelectionStatus.INCOMPLETE);
    }

    @Test
    void resolvesOnlyValidatedSelectionAndRechecksDetailUniqueness() {
        AuthorsFee fee = fee(null, FeeAuthorVariant.INTERNAL_AUTHORS, FeeSelectionSource.JOURNAL_COUNTRY);
        assertThat(policy.resolve(fee).status()).isEqualTo(FeeSelectionStatus.INCOMPLETE);
        fee.setFeeSelectionStatus(policy.validate(fee, "1.1", "PL").status());
        assertThat(policy.resolve(fee).selectedFeeDetail()).isNotNull();
        fee.setFeeDetails(List.of());
        assertThat(policy.resolve(fee).status()).isEqualTo(FeeSelectionStatus.INVALID);
    }

    @ParameterizedTest
    @CsvSource({"ONE_FEE", "VARIOUS_FEE", "NO_FEE"})
    void leavesOtherFeeTypesUnchanged(FeeType type) {
        AuthorsFee fee = new AuthorsFee();
        fee.setFeeType(type);
        fee.setFeeDetails(null);
        assertThat(policy.validate(fee, "1.1", "PL").status()).isNull();
        assertThat(policy.resolve(fee).selectedFeeDetail()).isNull();
    }

    private AuthorsFee fee(FeeAuthorVariant override, FeeAuthorVariant effective, FeeSelectionSource source) {
        CountrySpecificFeeSelection selection = new CountrySpecificFeeSelection();
        selection.setAuthorCountryCode("PL");
        selection.setOverrideVariant(override);
        selection.setEffectiveVariant(effective);
        selection.setSource(source);
        AuthorsFee fee = new AuthorsFee();
        fee.setFeeType(FeeType.COUNTRY_SPECIFIC_FEE);
        fee.setCountrySpecificFeeSelection(selection);
        fee.setFeeDetails(List.of(detail(FeeAuthorVariant.EXTERNAL_AUTHORS), detail(FeeAuthorVariant.INTERNAL_AUTHORS)));
        return fee;
    }

    @ParameterizedTest
    @CsvSource({"PL, INTERNAL_AUTHORS", "' pl ', INTERNAL_AUTHORS", "DE, EXTERNAL_AUTHORS", "US, EXTERNAL_AUTHORS"})
    void p1SelectsByJournalCountryWithoutRequiringExportMetadata(String country, FeeAuthorVariant expected) {
        AuthorsFee fee = fee(null, null, null);
        fee.setCountrySpecificFeeSelection(null);
        assertThat(policy.resolve(fee, country).status()).isEqualTo(FeeSelectionStatus.RESOLVED);
        assertThat(policy.resolve(fee, country).selectedFeeDetail().getDescription()).isEqualTo(expected.name());
        assertThat(fee.getCountrySpecificFeeSelection()).isNull();
        assertThat(fee.getFeeSelectionStatus()).isNull();
    }

    @Test
    void p1IgnoresOverridesAndLegacyStatusesWithoutChangingSourceMetadata() {
        AuthorsFee fee = fee(FeeAuthorVariant.EXTERNAL_AUTHORS, FeeAuthorVariant.EXTERNAL_AUTHORS, FeeSelectionSource.JOURNAL_OVERRIDE);
        for (var status : FeeSelectionStatus.values()) {
            fee.setFeeSelectionStatus(status);
            assertThat(policy.resolve(fee, "PL").selectedFeeDetail().getDescription()).isEqualTo("INTERNAL_AUTHORS");
            assertThat(fee.getFeeSelectionStatus()).isEqualTo(status);
            assertThat(fee.getCountrySpecificFeeSelection().getEffectiveVariant()).isEqualTo(FeeAuthorVariant.EXTERNAL_AUTHORS);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {" ", "POL", "?", "12"})
    void p1DoesNotGuessAVariantWithoutAValidCountry(String country) {
        AuthorsFee fee = fee(FeeAuthorVariant.INTERNAL_AUTHORS, FeeAuthorVariant.INTERNAL_AUTHORS, FeeSelectionSource.JOURNAL_OVERRIDE);
        assertThat(policy.resolve(fee, country).status()).isEqualTo(FeeSelectionStatus.UNRESOLVED);
        assertThat(policy.resolve(fee, country).selectedFeeDetail()).isNull();
    }

    @Test
    void p1DoesNotFallBackToAnotherVariantWhenTheSelectedDetailIsMissingOrDuplicated() {
        AuthorsFee fee = fee(null, null, null);
        fee.setCountrySpecificFeeSelection(null);
        fee.setFeeDetails(List.of(detail(FeeAuthorVariant.EXTERNAL_AUTHORS)));
        assertThat(policy.resolve(fee, "PL").status()).isEqualTo(FeeSelectionStatus.INVALID);
        fee.setFeeDetails(List.of(detail(FeeAuthorVariant.INTERNAL_AUTHORS), detail(FeeAuthorVariant.INTERNAL_AUTHORS)));
        assertThat(policy.resolve(fee, "PL").selectedFeeDetail()).isNull();
    }

    private FeeDetail detail(FeeAuthorVariant variant) {
        FeeDetail detail = new FeeDetail();
        detail.setDescription(variant.name());
        detail.setMinValue(100);
        detail.setMaxValue(200);
        detail.setCurrencyCode("PLN");
        return detail;
    }
}
