package com.osatum.wbn.infrastructure.persistence;


import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.repository.institution.InstitutionIpRuleRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionIpRuleSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class InstitutionIpRuleRepositoryAdapter implements InstitutionIpRuleRepository {
    private final InstitutionIpRuleSpringDataRepository springData;

    @Override
    public List<InstitutionIpRule> findAllEnabledWithInstitution(){
        return springData.findActiveByState(EntityState.ACTIVE);
    }

    @Override
    public List<InstitutionIpRule>  findByInstitutionIdAndEnabledTrue(EntityState entityState, Long institutionId) {
        return springData.findByInstitutionIdAndByState(entityState, institutionId);
    }
}
