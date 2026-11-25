package com.osatum.wbn.application.analytics.repository;



import com.osatum.wbn.domain.model.analytics.JournalAnalytics;

import java.util.List;

public interface AnalyticsJournalRepository {

    void saveAll(List<JournalAnalytics> analytics);

    void deleteForJournalId(Long journalId);

    List<JournalAnalytics> findAllByJournalId(Long journalId);
}
