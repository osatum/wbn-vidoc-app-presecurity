package com.osatum.wbn.domain.service.institution;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import com.osatum.wbn.domain.repository.institution.InstitutionIpRuleRepository;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstitutionIpMatcherServiceTest {

    @Test
    void matchesStoredIpv6CidrRule() {
        var repository = mock(InstitutionIpRuleRepository.class);
        var service = new InstitutionIpMatcherService(repository);
        var normalizedRule = IpAddressRuleParser.parse(IpRuleType.CIDR, "2001:db8:abcd::/48", IpAddressFamily.IPV6);
        var rule = new InstitutionIpRule();
        rule.setId(3L);
        rule.setRuleType(IpRuleType.CIDR);
        rule.setRuleRaw(normalizedRule.ruleRaw());
        rule.setAddressFamily(normalizedRule.addressFamily());
        rule.setIpFromBytes(normalizedRule.ipFrom());
        rule.setIpToBytes(normalizedRule.ipTo());

        when(repository.findByInstitutionIdAndEnabledTrue(EntityState.ACTIVE, 11L)).thenReturn(List.of(rule));

        assertThat(service.matchesInstitution(11L, "2001:db8:abcd:1::10")).isTrue();
        assertThat(service.matchesInstitution(11L, "2001:db8:abce::10")).isFalse();
    }

    @Test
    void keepsExactSemanticsForLegacyIpv4Wildcard() {
        var repository = mock(InstitutionIpRuleRepository.class);
        var service = new InstitutionIpMatcherService(repository);
        var rule = new InstitutionIpRule();
        rule.setRuleType(IpRuleType.WILDCARD);
        rule.setRuleRaw("10.*.1.*");

        assertThat(service.matches("10.99.1.5", rule)).isTrue();
        assertThat(service.matches("10.99.2.5", rule)).isFalse();
    }
}
