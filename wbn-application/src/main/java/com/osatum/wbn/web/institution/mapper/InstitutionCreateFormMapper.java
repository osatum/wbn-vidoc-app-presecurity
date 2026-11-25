package com.osatum.wbn.web.institution.mapper;

import com.osatum.wbn.application.institution.dto.InstitutionCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionEmailDomainCreateDto;
import com.osatum.wbn.application.institution.dto.InstitutionIpRuleCreateDto;
import com.osatum.wbn.web.institution.form.InstitutionCreateForm;
import com.osatum.wbn.web.institution.form.InstitutionEmailDomainCreateForm;
import com.osatum.wbn.web.institution.form.InstitutionIpRuleCreateForm;
import org.springframework.stereotype.Component;

@Component
public class InstitutionCreateFormMapper {
    public InstitutionCreateDto toDto(InstitutionCreateForm form) {
        InstitutionCreateDto dto = new InstitutionCreateDto();
        dto.setName(form.getName());
        dto.setDescription(form.getDescription());
        dto.setContactData(form.getContactData());
        dto.setInstitutionType(form.getInstitutionType());
        dto.setOperatingProfile(form.getOperatingProfile());
        dto.setOperatingProfileSpecified(form.isOperatingProfileSpecified());
        dto.setAuthMode(form.getAuthMode());
        dto.setAllowRegistration(form.isAllowRegistration());
        dto.setAllowOtherIp(form.isAllowOtherIp());
        dto.setAllowOtherEmailDomain(form.isAllowOtherEmailDomain());
        dto.setAutoActivateNewAccounts(form.isAutoActivateNewAccounts());
        dto.setInstitutionIpRules(form.getInstitutionIpRules().stream().map(this::toDto).toList());
        dto.setEmailDomains(form.getEmailDomains().stream().map(this::toDto).toList());
        return dto;
    }

    private InstitutionIpRuleCreateDto toDto(InstitutionIpRuleCreateForm form) {
        InstitutionIpRuleCreateDto dto = new InstitutionIpRuleCreateDto();
        dto.setRuleType(form.getRuleType());
        dto.setAddressFamily(form.getAddressFamily());
        dto.setRuleRaw(form.getRuleRaw());
        dto.setPriority(form.getPriority());
        return dto;
    }

    private InstitutionEmailDomainCreateDto toDto(InstitutionEmailDomainCreateForm form) {
        InstitutionEmailDomainCreateDto dto = new InstitutionEmailDomainCreateDto();
        dto.setDomain(form.getDomain());
        return dto;
    }
}
