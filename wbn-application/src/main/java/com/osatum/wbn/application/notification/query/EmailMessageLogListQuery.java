package com.osatum.wbn.application.notification.query;

import com.osatum.wbn.application.shared.list.PageRequest;
import com.osatum.wbn.domain.model.notification.EmailMessageStatus;
import com.osatum.wbn.domain.model.notification.EmailType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class EmailMessageLogListQuery extends PageRequest {
    private Long userId;
    private Long institutionId;
    private String name;
    private String email;
    private String recipient;
    private EmailType emailType;
    private EmailMessageStatus status;
    private OffsetDateTime createdFrom;
    private OffsetDateTime createdTo;
}
