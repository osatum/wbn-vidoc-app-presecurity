package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailTemplateDto;
import com.osatum.wbn.application.notification.dto.EmailTemplateUpdateDto;
import com.osatum.wbn.application.notification.mapper.EmailTemplateMapper;
import com.osatum.wbn.application.shared.exception.ResourceAlreadyExistsException;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;
import com.osatum.wbn.application.system.authorization.CanSystemManage;
import com.osatum.wbn.domain.model.notification.EmailTemplate;
import com.osatum.wbn.domain.repository.notification.EmailTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    private final EmailTemplateRepository repository;
    private final EmailTemplateMapper mapper;

    /**
     * Zwraca listę szablonów e-mail posortowaną według typu wiadomości i języka.
     */
    @CanSystemManage
    @Transactional(readOnly = true)
    public List<EmailTemplateDto> findTemplates() {
        return repository.findAllOrderByEmailTypeAndLanguage().stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * Pobiera szczegóły pojedynczego szablonu e-mail.
     */
    @CanSystemManage
    @Transactional(readOnly = true)
    public EmailTemplateDto findTemplate(Long id) {
        return mapper.toDto(findExisting(id));
    }

    /**
     * Aktualizuje edytowalne pola szablonu e-mail po walidacji unikalności języka dla typu wiadomości.
     */
    @CanSystemManage
    @Transactional
    public EmailTemplateDto updateTemplate(Long id, EmailTemplateUpdateDto dto) {
        EmailTemplate template = findExisting(id);
        String language = normalizeLanguage(dto.getLanguage());

        repository.findByEmailTypeAndLanguage(template.getEmailType(), language)
                .filter(existing -> !existing.getId().equals(template.getId()))
                .ifPresent(existing -> {
                    throw new ResourceAlreadyExistsException("language", "Template for this e-mail type and language already exists.");
                });

        template.setName(dto.getName().trim());
        template.setLanguage(language);
        template.setSubject(dto.getSubject().trim());
        template.setHtmlBody(dto.getHtmlBody().trim());
        template.setTextBody(normalizeNullable(dto.getTextBody()));
        template.setState(dto.getState());

        return mapper.toDto(repository.save(template));
    }

    private EmailTemplate findExisting(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Email template not found: " + id));
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("Template language is required.");
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
