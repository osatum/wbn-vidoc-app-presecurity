package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.notification.EmailTemplate;
import com.osatum.wbn.domain.model.notification.EmailType;
import com.osatum.wbn.domain.repository.notification.EmailTemplateRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.EmailTemplateSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailTemplateRepositoryAdapter implements EmailTemplateRepository {
    private final EmailTemplateSpringDataRepository springDataRepository;

    @Override
    public List<EmailTemplate> findAllOrderByEmailTypeAndLanguage() {
        return springDataRepository.findAllByOrderByEmailTypeAscLanguageAsc();
    }

    @Override
    public Optional<EmailTemplate> findById(Long id) {
        return springDataRepository.findById(id);
    }

    @Override
    public Optional<EmailTemplate> findByEmailTypeAndLanguage(EmailType emailType, String language) {
        return springDataRepository.findByEmailTypeAndLanguage(emailType, language);
    }

    @Override
    public Optional<EmailTemplate> findActiveByEmailTypeAndLanguage(EmailType emailType, String language) {
        return springDataRepository.findFirstByEmailTypeAndLanguageAndState(emailType, language, EntityState.ACTIVE);
    }

    @Override
    public EmailTemplate save(EmailTemplate template) {
        return springDataRepository.save(template);
    }
}
