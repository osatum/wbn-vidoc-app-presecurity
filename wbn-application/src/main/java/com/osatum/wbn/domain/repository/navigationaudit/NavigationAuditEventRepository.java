package com.osatum.wbn.domain.repository.navigationaudit;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;

import java.time.Instant;
import java.util.List;

public interface NavigationAuditEventRepository {
    void saveIfAbsent(NavigationAuditEvent event, Long userId, Long institutionId);

    int deleteJournalSearchEventsBefore(Instant cutoff, int limit);

    long countOccurredAtBetween(Instant from, Instant to);

    long countDistinctUsersOccurredAtBetween(Instant from, Instant to);

    long countDistinctInstitutionsOccurredAtBetween(Instant from, Instant to);

    long countDistinctLoginSessionsOccurredAtBetween(Instant from, Instant to);

    long countAuthenticatedServiceEntriesOccurredAtBetween(Instant from, Instant to);

    long countAuthenticatedServiceEntriesOccurredAtFrom(Instant from);

    long countDistinctAuthenticatedFrontendSessionsReceivedAtFrom(Instant from);

    List<NavigationAuditInstitutionEntryCount> findTopInstitutionServiceEntryCounts(Instant from, Instant to, int limit);

    List<NavigationAuditEvent> findLatestAuthenticatedServiceEntries(int limit);
}
