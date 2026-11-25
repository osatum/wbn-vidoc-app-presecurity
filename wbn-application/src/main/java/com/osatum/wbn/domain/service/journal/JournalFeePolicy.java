package com.osatum.wbn.domain.service.journal;

import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.journal.JournalFeeTerms;
import com.osatum.wbn.domain.model.survey.Standards;
import com.osatum.wbn.domain.model.survey.model.AuthorsFee;
import com.osatum.wbn.domain.model.survey.model.FeeDetail;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionSource;
import com.osatum.wbn.domain.model.survey.model.FeeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Buduje wspólną interpretację opłat ankiety dla indeksowania, filtrowania i prezentacji P1.
 * Zachowuje oryginalne waluty i wybiera wariant autora według kraju czasopisma; nie sumuje różnych kategorii opłat.
 */
@Service
@RequiredArgsConstructor
public class JournalFeePolicy {
    private final AuthorFeeSelectionPolicy selectionPolicy;

    /**
     * Tworzy projekcję wszystkich pięciu kategorii, aby brak opłat oceniać osobno w każdej z nich.
     */
    public List<JournalFeeTerms> project(Standards standards, String journalCountryCode) {
        return Arrays.stream(FeeCategory.values())
                .map(category -> project(category, category.from(standards), journalCountryCode)).toList();
    }

    /**
     * Zachowuje dane źródłowe i wybiera kwoty dopuszczalne dla typu opłaty; dla przedziałów używa maxValue.
     * Wariant zależy od tego samego kraju czasopisma, który jest używany w wynikach i filtrach P1.
     */
    public JournalFeeTerms project(FeeCategory category, AuthorsFee fee, String journalCountryCode) {
        var details = new ArrayList<JournalFeeTerms.Detail>();
        var amounts = new ArrayList<JournalFeeTerms.Amount>();
        var resolution = selectionPolicy.resolve(fee, journalCountryCode);
        FeeType type = fee == null ? null : fee.getFeeType();
        JournalFeeTerms.Selection selectionCopy;
        if (type == FeeType.COUNTRY_SPECIFIC_FEE) {
            var variant = selectionPolicy.variantForCountry(journalCountryCode);
            selectionCopy = new JournalFeeTerms.Selection("PL",
                    variant == null ? FeeSelectionSource.UNRESOLVED : FeeSelectionSource.JOURNAL_COUNTRY, null, variant);
        } else {
            var selection = fee == null ? null : fee.getCountrySpecificFeeSelection();
            selectionCopy = selection == null ? null : new JournalFeeTerms.Selection(selection.getAuthorCountryCode(),
                    selection.getSource(), selection.getOverrideVariant(), selection.getEffectiveVariant());
        }
        List<FeeDetail> source = fee == null || fee.getFeeDetails() == null ? List.of() : fee.getFeeDetails();
        int applicableCount = 0;
        for (int position = 0; position < source.size(); position++) {
            FeeDetail detail = source.get(position);
            details.add(detail == null ? new JournalFeeTerms.Detail(null, null, null, null)
                    : new JournalFeeTerms.Detail(detail.getMinValue(), detail.getMaxValue(), detail.getCurrencyCode(), detail.getDescription()));
            boolean applicable = type == FeeType.VARIOUS_FEE
                    || (type == FeeType.ONE_FEE && source.size() == 1)
                    || (type == FeeType.COUNTRY_SPECIFIC_FEE && detail != null && detail == resolution.selectedFeeDetail());
            if (applicable) {
                applicableCount++;
            }
            if (applicable && isNumeric(detail)) {
                String currency = detail.getCurrencyCode() != null && detail.getCurrencyCode().trim().matches("[A-Za-z]{3}")
                        ? detail.getCurrencyCode().trim().toUpperCase(Locale.ROOT) : null;
                amounts.add(new JournalFeeTerms.Amount(position, detail.getMaxValue(), currency, detail.getDescription()));
            }
        }
        boolean noFee = type == FeeType.NO_FEE || (!amounts.isEmpty() && amounts.size() == applicableCount
                && amounts.stream().allMatch(amount -> amount.value() == 0));
        return new JournalFeeTerms(category, type, fee == null ? null : fee.getFeeSelectionStatus(), resolution.status(),
                selectionCopy, details, amounts, noFee, noFee || !amounts.isEmpty());
    }

    /**
     * Rozpoznaje potrzebę porównania dodatnich kwot w różnych walutach wewnątrz jednej kategorii.
     */
    public boolean needsRates(JournalFeeTerms fee) {
        return fee.amounts().stream().filter(amount -> amount.value() > 0).map(JournalFeeTerms.Amount::currency).distinct().count() > 1;
    }

    /**
     * Wybiera najwyższą porównywalną kwotę, używając kursów tylko do porównania różnych walut.
     * Zwracana kwota zachowuje walutę źródłową; przy remisie pozostaje pierwsza pozycja z eksportu.
     */
    public JournalFeeTerms.Amount select(JournalFeeTerms fee, Map<String, BigDecimal> rates) {
        if (fee.noFee() || !fee.comparable()) {
            return null;
        }
        boolean convert = needsRates(fee);
        Comparator<JournalFeeTerms.Amount> comparator = Comparator.comparing(amount -> {
            BigDecimal value = BigDecimal.valueOf(amount.value());
            if (!convert || value.signum() == 0) {
                return value;
            }
            BigDecimal rate = rates.get(amount.currency());
            if (rate == null || rate.signum() <= 0) {
                throw new IllegalStateException("Missing exchange rate for " + amount.currency());
            }
            return value.multiply(rate);
        });
        return fee.amounts().stream().reduce((selected, candidate) -> comparator.compare(candidate, selected) > 0 ? candidate : selected).orElse(null);
    }

    private boolean isNumeric(FeeDetail detail) {
        if (detail == null || detail.getMaxValue() == null || detail.getMaxValue() < 0
                || (detail.getMinValue() != null && (detail.getMinValue() < 0 || detail.getMinValue() > detail.getMaxValue()))) {
            return false;
        }
        return detail.getMaxValue() == 0 || (detail.getCurrencyCode() != null && detail.getCurrencyCode().trim().matches("[A-Za-z]{3}"));
    }
}
