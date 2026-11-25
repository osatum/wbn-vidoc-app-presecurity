package com.osatum.wbn.application.system.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record NavigationAuditReportRequest(
        @NotNull LocalDate dateFrom,
        @NotNull LocalDate dateTo,
        @NotNull NavigationAuditReportType reportType
) {
}
