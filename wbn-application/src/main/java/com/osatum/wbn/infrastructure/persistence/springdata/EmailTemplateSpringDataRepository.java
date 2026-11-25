package com.osatum.wbn.infrastructure.persistence.springdata;

import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.notification.EmailTemplate;
import com.osatum.wbn.domain.model.notification.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailTemplateSpringDataRepository extends JpaRepository<EmailTemplate, Long> {
    List<EmailTemplate> findAllByOrderByEmailTypeAscLanguageAsc();

    Optional<EmailTemplate> findByEmailTypeAndLanguage(EmailType emailType, String language);

    Optional<EmailTemplate> findFirstByEmailTypeAndLanguageAndState(EmailType emailType, String language, EntityState state);
}
