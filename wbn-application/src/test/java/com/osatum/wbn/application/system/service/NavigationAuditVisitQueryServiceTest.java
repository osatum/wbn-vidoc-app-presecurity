package com.osatum.wbn.application.system.service;

import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.application.system.query.NavigationAuditVisitListQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NavigationAuditVisitQueryServiceTest {
    @Mock NavigationAuditVisitListQueryHandler handler;
    private NavigationAuditVisitQueryService service;

    @BeforeEach
    void setup() {
        service = new NavigationAuditVisitQueryService(handler);
    }

    @Test
    void defaultsToFirstPageOfTenWithEmptyFilters() {
        when(handler.search(any(), isNull(), isNull())).thenReturn(new PageResult<>(List.of(), 0, 0, 10));
        var result = service.findVisits(null);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.timeZone()).isEqualTo("Europe/Warsaw");
        verify(handler).search(argThat(query -> query.getPage() == 0 && query.getSize() == 10), isNull(), isNull());
    }

    @ParameterizedTest
    @CsvSource({"2026-03-29,2026-03-28T23:00:00Z,2026-03-29T22:00:00Z",
            "2026-10-25,2026-10-24T22:00:00Z,2026-10-25T23:00:00Z"})
    void inclusiveDatesRespectDaylightSaving(String date, String from, String until) {
        var query = new NavigationAuditVisitListQuery();
        query.setDateFrom(LocalDate.parse(date));
        query.setDateTo(LocalDate.parse(date));
        when(handler.search(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 0, 0, 10));
        service.findVisits(query);
        verify(handler).search(query, Instant.parse(from), Instant.parse(until));
    }

    @Test
    void supportsSingleBoundariesAndConfiguredZone() {
        ReflectionTestUtils.setField(service, "zone", "America/New_York");
        var query = new NavigationAuditVisitListQuery();
        query.setDateFrom(LocalDate.parse("2026-10-03"));
        when(handler.search(any(), any(), isNull())).thenReturn(new PageResult<>(List.of(), 0, 0, 10));
        assertThat(service.findVisits(query).timeZone()).isEqualTo("America/New_York");
        verify(handler).search(query, Instant.parse("2026-10-03T04:00:00Z"), null);
        query.setDateFrom(null);
        query.setDateTo(LocalDate.parse("2026-10-03"));
        when(handler.search(any(), isNull(), any())).thenReturn(new PageResult<>(List.of(), 0, 0, 10));
        service.findVisits(query);
        verify(handler).search(query, null, Instant.parse("2026-10-04T04:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"-1,10,0", "0,0,0", "0,101,0", "0,10,-1", "2147483647,100,0"})
    void rejectsInvalidPagingWithoutQuerying(int page, int size, int first) {
        var query = new NavigationAuditVisitListQuery();
        query.setPage(page); query.setSize(size); query.setFirst(first);
        assertThatThrownBy(() -> service.findVisits(query)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(handler);
    }

    @Test
    void rejectsReversedDatesAndOversizedFilters() {
        var query = new NavigationAuditVisitListQuery();
        query.setDateFrom(LocalDate.parse("2026-10-04"));
        query.setDateTo(LocalDate.parse("2026-10-03"));
        assertThatThrownBy(() -> service.findVisits(query)).isInstanceOf(IllegalArgumentException.class);
        query.setDateTo(null);
        query.setUser("x".repeat(201));
        assertThatThrownBy(() -> service.findVisits(query)).isInstanceOf(IllegalArgumentException.class);
        query.setUser(null);
        query.setInstitution("x".repeat(201));
        assertThatThrownBy(() -> service.findVisits(query)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(handler);
    }
}
