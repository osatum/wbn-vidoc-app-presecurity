package com.osatum.wbn.application.journal.view;

import com.osatum.wbn.domain.model.journal.FeeCategory;
import java.math.BigDecimal;

public record JournalFeeView(FeeCategory category, BigDecimal amount, String currencyCode) {
}
