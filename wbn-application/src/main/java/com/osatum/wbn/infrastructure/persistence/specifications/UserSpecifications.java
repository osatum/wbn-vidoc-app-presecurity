package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.application.user.query.UserListQuery;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.InstitutionMembership;
import com.osatum.wbn.domain.model.user.AppUser;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecifications implements EntitySpecifications<AppUser, UserListQuery> {

    @Override
    public Specification<AppUser> byFilter(UserListQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), containsIgnoreCase(filter.getEmail())));
            }

            if (filter.getName() != null && !filter.getName().isBlank()) {
                String pattern = containsIgnoreCase(filter.getName());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern),
                        cb.like(cb.lower(root.get("lastName")), pattern)
                ));
            }

            if (filter.getInstitutionId() != null) {
                Join<AppUser, InstitutionMembership> membership = root.join("memberships");
                query.distinct(true);
                predicates.add(cb.equal(membership.get("institution").get("id"), filter.getInstitutionId()));
                predicates.add(cb.equal(membership.get("state"), EntityState.ACTIVE));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(UserListQuery filterForm) {
        UserListQuery.SortBy sortBy = filterForm.getSortBy() == null
                ? UserListQuery.SortBy.SORT_BY_NAME_ASC
                : filterForm.getSortBy();

        return switch (sortBy) {
            case SORT_BY_EMAIL_ASC -> sortByEmail(SortOrderDef.Direction.ASC);
            case SORT_BY_EMAIL_DESC -> sortByEmail(SortOrderDef.Direction.DESC);
            case SORT_BY_NAME_ASC -> sortByName(SortOrderDef.Direction.ASC);
            case SORT_BY_NAME_DESC -> sortByName(SortOrderDef.Direction.DESC);
        };
    }

    private static List<SortOrderDef> sortByEmail(SortOrderDef.Direction direction) {
        return List.of(
                new SortOrderDef("email", direction),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    private static List<SortOrderDef> sortByName(SortOrderDef.Direction direction) {
        return List.of(
                new SortOrderDef("lastName", direction),
                new SortOrderDef("firstName", direction),
                new SortOrderDef("id", SortOrderDef.Direction.ASC)
        );
    }

    private static String containsIgnoreCase(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }
}
