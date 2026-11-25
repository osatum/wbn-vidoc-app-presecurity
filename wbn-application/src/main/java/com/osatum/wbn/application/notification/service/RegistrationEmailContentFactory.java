package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailContent;
import com.osatum.wbn.domain.model.notification.EmailTemplate;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.domain.repository.notification.EmailTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RegistrationEmailContentFactory {
    private final EmailTemplateRepository emailTemplateRepository;

    @Value("${app.registration.activation-url-template}")
    private String activationUrlTemplate;

    @Value("${app.password-reset.url-template}")
    private String passwordResetUrlTemplate = "http://localhost:4201/auth/password-reset/confirm/{token}";

    @Value("${app.user-data-deletion.url-template:http://localhost:4201/auth/delete-my-data/confirm/{token}}")
    private String userDataDeletionUrlTemplate;

    @Value("${app.mail.template-language:pl}")
    private String templateLanguage;

    /**
     * Tworzy treść wiadomości e-mail z aktywnego szablonu albo z domyślnej treści awaryjnej.
     */
    public EmailContent create(
            EmailType emailType,
            AppUser user,
            Institution institution,
            String rawActivationToken
    ) {
        validateActivationToken(emailType, rawActivationToken);

        return emailTemplateRepository.findActiveByEmailTypeAndLanguage(emailType, normalizeLanguage(templateLanguage))
                .map(template -> templateMessage(emailType, template, user, institution, rawActivationToken))
                .orElseGet(() -> defaultMessage(emailType, user, institution, rawActivationToken));
    }

    private EmailContent defaultMessage(
            EmailType emailType,
            AppUser user,
            Institution institution,
            String rawActivationToken
    ) {
        return switch (emailType) {
            case ACCOUNT_ACTIVATION_LINK -> activationLink(user, institution, rawActivationToken, false);
            case ACTIVATION_LINK_REGENERATED -> activationLink(user, institution, rawActivationToken, true);
            case PASSWORD_RESET_LINK -> passwordResetLink(user, rawActivationToken);
            case USER_DATA_DELETION_CONFIRMATION -> userDataDeletionConfirmation(user, institution, rawActivationToken);
            case ACCOUNT_ACTIVATED -> simpleMessage(
                    EmailType.ACCOUNT_ACTIVATED,
                    "Your account is active",
                    user,
                    institution,
                    "Your account has been activated."
            );
            case ACCOUNT_WAITING_FOR_CONFIRMATION -> simpleMessage(
                    EmailType.ACCOUNT_WAITING_FOR_CONFIRMATION,
                    "Your account is waiting for confirmation",
                    user,
                    institution,
                    "Your account has been activated by link and is waiting for institution administrator confirmation."
            );
        };
    }

    private EmailContent templateMessage(
            EmailType emailType,
            EmailTemplate template,
            AppUser user,
            Institution institution,
            String rawActivationToken
    ) {
        String activationUrl = rawActivationToken == null || rawActivationToken.isBlank() || EmailType.PASSWORD_RESET_LINK.equals(emailType)
                ? ""
                : activationUrl(rawActivationToken);
        String passwordResetUrl = rawActivationToken == null || rawActivationToken.isBlank() || !EmailType.PASSWORD_RESET_LINK.equals(emailType)
                ? ""
                : passwordResetUrl(rawActivationToken);
        String userDataDeletionUrl = rawActivationToken == null || rawActivationToken.isBlank() || !EmailType.USER_DATA_DELETION_CONFIRMATION.equals(emailType)
                ? ""
                : userDataDeletionUrl(rawActivationToken);
        Map<String, String> textPlaceholders = placeholders(user, institution, activationUrl, passwordResetUrl, userDataDeletionUrl, false);
        Map<String, String> htmlPlaceholders = placeholders(user, institution, activationUrl, passwordResetUrl, userDataDeletionUrl, true);

        return new EmailContent(
                render(template.getSubject(), textPlaceholders),
                render(template.getTextBody(), textPlaceholders),
                render(template.getHtmlBody(), htmlPlaceholders),
                templateIdentifier(template)
        );
    }

    private EmailContent activationLink(
            AppUser user,
            Institution institution,
            String rawActivationToken,
            boolean regenerated
    ) {
        String activationUrl = activationUrl(rawActivationToken);
        String subject = regenerated ? "New account activation link" : "Account activation link";
        String text = greeting(user)
                + "\n\n"
                + "Use this link to activate your account for institution: " + institution.getName() + "\n"
                + activationUrl
                + "\n\n"
                + "If you did not request this account, ignore this message.";
        String html = "<p>" + escape(greeting(user)) + "</p>"
                + "<p>Use this link to activate your account for institution: "
                + escape(institution.getName()) + "</p>"
                + "<p><a href=\"" + escape(activationUrl) + "\">Activate account</a></p>"
                + "<p>If you did not request this account, ignore this message.</p>";
        return new EmailContent(subject, text, html, emailTypeIdentifier(regenerated
                ? EmailType.ACTIVATION_LINK_REGENERATED
                : EmailType.ACCOUNT_ACTIVATION_LINK));
    }

    private EmailContent passwordResetLink(AppUser user, String rawToken) {
        String resetUrl = passwordResetUrl(rawToken);
        String subject = "Password reset link";
        String text = greeting(user)
                + "\n\n"
                + "We received a password reset request for account: " + user.getEmail() + "\n"
                + resetUrl
                + "\n\n"
                + "This link is valid for 1 hour. If you did not request a password reset, ignore this message.";
        String html = "<p>" + escape(greeting(user)) + "</p>"
                + "<p>We received a password reset request for account: " + escape(user.getEmail()) + "</p>"
                + "<p><a href=\"" + escape(resetUrl) + "\">Set new password</a></p>"
                + "<p>This link is valid for 1 hour. If you did not request a password reset, ignore this message.</p>";
        return new EmailContent(subject, text, html, emailTypeIdentifier(EmailType.PASSWORD_RESET_LINK));
    }

    private EmailContent userDataDeletionConfirmation(AppUser user, Institution institution, String rawToken) {
        String deletionUrl = userDataDeletionUrl(rawToken);
        String subject = "Confirm personal data deletion";
        String text = greeting(user)
                + "\n\n"
                + "We received a personal data deletion request for account: " + user.getEmail() + "\n"
                + "Institution: " + institution.getName() + "\n"
                + deletionUrl
                + "\n\n"
                + "This link is valid for 1 hour. If you did not request deletion, ignore this message.";
        String html = "<p>" + escape(greeting(user)) + "</p>"
                + "<p>We received a personal data deletion request for account: " + escape(user.getEmail()) + "</p>"
                + "<p>Institution: " + escape(institution.getName()) + "</p>"
                + "<p><a href=\"" + escape(deletionUrl) + "\">Confirm data deletion</a></p>"
                + "<p>This link is valid for 1 hour. If you did not request deletion, ignore this message.</p>";
        return new EmailContent(subject, text, html, emailTypeIdentifier(EmailType.USER_DATA_DELETION_CONFIRMATION));
    }

    private EmailContent simpleMessage(EmailType emailType, String subject, AppUser user, Institution institution, String message) {
        String text = greeting(user)
                + "\n\n"
                + message
                + "\nInstitution: " + institution.getName();
        String html = "<p>" + escape(greeting(user)) + "</p>"
                + "<p>" + escape(message) + "</p>"
                + "<p>Institution: " + escape(institution.getName()) + "</p>";
        return new EmailContent(subject, text, html, emailTypeIdentifier(emailType));
    }

    private void validateActivationToken(EmailType emailType, String rawActivationToken) {
        boolean tokenRequired = EmailType.ACCOUNT_ACTIVATION_LINK.equals(emailType)
                || EmailType.ACTIVATION_LINK_REGENERATED.equals(emailType)
                || EmailType.PASSWORD_RESET_LINK.equals(emailType)
                || EmailType.USER_DATA_DELETION_CONFIRMATION.equals(emailType);
        if (tokenRequired && (rawActivationToken == null || rawActivationToken.isBlank())) {
            throw new IllegalArgumentException("Token is required for token link email.");
        }
    }

    private Map<String, String> placeholders(
            AppUser user,
            Institution institution,
            String activationUrl,
            String passwordResetUrl,
            String userDataDeletionUrl,
            boolean html
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("firstName", value(user.getFirstName(), html));
        values.put("lastName", value(user.getLastName(), html));
        values.put("fullName", value(fullName(user), html));
        values.put("email", value(user.getEmail(), html));
        values.put("institutionName", value(institution == null ? "" : institution.getName(), html));
        values.put("activationUrl", value(activationUrl, html));
        values.put("passwordResetUrl", value(passwordResetUrl, html));
        values.put("userDataDeletionUrl", value(userDataDeletionUrl, html));
        return values;
    }

    private String render(String template, Map<String, String> placeholders) {
        if (template == null) {
            return "";
        }

        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private String templateIdentifier(EmailTemplate template) {
        return template.getId() == null
                ? emailTypeIdentifier(template.getEmailType()) + ":" + template.getLanguage()
                : "email-template:" + template.getId();
    }

    private String emailTypeIdentifier(EmailType emailType) {
        return emailType.name();
    }

    private String fullName(AppUser user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isBlank() ? user.getEmail() : fullName;
    }

    private String value(String value, boolean html) {
        return html ? escape(value) : value == null ? "" : value;
    }

    private String activationUrl(String rawActivationToken) {
        String encodedToken = URLEncoder.encode(rawActivationToken, StandardCharsets.UTF_8);
        return activationUrlTemplate.replace("{token}", encodedToken);
    }

    private String passwordResetUrl(String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return passwordResetUrlTemplate.replace("{token}", encodedToken);
    }

    private String userDataDeletionUrl(String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return userDataDeletionUrlTemplate.replace("{token}", encodedToken);
    }

    private String greeting(AppUser user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        if (firstName.isBlank()) {
            return "Hello";
        }
        return "Hello " + firstName;
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "pl";
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }
}
