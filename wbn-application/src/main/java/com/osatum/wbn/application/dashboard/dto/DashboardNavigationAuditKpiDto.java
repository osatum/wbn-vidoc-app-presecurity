package com.osatum.wbn.application.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardNavigationAuditKpiDto {
    private Instant periodFrom;
    private Instant periodTo;
    private long users;
    private long institutions;
    private long sessions;
    private long events;
}
