package com.osatum.wbn.application.system.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record NavigationVisitActivityDto(@NotNull UUID eventId, @NotNull Instant occurredAt) {
}
