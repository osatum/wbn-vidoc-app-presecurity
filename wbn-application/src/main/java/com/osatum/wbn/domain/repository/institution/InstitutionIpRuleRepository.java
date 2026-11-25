package com.osatum.wbn.domain.repository.institution;



import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;

import java.util.List;

public interface InstitutionIpRuleRepository {
    List<InstitutionIpRule> findAllEnabledWithInstitution();

    List<InstitutionIpRule>  findByInstitutionIdAndEnabledTrue(EntityState entityState, Long institutionId);
}
