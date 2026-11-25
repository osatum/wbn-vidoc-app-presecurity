package com.osatum.wbn.application.journal.service;

import com.osatum.wbn.domain.model.journal.ExchangeRate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

public record ExchangeRateSnapshot(Map<String, ExchangeRate> quotations) {
    public ExchangeRateSnapshot {
        quotations = Map.copyOf(quotations);
    }

    public static ExchangeRateSnapshot plnOnly() {
        return new ExchangeRateSnapshot(Map.of());
    }

    public Map<String, BigDecimal> rates() {
        Map<String, BigDecimal> rates = new TreeMap<>();
        quotations.forEach((code, rate) -> rates.put(code, rate.mid()));
        rates.put("PLN", BigDecimal.ONE);
        return Map.copyOf(rates);
    }

    public void require(String currency) {
        if (!"PLN".equals(currency) && !quotations.containsKey(currency)) {
            throw new ExchangeRatesUnavailableException("Missing NBP rate for " + currency);
        }
    }
}
