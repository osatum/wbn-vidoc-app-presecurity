package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.repository.institution.InstitutionMembershipRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionMembershipSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InstitutionMembershipRepositoryAdapter implements InstitutionMembershipRepository {
    private final InstitutionMembershipSpringDataRepository springDataRepository;

    @Override
    public Optional<InstitutionMembership> findById(Long id) {
        return springDataRepository.findById(id);
    }

    @Override
    public Optional<InstitutionMembership> findByUserIdAndInstitutionId(Long userId, Long institutionId) {
        return springDataRepository.findByUserIdAndInstitutionId(userId, institutionId);
    }

    @Override
    public Optional<InstitutionMembership> findByUserIdAndInstitutionIdAndRole(Long userId, Long institutionId, InstitutionRole role) {
        return springDataRepository.findByUserIdAndInstitutionIdAndRole(userId, institutionId, role);
    }

    @Override
    public List<InstitutionMembership> findByUserId(Long userId) {
        return springDataRepository.findByUserId(userId);
    }

    @Override
    public List<InstitutionMembership> findByInstitutionId(Long institutionId) {
        return springDataRepository.findByInstitutionId(institutionId);
    }

    @Override
    public InstitutionMembership save(InstitutionMembership membership) {
        return springDataRepository.save(membership);
    }

    @Override
    public long countActive() {
        return springDataRepository.countByState(EntityState.ACTIVE);
    }

    @Override
    public long countUsersAssignedToMultipleInstitutions() {
        return springDataRepository.countUsersAssignedToMultipleInstitutions();
    }
}
