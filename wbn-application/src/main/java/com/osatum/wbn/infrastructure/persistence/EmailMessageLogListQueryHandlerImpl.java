package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.notification.dto.EmailMessageLogListDto;
import com.osatum.wbn.application.notification.mapper.EmailMessageLogMapper;
import com.osatum.wbn.application.notification.query.EmailMessageLogListQuery;
import com.osatum.wbn.application.notification.service.EmailMessageLogListQueryHandler;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import com.osatum.wbn.infrastructure.persistence.specifications.EmailMessageLogSpecifications;
import com.osatum.wbn.infrastructure.persistence.specifications.EntitySpecifications;
import com.osatum.wbn.infrastructure.persistence.springdata.EmailMessageLogSpringDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailMessageLogListQueryHandlerImpl extends AbstractQueryHandler<EmailMessageLog, EmailMessageLogListDto, EmailMessageLogListQuery> implements EmailMessageLogListQueryHandler {
    private static final EmailMessageLogSpecifications SPECIFICATIONS = new EmailMessageLogSpecifications();

    private final EmailMessageLogSpringDataRepository repository;
    private final EmailMessageLogMapper mapper;

    @Override
    protected EntitySpecifications<EmailMessageLog, EmailMessageLogListQuery> getSpecifications() {
        return SPECIFICATIONS;
    }

    @Override
    protected JpaSpecificationExecutor<EmailMessageLog> getRepository() {
        return repository;
    }

    @Override
    protected EmailMessageLogListDto toDto(EmailMessageLog entity) {
        return mapper.toListDto(entity);
    }
}
