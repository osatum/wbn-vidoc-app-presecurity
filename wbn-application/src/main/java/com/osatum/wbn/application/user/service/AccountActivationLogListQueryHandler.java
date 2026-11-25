package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.AccountActivationLogListDto;
import com.osatum.wbn.application.user.query.AccountActivationLogListQuery;
import com.osatum.wbn.application.shared.list.PageQueryHandler;

public interface AccountActivationLogListQueryHandler extends PageQueryHandler<AccountActivationLogListQuery, AccountActivationLogListDto> {
}
