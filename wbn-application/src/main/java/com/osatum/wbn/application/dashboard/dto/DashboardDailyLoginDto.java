package com.osatum.wbn.application.dashboard.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDailyLoginDto {
    private LocalDate date;
    private String label;
    private long loginCount;
}
