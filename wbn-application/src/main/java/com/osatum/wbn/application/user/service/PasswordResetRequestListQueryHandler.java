package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.PasswordResetRequestListDto;
import com.osatum.wbn.application.user.query.PasswordResetRequestListQuery;
import com.osatum.wbn.application.shared.list.PageQueryHandler;

public interface PasswordResetRequestListQueryHandler extends PageQueryHandler<PasswordResetRequestListQuery, PasswordResetRequestListDto> {
}
