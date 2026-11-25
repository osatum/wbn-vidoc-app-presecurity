package com.osatum.wbn.domain.model.institution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "institution_deletion_confirmation", indexes = {
        @Index(name = "idx_institution_deletion_confirmation_institution", columnList = "institution_id"),
        @Index(name = "idx_institution_deletion_confirmation_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class InstitutionDeletionConfirmation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private Long institutionId;

    @Column(name = "actor_key", nullable = false, length = 255)
    private String actorKey;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
