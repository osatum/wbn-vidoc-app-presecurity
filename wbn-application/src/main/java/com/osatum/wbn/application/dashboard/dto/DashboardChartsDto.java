package com.osatum.wbn.application.dashboard.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardChartsDto {
    private List<DashboardDailyLoginDto> loginsLast7Days;
    private List<DashboardInstitutionLoginDto> topInstitutions;
}
