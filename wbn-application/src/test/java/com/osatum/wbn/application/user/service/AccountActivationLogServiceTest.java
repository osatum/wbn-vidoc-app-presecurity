package com.osatum.wbn.application.user.service;

import com.osatum.wbn.application.user.dto.AccountActivationLogListDto;
import com.osatum.wbn.application.user.query.AccountActivationLogListQuery;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.user.service.AccountActivationLogListQueryHandler;
import com.osatum.wbn.application.user.service.AccountActivationLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivationLogServiceTest {
    private final AccountActivationLogListQueryHandler queryHandler = mock(AccountActivationLogListQueryHandler.class);
    private final AccountActivationLogService service = new AccountActivationLogService(queryHandler);

    @Test
    void normalizesEmptyQueryBeforeSearch() {
        PageResult<AccountActivationLogListDto> expected = new PageResult<>(List.of(), 0, 0, 20);
        when(queryHandler.execute(org.mockito.ArgumentMatchers.any(AccountActivationLogListQuery.class))).thenReturn(expected);

        PageResult<AccountActivationLogListDto> result = service.findActivationLogs(null);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<AccountActivationLogListQuery> queryCaptor = ArgumentCaptor.forClass(AccountActivationLogListQuery.class);
        verify(queryHandler).execute(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getPage()).isZero();
        assertThat(queryCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void keepsPositivePagingValues() {
        AccountActivationLogListQuery query = new AccountActivationLogListQuery();
        query.setPage(2);
        query.setSize(50);
        when(queryHandler.execute(query)).thenReturn(new PageResult<>(List.of(), 0, 0, 50));

        service.findActivationLogs(query);

        ArgumentCaptor<AccountActivationLogListQuery> queryCaptor = ArgumentCaptor.forClass(AccountActivationLogListQuery.class);
        verify(queryHandler).execute(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(queryCaptor.getValue().getSize()).isEqualTo(50);
    }
}
