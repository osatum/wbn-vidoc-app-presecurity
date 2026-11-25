package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NavigationAuditEventSpecifications implements EntitySpecifications<NavigationAuditEvent, NavigationAuditEventListQuery> {
    @Override
    public Specification<NavigationAuditEvent> byFilter(NavigationAuditEventListQuery filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getOccurredFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("occurredAt"), filter.getOccurredFrom()));
            }

            if (filter.getOccurredTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("occurredAt"), filter.getOccurredTo()));
            }

            if (hasText(filter.getInstitution())) {
                String value = filter.getInstitution().trim();
                var institution = root.join("institution", JoinType.LEFT);
                List<Predicate> institutionPredicates = new ArrayList<>();
                institutionPredicates.add(cb.like(cb.lower(institution.get("name")), containsIgnoreCase(value)));
                parseLong(value).ifPresent(id -> institutionPredicates.add(cb.equal(institution.get("id"), id)));
                predicates.add(cb.or(institutionPredicates.toArray(Predicate[]::new)));
            }

            if (hasText(filter.getUser())) {
                String value = filter.getUser().trim();
                var user = root.join("user", JoinType.LEFT);
                List<Predicate> userPredicates = new ArrayList<>();
                userPredicates.add(cb.like(cb.lower(user.get("email")), containsIgnoreCase(value)));
                userPredicates.add(cb.like(cb.lower(user.get("firstName")), containsIgnoreCase(value)));
                userPredicates.add(cb.like(cb.lower(user.get("lastName")), containsIgnoreCase(value)));
                parseLong(value).ifPresent(id -> userPredicates.add(cb.equal(user.get("id"), id)));
                predicates.add(cb.or(userPredicates.toArray(Predicate[]::new)));
            }

            if (hasText(filter.getPageKey())) {
                predicates.add(cb.equal(root.get("pageKey"), filter.getPageKey().trim()));
            }

            if (filter.getEventType() != null) {
                predicates.add(cb.equal(root.get("eventType"), filter.getEventType()));
            }

            if (hasText(filter.getJournalId())) {
                predicates.add(cb.equal(root.get("resourceType"), "journal"));
                predicates.add(cb.equal(root.get("resourceId"), filter.getJournalId().trim()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(NavigationAuditEventListQuery filterForm) {
        return List.of(new SortOrderDef("occurredAt", SortOrderDef.Direction.DESC));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String containsIgnoreCase(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
