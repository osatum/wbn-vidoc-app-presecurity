package com.osatum.wbn.application.institution.dto;

import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class InstitutionIpRuleDto {
    private IpRuleType ruleType;
    private IpAddressFamily addressFamily;
    private Long id;
    private String ruleRaw;
    private String ipFrom;
    private String ipTo;
    private String displayValue;
    private String displayRange;
    private int priority;
}
