package com.osatum.wbn.application.system.query;

import com.osatum.wbn.application.system.dto.NavigationAuditVisitEndReason;
import com.osatum.wbn.application.system.dto.NavigationAuditVisitStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class NavigationAuditVisitListQuery {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    @Size(max = 200)
    private String institution;
    @Size(max = 200)
    private String user;
    private NavigationAuditVisitStatus status;
    private NavigationAuditVisitEndReason endReason;
    @Min(0)
    private int page;
    @Min(1)
    @Max(100)
    private int size = 10;
    @Min(0)
    private int first;
}
