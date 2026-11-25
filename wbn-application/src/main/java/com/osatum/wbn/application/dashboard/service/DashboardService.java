package com.osatum.wbn.application.dashboard.service;

import com.osatum.wbn.application.dashboard.dto.DashboardCardinalityDto;
import com.osatum.wbn.application.dashboard.dto.DashboardChartsDto;
import com.osatum.wbn.application.dashboard.dto.DashboardDailyLoginDto;
import com.osatum.wbn.application.dashboard.dto.DashboardInstitutionLoginDto;
import com.osatum.wbn.application.dashboard.dto.DashboardNavigationAuditKpiDto;
import com.osatum.wbn.application.dashboard.dto.DashboardSessionDto;
import com.osatum.wbn.application.institution.authorization.CanInstitutionManage;
import com.osatum.wbn.domain.model.auth.LoginActorType;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditEventRepository;
import com.osatum.wbn.domain.repository.navigationaudit.NavigationAuditInstitutionEntryCount;
import com.osatum.wbn.domain.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final int LAST_DAYS_COUNT = 7;
    private static final int TOP_INSTITUTIONS_LIMIT = 10;
    private static final int LATEST_SESSIONS_LIMIT = 10;
    private static final int ACTIVE_AUDIT_WINDOW_MINUTES = 15;
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("dd.MM");

    private final InstitutionRepository institutionRepository;
    private final UserRepository userRepository;
    private final InstitutionMembershipRepository membershipRepository;
    private final NavigationAuditEventRepository navigationAuditEventRepository;

    /**
     * Zwraca podstawowe liczniki prezentowane w dashboardzie administracyjnym.
     */
    @CanInstitutionManage
    @Transactional(readOnly = true)
    public DashboardCardinalityDto getCardinality() {
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        Instant todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant tomorrowStart = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
        Instant lastSevenDaysStart = now.minus(7, ChronoUnit.DAYS);
        Instant activeAuditWindowStart = now.minus(ACTIVE_AUDIT_WINDOW_MINUTES, ChronoUnit.MINUTES);

        return DashboardCardinalityDto.builder()
                .institutionsTotal(institutionRepository.countActive())
                .researchUnits(institutionRepository.countActiveByType(InstitutionType.RESEARCH_UNIT))
                .usersTotal(userRepository.countActive())
                .institutionUserMemberships(membershipRepository.countActive())
                .activeSessions(navigationAuditEventRepository.countDistinctAuthenticatedFrontendSessionsReceivedAtFrom(activeAuditWindowStart))
                .sessionsToday(navigationAuditEventRepository.countAuthenticatedServiceEntriesOccurredAtBetween(todayStart, tomorrowStart))
                .sessionsLast7Days(navigationAuditEventRepository.countAuthenticatedServiceEntriesOccurredAtFrom(lastSevenDaysStart))
                .usersAssignedToMultipleInstitutions(membershipRepository.countUsersAssignedToMultipleInstitutions())
                .build();
    }

    /**
     * Buduje dane wykresów logowań z ostatnich dni oraz najaktywniejszych instytucji.
     */
    @CanInstitutionManage
    @Transactional(readOnly = true)
    public DashboardChartsDto getCharts() {
        return DashboardChartsDto.builder()
                .loginsLast7Days(buildDailyLoginCounts())
                .topInstitutions(buildTopInstitutionLoginCounts())
                .build();
    }

    /**
     * Zwraca KPI audytu nawigacji dla ostatnich siedmiu dni.
     */
    @CanInstitutionManage
    @Transactional(readOnly = true)
    public DashboardNavigationAuditKpiDto getNavigationAuditKpi() {
        Instant now = Instant.now();
        Instant from = now.minus(7, ChronoUnit.DAYS);

        return DashboardNavigationAuditKpiDto.builder()
                .periodFrom(from)
                .periodTo(now)
                .users(navigationAuditEventRepository.countDistinctUsersOccurredAtBetween(from, now))
                .institutions(navigationAuditEventRepository.countDistinctInstitutionsOccurredAtBetween(from, now))
                .sessions(navigationAuditEventRepository.countDistinctLoginSessionsOccurredAtBetween(from, now))
                .events(navigationAuditEventRepository.countOccurredAtBetween(from, now))
                .build();
    }

    /**
     * Zwraca najnowsze uwierzytelnione wejścia do serwisu jako listę ostatnich sesji.
     */
    @CanInstitutionManage
    @Transactional(readOnly = true)
    public List<DashboardSessionDto> getLatestSessions() {
        return navigationAuditEventRepository.findLatestAuthenticatedServiceEntries(LATEST_SESSIONS_LIMIT).stream()
                .map(this::toDashboardSessionDto)
                .toList();
    }

    private List<DashboardDailyLoginDto> buildDailyLoginCounts() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        List<DashboardDailyLoginDto> items = new ArrayList<>(LAST_DAYS_COUNT);

        for (int dayOffset = LAST_DAYS_COUNT - 1; dayOffset >= 0; dayOffset--) {
            LocalDate date = today.minusDays(dayOffset);
            Instant from = date.atStartOfDay(zone).toInstant();
            Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();

            items.add(DashboardDailyLoginDto.builder()
                    .date(date)
                    .label(date.format(DAY_LABEL_FORMATTER))
                    .loginCount(navigationAuditEventRepository.countAuthenticatedServiceEntriesOccurredAtBetween(from, to))
                    .build());
        }

        return items;
    }

    private List<DashboardInstitutionLoginDto> buildTopInstitutionLoginCounts() {
        List<DashboardInstitutionLoginDto> items = new ArrayList<>(TOP_INSTITUTIONS_LIMIT);
        Set<Long> usedInstitutionIds = new HashSet<>();
        Instant now = Instant.now();
        Instant from = now.minus(7, ChronoUnit.DAYS);

        for (NavigationAuditInstitutionEntryCount entryCount : navigationAuditEventRepository.findTopInstitutionServiceEntryCounts(from, now, TOP_INSTITUTIONS_LIMIT)) {
            items.add(DashboardInstitutionLoginDto.builder()
                    .institutionId(entryCount.institutionId())
                    .institutionName(entryCount.institutionName())
                    .loginCount(entryCount.entryCount())
                    .build());
            usedInstitutionIds.add(entryCount.institutionId());
        }

        if (items.size() < TOP_INSTITUTIONS_LIMIT) {
            for (Institution institution : institutionRepository.findActive(TOP_INSTITUTIONS_LIMIT)) {
                if (items.size() == TOP_INSTITUTIONS_LIMIT) {
                    break;
                }
                if (usedInstitutionIds.contains(institution.getId())) {
                    continue;
                }

                items.add(DashboardInstitutionLoginDto.builder()
                        .institutionId(institution.getId())
                        .institutionName(institution.getName())
                        .loginCount(0)
                        .build());
            }
        }

        return items;
    }

    private DashboardSessionDto toDashboardSessionDto(NavigationAuditEvent event) {
        return DashboardSessionDto.builder()
                .id(event.getId())
                .createdAt(event.getReceivedAt() == null ? event.getCreatedAt() : event.getReceivedAt().atOffset(ZoneOffset.UTC))
                .actorType(toLoginActorType(event.getActorType()))
                .userId(event.getUser() == null ? null : event.getUser().getId())
                .userEmail(event.getUser() == null ? null : event.getUser().getEmail())
                .institutionId(event.getInstitution() == null ? null : event.getInstitution().getId())
                .institutionName(event.getInstitution() == null ? null : event.getInstitution().getName())
                .institutionType(event.getInstitutionType())
                .institutionRole(event.getInstitutionRole())
                .ipAddress(event.getIpAddress())
                .browser(resolveBrowser(event.getUserAgent()))
                .userAgent(event.getUserAgent())
                .startedAt(event.getOccurredAt())
                .lastSeenAt(event.getReceivedAt())
                .build();
    }

    private LoginActorType toLoginActorType(NavigationAuditActorType actorType) {
        if (actorType == null || actorType == NavigationAuditActorType.ANONYMOUS) {
            return null;
        }

        return switch (actorType) {
            case USER -> LoginActorType.USER;
            case INSTITUTION -> LoginActorType.INSTITUTION;
            case ANONYMOUS -> null;
        };
    }

    private String resolveBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }

        String normalized = userAgent.toLowerCase();
        if (normalized.contains("edg/")) {
            return "Microsoft Edge";
        }
        if (normalized.contains("opr/") || normalized.contains("opera")) {
            return "Opera";
        }
        if (normalized.contains("firefox/")) {
            return "Firefox";
        }
        if (normalized.contains("chrome/") || normalized.contains("crios/")) {
            return "Chrome";
        }
        if (normalized.contains("safari/")) {
            return "Safari";
        }

        return "Other";
    }
}
