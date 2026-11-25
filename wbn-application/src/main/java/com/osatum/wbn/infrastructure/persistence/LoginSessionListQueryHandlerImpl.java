package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.auth.dto.LoginSessionViewDto;
import com.osatum.wbn.application.auth.mapper.LoginSessionMapper;
import com.osatum.wbn.application.auth.query.LoginSessionListQuery;
import com.osatum.wbn.application.auth.service.LoginSessionListQueryHandler;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.LoginSessionSpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.LoginSessionSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class LoginSessionListQueryHandlerImpl extends AbstractQueryHandler<LoginSession, LoginSessionViewDto, LoginSessionListQuery> implements LoginSessionListQueryHandler {

    private final LoginSessionSpringDataRepository repository;
    private static final LoginSessionSpecifications specifications = new LoginSessionSpecifications();
    private final LoginSessionMapper mapper;
    private final Clock clock;

    @Override
    protected EntitySpecifications<LoginSession, LoginSessionListQuery> getSpecifications() {
        return specifications;
    }

    @Override
    protected JpaSpecificationExecutor<LoginSession> getRepository() {
        return repository;
    }

    @Override
    protected LoginSessionViewDto toDto(LoginSession entity) {
        return mapper.toDto(entity, clock.instant());
    }
}
