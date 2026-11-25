package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.user.query.AccountActivationLogListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.registration.AccountActivationLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AccountActivationLogSpecifications implements EntitySpecifications<AccountActivationLog, AccountActivationLogListQuery> {
    @Override
    public Specification<AccountActivationLog> byFilter(AccountActivationLogListQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), filter.getUserId()));
            }

            if (filter.getInstitutionId() != null) {
                predicates.add(cb.equal(root.get("institution").get("id"), filter.getInstitutionId()));
            }

            if (filter.getName() != null && !filter.getName().isBlank()) {
                String pattern = containsIgnoreCase(filter.getName());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("user").get("firstName")), pattern),
                        cb.like(cb.lower(root.get("user").get("lastName")), pattern),
                        cb.like(cb.lower(root.get("institution").get("name")), pattern)
                ));
            }

            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("user").get("email")), containsIgnoreCase(filter.getEmail())));
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

            if (filter.getAttemptedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("attemptedAt"), filter.getAttemptedFrom()));
            }

            if (filter.getAttemptedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("attemptedAt"), filter.getAttemptedTo()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(AccountActivationLogListQuery filterForm) {
        return List.of(new SortOrderDef("attemptedAt", SortOrderDef.Direction.DESC));
    }

    private static String containsIgnoreCase(String value) {
        return contains(value).toLowerCase();
    }

    private static String contains(String value) {
        return "%" + value.trim() + "%";
    }
}
