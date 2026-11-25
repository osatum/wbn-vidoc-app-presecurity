package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.domain.repository.notification.EmailMessageLogRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.EmailMessageLogSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailMessageLogRepositoryAdapter implements EmailMessageLogRepository {
    private final EmailMessageLogSpringDataRepository springDataRepository;

    @Override
    public Optional<EmailMessageLog> findById(Long id) {
        return springDataRepository.findById(id);
    }

    @Override
    public EmailMessageLog save(EmailMessageLog log) {
        return springDataRepository.save(log);
    }
}
