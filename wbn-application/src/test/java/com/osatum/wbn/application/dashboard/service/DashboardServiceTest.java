package com.osatum.wbn.application.dashboard.service;

import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import com.osatum.wbn.domain.repository.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final InstitutionRepository institutionRepository = mock(InstitutionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InstitutionMembershipRepository membershipRepository = mock(InstitutionMembershipRepository.class);
    private final NavigationAuditEventRepository navigationAuditEventRepository = mock(NavigationAuditEventRepository.class);
    private final DashboardService service = new DashboardService(
            institutionRepository,
            userRepository,
            membershipRepository,
            navigationAuditEventRepository
    );

    @Test
    void usesNavigationAuditForSessionCardinalityMetrics() {
        when(institutionRepository.countActive()).thenReturn(10L);
        when(institutionRepository.countActiveByType(InstitutionType.RESEARCH_UNIT)).thenReturn(8L);
        when(userRepository.countActive()).thenReturn(20L);
        when(membershipRepository.countActive()).thenReturn(30L);
        when(membershipRepository.countUsersAssignedToMultipleInstitutions()).thenReturn(4L);
        when(navigationAuditEventRepository.countDistinctAuthenticatedFrontendSessionsReceivedAtFrom(any(Instant.class))).thenReturn(2L);
        when(navigationAuditEventRepository.countAuthenticatedServiceEntriesOccurredAtBetween(any(Instant.class), any(Instant.class))).thenReturn(3L);
        when(navigationAuditEventRepository.countAuthenticatedServiceEntriesOccurredAtFrom(any(Instant.class))).thenReturn(7L);

        var result = service.getCardinality();

        assertThat(result.getActiveSessions()).isEqualTo(2L);
        assertThat(result.getSessionsToday()).isEqualTo(3L);
        assertThat(result.getSessionsLast7Days()).isEqualTo(7L);
        assertThat(result.getInstitutionsTotal()).isEqualTo(10L);
        assertThat(result.getUsersTotal()).isEqualTo(20L);
    }

    @Test
    void mapsLatestNavigationAuditServiceEntryToDashboardSessionDto() {
        AppUser user = new AppUser();
        user.setId(11L);
        user.setEmail("user@example.org");

        Institution institution = new Institution();
        institution.setId(22L);
        institution.setName("Research Unit");

        NavigationAuditEvent event = NavigationAuditEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType(NavigationAuditEventType.SERVICE_ENTRY)
                .occurredAt(Instant.parse("2026-06-16T10:00:00Z"))
                .receivedAt(Instant.parse("2026-06-16T10:00:05Z"))
                .actorType(NavigationAuditActorType.USER)
                .institutionType(InstitutionType.RESEARCH_UNIT)
                .institutionRole(InstitutionRole.INSTITUTION_USER)
                .ipAddress("127.0.0.1")
                .userAgent("Mozilla/5.0 Chrome/120.0")
                .build();
        event.setId(100L);
        event.setUser(user);
        event.setInstitution(institution);

        when(navigationAuditEventRepository.findLatestAuthenticatedServiceEntries(10)).thenReturn(List.of(event));

        var result = service.getLatestSessions();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(100L);
        assertThat(result.getFirst().getUserEmail()).isEqualTo("user@example.org");
        assertThat(result.getFirst().getInstitutionName()).isEqualTo("Research Unit");
        assertThat(result.getFirst().getStartedAt()).isEqualTo(Instant.parse("2026-06-16T10:00:00Z"));
        assertThat(result.getFirst().getLastSeenAt()).isEqualTo(Instant.parse("2026-06-16T10:00:05Z"));
        assertThat(result.getFirst().getBrowser()).isEqualTo("Chrome");
    }
}
