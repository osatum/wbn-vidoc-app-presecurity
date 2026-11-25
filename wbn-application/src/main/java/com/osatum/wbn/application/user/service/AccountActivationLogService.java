package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.AccountActivationLogListDto;
import com.osatum.wbn.application.user.query.AccountActivationLogListQuery;
import com.osatum.wbn.application.shared.list.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountActivationLogService {
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AccountActivationLogListQueryHandler queryHandler;

    /**
     * Zwraca stronicowaną listę zdarzeń aktywacji kont użytkowników.
     */
    @Transactional(readOnly = true)
    public PageResult<AccountActivationLogListDto> findActivationLogs(AccountActivationLogListQuery query) {
        return queryHandler.execute(normalize(query));
    }

    private static AccountActivationLogListQuery normalize(AccountActivationLogListQuery query) {
        AccountActivationLogListQuery normalized = query == null ? new AccountActivationLogListQuery() : query;

        if (normalized.getSize() < 1) {
            normalized.setSize(DEFAULT_PAGE_SIZE);
        }

        if (normalized.getPage() < 0) {
            normalized.setPage(0);
        }

        return normalized;
    }
}
