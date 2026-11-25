package com.osatum.wbn.application.system.dto;

import jakarta.validation.constraints.Size;

public record NavigationAuditResourceDto(
        @Size(max = 80) String type,
        @Size(max = 120) String id
) {
}
