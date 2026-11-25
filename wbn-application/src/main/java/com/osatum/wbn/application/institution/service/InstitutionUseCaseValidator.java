package com.osatum.wbn.application.institution.service;

import com.osatum.wbn.application.institution.dto.InstitutionCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionEmailDomainCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionIpRuleCreateDto;
import com.osatum.wbn.application.shared.exception.ResourceAlreadyExistsException;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
class InstitutionUseCaseValidator {
    private final InstitutionRepository repository;

    void validateCreate(InstitutionCreateDto dto) {
        validateOperatingProfile(dto);
        validateUniqueName(dto.getName(), null);
        validateUniqueEmailDomains(dto);
        validateUniqueIpRules(dto);
    }

    void validateUpdate(Long institutionId, InstitutionCreateDto dto) {
        validateOperatingProfile(dto);
        validateUniqueName(dto.getName(), institutionId);
        validateUniqueEmailDomains(dto);
        validateUniqueIpRules(dto);
    }

    private static void validateOperatingProfile(InstitutionCreateDto dto) {
        if (dto.getOperatingProfile() != null && dto.getInstitutionType() != InstitutionType.RESEARCH_UNIT) {
            throw new IllegalArgumentException("Operating profile is only available for research units");
        }
    }

    private void validateUniqueName(String name, Long institutionId) {
        String normalizedName = name == null ? null : name.trim();
        boolean exists = institutionId == null
                ? repository.existsByName(normalizedName)
                : repository.existsByNameAndIdNot(normalizedName, institutionId);

        if (exists) {
            throw new ResourceAlreadyExistsException("Institution with this name already exists");
        }
    }

    private static void validateUniqueEmailDomains(InstitutionCreateDto dto) {
        if (dto.getEmailDomains() == null) {
            return;
        }

        Set<String> uniqueDomains = new HashSet<>();
        for (InstitutionEmailDomainCreateDto emailDomain : dto.getEmailDomains()) {
            String normalized = emailDomain.getDomain().trim().toLowerCase(Locale.ROOT);
            if (!uniqueDomains.add(normalized)) {
                throw new IllegalArgumentException("Email domains must be unique");
            }
        }
    }

    private static void validateUniqueIpRules(InstitutionCreateDto dto) {
        if (dto.getInstitutionIpRules() == null) {
            return;
        }

        Set<String> uniqueRules = new HashSet<>();
        for (InstitutionIpRuleCreateDto ipRule : dto.getInstitutionIpRules()) {
            var parsed = IpAddressRuleParser.parse(
                    ipRule.getRuleType(),
                    ipRule.getRuleRaw(),
                    ipRule.getAddressFamily()
            );
            String normalized = ipRule.getRuleType() + ":" + parsed.addressFamily() + ":" + parsed.ruleRaw();
            if (!uniqueRules.add(normalized)) {
                throw new IllegalArgumentException("IP rules must be unique");
            }
        }
    }
}
