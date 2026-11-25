package com.osatum.wbn.application.institution.dto;

import java.time.Instant;
import java.util.UUID;

public record InstitutionDeletionConfirmationDto(
        UUID confirmationId,
        String confirmationCode,
        Instant expiresAt
) {
}
