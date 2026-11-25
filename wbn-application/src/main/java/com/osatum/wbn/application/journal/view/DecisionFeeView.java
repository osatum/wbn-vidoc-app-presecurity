package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.ExchangeRate;
import com.osatum.wbn.domain.model.journal.FeeCategory;
import com.osatum.wbn.domain.model.survey.model.FeeSelectionStatus;
import com.osatum.wbn.domain.model.survey.model.FeeAuthorVariant;
import com.osatum.wbn.domain.model.survey.model.FeeType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Opłata P2 w walucie źródłowej; oba warianty krajowe/zagraniczne pozostają odrębne.
 * Pola wyboru P1 i kursów zachowano dla zgodności kontraktu: effectiveVariant jest null, a exchangeRates puste.
 * Wiele walut nie otrzymuje sztucznej wspólnej kwoty; oryginały pozostają w sourceRanges.
 */
public record DecisionFeeView(FeeCategory category, FeeType type, DecisionValue.State status,
                              Boolean charged, BigDecimal minimum, BigDecimal maximum, String currency,
                              FeeSelectionStatus selectionStatus, FeeAuthorVariant effectiveVariant,
                              List<SourceRange> sourceRanges, List<ExchangeRate> exchangeRates,
                              List<Variant> variants) {
    public record SourceRange(BigDecimal minimum, BigDecimal maximum, String currency) { }
    /** Oba warianty P2 są niezależne od wyboru autora PL stosowanego przez wyszukiwarkę P1. */
    public record Variant(FeeAuthorVariant variant, DecisionValue.State status, Boolean charged,
                          BigDecimal minimum, BigDecimal maximum, String currency) { }
}
