package com.osatum.wbn.application.system.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class NavigationAuditEventListQuery extends PageRequest {
    private Instant occurredFrom;
    private Instant occurredTo;
    private String institution;
    private String user;
    private String pageKey;
    private NavigationAuditEventType eventType;
    private String journalId;
}
