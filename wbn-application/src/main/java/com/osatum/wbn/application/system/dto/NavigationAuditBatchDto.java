package com.osatum.wbn.application.system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NavigationAuditBatchDto(
        @NotEmpty
        @Size(max = 100)
        List<@Valid NavigationAuditEventDto> events
) {
}
