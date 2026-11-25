package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import com.osatum.wbn.domain.repository.registration.AccountActivationLogRepository;
import com.osatum.wbn.infrastructure.persistence.springdata.AccountActivationLogSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountActivationLogRepositoryAdapter implements AccountActivationLogRepository {
    private final AccountActivationLogSpringDataRepository springDataRepository;

    @Override
    public AccountActivationLog save(AccountActivationLog log) {
        return springDataRepository.save(log);
    }
}
