package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.user.dto.UserDataDeletionRequestListDto;
import com.osatum.wbn.application.user.mapper.UserDataDeletionRequestMapper;
import com.osatum.wbn.application.user.query.UserDataDeletionRequestListQuery;
import com.osatum.wbn.application.user.service.UserDataDeletionRequestListQueryHandler;
import com.osatum.wbn.domain.model.user.UserDataDeletionRequest;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.UserDataDeletionRequestSpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.UserDataDeletionRequestSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDataDeletionRequestListQueryHandlerImpl extends AbstractQueryHandler<UserDataDeletionRequest, UserDataDeletionRequestListDto, UserDataDeletionRequestListQuery> implements UserDataDeletionRequestListQueryHandler {
    private static final UserDataDeletionRequestSpecifications SPECIFICATIONS = new UserDataDeletionRequestSpecifications();

    private final UserDataDeletionRequestSpringDataRepository repository;
    private final UserDataDeletionRequestMapper mapper;

    @Override
    protected EntitySpecifications<UserDataDeletionRequest, UserDataDeletionRequestListQuery> getSpecifications() {
        return SPECIFICATIONS;
    }

    @Override
    protected JpaSpecificationExecutor<UserDataDeletionRequest> getRepository() {
        return repository;
    }

    @Override
    protected UserDataDeletionRequestListDto toDto(UserDataDeletionRequest entity) {
        return mapper.toListDto(entity);
    }
}
