package com.osatum.wbn.domain.service.institution;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import com.osatum.wbn.domain.repository.institution.InstitutionIpRuleRepository;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import com.osatum.wbn.domain.service.net.NormalizedIpRule;
import com.osatum.wbn.domain.service.net.ParsedIpAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionIpMatcherService {

    private final InstitutionIpRuleRepository ipRuleRepository;

    public boolean matchesInstitution(Long institutionId, String clientIp) {
        if (institutionId == null) {
            return false;
        }

        ParsedIpAddress address = parseClientAddress(clientIp);
        if (address == null) {
            return false;
        }

        var rules = ipRuleRepository.findByInstitutionIdAndEnabledTrue(EntityState.ACTIVE, institutionId);
        log.debug("Checking {} active IP rules for institution {}", rules.size(), institutionId);
        return rules.stream().anyMatch(rule -> matches(address, rule));
    }

    public boolean matches(String clientIp, InstitutionIpRule rule) {
        ParsedIpAddress address = parseClientAddress(clientIp);
        return address != null && matches(address, rule);
    }

    private boolean matches(ParsedIpAddress address, InstitutionIpRule rule) {
        if (rule == null || rule.getRuleType() == null || rule.getRuleRaw() == null) {
            return false;
        }

        try {
            if (rule.getRuleType() == IpRuleType.WILDCARD) {
                return IpAddressRuleParser.matchesIpv4Wildcard(address, rule.getRuleRaw());
            }

            IpAddressFamily addressFamily = rule.getAddressFamily();
            byte[] from = rule.getIpFromBytes();
            byte[] to = rule.getIpToBytes();
            if (addressFamily == null || from == null || to == null) {
                NormalizedIpRule normalizedRule = IpAddressRuleParser.parse(
                        rule.getRuleType(),
                        rule.getRuleRaw(),
                        addressFamily
                );
                addressFamily = normalizedRule.addressFamily();
                from = normalizedRule.ipFrom();
                to = normalizedRule.ipTo();
            }
            return IpAddressRuleParser.isWithinRange(address, from, to, addressFamily);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignoring invalid IP rule {}", rule.getId());
            return false;
        }
    }

    private ParsedIpAddress parseClientAddress(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        try {
            return IpAddressRuleParser.parseClientAddress(clientIp);
        } catch (IllegalArgumentException exception) {
            log.debug("Ignoring invalid client IP address");
            return null;
        }
    }
}
