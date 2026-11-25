package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.institution.InstitutionDeletionConfirmation;
import com.osatum.wbn.domain.repository.institution.InstitutionDeletionConfirmationRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionDeletionConfirmationSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InstitutionDeletionConfirmationRepositoryAdapter implements InstitutionDeletionConfirmationRepository {

    private final InstitutionDeletionConfirmationSpringDataRepository springDataRepository;

    @Override
    public InstitutionDeletionConfirmation save(InstitutionDeletionConfirmation confirmation) {
        return springDataRepository.save(confirmation);
    }

    @Override
    public Optional<InstitutionDeletionConfirmation> findByIdForUpdate(UUID confirmationId) {
        return springDataRepository.findByIdForUpdate(confirmationId);
    }

    @Override
    public void deleteActiveForInstitutionAndActor(Long institutionId, String actorKey) {
        springDataRepository.deleteActiveForInstitutionAndActor(institutionId, actorKey);
    }

    @Override
    public void deleteByInstitutionId(Long institutionId) {
        springDataRepository.deleteByInstitutionId(institutionId);
    }
}
