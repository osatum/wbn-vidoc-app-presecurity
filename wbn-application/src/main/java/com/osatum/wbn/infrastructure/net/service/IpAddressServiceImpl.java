package com.osatum.wbn.infrastructure.net.service;

import com.osatum.wbn.application.net.dto.IpRangeStrings;
import com.osatum.wbn.application.net.service.IpAddressService;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import com.osatum.wbn.domain.service.net.NormalizedIpRule;
import org.springframework.stereotype.Service;

@Service
public class IpAddressServiceImpl implements IpAddressService {

    @Override
    public IpRangeStrings ruleToIpStrings(InstitutionIpRule rule) {
        if (rule == null) {
            return null;
        }
        NormalizedIpRule normalizedRule = normalizeStoredRule(rule);
        return new IpRangeStrings(
                IpAddressRuleParser.toAddressString(normalizedRule.ipFrom()),
                IpAddressRuleParser.toAddressString(normalizedRule.ipTo())
        );
    }

    @Override
    public NormalizedIpRule normalizeRule(
            IpRuleType ruleType,
            String ruleRaw,
            IpAddressFamily addressFamily
    ) {
        return IpAddressRuleParser.parse(ruleType, ruleRaw, addressFamily);
    }

    private NormalizedIpRule normalizeStoredRule(InstitutionIpRule rule) {
        if (rule.getAddressFamily() != null
                && rule.getIpFromBytes() != null
                && rule.getIpToBytes() != null) {
            return new NormalizedIpRule(
                    rule.getAddressFamily(),
                    rule.getRuleRaw(),
                    rule.getIpFromBytes(),
                    rule.getIpToBytes(),
                    rule.getCidrPrefix()
            );
        }
        return normalizeRule(rule.getRuleType(), rule.getRuleRaw(), rule.getAddressFamily());
    }
}
