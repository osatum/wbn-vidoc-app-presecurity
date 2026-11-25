package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.PasswordResetRequestListDto;
import com.osatum.wbn.application.user.query.PasswordResetRequestListQuery;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.user.authorization.CanUserManage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetRequestService {
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PasswordResetRequestListQueryHandler queryHandler;

    /**
     * Zwraca stronicowaną listę zgłoszeń resetu hasła zgodnie z filtrami administracyjnymi.
     */
    @CanUserManage
    @Transactional(readOnly = true)
    public PageResult<PasswordResetRequestListDto> findPasswordResetRequests(PasswordResetRequestListQuery query) {
        return queryHandler.execute(normalize(query));
    }

    private static PasswordResetRequestListQuery normalize(PasswordResetRequestListQuery query) {
        PasswordResetRequestListQuery normalized = query == null ? new PasswordResetRequestListQuery() : query;

        if (normalized.getSize() < 1) {
            normalized.setSize(DEFAULT_PAGE_SIZE);
        }

        if (normalized.getPage() < 0) {
            normalized.setPage(0);
        }

        return normalized;
    }
}
