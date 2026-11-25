package com.osatum.wbn.application.analytics.assembler;



import com.osatum.wbn.application.analytics.view.AnalyticsJournalView;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import com.osatum.wbn.shared.assembler.DefaultParameters;

import java.util.List;

public class AnalyticsJournalViewParameter extends DefaultParameters<List<JournalAnalytics>, AnalyticsJournalView> {
    public AnalyticsJournalViewParameter(List<JournalAnalytics> entity, AnalyticsJournalView dto, Long journalId) {
        super(entity, dto);
        this.journalId = journalId;
    }
    private Long journalId;

    public Long getJournalId() {
        return journalId;
    }
}
