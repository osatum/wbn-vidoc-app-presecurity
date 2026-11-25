package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailContent;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.notification.EmailTemplate;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.notification.EmailTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationEmailContentFactoryTest {
    private EmailTemplateRepository repository;
    private RegistrationEmailContentFactory factory;

    @BeforeEach
    void setUp() {
        repository = mock(EmailTemplateRepository.class);
        factory = new RegistrationEmailContentFactory(repository);
        ReflectionTestUtils.setField(factory, "activationUrlTemplate", "https://app.example.org/activate/{token}");
        ReflectionTestUtils.setField(factory, "templateLanguage", "pl");
    }

    @Test
    void rendersActiveTemplateForEmailType() {
        EmailTemplate template = template();
        when(repository.findActiveByEmailTypeAndLanguage(EmailType.ACCOUNT_ACTIVATION_LINK, "pl"))
                .thenReturn(Optional.of(template));

        EmailContent content = factory.create(
                EmailType.ACCOUNT_ACTIVATION_LINK,
                user(),
                institution(),
                "raw token"
        );

        assertThat(content.subject()).isEqualTo("Aktywacja Research & Development");
        assertThat(content.textBody()).contains("Jan");
        assertThat(content.textBody()).contains("https://app.example.org/activate/raw+token");
        assertThat(content.htmlBody()).contains("Research &amp; Development");
        assertThat(content.templateIdentifier()).isEqualTo("email-template:7");
    }

    @Test
    void fallsBackToBuiltInContentWhenTemplateDoesNotExist() {
        when(repository.findActiveByEmailTypeAndLanguage(any(), any())).thenReturn(Optional.empty());

        EmailContent content = factory.create(
                EmailType.ACCOUNT_ACTIVATED,
                user(),
                institution(),
                null
        );

        assertThat(content.subject()).isEqualTo("Your account is active");
        assertThat(content.templateIdentifier()).isEqualTo(EmailType.ACCOUNT_ACTIVATED.name());
    }

    @Test
    void requiresRawTokenForActivationLinkEmail() {
        assertThatThrownBy(() -> factory.create(
                EmailType.ACCOUNT_ACTIVATION_LINK,
                user(),
                institution(),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private EmailTemplate template() {
        EmailTemplate template = new EmailTemplate();
        template.setId(7L);
        template.setName("Activation");
        template.setEmailType(EmailType.ACCOUNT_ACTIVATION_LINK);
        template.setLanguage("pl");
        template.setSubject("Aktywacja {{institutionName}}");
        template.setTextBody("Dzien dobry {{firstName}}\n{{activationUrl}}");
        template.setHtmlBody("<p>{{institutionName}}</p><a href=\"{{activationUrl}}\">Link</a>");
        template.setState(EntityState.ACTIVE);
        return template;
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setEmail("jan@example.edu");
        user.setFirstName("Jan");
        user.setLastName("Kowalski");
        return user;
    }

    private Institution institution() {
        Institution institution = new Institution();
        institution.setName("Research & Development");
        return institution;
    }
}
