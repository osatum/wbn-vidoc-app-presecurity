package com.osatum.wbn.application.notification.mapper;

import com.osatum.wbn.application.notification.dto.EmailMessageLogListDto;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.model.user.AppUser;
import org.springframework.stereotype.Component;

@Component
public class EmailMessageLogMapper {
    public EmailMessageLogListDto toListDto(EmailMessageLog log) {
        EmailMessageLogListDto dto = new EmailMessageLogListDto();
        dto.setId(log.getId());
        dto.setEmailType(log.getEmailType());
        dto.setRecipient(log.getRecipient());
        dto.setStatus(log.getStatus());
        dto.setCreatedAt(log.getCreatedAt());
        dto.setSentAt(log.getSentAt());
        dto.setAttemptCount(log.getAttemptCount());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setTemplateIdentifier(log.getTemplateIdentifier());

        AppUser user = log.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setUserEmail(user.getEmail());
            dto.setUserFirstName(user.getFirstName());
            dto.setUserLastName(user.getLastName());
        }

        Institution institution = log.getInstitution();
        if (institution != null) {
            dto.setInstitutionId(institution.getId());
            dto.setInstitutionName(institution.getName());
        }

        InstitutionMembership membership = log.getMembership();
        if (membership != null) {
            dto.setMembershipId(membership.getId());
        }

        return dto;
    }
}
