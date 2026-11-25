package com.osatum.wbn.application.institution.mapper;

import com.osatum.wbn.application.institution.dto.InstitutionCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionEmailDomainCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionIpRuleDto;
import com.osatum.wbn.application.institution.dto.InstitutionIpRuleCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionListViewDto;
import com.osatum.wbn.application.institution.dto.InstitutionViewDto;
import com.osatum.wbn.application.net.dto.IpRangeStrings;
import com.osatum.wbn.application.net.service.IpAddressService;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionEmailDomain;
import com.osatum.wbn.domain.model.institution.InstitutionIpRule;
import com.osatum.wbn.domain.model.institution.IpAddressFamily;
import com.osatum.wbn.domain.service.net.IpAddressRuleParser;
import com.osatum.wbn.domain.service.net.NormalizedIpRule;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class InstitutionMapper {

    @Autowired
    private IpAddressService ipAddressService;

    public abstract InstitutionListViewDto toListViewDto(Institution institution);
    public abstract InstitutionViewDto toViewDto(Institution institution);

    public Institution toEntity(InstitutionCreateDto dto) {
        Institution institution = new Institution();
        applyScalars(dto, institution);
        institution.setInstitutionIpRules(toIpRules(dto.getInstitutionIpRules(), institution));
        institution.setEmailDomains(toEmailDomains(dto.getEmailDomains(), institution));
        institution.setState(EntityState.ACTIVE);
        institution.setCreatedAt(OffsetDateTime.now());
        return institution;
    }

    public void updateEntity(InstitutionCreateDto dto, @MappingTarget Institution institution) {
        applyScalars(dto, institution);
        replaceIpRules(dto.getInstitutionIpRules(), institution);
        replaceEmailDomains(dto.getEmailDomains(), institution);
        institution.setEditedAt(OffsetDateTime.now());
    }

    public InstitutionIpRuleDto institutionIpRuleDto(InstitutionIpRule rule) {
        if (rule == null) {
            return null;
        }

        IpRangeStrings ipRangeStrings = ipAddressService.ruleToIpStrings(rule);


        return InstitutionIpRuleDto.builder()
                .id(rule.getId())
                .ruleType(rule.getRuleType())
                .addressFamily(rule.getAddressFamily())
                .ruleRaw(rule.getRuleRaw())
                .ipTo(ipRangeStrings.ipTo())
                .ipFrom(ipRangeStrings.ipFrom())
                .displayRange(buildDisplayRange(ipRangeStrings))
                .displayValue(buildDisplayValue(rule, ipRangeStrings))
                .priority(rule.getPriority())
                .build();
    }

    private String buildDisplayValue(
            InstitutionIpRule rule,
            IpRangeStrings range
    ) {
        if (rule.getRuleType() == null) {
            return rule.getRuleRaw();
        }

        return switch (rule.getRuleType()) {
            case SINGLE_IP -> {
                if (range != null && range.ipFrom() != null) {
                    yield range.ipFrom();
                }
                yield rule.getRuleRaw();
            }

            case RANGE -> {
                if (range != null) {
                    yield range.ipFrom() + " - " + range.ipTo();
                }
                yield rule.getRuleRaw();
            }

            case CIDR -> rule.getRuleRaw();

            case WILDCARD -> rule.getRuleRaw();
        };
    }

    private String buildDisplayRange(IpRangeStrings range) {
        if (range == null) {
            return null;
        }

        if (range.ipFrom() == null || range.ipTo() == null) {
            return null;
        }

        if (range.ipFrom().equals(range.ipTo())) {
            return range.ipFrom();
        }

        return range.ipFrom() + " - " + range.ipTo();
    }

    private void applyScalars(InstitutionCreateDto dto, Institution institution) {
        institution.setName(normalize(dto.getName()));
        institution.setDescription(normalizeNullable(dto.getDescription()));
        institution.setContactData(normalizeNullable(dto.getContactData()));
        institution.setInstitutionType(dto.getInstitutionType());
        if (dto.getInstitutionType() != com.osatum.wbn.domain.model.institution.InstitutionType.RESEARCH_UNIT) {
            institution.setOperatingProfile(null);
        } else if (dto.isOperatingProfileSpecified()) {
            institution.setOperatingProfile(dto.getOperatingProfile());
        }
        institution.setAuthMode(dto.getAuthMode());
        institution.setAllowRegistration(dto.isAllowRegistration());
        institution.setAllowOtherIp(dto.isAllowOtherIp());
        institution.setAllowOtherEmailDomain(dto.isAllowOtherEmailDomain());
        institution.setAutoActivateNewAccounts(dto.isAutoActivateNewAccounts());
    }

    private void replaceIpRules(Iterable<InstitutionIpRuleCreateDto> dtos, Institution institution) {
        if (institution.getInstitutionIpRules() == null) {
            institution.setInstitutionIpRules(toIpRules(dtos, institution));
            return;
        }

        Set<String> requestedKeys = new HashSet<>();
        if (dtos != null) {
            for (InstitutionIpRuleCreateDto dto : dtos) {
                requestedKeys.add(ipRuleKey(dto));
            }
        }

        institution.getInstitutionIpRules().removeIf(rule -> !requestedKeys.contains(ipRuleKey(rule)));
        Map<String, InstitutionIpRule> existingRules = institution.getInstitutionIpRules().stream()
                .collect(Collectors.toMap(this::ipRuleKey, Function.identity(), (first, second) -> first));

        if (dtos == null) {
            return;
        }

        for (InstitutionIpRuleCreateDto dto : dtos) {
            InstitutionIpRule rule = existingRules.get(ipRuleKey(dto));
            if (rule == null) {
                institution.getInstitutionIpRules().add(toIpRule(dto, institution));
                continue;
            }

            applyIpRule(dto, rule);
            rule.setEditedAt(OffsetDateTime.now());
        }
    }

    private void replaceEmailDomains(Iterable<InstitutionEmailDomainCreateDto> dtos, Institution institution) {
        if (institution.getEmailDomains() == null) {
            institution.setEmailDomains(toEmailDomains(dtos, institution));
            return;
        }

        Set<String> requestedDomains = new HashSet<>();
        if (dtos != null) {
            for (InstitutionEmailDomainCreateDto dto : dtos) {
                requestedDomains.add(emailDomainKey(dto));
            }
        }

        institution.getEmailDomains().removeIf(domain -> !requestedDomains.contains(emailDomainKey(domain)));
        Map<String, InstitutionEmailDomain> existingDomains = institution.getEmailDomains().stream()
                .collect(Collectors.toMap(this::emailDomainKey, Function.identity(), (first, second) -> first));

        if (dtos == null) {
            return;
        }

        for (InstitutionEmailDomainCreateDto dto : dtos) {
            String key = emailDomainKey(dto);
            if (!existingDomains.containsKey(key)) {
                institution.getEmailDomains().add(toEmailDomain(dto, institution));
            }
        }
    }

    private Set<InstitutionIpRule> toIpRules(Iterable<InstitutionIpRuleCreateDto> dtos, Institution institution) {
        Set<InstitutionIpRule> rules = new LinkedHashSet<>();
        if (dtos == null) {
            return rules;
        }

        for (InstitutionIpRuleCreateDto dto : dtos) {
            rules.add(toIpRule(dto, institution));
        }

        return rules;
    }

    private InstitutionIpRule toIpRule(InstitutionIpRuleCreateDto dto, Institution institution) {
        InstitutionIpRule rule = new InstitutionIpRule();
        rule.setInstitution(institution);
        applyIpRule(dto, rule);
        rule.setState(EntityState.ACTIVE);
        rule.setCreatedAt(OffsetDateTime.now());
        return rule;
    }

    private void applyIpRule(InstitutionIpRuleCreateDto dto, InstitutionIpRule rule) {
        NormalizedIpRule normalizedRule = ipAddressService.normalizeRule(
                dto.getRuleType(),
                dto.getRuleRaw(),
                dto.getAddressFamily()
        );
        rule.setRuleType(dto.getRuleType());
        rule.setAddressFamily(normalizedRule.addressFamily());
        rule.setRuleRaw(normalizedRule.ruleRaw());
        rule.setIpFromBytes(normalizedRule.ipFrom());
        rule.setIpToBytes(normalizedRule.ipTo());
        rule.setCidrPrefix(normalizedRule.cidrPrefix());
        if (normalizedRule.addressFamily() == IpAddressFamily.IPV4) {
            rule.setIpFromLong(IpAddressRuleParser.ipv4BytesToLong(normalizedRule.ipFrom()));
            rule.setIpToLong(IpAddressRuleParser.ipv4BytesToLong(normalizedRule.ipTo()));
        } else {
            rule.setIpFromLong(null);
            rule.setIpToLong(null);
        }
        rule.setPriority(dto.getPriority());
    }

    private Set<InstitutionEmailDomain> toEmailDomains(Iterable<InstitutionEmailDomainCreateDto> dtos, Institution institution) {
        Set<InstitutionEmailDomain> domains = new LinkedHashSet<>();
        if (dtos == null) {
            return domains;
        }

        for (InstitutionEmailDomainCreateDto dto : dtos) {
            domains.add(toEmailDomain(dto, institution));
        }

        return domains;
    }

    private InstitutionEmailDomain toEmailDomain(InstitutionEmailDomainCreateDto dto, Institution institution) {
        InstitutionEmailDomain domain = new InstitutionEmailDomain();
        domain.setInstitution(institution);
        domain.setDomain(emailDomainKey(dto));
        domain.setState(EntityState.ACTIVE);
        domain.setCreatedAt(OffsetDateTime.now());
        return domain;
    }

    private String ipRuleKey(InstitutionIpRuleCreateDto dto) {
        NormalizedIpRule normalizedRule = ipAddressService.normalizeRule(
                dto.getRuleType(),
                dto.getRuleRaw(),
                dto.getAddressFamily()
        );
        return dto.getRuleType() + ":" + normalizedRule.addressFamily() + ":" + normalizedRule.ruleRaw();
    }

    private String ipRuleKey(InstitutionIpRule rule) {
        return rule.getRuleType() + ":" + rule.getAddressFamily() + ":" + normalize(rule.getRuleRaw()).toLowerCase(Locale.ROOT);
    }

    private String emailDomainKey(InstitutionEmailDomainCreateDto dto) {
        return normalize(dto.getDomain()).toLowerCase(Locale.ROOT);
    }

    private String emailDomainKey(InstitutionEmailDomain domain) {
        return normalize(domain.getDomain()).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
