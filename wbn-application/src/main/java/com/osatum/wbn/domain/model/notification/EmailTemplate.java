package com.osatum.wbn.domain.model.notification;

import com.osatum.wbn.domain.model.GeneratedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "email_template",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_email_template_type_language", columnNames = {"email_type", "language"})
        },
        indexes = {
                @Index(name = "idx_email_template_type", columnList = "email_type"),
                @Index(name = "idx_email_template_language", columnList = "language")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate extends GeneratedIdEntity {
    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_type", nullable = false, length = 64)
    private EmailType emailType;

    @Column(nullable = false, length = 16)
    private String language;

    @Column(nullable = false, length = 256)
    private String subject;

    @Column(name = "html_body", nullable = false, columnDefinition = "text")
    private String htmlBody;

    @Column(name = "text_body", columnDefinition = "text")
    private String textBody;
}
