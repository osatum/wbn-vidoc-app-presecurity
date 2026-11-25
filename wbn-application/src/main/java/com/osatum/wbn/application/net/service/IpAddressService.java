package com.osatum.wbn.application.net.service;

import com.osatum.wbn.application.net.dto.IpRangeStrings;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import com.osatum.wbn.domain.service.net.NormalizedIpRule;

public interface IpAddressService {
    IpRangeStrings ruleToIpStrings(InstitutionIpRule rule);

    NormalizedIpRule normalizeRule(IpRuleType ruleType, String ruleRaw, IpAddressFamily addressFamily);
}
