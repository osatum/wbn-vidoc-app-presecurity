package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.user.dto.UserListViewDto;
import com.osatum.wbn.application.user.mapper.UserMapper;
import com.osatum.wbn.application.user.query.UserListQuery;
import com.osatum.wbn.application.user.service.UserListQueryHandler;
import com.osatum.wbn.domain.model.user.AppUser;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.UserSpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.UserSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserListQueryHandlerImpl extends AbstractQueryHandler<AppUser, UserListViewDto, UserListQuery> implements UserListQueryHandler {

    private final static UserSpecifications specifications = new UserSpecifications();
    private final UserSpringDataRepository repository;
    private final UserMapper mapper;

    @Override
    protected EntitySpecifications<AppUser, UserListQuery> getSpecifications() {
        return specifications;
    }

    @Override
    protected JpaSpecificationExecutor<AppUser> getRepository() {
        return repository;
    }

    @Override
    protected UserListViewDto toDto(AppUser entity) {
        return mapper.listViewDto(entity);
    }
}
