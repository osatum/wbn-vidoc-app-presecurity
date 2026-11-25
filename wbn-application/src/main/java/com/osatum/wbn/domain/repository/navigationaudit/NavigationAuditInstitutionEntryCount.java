package com.osatum.wbn.domain.repository.navigationaudit;

public record NavigationAuditInstitutionEntryCount(
        Long institutionId,
        String institutionName,
        long entryCount
) {
}
