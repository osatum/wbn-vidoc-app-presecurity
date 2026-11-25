package com.osatum.wbn.application.system.dto;

import java.util.List;

public record NavigationAuditVisitPageResult(
        List<NavigationAuditVisitListDto> data, long totalElements, int totalPages, int size, String timeZone
) {
}
