package com.osatum.wbn.domain.repository.institution;


import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionType;

import java.util.List;
import java.util.Optional;

public interface InstitutionRepository {
    Optional<Institution> findById(Long id);

    Optional<Institution> findByIdForUpdate(Long id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Institution save(Institution institution);

    long countActive();

    long countActiveByType(InstitutionType institutionType);

    List<Long> findIdsActiveByType(InstitutionType institutionType);

    List<Institution> findActive(int limit);

    List<Institution> findActiveRegistrationEnabled();

    // pobiera instytucje z administatorami dla zadanych ids
    List<Institution> findActiveWithUsersInIds(List<Long> unitsIds);
}
