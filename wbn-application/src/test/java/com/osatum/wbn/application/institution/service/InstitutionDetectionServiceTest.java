package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.dto.InstitutionAccessDto;
import com.osatum.wbn.application.institution.mapper.InstitutionAccessDtoMapper;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import com.osatum.wbn.domain.repository.institution.InstitutionIpRuleRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstitutionDetectionServiceTest {

    @Test
    void detectsInstitutionsConfiguredForBothLoopbackAddressFamilies() {
        var ipRuleRepository = mock(InstitutionIpRuleRepository.class);
        var institutionRepository = mock(InstitutionRepository.class);
        var institutionAccessDtoMapper = mock(InstitutionAccessDtoMapper.class);
        var matcher = new InstitutionIpMatcherService(mock(InstitutionIpRuleRepository.class));
        var service = new InstitutionDetectionService(
                ipRuleRepository,
                institutionRepository,
                matcher,
                institutionAccessDtoMapper
        );

        var ipv4Institution = institution(1L, "Instytucja IPv4");
        var ipv6Institution = institution(2L, "Instytucja IPv6");
        var ipv4Rule = singleIpRule(ipv4Institution, IpAddressFamily.IPV4, "127.0.0.1");
        var ipv6Rule = singleIpRule(ipv6Institution, IpAddressFamily.IPV6, "::1");

        when(ipRuleRepository.findAllEnabledWithInstitution()).thenReturn(List.of(ipv4Rule, ipv6Rule));
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(ipv4Institution));
        when(institutionRepository.findById(2L)).thenReturn(Optional.of(ipv6Institution));
        when(institutionRepository.findActiveWithUsersInIds(List.of(1L, 2L))).thenReturn(List.of());
        when(institutionAccessDtoMapper.toDto(ipv4Institution))
                .thenReturn(new InstitutionAccessDto(1L, "Instytucja IPv4", null, null, null, null, false, false, false, false));
        when(institutionAccessDtoMapper.toDto(ipv6Institution))
                .thenReturn(new InstitutionAccessDto(2L, "Instytucja IPv6", null, null, null, null, false, false, false, false));

        var result = service.findInstitutionsForIp("::1");

        assertThat(result.getInstitutions()).extracting(InstitutionAccessDto::getId).containsExactly(1L, 2L);

        var ipv4Result = service.findInstitutionsForIp("127.0.0.1");

        assertThat(ipv4Result.getInstitutions())
                .extracting(InstitutionAccessDto::getId)
                .containsExactly(1L, 2L);
    }

    private Institution institution(Long id, String name) {
        var institution = new Institution();
        institution.setId(id);
        institution.setName(name);
        return institution;
    }

    private InstitutionIpRule singleIpRule(
            Institution institution,
            IpAddressFamily addressFamily,
            String rawValue
    ) {
        var normalizedRule = IpAddressRuleParser.parse(IpRuleType.SINGLE_IP, rawValue, addressFamily);
        var rule = new InstitutionIpRule();
        rule.setInstitution(institution);
        rule.setRuleType(IpRuleType.SINGLE_IP);
        rule.setRuleRaw(normalizedRule.ruleRaw());
        rule.setAddressFamily(normalizedRule.addressFamily());
        rule.setIpFromBytes(normalizedRule.ipFrom());
        rule.setIpToBytes(normalizedRule.ipTo());
        return rule;
    }
}
