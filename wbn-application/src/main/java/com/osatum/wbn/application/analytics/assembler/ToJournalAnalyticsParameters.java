package com.osatum.wbn.application.analytics.assembler;

import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import com.osatum.wbn.shared.assembler.DefaultParameters;


import java.util.List;

public class ToJournalAnalyticsParameters extends DefaultParameters<Journal, List<JournalAnalytics>> {
    public ToJournalAnalyticsParameters(Journal entity, List<JournalAnalytics> dto, Integer year) {
        super(entity, dto);
        this.year = year;
    }
    private Integer year;

    public Integer getYear() {
        return year;
    }
}
