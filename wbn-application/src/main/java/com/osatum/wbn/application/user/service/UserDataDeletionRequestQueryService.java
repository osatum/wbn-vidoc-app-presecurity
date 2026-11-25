package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.user.authorization.CanUserManage;
import com.osatum.wbn.application.user.dto.UserDataDeletionRequestListDto;
import com.osatum.wbn.application.user.query.UserDataDeletionRequestListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDataDeletionRequestQueryService {
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final UserDataDeletionRequestListQueryHandler queryHandler;

    /**
     * Zwraca stronicowaną listę zgłoszeń usunięcia danych użytkowników.
     */
    @CanUserManage
    @Transactional(readOnly = true)
    public PageResult<UserDataDeletionRequestListDto> findUserDataDeletionRequests(UserDataDeletionRequestListQuery query) {
        return queryHandler.execute(normalize(query));
    }

    private static UserDataDeletionRequestListQuery normalize(UserDataDeletionRequestListQuery query) {
        UserDataDeletionRequestListQuery normalized = query == null ? new UserDataDeletionRequestListQuery() : query;

        if (normalized.getSize() < 1) {
            normalized.setSize(DEFAULT_PAGE_SIZE);
        }

        if (normalized.getPage() < 0) {
            normalized.setPage(0);
        }

        return normalized;
    }
}
