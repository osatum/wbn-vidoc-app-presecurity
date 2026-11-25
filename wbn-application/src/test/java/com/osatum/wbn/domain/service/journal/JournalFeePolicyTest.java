package com.osatum.wbn.domain.service.journal;

import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.journal.JournalFeeTerms;
import com.osatum.wbn.domain.model.survey.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JournalFeePolicyTest {
    private final JournalFeePolicy policy = new JournalFeePolicy(new AuthorFeeSelectionPolicy());

    @ParameterizedTest
    @EnumSource(FeeCategory.class)
    void preservesOriginalRangeAndUsesItsMaximumWithoutMutatingTheSurvey(FeeCategory category) {
        AuthorsFee fee = fee(FeeType.ONE_FEE, detail(10, 100, " usd ", "Range"));
        JournalFeeTerms terms = policy.project(category, fee, "PL");
        assertThat(terms.category()).isEqualTo(category);
        assertThat(terms.details().getFirst().currencyCode()).isEqualTo(" usd ");
        assertThat(terms.details().getFirst().minValue()).isEqualTo(10);
        assertThat(terms.amounts()).containsExactly(new JournalFeeTerms.Amount(0, 100, "USD", "Range"));
        assertThat(fee.getFeeDetails().getFirst().getCurrencyCode()).isEqualTo(" usd ");
        assertThat(terms.noFee()).isFalse();
    }

    @Test
    void zeroIsRealAndDoesNotRequireACurrencyOrARate() {
        var terms = policy.project(FeeCategory.PUBLICATION, fee(FeeType.ONE_FEE, detail(0, 0, null, null)), "PL");
        assertThat(terms.noFee()).isTrue();
        assertThat(terms.comparable()).isTrue();
        assertThat(policy.select(terms, Map.of())).isNull();
        assertThat(policy.project(FeeCategory.PUBLICATION, fee(FeeType.NO_FEE), "PL").noFee()).isTrue();
    }

    @Test
    void aKnownZeroAlongsideAnUnspecifiedVariousVariantDoesNotDeclareTheCategoryFree() {
        var terms = policy.project(FeeCategory.PUBLICATION, fee(FeeType.VARIOUS_FEE,
                detail(0, 0, "USD", "free variant"), detail(null, null, null, "unspecified")), "PL");
        assertThat(terms.noFee()).isFalse();
        assertThat(terms.comparable()).isTrue();
        assertThat(policy.select(terms, Map.of()).value()).isZero();
        assertThat(policy.select(terms, Map.of()).currency()).isEqualTo("USD");
    }

    @Test
    void missingAndInvalidAmountsAreNotFreeOrComparable() {
        for (FeeDetail detail : List.of(detail(null, null, "PLN", null), detail(0, -1, "PLN", null),
                detail(11, 10, "PLN", null), detail(0, 10, null, null))) {
            var terms = policy.project(FeeCategory.PUBLICATION, fee(FeeType.ONE_FEE, detail), "PL");
            assertThat(terms.noFee()).isFalse();
            assertThat(terms.comparable()).isFalse();
        }
        assertThat(policy.project(FeeCategory.PUBLICATION, null, "PL").noFee()).isFalse();
        assertThat(policy.project(FeeCategory.PUBLICATION, fee(FeeType.ONE_FEE,
                detail(0, 10, "PLN", null), detail(0, 20, "PLN", null)), "PL").comparable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(FeeAuthorVariant.class)
    void usesJournalCountryRegardlessOfStoredOverrideOrDetailOrder(FeeAuthorVariant variant) {
        AuthorsFee fee = fee(FeeType.COUNTRY_SPECIFIC_FEE,
                detail(0, 200, "EUR", "EXTERNAL_AUTHORS"), detail(0, 10, "USD", "INTERNAL_AUTHORS"));
        var selection = new CountrySpecificFeeSelection();
        selection.setAuthorCountryCode("PL");
        selection.setSource(FeeSelectionSource.JOURNAL_OVERRIDE);
        selection.setOverrideVariant(variant);
        selection.setEffectiveVariant(variant);
        fee.setCountrySpecificFeeSelection(selection);
        fee.setFeeSelectionStatus(FeeSelectionStatus.RESOLVED);
        JournalFeeTerms terms = policy.project(FeeCategory.PUBLICATION, fee, variant == FeeAuthorVariant.INTERNAL_AUTHORS ? "PL" : "DE");
        assertThat(terms.amounts()).singleElement().satisfies(amount -> assertThat(amount.description()).isEqualTo(variant.name()));
        assertThat(terms.selection().effectiveVariant()).isEqualTo(variant);
        assertThat(terms.selection().source()).isEqualTo(FeeSelectionSource.JOURNAL_COUNTRY);
        assertThat(terms.selection().overrideVariant()).isNull();
        assertThat(fee.getFeeSelectionStatus()).isEqualTo(FeeSelectionStatus.RESOLVED);
        assertThat(terms.details()).hasSize(2);
    }

    @ParameterizedTest
    @EnumSource(value = FeeSelectionStatus.class, names = "RESOLVED", mode = EnumSource.Mode.EXCLUDE)
    void missingCountryNeverBecomesZeroOrNoFeeRegardlessOfStoredStatus(FeeSelectionStatus status) {
        AuthorsFee fee = fee(FeeType.COUNTRY_SPECIFIC_FEE, detail(0, 0, "PLN", "INTERNAL_AUTHORS"));
        fee.setFeeSelectionStatus(status);
        JournalFeeTerms terms = policy.project(FeeCategory.PUBLICATION, fee, null);
        assertThat(terms.selectionStatus()).isEqualTo(status);
        assertThat(terms.comparable()).isFalse();
        assertThat(terms.noFee()).isFalse();
    }

    @Test
    void retainsTheStoredStatusButNeverSelectsAnAmbiguousCountryDetail() {
        var fee = fee(FeeType.COUNTRY_SPECIFIC_FEE, detail(0, 0, "PLN", "INTERNAL_AUTHORS"),
                detail(0, 10, "PLN", "INTERNAL_AUTHORS"));
        var selection = new CountrySpecificFeeSelection();
        selection.setAuthorCountryCode("PL");
        selection.setSource(FeeSelectionSource.JOURNAL_OVERRIDE);
        selection.setOverrideVariant(FeeAuthorVariant.INTERNAL_AUTHORS);
        selection.setEffectiveVariant(FeeAuthorVariant.INTERNAL_AUTHORS);
        fee.setCountrySpecificFeeSelection(selection);
        fee.setFeeSelectionStatus(FeeSelectionStatus.RESOLVED);
        var terms = policy.project(FeeCategory.PUBLICATION, fee, "PL");
        assertThat(terms.selectionStatus()).isEqualTo(FeeSelectionStatus.RESOLVED);
        assertThat(terms.resolutionStatus()).isEqualTo(FeeSelectionStatus.INVALID);
        assertThat(terms.amounts()).isEmpty();
        assertThat(terms.noFee()).isFalse();
        assertThat(terms.details()).hasSize(2);
    }

    @Test
    void selectsTheHighestVariousFeeUsingExactCurrentRatesNotAValueFrozenAtImport() {
        var fee = fee(FeeType.VARIOUS_FEE, detail(0, 100, "USD", "first"), detail(0, 200, "EUR", "second"),
                detail(null, null, "PLN", "Text only"));
        var terms = policy.project(FeeCategory.PUBLICATION, fee, "PL");
        assertThat(terms.amounts()).hasSize(2);
        assertThat(terms.details()).hasSize(3);
        assertThat(policy.select(terms, Map.of("USD", new BigDecimal("4.1234"), "EUR", new BigDecimal("3.0001"))).description()).isEqualTo("second");
        assertThat(policy.select(terms, Map.of("USD", new BigDecimal("7.0003"), "EUR", new BigDecimal("2.0001"))).description()).isEqualTo("first");
        assertThat(terms.amounts()).extracting(JournalFeeTerms.Amount::currency).containsExactly("USD", "EUR");
        assertThatThrownBy(() -> policy.select(terms, Map.of("USD", BigDecimal.ONE))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resolvesEqualVariousFeesByOriginalOrderAndDoesNotNeedRatesForOneCurrency() {
        var terms = policy.project(FeeCategory.PUBLICATION, fee(FeeType.VARIOUS_FEE,
                detail(0, 100, "USD", "first"), detail(0, 100, "USD", "second")), "PL");
        assertThat(policy.needsRates(terms)).isFalse();
        assertThat(policy.select(terms, Map.of()).description()).isEqualTo("first");
    }

    private AuthorsFee fee(FeeType type, FeeDetail... details) {
        var fee = new AuthorsFee();
        fee.setFeeType(type);
        fee.setFeeDetails(List.of(details));
        return fee;
    }

    @ParameterizedTest
    @EnumSource(FeeCategory.class)
    void allCategoriesUseTheSelectedCountryVariantAndOriginalCurrencyWithoutMetadata(FeeCategory category) {
        var fee = fee(FeeType.COUNTRY_SPECIFIC_FEE,
                detail(350, 350, "EUR", "EXTERNAL_AUTHORS"), detail(1500, 1500, "PLN", "INTERNAL_AUTHORS"));
        var polish = policy.project(category, fee, "PL");
        assertThat(polish.amounts()).containsExactly(new JournalFeeTerms.Amount(1, 1500, "PLN", "INTERNAL_AUTHORS"));
        assertThat(polish.comparable()).isTrue();
        var foreign = policy.project(category, fee, "DE");
        assertThat(foreign.amounts()).containsExactly(new JournalFeeTerms.Amount(0, 350, "EUR", "EXTERNAL_AUTHORS"));
        assertThat(foreign.comparable()).isTrue();
        assertThat(fee.getCountrySpecificFeeSelection()).isNull();
    }

    private FeeDetail detail(Integer minimum, Integer maximum, String currency, String description) {
        var detail = new FeeDetail();
        detail.setMinValue(minimum);
        detail.setMaxValue(maximum);
        detail.setCurrencyCode(currency);
        detail.setDescription(description);
        return detail;
    }
}
