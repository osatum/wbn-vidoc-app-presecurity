package com.osatum.wbn.infrastructure.report;

import com.osatum.wbn.application.system.dto.NavigationAuditReportRequest;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import com.osatum.wbn.application.system.service.NavigationAuditReportGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

@Service
@Primary
@RequiredArgsConstructor
public class NavigationAuditReportGeneratorRouter implements NavigationAuditReportGenerator {
    private final NavigationAuditReportXlsxGenerator xlsxGenerator;
    private final NavigationAuditReportXlsGenerator xlsGenerator;

    @Override
    public PreparedNavigationAuditReport generate(NavigationAuditReportRequest request, ZoneId zone, int maxRows) {
        return switch (request.reportType()) {
            case SERVICE_ENTRIES -> xlsGenerator.generate(request, zone, maxRows);
            case VISITS, PAGE_VIEWS, FILE_DOWNLOADS -> xlsxGenerator.generate(request, zone, maxRows);
        };
    }
}
