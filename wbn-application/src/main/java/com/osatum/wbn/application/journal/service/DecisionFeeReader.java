package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.view.DecisionFeeView;
import com.osatum.wbn.application.journal.view.DecisionFeeView.SourceRange;
import com.osatum.wbn.application.journal.view.DecisionFeeView.Variant;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.survey.Standards;
import com.osatum.wbn.domain.model.survey.model.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/**
 * Odczytuje pełne deklaracje opłat P2 w walutach źródłowych, bez kursów i wyboru wariantu P1.
 * Kategoria jest pobierana, jeśli dowolny poprawny wariant dopuszcza dodatnią kwotę.
 * Brakujące lub sprzeczne szczegóły nie stają się deklaracją braku opłaty.
 */
@Service
public class DecisionFeeReader {
    private static final List<FeeCategory> ORDER = List.of(FeeCategory.SUBMISSION, FeeCategory.PUBLICATION,
            FeeCategory.OPEN_ACCESS, FeeCategory.RAPID_REVIEW, FeeCategory.OTHER);

    public List<DecisionFeeView> read(Standards standards) { return classify(standards); }

    /** Wartość własna i populacja benchmarku korzystają z tej samej kwalifikacji całego cennika. */
    public List<DecisionFeeView> classify(Standards standards) {
        return ORDER.stream().map(category -> prepare(category, category.from(standards))).toList();
    }

    private DecisionFeeView prepare(FeeCategory category, AuthorsFee source) {
        FeeType type = source == null ? null : source.getFeeType();
        if (type == null) return result(category, source, State.MISSING, null, List.of(), List.of());
        if (type == FeeType.NO_FEE) return result(category, source, State.AVAILABLE, false, List.of(), List.of());
        var details = source.getFeeDetails();
        if (details == null || details.isEmpty()) return result(category, source, State.MISSING, null, List.of(), List.of());
        if (type == FeeType.COUNTRY_SPECIFIC_FEE) return countrySpecific(category, source, details);
        if (type == FeeType.ONE_FEE && details.size() != 1) return result(category, source, State.INVALID, null, List.of(), List.of());
        var ranges = new ArrayList<SourceRange>();
        for (var detail : details) {
            State state = state(detail);
            if (state != State.AVAILABLE) return result(category, source, state, null, List.of(), List.of());
            ranges.add(range(detail));
        }
        return result(category, source, State.AVAILABLE, charged(ranges), ranges, List.of());
    }

    private DecisionFeeView countrySpecific(FeeCategory category, AuthorsFee source, List<FeeDetail> details) {
        // Nie wybieramy pierwszego wiersza ani nie zgadujemy znaczenia nieznanych opisów wariantów.
        if (details.stream().anyMatch(d -> d == null || d.getDescription() == null
                || !Set.of("INTERNAL_AUTHORS", "EXTERNAL_AUTHORS").contains(d.getDescription()))) {
            return result(category, source, State.INVALID, null, List.of(), List.of());
        }
        var variants = new ArrayList<Variant>();
        var ranges = new ArrayList<SourceRange>();
        State aggregate = State.AVAILABLE;
        for (var variant : FeeAuthorVariant.values()) {
            var matches = details.stream().filter(d -> variant.name().equals(d.getDescription())).toList();
            var detail = matches.size() == 1 ? matches.getFirst() : null;
            State state = matches.size() > 1 ? State.INVALID : state(detail);
            if (state == State.INVALID || (aggregate == State.AVAILABLE && state != State.AVAILABLE)) aggregate = state;
            var range = state == State.AVAILABLE ? range(detail) : null;
            if (range != null) ranges.add(range);
            variants.add(new Variant(variant, state, range == null ? null : range.maximum().signum() > 0,
                    range == null ? null : range.minimum(), range == null ? null : range.maximum(),
                    range == null ? null : range.currency()));
        }
        return result(category, source, aggregate, aggregate == State.AVAILABLE ? charged(ranges) : null, ranges, variants);
    }

    private State state(FeeDetail detail) {
        if (detail == null || detail.getMinValue() == null || detail.getMaxValue() == null) return State.MISSING;
        String currency = currency(detail);
        return detail.getMinValue() < 0 || detail.getMaxValue() < detail.getMinValue()
                || (detail.getMaxValue() > 0 && (currency == null || !currency.matches("[A-Z]{3}")))
                ? State.INVALID : State.AVAILABLE;
    }

    private String currency(FeeDetail detail) {
        String value = detail.getCurrencyCode();
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private SourceRange range(FeeDetail detail) {
        return new SourceRange(BigDecimal.valueOf(detail.getMinValue()), BigDecimal.valueOf(detail.getMaxValue()), currency(detail));
    }

    private boolean charged(List<SourceRange> ranges) { return ranges.stream().anyMatch(r -> r.maximum().signum() > 0); }

    private DecisionFeeView result(FeeCategory category, AuthorsFee source, State state, Boolean charged,
                                   List<SourceRange> ranges, List<Variant> variants) {
        boolean amounts = category != FeeCategory.OTHER && state == State.AVAILABLE && Boolean.TRUE.equals(charged);
        var currencies = new HashSet<String>();
        ranges.stream().map(SourceRange::currency).filter(Objects::nonNull).forEach(currencies::add);
        boolean single = amounts && source.getFeeType() != FeeType.COUNTRY_SPECIFIC_FEE && currencies.size() == 1;
        return new DecisionFeeView(category, source == null ? null : source.getFeeType(), state, charged,
                single ? ranges.stream().map(SourceRange::minimum).min(BigDecimal::compareTo).orElseThrow() : null,
                single ? ranges.stream().map(SourceRange::maximum).max(BigDecimal::compareTo).orElseThrow() : null,
                single ? currencies.iterator().next() : null,
                source == null ? null : source.getFeeSelectionStatus(), null,
                category == FeeCategory.OTHER ? List.of() : List.copyOf(ranges), List.of(),
                category == FeeCategory.OTHER ? List.of() : List.copyOf(variants));
    }
}
