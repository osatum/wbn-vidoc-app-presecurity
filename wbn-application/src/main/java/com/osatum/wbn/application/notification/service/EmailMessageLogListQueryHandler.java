package com.osatum.wbn.application.notification.service;

import com.osatum.wbn.application.notification.dto.EmailMessageLogListDto;
import com.osatum.wbn.application.notification.query.EmailMessageLogListQuery;
import com.osatum.wbn.application.shared.list.PageQueryHandler;

public interface EmailMessageLogListQueryHandler extends PageQueryHandler<EmailMessageLogListQuery, EmailMessageLogListDto> {
}
