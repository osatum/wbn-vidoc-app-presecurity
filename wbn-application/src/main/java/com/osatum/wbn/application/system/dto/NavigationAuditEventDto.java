package com.osatum.wbn.application.system.dto;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NavigationAuditEventDto(
        @NotNull UUID eventId,
        @NotNull NavigationAuditEventType eventType,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 80) String sessionId,
        @Size(max = 80) String viewId,
        @NotBlank @Size(max = 120) String pageKey,
        @NotBlank @Size(max = 250) String routeTemplate,
        @Size(max = 500) String url,
        Map<String, String> params,
        @Valid NavigationAuditResourceDto resource,
        @Size(max = 120) String action,
        @Size(max = 120) String target,
        @PositiveOrZero Integer durationMs,
        Boolean success,
        @Min(100) @Max(599) Integer statusCode,
        Map<String, Object> properties
) {
}
