package com.osatum.wbn.infrastructure.persistence;


import com.osatum.wbn.application.analytics.repository.AnalyticsJournalRepository;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.model.analytics.JournalAnalytics;
import com.osatum.wbn.infrastructure.persistence.springdata.AnalyticsJournalSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AnalyticsJournalRepositoryAdapter implements AnalyticsJournalRepository {
    private final AnalyticsJournalSpringDataRepository repository;


    @Override
    public List<JournalAnalytics> findAllByJournalId(Long journalId) {
        return repository.findAllByJournalId(journalId);
    }

    @Override
    public void saveAll(List<JournalAnalytics> analytics) {
        repository.saveAll(analytics);
    }

    @Override
    public void deleteForJournalId(Long journalId) {
        repository.deleteAllByJournalId(journalId);
    }

}
