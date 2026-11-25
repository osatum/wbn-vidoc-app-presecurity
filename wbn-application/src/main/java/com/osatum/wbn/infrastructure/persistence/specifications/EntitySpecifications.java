package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface EntitySpecifications<E, F extends PageRequest> {
    Specification<E> byFilter(F filter);

    List<SortOrderDef> sortByFilter(F filterForm);
}