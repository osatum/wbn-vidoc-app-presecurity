package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.user.dto.AccountActivationLogListDto;
import com.osatum.wbn.application.user.mapper.AccountActivationLogMapper;
import com.osatum.wbn.application.user.query.AccountActivationLogListQuery;
import com.osatum.wbn.application.user.service.AccountActivationLogListQueryHandler;
import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import com.osatum.wbn.infrastructure.persistence.specifications.AccountActivationLogSpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.AccountActivationLogSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountActivationLogListQueryHandlerImpl extends AbstractQueryHandler<AccountActivationLog, AccountActivationLogListDto, AccountActivationLogListQuery> implements AccountActivationLogListQueryHandler {
    private static final AccountActivationLogSpecifications SPECIFICATIONS = new AccountActivationLogSpecifications();

    private final AccountActivationLogSpringDataRepository repository;
    private final AccountActivationLogMapper mapper;

    @Override
    protected EntitySpecifications<AccountActivationLog, AccountActivationLogListQuery> getSpecifications() {
        return SPECIFICATIONS;
    }

    @Override
    protected JpaSpecificationExecutor<AccountActivationLog> getRepository() {
        return repository;
    }

    @Override
    protected AccountActivationLogListDto toDto(AccountActivationLog entity) {
        return mapper.toListDto(entity);
    }
}
