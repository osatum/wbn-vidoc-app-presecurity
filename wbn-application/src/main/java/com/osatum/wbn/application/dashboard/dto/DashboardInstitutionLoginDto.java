package com.osatum.wbn.application.dashboard.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardInstitutionLoginDto {
    private Long institutionId;
    private String institutionName;
    private long loginCount;
}
