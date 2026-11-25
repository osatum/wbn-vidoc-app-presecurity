package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.system.dto.NavigationAuditEventListDto;
import com.osatum.wbn.application.system.mapper.NavigationAuditEventMapper;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.system.service.NavigationAuditEventListQueryHandler;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.NavigationAuditEventSpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.NavigationAuditEventSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NavigationAuditEventListQueryHandlerImpl extends AbstractQueryHandler<NavigationAuditEvent, NavigationAuditEventListDto, NavigationAuditEventListQuery> implements NavigationAuditEventListQueryHandler {
    private static final NavigationAuditEventSpecifications SPECIFICATIONS = new NavigationAuditEventSpecifications();

    private final NavigationAuditEventSpringDataRepository repository;
    private final NavigationAuditEventMapper mapper;

    @Override
    protected EntitySpecifications<NavigationAuditEvent, NavigationAuditEventListQuery> getSpecifications() {
        return SPECIFICATIONS;
    }

    @Override
    protected JpaSpecificationExecutor<NavigationAuditEvent> getRepository() {
        return repository;
    }

    @Override
    protected NavigationAuditEventListDto toDto(NavigationAuditEvent entity) {
        return mapper.toListDto(entity);
    }
}
