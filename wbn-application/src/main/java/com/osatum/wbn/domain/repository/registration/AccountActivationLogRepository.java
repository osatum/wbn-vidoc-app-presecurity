package com.osatum.wbn.domain.repository.registration;

import com.osatum.wbn.domain.model.registration.AccountActivationLog;

public interface AccountActivationLogRepository {
    AccountActivationLog save(AccountActivationLog log);
}
