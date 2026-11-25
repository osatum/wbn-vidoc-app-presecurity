package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.institution.query.InstitutionListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.institution.Institution;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InstitutionSpecifications implements EntitySpecifications<Institution, InstitutionListQuery>{

    @Override
    public Specification<Institution> byFilter(InstitutionListQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getName() != null && !filter.getName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), containsIgnoreCase(filter.getName())));
            }

            if (filter.getInstitutionType() != null) {
                predicates.add(cb.equal(root.get("institutionType"), filter.getInstitutionType()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(InstitutionListQuery filterForm) {
        SortOrderDef.Direction direction = filterForm.getSortBy() == InstitutionListQuery.SortBy.SORT_BY_NAME_DESC
                ? SortOrderDef.Direction.DESC
                : SortOrderDef.Direction.ASC;

        return List.of(
                new SortOrderDef("name", direction),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    private static String containsIgnoreCase(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
