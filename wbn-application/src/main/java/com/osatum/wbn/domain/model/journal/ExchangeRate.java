package com.osatum.wbn.domain.model.journal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(String currency, BigDecimal mid, String tableType, String tableNumber, LocalDate effectiveDate) {
    public ExchangeRate {
        if (currency == null || !currency.matches("[A-Z]{3}") || "PLN".equals(currency)
                || mid == null || mid.signum() <= 0 || mid.scale() > 12 || mid.precision() - mid.scale() > 12
                || !("A".equals(tableType) || "B".equals(tableType))
                || tableNumber == null || tableNumber.isBlank() || tableNumber.length() > 64 || effectiveDate == null) {
            throw new IllegalArgumentException("Invalid NBP exchange rate");
        }
    }
}
