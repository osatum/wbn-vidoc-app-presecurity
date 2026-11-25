package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.institution.query.InstitutionMembershipListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class InstitutionMembershipSpecifications implements EntitySpecifications<InstitutionMembership, InstitutionMembershipListQuery> {

    @Override
    public Specification<InstitutionMembership> byFilter(InstitutionMembershipListQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            /*
            predicates.add(cb.equal(root.get("state"), EntityState.ACTIVE));
            predicates.add(cb.equal(root.get("user").get("state"), EntityState.ACTIVE));
            predicates.add(cb.equal(root.get("institution").get("state"), EntityState.ACTIVE));
            */
            if (filter.getInstitutionId() != null) {
                predicates.add(cb.equal(root.get("institution").get("id"), filter.getInstitutionId()));
            }

            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("user").get("email")), containsIgnoreCase(filter.getEmail())));
            }

            if (filter.getName() != null && !filter.getName().isBlank()) {
                String pattern = containsIgnoreCase(filter.getName());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("user").get("firstName")), pattern),
                        cb.like(cb.lower(root.get("user").get("lastName")), pattern)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(InstitutionMembershipListQuery filterForm) {
        return List.of(
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    private static String containsIgnoreCase(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
