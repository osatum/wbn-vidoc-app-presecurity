package com.osatum.wbn.application.journal.view;

import java.util.List;

public record JournalFeesView(boolean noFees, List<JournalFeeView> items) {
}
