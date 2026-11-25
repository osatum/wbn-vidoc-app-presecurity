package com.osatum.wbn.domain.repository.institution;

import com.osatum.wbn.domain.model.institution.InstitutionDeletionConfirmation;

import java.util.Optional;
import java.util.UUID;

public interface InstitutionDeletionConfirmationRepository {

    InstitutionDeletionConfirmation save(InstitutionDeletionConfirmation confirmation);

    Optional<InstitutionDeletionConfirmation> findByIdForUpdate(UUID confirmationId);

    void deleteActiveForInstitutionAndActor(Long institutionId, String actorKey);

    void deleteByInstitutionId(Long institutionId);
}
