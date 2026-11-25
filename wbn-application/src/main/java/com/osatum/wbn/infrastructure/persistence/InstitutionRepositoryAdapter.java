package com.osatum.wbn.infrastructure.persistence;


import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionRepositorySpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class InstitutionRepositoryAdapter implements InstitutionRepository {
    private final InstitutionRepositorySpringDataRepository springDataRepository;

    @Override
    public Optional<Institution> findById(Long id) {
        return springDataRepository.findById(id);
    }

    @Override
    public Optional<Institution> findByIdForUpdate(Long id) {
        return springDataRepository.findByIdForUpdate(id);
    }

    @Override
    public boolean existsByName(String name) {
        return springDataRepository.existsByStateAndNameIgnoreCase(EntityState.ACTIVE, name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return springDataRepository.existsByStateAndNameIgnoreCaseAndIdNot(EntityState.ACTIVE, name, id);
    }

    @Override
    public Institution save(Institution institution) {
        return springDataRepository.save(institution);
    }

    @Override
    public long countActive() {
        return springDataRepository.countByState(EntityState.ACTIVE);
    }

    @Override
    public long countActiveByType(InstitutionType institutionType) {
        return springDataRepository.countByStateAndInstitutionType(EntityState.ACTIVE, institutionType);
    }

    @Override
    public List<Long> findIdsActiveByType(InstitutionType institutionType) {
        return springDataRepository.findIdsActiveByType(EntityState.ACTIVE, institutionType);
    }

    @Override
    public List<Institution> findActive(int limit) {
        return springDataRepository.findByStateOrderByNameAsc(EntityState.ACTIVE, PageRequest.of(0, limit));
    }

    @Override
    public List<Institution> findActiveRegistrationEnabled() {
        return springDataRepository.findByStateAndAllowRegistrationTrueOrderByNameAsc(EntityState.ACTIVE);
    }

    @Override
    public List<Institution> findActiveWithUsersInIds(List<Long> unitsIds) {
        return springDataRepository.findAllById(unitsIds);
    }
}
