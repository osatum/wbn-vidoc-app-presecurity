package com.osatum.wbn.infrastructure.persistence.specifications;

import com.osatum.wbn.application.notification.query.EmailMessageLogListQuery;
import com.osatum.wbn.application.shared.list.SortOrderDef;
import com.osatum.wbn.domain.model.notification.EmailMessageLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class EmailMessageLogSpecifications implements EntitySpecifications<EmailMessageLog, EmailMessageLogListQuery> {
    @Override
    public Specification<EmailMessageLog> byFilter(EmailMessageLogListQuery filter) {
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

            if (filter.getRecipient() != null && !filter.getRecipient().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("recipient")), containsIgnoreCase(filter.getRecipient())));
            }

            if (filter.getEmailType() != null) {
                predicates.add(cb.equal(root.get("emailType"), filter.getEmailType()));
            }

            if (filter.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getCreatedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("createdAt"), filter.getCreatedFrom()));
            }

            if (filter.getCreatedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<OffsetDateTime>get("createdAt"), filter.getCreatedTo()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<SortOrderDef> sortByFilter(EmailMessageLogListQuery filterForm) {
        return List.of(new SortOrderDef("createdAt", SortOrderDef.Direction.DESC));
    }

    private static String containsIgnoreCase(String value) {
        return contains(value).toLowerCase();
    }

    private static String contains(String value) {
        return "%" + value.trim() + "%";
    }
}
