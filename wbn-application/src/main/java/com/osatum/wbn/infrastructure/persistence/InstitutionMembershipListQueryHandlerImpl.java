package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.institution.dto.InstitutionMembershipListDto;
import com.osatum.wbn.application.institution.mapper.InstitutionMembershipMapper;
import com.osatum.wbn.application.institution.query.InstitutionMembershipListQuery;
import com.osatum.wbn.application.institution.service.InstitutionMembershipListQueryHandler;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.InstitutionMembershipSpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.InstitutionMembershipSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InstitutionMembershipListQueryHandlerImpl extends AbstractQueryHandler<InstitutionMembership, InstitutionMembershipListDto, InstitutionMembershipListQuery> implements InstitutionMembershipListQueryHandler {

    private static final InstitutionMembershipSpecifications specifications = new InstitutionMembershipSpecifications();
    private final InstitutionMembershipSpringDataRepository repository;
    private final InstitutionMembershipMapper mapper;

    @Override
    protected EntitySpecifications<InstitutionMembership, InstitutionMembershipListQuery> getSpecifications() {
        return specifications;
    }

    @Override
    protected JpaSpecificationExecutor<InstitutionMembership> getRepository() {
        return repository;
    }

    @Override
    protected InstitutionMembershipListDto toDto(InstitutionMembership entity) {
        return mapper.toListDto(entity);
    }
}
