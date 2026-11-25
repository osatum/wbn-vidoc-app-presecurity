package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.auth.query.LoginSessionListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.auth.LoginSession;
import com.osatum.wbn.domain.model.auth.LoginSessionStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LoginSessionSpecifications implements EntitySpecifications<LoginSession, LoginSessionListQuery> {
    @Override
    public Specification<LoginSession> byFilter(LoginSessionListQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getName() != null && !filter.getName().isBlank()) {
                String pattern = containsIgnoreCase(filter.getName());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("institutionName")), pattern),
                        cb.like(cb.lower(root.<String>get("userEmail")), pattern)
                ));
            }

            if (filter.getEmail() != null && !filter.getEmail().isBlank()) {
                predicates.add(cb.like(cb.lower(root.<String>get("userEmail")), containsIgnoreCase(filter.getEmail())));
            }

            if (filter.getIpAddress() != null && !filter.getIpAddress().isBlank()) {
                predicates.add(cb.like(root.<String>get("ipAddress"), contains(filter.getIpAddress())));
            }

            if (filter.getActorType() != null) {
                predicates.add(cb.equal(root.get("actorType"), filter.getActorType()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getEndReason() != null) {
                predicates.add(cb.equal(root.get("endReason"), filter.getEndReason()));
            }

            if (filter.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), filter.getUserId()));
            }

            if (filter.getInstitutionId() != null) {
                predicates.add(cb.equal(root.get("institution").get("id"), filter.getInstitutionId()));
            }

            if (Boolean.TRUE.equals(filter.getActiveOnly())) {
                predicates.add(cb.equal(root.get("status"), LoginSessionStatus.ACTIVE));
                predicates.add(cb.greaterThan(root.<Instant>get("expiresAt"), Instant.now()));
                predicates.add(cb.isNull(root.get("endedAt")));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(LoginSessionListQuery filterForm) {
        List<SortOrderDef> sortBy = new ArrayList<>();
        sortBy.add(new SortOrderDef("startedAt", SortOrderDef.Direction.DESC));
        return sortBy;
    }

    private static String containsIgnoreCase(String value) {
        return contains(value).toLowerCase();
    }

    private static String contains(String value) {
        return "%" + value.trim() + "%";
    }
}
