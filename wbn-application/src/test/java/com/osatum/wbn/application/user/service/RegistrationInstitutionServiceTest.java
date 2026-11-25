package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.RegistrationAvailabilityReason;
import com.osatum.wbn.application.user.service.RegistrationInstitutionService;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.AuthMode;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.IpRuleType;
import com.osatum.wbn.domain.repository.institution.InstitutionIpRuleRepository;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.service.institution.InstitutionIpMatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationInstitutionServiceTest {
    private InstitutionRepository institutionRepository;
    private InstitutionIpRuleRepository ipRuleRepository;
    private InstitutionIpMatcherService matcher;
    private RegistrationInstitutionService service;

    @BeforeEach
    void setUp() {
        institutionRepository = mock(InstitutionRepository.class);
        ipRuleRepository = mock(InstitutionIpRuleRepository.class);
        matcher = mock(InstitutionIpMatcherService.class);
        service = new RegistrationInstitutionService(institutionRepository, ipRuleRepository, matcher);
    }

    @Test
    void returnsGeneralRegistrationInstitutionsAllowedForClientIp() {
        Institution openInstitution = activeInstitution(1L, "Open Research Unit");
        openInstitution.setAllowOtherIp(true);
        openInstitution.setAllowOtherEmailDomain(true);
        Institution matchedInstitution = activeInstitution(2L, "Matched Research Unit");
        Institution blockedInstitution = activeInstitution(3L, "Blocked Research Unit");
        Institution operator = activeInstitution(4L, "System Operator");
        operator.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);

        when(institutionRepository.findActiveRegistrationEnabled())
                .thenReturn(List.of(openInstitution, matchedInstitution, blockedInstitution, operator));
        when(matcher.matchesInstitution(2L, "192.0.2.10")).thenReturn(true);
        when(matcher.matchesInstitution(3L, "192.0.2.10")).thenReturn(false);

        var result = service.findGeneralRegistrationInstitutions("192.0.2.10");

        assertThat(result)
                .extracting("id")
                .containsExactly(1L, 2L);
        assertThat(result.getFirst().getName()).isEqualTo("Open Research Unit");
        assertThat(result.getFirst().isAllowOtherIp()).isTrue();
        assertThat(result.getFirst().isAllowOtherEmailDomain()).isTrue();
        verify(matcher, never()).matchesInstitution(1L, "192.0.2.10");
        verify(matcher, never()).matchesInstitution(4L, "192.0.2.10");
    }

    @Test
    void returnsOnlyActiveRegistrationInstitutionsMatchedByIp() {
        Institution allowed = activeInstitution(1L, "Allowed");
        Institution allowedDuplicate = activeInstitution(1L, "Allowed");
        Institution disabled = activeInstitution(2L, "Disabled");
        disabled.setAllowRegistration(false);
        Institution inactive = activeInstitution(3L, "Inactive");
        inactive.setState(EntityState.INACTIVE);
        Institution operator = activeInstitution(4L, "Operator");
        operator.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);

        InstitutionIpRule allowedRule = ipRule(allowed);
        InstitutionIpRule allowedDuplicateRule = ipRule(allowedDuplicate);
        InstitutionIpRule disabledRule = ipRule(disabled);
        InstitutionIpRule inactiveRule = ipRule(inactive);
        InstitutionIpRule operatorRule = ipRule(operator);
        when(ipRuleRepository.findAllEnabledWithInstitution()).thenReturn(List.of(disabledRule, allowedDuplicateRule, inactiveRule, operatorRule, allowedRule));
        when(matcher.matches("192.0.2.10", allowedRule)).thenReturn(true);
        when(matcher.matches("192.0.2.10", allowedDuplicateRule)).thenReturn(true);
        when(matcher.matches("192.0.2.10", disabledRule)).thenReturn(true);
        when(matcher.matches("192.0.2.10", inactiveRule)).thenReturn(true);
        when(matcher.matches("192.0.2.10", operatorRule)).thenReturn(true);

        var result = service.findIpRegistrationInstitutions("192.0.2.10");

        assertThat(result)
                .singleElement()
                .satisfies(dto -> {
                    assertThat(dto.getId()).isEqualTo(1L);
                    assertThat(dto.getName()).isEqualTo("Allowed");
                });
    }

    @Test
    void allowsRegistrationStartWhenIpMatches() {
        Institution institution = activeInstitution(1L, "Allowed");
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "192.0.2.10")).thenReturn(true);

        var result = service.validateRegistrationStart(1L, "192.0.2.10");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getReason()).isEqualTo(RegistrationAvailabilityReason.AVAILABLE);
        assertThat(result.getInstitution().getId()).isEqualTo(1L);
    }

    @Test
    void allowsRegistrationStartForOtherIpWhenInstitutionAllowsIt() {
        Institution institution = activeInstitution(1L, "Allowed");
        institution.setAllowOtherIp(true);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));

        var result = service.validateRegistrationStart(1L, "not-ipv4");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getReason()).isEqualTo(RegistrationAvailabilityReason.AVAILABLE);
        verify(matcher, never()).matchesInstitution(1L, "not-ipv4");
    }

    @Test
    void blocksRegistrationStartWhenIpDoesNotMatch() {
        Institution institution = activeInstitution(1L, "Allowed");
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));
        when(matcher.matchesInstitution(1L, "192.0.2.10")).thenReturn(false);

        var result = service.validateRegistrationStart(1L, "192.0.2.10");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).isEqualTo(RegistrationAvailabilityReason.IP_NOT_ALLOWED);
        assertThat(result.getInstitution().getId()).isEqualTo(1L);
    }

    @Test
    void blocksRegistrationStartWhenRegistrationIsDisabled() {
        Institution institution = activeInstitution(1L, "Disabled");
        institution.setAllowRegistration(false);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));

        var result = service.validateRegistrationStart(1L, "192.0.2.10");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).isEqualTo(RegistrationAvailabilityReason.REGISTRATION_DISABLED);
        assertThat(result.getInstitution()).isNull();
    }

    @Test
    void blocksRegistrationStartForSystemOperator() {
        Institution institution = activeInstitution(1L, "Operator");
        institution.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);
        institution.setAllowOtherIp(true);
        when(institutionRepository.findById(1L)).thenReturn(Optional.of(institution));

        var result = service.validateRegistrationStart(1L, "192.0.2.10");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).isEqualTo(RegistrationAvailabilityReason.REGISTRATION_DISABLED);
        assertThat(result.getInstitution()).isNull();
    }

    @Test
    void blocksRegistrationStartWhenInstitutionDoesNotExist() {
        when(institutionRepository.findById(99L)).thenReturn(Optional.empty());

        var result = service.validateRegistrationStart(99L, "192.0.2.10");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getReason()).isEqualTo(RegistrationAvailabilityReason.INSTITUTION_NOT_FOUND);
        assertThat(result.getInstitution()).isNull();
    }

    private Institution activeInstitution(Long id, String name) {
        Institution institution = new Institution();
        institution.setId(id);
        institution.setName(name);
        institution.setDescription(name + " description");
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setAuthMode(AuthMode.AUTH_REQUIRED);
        institution.setAllowRegistration(true);
        institution.setAutoActivateNewAccounts(false);
        return institution;
    }

    private InstitutionIpRule ipRule(Institution institution) {
        InstitutionIpRule rule = new InstitutionIpRule();
        rule.setInstitution(institution);
        rule.setRuleType(IpRuleType.SINGLE_IP);
        rule.setRuleRaw("192.0.2.10");
        return rule;
    }
}
