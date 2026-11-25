package com.osatum.wbn.application.system.query;

import com.osatum.wbn.application.system.dto.NavigationAuditReportType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NavigationAuditReportQuery {
    List<NavigationAuditReportRow> fetch(NavigationAuditReportType type, Instant from, Instant until,
                                        NavigationAuditReportRow cursor, int size);
    Optional<Instant> firstVisitAt();
}
