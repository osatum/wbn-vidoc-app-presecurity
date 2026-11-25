package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.system.dto.NavigationAuditEventListDto;
import com.osatum.wbn.application.system.query.NavigationAuditEventListQuery;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.system.service.NavigationAuditEventListQueryHandler;
import com.osatum.wbn.application.system.service.NavigationAuditEventQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NavigationAuditEventQueryServiceTest {
    private final NavigationAuditEventListQueryHandler queryHandler = mock(NavigationAuditEventListQueryHandler.class);
    private final NavigationAuditEventQueryService service = new NavigationAuditEventQueryService(queryHandler);

    @Test
    void normalizesEmptyQueryBeforeSearch() {
        PageResult<NavigationAuditEventListDto> expected = new PageResult<>(List.of(), 0, 0, 20);
        when(queryHandler.execute(any(NavigationAuditEventListQuery.class))).thenReturn(expected);

        PageResult<NavigationAuditEventListDto> result = service.findEvents(null);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<NavigationAuditEventListQuery> queryCaptor = ArgumentCaptor.forClass(NavigationAuditEventListQuery.class);
        verify(queryHandler).execute(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getPage()).isZero();
        assertThat(queryCaptor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void keepsPositivePagingValues() {
        NavigationAuditEventListQuery query = new NavigationAuditEventListQuery();
        query.setPage(2);
        query.setSize(50);
        when(queryHandler.execute(query)).thenReturn(new PageResult<>(List.of(), 0, 0, 50));

        service.findEvents(query);

        ArgumentCaptor<NavigationAuditEventListQuery> queryCaptor = ArgumentCaptor.forClass(NavigationAuditEventListQuery.class);
        verify(queryHandler).execute(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getPage()).isEqualTo(2);
        assertThat(queryCaptor.getValue().getSize()).isEqualTo(50);
    }
}
