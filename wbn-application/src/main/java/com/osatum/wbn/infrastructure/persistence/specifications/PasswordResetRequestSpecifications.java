package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.user.query.PasswordResetRequestListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.passwordreset.PasswordResetRequest;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PasswordResetRequestSpecifications implements EntitySpecifications<PasswordResetRequest, PasswordResetRequestListQuery> {
    @Override
    public Specification<PasswordResetRequest> byFilter(PasswordResetRequestListQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            var user = root.join("user", JoinType.LEFT);

            if (filter.getUserId() != null) {
                predicates.add(cb.equal(user.get("id"), filter.getUserId()));
            }

            if (filter.getName() != null && !filter.getName().isBlank()) {
                String pattern = containsIgnoreCase(filter.getName());
                predicates.add(cb.or(
                        cb.like(cb.lower(user.get("firstName")), pattern),
                        cb.like(cb.lower(user.get("lastName")), pattern)
                ));
            }

            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), containsIgnoreCase(filter.getEmail())));
            }

            if (filter.getIpAddress() != null && !filter.getIpAddress().isBlank()) {
                predicates.add(cb.like(root.get("ipAddress"), contains(filter.getIpAddress())));
            }

            if (filter.getUserAgent() != null && !filter.getUserAgent().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("userAgent")), containsIgnoreCase(filter.getUserAgent())));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getRequestedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("requestedAt"), filter.getRequestedFrom()));
            }

            if (filter.getRequestedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("requestedAt"), filter.getRequestedTo()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(PasswordResetRequestListQuery filterForm) {
        return List.of(new SortOrderDef("requestedAt", SortOrderDef.Direction.DESC));
    }

    private static String containsIgnoreCase(String value) {
        return contains(value).toLowerCase();
    }

    private static String contains(String value) {
        return "%" + value.trim() + "%";
    }
}
