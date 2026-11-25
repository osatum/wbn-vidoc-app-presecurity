package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.shared.list.PageQueryHandler;
import com.osatum.wbn.application.user.dto.UserListViewDto;
import com.osatum.wbn.application.user.query.UserListQuery;

public interface UserListQueryHandler extends PageQueryHandler<UserListQuery, UserListViewDto> {
}
