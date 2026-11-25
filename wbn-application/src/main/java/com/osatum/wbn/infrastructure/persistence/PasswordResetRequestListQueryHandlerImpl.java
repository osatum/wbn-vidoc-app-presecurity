package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.user.dto.PasswordResetRequestListDto;
import com.osatum.wbn.application.user.mapper.PasswordResetRequestMapper;
import com.osatum.wbn.application.user.query.PasswordResetRequestListQuery;
import com.osatum.wbn.application.user.service.PasswordResetRequestListQueryHandler;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.PasswordResetRequestSpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.PasswordResetRequestSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetRequestListQueryHandlerImpl extends AbstractQueryHandler<PasswordResetRequest, PasswordResetRequestListDto, PasswordResetRequestListQuery> implements PasswordResetRequestListQueryHandler {
    private static final PasswordResetRequestSpecifications SPECIFICATIONS = new PasswordResetRequestSpecifications();

    private final PasswordResetRequestSpringDataRepository repository;
    private final PasswordResetRequestMapper mapper;

    @Override
    protected EntitySpecifications<PasswordResetRequest, PasswordResetRequestListQuery> getSpecifications() {
        return SPECIFICATIONS;
    }

    @Override
    protected JpaSpecificationExecutor<PasswordResetRequest> getRepository() {
        return repository;
    }

    @Override
    protected PasswordResetRequestListDto toDto(PasswordResetRequest entity) {
        return mapper.toListDto(entity);
    }
}
