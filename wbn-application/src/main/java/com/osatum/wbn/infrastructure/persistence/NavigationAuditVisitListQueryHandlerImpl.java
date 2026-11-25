package com.osatum.wbn.infrastructure.persistence;

import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.system.dto.NavigationAuditVisitListDto;
import com.osatum.wbn.application.system.dto.NavigationAuditVisitStatus;
import com.osatum.wbn.application.system.query.NavigationAuditVisitListQuery;
import com.osatum.wbn.application.system.service.NavigationAuditVisitListQueryHandler;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

@Repository
@RequiredArgsConstructor
public class NavigationAuditVisitListQueryHandlerImpl implements NavigationAuditVisitListQueryHandler {
    private final EntityManager entityManager;

    @Override
    public PageResult<NavigationAuditVisitListDto> search(NavigationAuditVisitListQuery query, Instant from, Instant until) {
        var predicates = new ArrayList<String>();
        var parameters = new LinkedHashMap<String, Object>();
        if (from != null) {
            predicates.add("visit.startedAt >= :from");
            parameters.put("from", from);
        }
        if (until != null) {
            predicates.add("visit.startedAt < :until");
            parameters.put("until", until);
        }
        if (query.getStatus() != null) {
            predicates.add(query.getStatus() == NavigationAuditVisitStatus.OPEN
                    ? "visit.endedAt is null" : "visit.endedAt is not null");
        }
        if (query.getEndReason() != null) {
            predicates.add("visit.endReason = :reason");
            parameters.put("reason", query.getEndReason().name());
        }
        if (query.getInstitution() != null && !query.getInstitution().isBlank()) {
            parameters.put("institution", contains(query.getInstitution()));
            String match = "lower(institution.name) like :institution escape '!'";
            Long institutionId = parseId(query.getInstitution());
            if (institutionId != null) {
                match += " or institution.id = :institutionId";
                parameters.put("institutionId", institutionId);
            }
            predicates.add("(" + match + ")");
        }
        if (query.getUser() != null && !query.getUser().isBlank()) {
            parameters.put("user", contains(query.getUser()));
            String match = "lower(actor.email) like :user escape '!' or lower(actor.firstName) like :user escape '!'"
                    + " or lower(actor.lastName) like :user escape '!'";
            Long userId = parseId(query.getUser());
            if (userId != null) {
                match += " or actor.id = :userId";
                parameters.put("userId", userId);
            }
            predicates.add("(" + match + ")");
        }
        String source = " from NavigationAuditVisit visit left join visit.institution institution left join visit.user actor"
                + (predicates.isEmpty() ? "" : " where " + String.join(" and ", predicates));
        var countQuery = entityManager.createQuery("select count(visit)" + source, Long.class);
        parameters.forEach(countQuery::setParameter);
        long total = countQuery.getSingleResult();
        var pageQuery = entityManager.createQuery("""
                select new com.osatum.wbn.application.system.dto.NavigationAuditVisitListDto(
                    visit.id, visit.actorType, institution.id, institution.name,
                    actor.id, actor.email, actor.firstName, actor.lastName,
                    visit.startedAt, visit.lastActivityAt, visit.endedAt, visit.endReason)
                """ + source + " order by visit.startedAt desc, visit.id desc", NavigationAuditVisitListDto.class);
        parameters.forEach(pageQuery::setParameter);
        var data = pageQuery.setFirstResult(query.getPage() * query.getSize()).setMaxResults(query.getSize()).getResultList();
        return new PageResult<>(data, total, Math.toIntExact((total + query.getSize() - 1) / query.getSize()), query.getSize());
    }

    private static String contains(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
