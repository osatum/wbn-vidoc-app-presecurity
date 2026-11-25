package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.institution.dto.InstitutionListViewDto;
import com.osatum.wbn.application.institution.mapper.InstitutionMapper;
import com.osatum.wbn.application.institution.query.InstitutionListQuery;
import com.osatum.wbn.application.institution.service.InstitutionListQueryHandler;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.InstitutionSpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstitutionListQueryHandlerImpl extends AbstractQueryHandler<Institution, InstitutionListViewDto, InstitutionListQuery> implements InstitutionListQueryHandler {

    private final InstitutionSpringDataRepository repository;
    private final InstitutionMapper mapper;
    private static final InstitutionSpecifications specifications = new InstitutionSpecifications();

    @Override
    protected EntitySpecifications<Institution, InstitutionListQuery> getSpecifications() {
        return specifications;
    }

    @Override
    protected JpaSpecificationExecutor<Institution> getRepository() {
        return repository;
    }

    @Override
    protected InstitutionListViewDto toDto(Institution entity) {
        return mapper.toListViewDto(entity);
    }
}
