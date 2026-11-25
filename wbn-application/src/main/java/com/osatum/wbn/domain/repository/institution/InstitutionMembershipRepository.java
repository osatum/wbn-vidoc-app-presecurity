package com.osatum.wbn.domain.repository.institution;

import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.institution.InstitutionRole;

import java.util.List;
import java.util.Optional;

public interface InstitutionMembershipRepository {
    Optional<InstitutionMembership> findById(Long id);

    Optional<InstitutionMembership> findByUserIdAndInstitutionId(Long userId, Long institutionId);

    InstitutionMembership save(InstitutionMembership membership);

    Optional<InstitutionMembership> findByUserIdAndInstitutionIdAndRole(Long id, Long institutionId, InstitutionRole role);

    List<InstitutionMembership> findByUserId(Long userId);

    List<InstitutionMembership> findByInstitutionId(Long institutionId);

    long countActive();

    long countUsersAssignedToMultipleInstitutions();
}
