package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.application.journal.view.DecisionValue;
import com.osatum.wbn.application.journal.view.DecisionValue.State;
import com.osatum.wbn.application.journal.view.JournalDecisionAnnualView;
import com.osatum.wbn.domain.model.survey.Survey;
import com.osatum.wbn.domain.model.survey.model.ScientistMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.EnumMap;
import java.util.Map;
import static com.osatum.wbn.application.journal.service.DecisionMetric.*;

/**
 * Normalizuje wartości ankiety rocznej do wspólnej skali metryk obliczeniowych P2.
 * Zachowuje statusy brakujących i błędnych obserwacji, a do obliczeń przekazuje wyłącznie poprawne wartości.
 * Udziały dotyczą afiliacji zagranicznej w skali 0–100; brak wartości nie staje się zerem.
 * Kwalifikację opłat pobiera bez przeliczania walut, ponieważ benchmark porównuje sam fakt pobierania opłaty.
 */
@Service
@RequiredArgsConstructor
public class DecisionMetricReader {
    private final DecisionFeeReader fees;

    public Map<DecisionMetric, Double> read(Survey survey, int year, Double icv) {
        var result = new EnumMap<DecisionMetric, Double>(DecisionMetric.class);
        states(survey, year, icv).forEach((metric, value) -> put(result, metric, value));
        return Map.copyOf(result);
    }

    /** Zachowuje statusy odrzuconych obserwacji do kontroli; mapa obliczeń zawiera tylko AVAILABLE. */
    public Map<DecisionMetric, DecisionValue<Double>> states(Survey survey, int year, Double icv) {
        var result = new EnumMap<DecisionMetric, DecisionValue<Double>>(DecisionMetric.class);
        result.put(ICV, icv == null ? DecisionValue.missing() : !Double.isFinite(icv) || icv < 0
                ? DecisionValue.invalid() : DecisionValue.available(icv));
        var times = survey.getStandards() == null ? null : survey.getStandards().getAvgManuscriptTimes();
        result.put(INITIAL_ASSESSMENT, asDouble(DecisionAnnualReader.nonNegative(times == null ? null : times.getInitialAssessment())));
        result.put(REVIEW, asDouble(DecisionAnnualReader.nonNegative(times == null ? null : times.getReview())));
        result.put(PUBLICATION, asDouble(DecisionAnnualReader.nonNegative(times == null ? null : times.getPublication())));
        var rows = DecisionAnnualReader.annualRows(survey, year);
        result.put(REJECTION_RATE, rows.size() > 1 ? DecisionValue.invalid()
                : DecisionAnnualReader.rejection(rows.isEmpty() ? null : rows.getFirst()));
        var cooperation = survey.getCooperation();
        result.put(FOREIGN_AUTHORS, percent(cooperation == null ? null : cooperation.getAuthorMetric()));
        result.put(FOREIGN_REVIEWERS, percent(cooperation == null ? null : cooperation.getReviewerMetric()));
        result.put(FOREIGN_COUNCIL, percent(cooperation == null ? null : cooperation.getDirectorsMetric()));
        for (var fee : fees.classify(survey.getStandards())) {
            result.put(fee(fee.category()), fee.charged() == null ? new DecisionValue<>(fee.status(), null)
                    : DecisionValue.available(fee.charged() ? 1.0 : 0.0));
        }
        return Map.copyOf(result);
    }

    private DecisionValue<Double> asDouble(DecisionValue<? extends Number> value) {
        return new DecisionValue<>(value.status(), value.value() == null ? null : value.value().doubleValue());
    }

    /** Odtwarza wartości własne Karty do sprawdzenia ich zgodności z zapisaną generacją, bez liczenia porównań. */
    public static Map<DecisionMetric, Double> own(JournalDecisionAnnualView annual, Double icv) {
        var result = new EnumMap<DecisionMetric, Double>(DecisionMetric.class);
        put(result, ICV, icv);
        if (annual == null) return Map.copyOf(result);
        put(result, INITIAL_ASSESSMENT, annual.times().initialAssessment());
        put(result, REVIEW, annual.times().review());
        put(result, PUBLICATION, annual.times().publication());
        put(result, REJECTION_RATE, annual.process().rejectionRate());
        put(result, FOREIGN_AUTHORS, annual.international().authors());
        put(result, FOREIGN_REVIEWERS, annual.international().reviewers());
        put(result, FOREIGN_COUNCIL, annual.international().scientificCouncil());
        for (var fee : annual.fees()) {
            if (fee.charged() != null) put(result, fee(fee.category()), fee.charged() ? 1.0 : 0.0);
        }
        return Map.copyOf(result);
    }

    private static DecisionValue<Double> percent(ScientistMetric metric) {
        return DecisionAnnualReader.percentage(metric == null ? null : metric.getForeignAffiliationPercentage(), false);
    }

    private static void put(Map<DecisionMetric, Double> result, DecisionMetric metric, DecisionValue<? extends Number> value) {
        if (value != null && value.status() == State.AVAILABLE) put(result, metric, value.value());
    }

    private static void put(Map<DecisionMetric, Double> result, DecisionMetric metric, Number value) {
        if (value != null && Double.isFinite(value.doubleValue()) && value.doubleValue() >= 0) {
            result.put(metric, value.doubleValue());
        }
    }
}
