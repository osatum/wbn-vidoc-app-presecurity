package com.osatum.wbn.application.system.mapper;

import com.osatum.wbn.application.system.dto.NavigationAuditEventListDto;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEvent;
import com.osatum.wbn.domain.model.user.AppUser;
import org.springframework.stereotype.Component;

@Component
public class NavigationAuditEventMapper {
    public NavigationAuditEventListDto toListDto(NavigationAuditEvent event) {
        NavigationAuditEventListDto dto = new NavigationAuditEventListDto();
        dto.setId(event.getId());
        dto.setEventId(event.getEventId());
        dto.setEventType(event.getEventType());
        dto.setOccurredAt(event.getOccurredAt());
        dto.setReceivedAt(event.getReceivedAt());
        dto.setActorType(event.getActorType());
        dto.setInstitutionType(event.getInstitutionType());
        dto.setInstitutionRole(event.getInstitutionRole());
        dto.setLoginSessionId(event.getLoginSessionId());
        dto.setViewId(event.getViewId());
        dto.setPageKey(event.getPageKey());
        dto.setRouteTemplate(event.getRouteTemplate());
        dto.setRoutePath(event.getRoutePath());
        dto.setResourceType(event.getResourceType());
        dto.setResourceId(event.getResourceId());
        dto.setAction(event.getAction());
        dto.setTarget(event.getTarget());
        dto.setDurationMs(event.getDurationMs());
        dto.setSuccess(event.getSuccess());
        dto.setStatusCode(event.getStatusCode());
        dto.setIpAddress(event.getIpAddress());
        dto.setUserAgent(event.getUserAgent());
        if (event.getEventType().isJournalAnalyticsEvent()) dto.setPropertiesJson(event.getPropertiesJson());

        AppUser user = event.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setUserEmail(user.getEmail());
            dto.setUserFirstName(user.getFirstName());
            dto.setUserLastName(user.getLastName());
        }

        Institution institution = event.getInstitution();
        if (institution != null) {
            dto.setInstitutionId(institution.getId());
            dto.setInstitutionName(institution.getName());
        }

        return dto;
    }
}
