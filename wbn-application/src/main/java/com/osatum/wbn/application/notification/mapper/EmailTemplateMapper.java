package com.osatum.wbn.application.notification.mapper;

import com.osatum.wbn.application.notification.dto.EmailTemplateDto;
import com.osatum.wbn.domain.model.notification.EmailTemplate;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateMapper {
    public EmailTemplateDto toDto(EmailTemplate template) {
        EmailTemplateDto dto = new EmailTemplateDto();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setEmailType(template.getEmailType());
        dto.setLanguage(template.getLanguage());
        dto.setSubject(template.getSubject());
        dto.setHtmlBody(template.getHtmlBody());
        dto.setTextBody(template.getTextBody());
        dto.setState(template.getState());
        dto.setCreatedAt(template.getCreatedAt());
        dto.setEditedAt(template.getEditedAt());
        dto.setCreatedBy(template.getCreatedBy());
        dto.setEditedBy(template.getEditedBy());
        return dto;
    }
}
