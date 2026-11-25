package com.osatum.wbn.application.report.survey.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.survey-report")
public class SurveyReportProperties {

    private Map<Integer, Map<String, String>> templates = new HashMap<>();

    public Optional<String> templatePath(Integer year, String language) {
        if (year == null || language == null || language.isBlank()) {
            return Optional.empty();
        }

        Map<String, String> languageTemplates = templates.get(year);
        if (languageTemplates == null || languageTemplates.isEmpty()) {
            return Optional.empty();
        }

        String normalizedLanguage = language.toLowerCase(Locale.ROOT);
        return Optional.ofNullable(languageTemplates.get(normalizedLanguage));
    }
}
