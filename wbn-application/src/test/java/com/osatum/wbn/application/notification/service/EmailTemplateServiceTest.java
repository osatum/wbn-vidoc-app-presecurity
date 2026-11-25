package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailTemplateUpdateDto;
import com.osatum.wbn.application.notification.mapper.EmailTemplateMapper;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.notification.EmailTemplate;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.repository.notification.EmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailTemplateServiceTest {
    private EmailTemplateRepository repository;
    private EmailTemplateService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailTemplateRepository.class);
        service = new EmailTemplateService(repository, new EmailTemplateMapper());
        when(repository.save(any(EmailTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatesTemplateAndNormalizesLanguage() {
        EmailTemplate template = template();
        when(repository.findById(10L)).thenReturn(Optional.of(template));
        when(repository.findByEmailTypeAndLanguage(EmailType.ACCOUNT_ACTIVATED, "pl")).thenReturn(Optional.of(template));

        EmailTemplateUpdateDto dto = new EmailTemplateUpdateDto();
        dto.setName(" Konto aktywne ");
        dto.setLanguage(" PL ");
        dto.setSubject(" Temat ");
        dto.setHtmlBody(" <p>Tresc</p> ");
        dto.setTextBody(" Tresc ");
        dto.setState(EntityState.INACTIVE);

        service.updateTemplate(10L, dto);

        ArgumentCaptor<EmailTemplate> captor = ArgumentCaptor.forClass(EmailTemplate.class);
        verify(repository).save(captor.capture());
        EmailTemplate saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Konto aktywne");
        assertThat(saved.getLanguage()).isEqualTo("pl");
        assertThat(saved.getSubject()).isEqualTo("Temat");
        assertThat(saved.getHtmlBody()).isEqualTo("<p>Tresc</p>");
        assertThat(saved.getTextBody()).isEqualTo("Tresc");
        assertThat(saved.getState()).isEqualTo(EntityState.INACTIVE);
    }

    private EmailTemplate template() {
        EmailTemplate template = new EmailTemplate();
        template.setId(10L);
        template.setEmailType(EmailType.ACCOUNT_ACTIVATED);
        template.setLanguage("pl");
        template.setName("Old");
        template.setSubject("Old");
        template.setHtmlBody("<p>Old</p>");
        template.setTextBody("Old");
        template.setState(EntityState.ACTIVE);
        return template;
    }
}
