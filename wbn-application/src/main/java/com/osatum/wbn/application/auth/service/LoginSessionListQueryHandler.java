package com.osatum.wbn.application.auth.service;

import com.osatum.wbn.application.auth.dto.LoginSessionViewDto;
import com.osatum.wbn.application.auth.query.LoginSessionListQuery;
import com.osatum.wbn.application.shared.list.PageQueryHandler;

public interface LoginSessionListQueryHandler extends PageQueryHandler<LoginSessionListQuery, LoginSessionViewDto> {
}
