package com.osatum.wbn.infrastructure.persistence.springdata;


import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalyticsJournalSpringDataRepository extends JpaRepository<JournalAnalytics, Long> {

    void deleteAllByJournalId(Long journalId);

    List<JournalAnalytics> findAllByJournalId(Long journalId);
}
