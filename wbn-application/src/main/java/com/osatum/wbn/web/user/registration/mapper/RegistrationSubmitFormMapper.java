package com.osatum.wbn.web.user.registration.mapper;

import com.osatum.wbn.application.user.dto.RegistrationSubmitDto;
import com.osatum.wbn.web.user.registration.form.RegistrationSubmitForm;
import org.springframework.stereotype.Component;

@Component
public class RegistrationSubmitFormMapper {
    public RegistrationSubmitDto toDto(Long institutionId, RegistrationSubmitForm form) {
        RegistrationSubmitDto dto = new RegistrationSubmitDto();
        dto.setInstitutionId(institutionId);
        dto.setEmail(form.getEmail());
        dto.setFirstName(form.getFirstName());
        dto.setLastName(form.getLastName());
        dto.setOrcid(form.getOrcid());
        dto.setPassword(form.getPassword());
        dto.setTermsAndPrivacyPolicyAccepted(form.isTermsAndPrivacyPolicyAccepted());
        dto.setPersonalDataProcessingAcknowledged(form.isPersonalDataProcessingAcknowledged());
        return dto;
    }
}
