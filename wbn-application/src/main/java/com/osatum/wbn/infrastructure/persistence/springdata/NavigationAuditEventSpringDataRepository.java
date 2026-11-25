package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditInstitutionEntryCount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NavigationAuditEventSpringDataRepository extends JpaRepository<NavigationAuditEvent, Long>, JpaSpecificationExecutor<NavigationAuditEvent> {
    boolean existsByEventId(UUID eventId);

    long countByOccurredAtGreaterThanEqualAndOccurredAtLessThan(Instant from, Instant to);

    @Query("""
            select count(distinct event.user.id)
            from NavigationAuditEvent event
            where event.occurredAt >= :from
              and event.occurredAt < :to
              and event.user is not null
            """)
    long countDistinctUsersOccurredAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(distinct event.institution.id)
            from NavigationAuditEvent event
            where event.occurredAt >= :from
              and event.occurredAt < :to
              and event.institution is not null
            """)
    long countDistinctInstitutionsOccurredAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(distinct event.loginSessionId)
            from NavigationAuditEvent event
            where event.occurredAt >= :from
              and event.occurredAt < :to
              and event.loginSessionId is not null
            """)
    long countDistinctLoginSessionsOccurredAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(event)
            from NavigationAuditEvent event
            where event.eventType = com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType.SERVICE_ENTRY
              and event.actorType <> com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType.ANONYMOUS
              and event.occurredAt >= :from
              and event.occurredAt < :to
            """)
    long countAuthenticatedServiceEntriesOccurredAtBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(event)
            from NavigationAuditEvent event
            where event.eventType = com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType.SERVICE_ENTRY
              and event.actorType <> com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType.ANONYMOUS
              and event.occurredAt >= :from
            """)
    long countAuthenticatedServiceEntriesOccurredAtFrom(@Param("from") Instant from);

    @Query("""
            select count(distinct event.frontendSessionId)
            from NavigationAuditEvent event
            where event.actorType <> com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType.ANONYMOUS
              and event.frontendSessionId is not null
              and event.receivedAt >= :from
            """)
    long countDistinctAuthenticatedFrontendSessionsReceivedAtFrom(@Param("from") Instant from);

    @Query("""
            select new com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditInstitutionEntryCount(
                event.institution.id,
                event.institution.name,
                count(event)
            )
            from NavigationAuditEvent event
            where event.eventType = com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType.SERVICE_ENTRY
              and event.actorType <> com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType.ANONYMOUS
              and event.institution is not null
              and event.occurredAt >= :from
              and event.occurredAt < :to
            group by event.institution.id, event.institution.name
            order by count(event) desc, event.institution.name asc
            """)
    List<NavigationAuditInstitutionEntryCount> findTopInstitutionServiceEntryCounts(
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("""
            select event
            from NavigationAuditEvent event
            left join fetch event.user
            left join fetch event.institution
            where event.eventType = com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType.SERVICE_ENTRY
              and event.actorType <> com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType.ANONYMOUS
            order by event.occurredAt desc, event.id desc
            """)
    List<NavigationAuditEvent> findLatestAuthenticatedServiceEntries(Pageable pageable);
}
