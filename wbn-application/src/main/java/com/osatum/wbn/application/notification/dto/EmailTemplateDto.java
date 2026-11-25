package com.osatum.wbn.application.notification.dto;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.notification.EmailType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class EmailTemplateDto {
    private Long id;
    private String name;
    private EmailType emailType;
    private String language;
    private String subject;
    private String htmlBody;
    private String textBody;
    private EntityState state;
    private OffsetDateTime createdAt;
    private OffsetDateTime editedAt;
    private String createdBy;
    private String editedBy;
}
