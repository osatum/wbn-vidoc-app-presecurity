package com.osatum.wbn.web.navigationaudit.controller;

import com.osatum.wbn.application.system.service.*;
import com.osatum.wbn.application.system.dto.PreparedNavigationAuditReport;
import com.osatum.wbn.application.system.dto.NavigationAuditEventListDto;
import com.osatum.wbn.application.shared.list.PageResult;
import com.osatum.wbn.web.shared.request.ClientRequestMetadataResolver;
import com.osatum.wbn.infrastructure.security.*;
import com.osatum.wbn.web.error.ApiExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = NavigationAuditReportControllerTest.Configuration.class)
@WebAppConfiguration
class NavigationAuditReportControllerTest {
    @TestConfiguration
    @EnableWebMvc
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({NavigationAuditReportController.class, NavigationAuditVisitController.class, NavigationAuditController.class, ApiExceptionHandler.class, SecurityConfig.class})
    static class Configuration {
    }
    @Autowired WebApplicationContext context;
    @Autowired org.springframework.security.web.FilterChainProxy security;
    @MockitoBean NavigationAuditReportService reports;
    @MockitoBean NavigationAuditVisitService visits;
    @MockitoBean NavigationAuditVisitQueryService visitQueries;
    @MockitoBean NavigationAuditEventService events;
    @MockitoBean NavigationAuditEventQueryService eventQueries;
    @MockitoBean ClientRequestMetadataResolver metadata;
    @MockitoBean org.springframework.security.core.userdetails.UserDetailsService userDetails;
    @MockitoBean JwtAuthFilter jwt;
    @MockitoBean IpAccessEnforcementFilter ip;
    @MockitoBean LoginSessionActivityFilter activity;
    private MockMvc mvc;
    private String authority;
    private final String body = "{\"dateFrom\":\"2026-10-03\",\"dateTo\":\"2026-10-03\",\"reportType\":\"VISITS\"}";

    @BeforeEach
    void setup() throws Exception {
        for (var filter : List.of(jwt, ip, activity)) {
            doAnswer(invocation -> {
                ((FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(filter).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        }
        doAnswer(invocation -> {
            if (authority != null) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", null,
                        List.of(new SimpleGrantedAuthority(authority))));
            }
            ((FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwt).doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
        mvc = webAppContextSetup(context).addFilters(security).build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"VISITS", "SERVICE_ENTRIES"})
    void deniesAnonymousAndNonAdminWithoutGeneratingFiles(String type) throws Exception {
        String request = body.replace("VISITS", type);
        mvc.perform(post("/api/navigation-audit/reports/export").contentType("application/json").content(request)).andExpect(status().is4xxClientError());
        authenticate("USER");
        mvc.perform(post("/api/navigation-audit/reports/export").contentType("application/json").content(request)).andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VISITS", "SERVICE_ENTRIES"})
    void adminReceivesAttachmentAndTemporaryFileIsDeleted(String type) throws Exception {
        authenticate("ADMIN");
        boolean xls = type.equals("SERVICE_ENTRIES");
        String extension = xls ? ".xls" : ".xlsx";
        String mediaType = xls ? PreparedNavigationAuditReport.XLS_MEDIA_TYPE : PreparedNavigationAuditReport.XLSX_MEDIA_TYPE;
        var path = Files.createTempFile("audit-controller-test", extension);
        Files.write(path, new byte[]{1, 2, 3});
        when(reports.export(any())).thenReturn(new PreparedNavigationAuditReport(path, "report" + extension, mediaType));
        mvc.perform(post("/api/navigation-audit/reports/export").contentType("application/json").accept(mediaType).content(body.replace("VISITS", type)))
                .andExpect(status().isOk()).andExpect(content().contentType(mediaType))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report" + extension + "\""))
                .andExpect(header().string("Cache-Control", "no-store"));
        org.assertj.core.api.Assertions.assertThat(Files.exists(path)).isFalse();
    }

    @Test
    void validatesRequiredFieldsAndEnum() throws Exception {
        authenticate("ADMIN");
        mvc.perform(post("/api/navigation-audit/reports/export").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/navigation-audit/reports/export").contentType("application/json").content(body.replace("VISITS", "UNKNOWN"))).andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test
    void publishesOpenApiForClientGeneration() throws Exception {
        var json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json).contains("exportNavigationAuditReport", "recordNavigationVisitActivity", "findNavigationAuditVisits", "binary",
                "SERVICE_ENTRIES", "JOURNAL_SEARCH", "JOURNAL_FILTERS_CLEAR", "JOURNAL_SEARCH_RESULT", "JOURNAL_SEARCH_EMPTY", "JOURNAL_CARD_OPEN", "JOURNAL_ISSUES_CLICK",
                PreparedNavigationAuditReport.XLS_MEDIA_TYPE, PreparedNavigationAuditReport.XLSX_MEDIA_TYPE);
        Files.writeString(java.nio.file.Path.of("target/navigation-audit-openapi.json"), json);
    }

    @Test
    void onlyAdminCanReadP1Criteria() throws Exception {
        mvc.perform(post("/api/navigation-audit/events/search").contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());
        authenticate("USER");
        mvc.perform(post("/api/navigation-audit/events/search").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(eventQueries);
        authenticate("ADMIN");
        var event = new NavigationAuditEventListDto();
        event.setEventType(com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType.JOURNAL_SEARCH);
        event.setPropertiesJson("{\"criteria\":{\"countryCodes\":[\"pl\"]}}");
        when(eventQueries.findEvents(any())).thenReturn(new PageResult<>(List.of(event), 1, 1, 20));
        mvc.perform(post("/api/navigation-audit/events/search").contentType("application/json").content("{\"eventType\":\"JOURNAL_SEARCH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].eventType").value("JOURNAL_SEARCH"))
                .andExpect(jsonPath("$.data[0].propertiesJson").value(event.getPropertiesJson()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"JOURNAL_SEARCH", "JOURNAL_FILTERS_CLEAR", "JOURNAL_SEARCH_RESULT", "JOURNAL_SEARCH_EMPTY", "JOURNAL_CARD_OPEN", "JOURNAL_ISSUES_CLICK"})
    void acceptsTheP1EventContractWithoutRelaxingTheServerSideActorRegistry(String type) throws Exception {
        boolean result = type.equals("JOURNAL_SEARCH_RESULT") || type.equals("JOURNAL_SEARCH_EMPTY");
        String origin = type.equals("JOURNAL_FILTERS_CLEAR") ? "CLEAR" : "SEARCH";
        String additional = result ? ",\"attempt\":1,\"resultCount\":0" : "";
        String eventFields = result ? ",\"success\":true" : "";
        if (type.equals("JOURNAL_CARD_OPEN") || type.equals("JOURNAL_ISSUES_CLICK")) eventFields = ",\"resource\":{\"type\":\"journal\",\"id\":\"28032\"}";
        String batch = """
                {"events":[{"eventId":"11111111-1111-4111-8111-111111111111","eventType":"%s",
                "occurredAt":"2026-10-17T10:00:00Z","sessionId":"session", "pageKey":"institution.journals.list",
                "routeTemplate":"/institution/user/journals","properties":{"schemaVersion":1,
                "operationId":"22222222-2222-4222-8222-222222222222","origin":"%s","criteria":{},
                "page":0,"sortBy":"SORT_BY_TITLE_ASC"%s}%s}]}
                """.formatted(type, origin, additional, eventFields);
        mvc.perform(post("/api/navigation-audit/events/batch").contentType("application/json").content(batch))
                .andExpect(status().isAccepted());
        verify(events).accept(argThat(value -> value.events().getFirst().eventType().name().equals(type)), any());
    }

    @Test
    void activityRequiresAuthenticationAndValidDto() throws Exception {
        mvc.perform(post("/api/navigation-audit/visits/activity").contentType("application/json").content("{}")).andExpect(status().is4xxClientError());
        authenticate("USER");
        mvc.perform(post("/api/navigation-audit/visits/activity").contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/navigation-audit/visits/activity").contentType("application/json").content(
                "{\"eventId\":\"6e5d0357-5e82-4a34-87ca-dc49c208d9e8\",\"occurredAt\":\"2026-10-03T10:00:00Z\"}"))
                .andExpect(status().isNoContent());
        verify(visits).acceptActivity(any());
    }

    private void authenticate(String authority) {
        this.authority = authority;
    }

    @Test
    void visitSearchRequiresAdminButActivityRemainsAvailableToUsers() throws Exception {
        mvc.perform(post("/api/navigation-audit/visits/search").contentType("application/json").content("{}"))
                .andExpect(status().is4xxClientError());
        authenticate("USER");
        mvc.perform(post("/api/navigation-audit/visits/search").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(visitQueries);
    }

    @Test
    void adminReceivesTypedVisitPageAndTimeZone() throws Exception {
        authenticate("ADMIN");
        when(visitQueries.findVisits(any())).thenReturn(new com.osatum.wbn.application.system.dto.NavigationAuditVisitPageResult(
                List.of(), 0, 0, 10, "Europe/Warsaw"));
        mvc.perform(post("/api/navigation-audit/visits/search").contentType("application/json").content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.timeZone").value("Europe/Warsaw"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void visitSearchRejectsInvalidDatesEnumsAndPaging() throws Exception {
        authenticate("ADMIN");
        for (String invalid : List.of("{\"dateFrom\":\"2026-02-30\"}", "{\"status\":\"ONLINE\"}",
                "{\"endReason\":\"UNKNOWN\"}", "{\"size\":101}", "{\"size\":0}", "{\"page\":-1}",
                "{\"first\":-1}", "{\"user\":\"" + "x".repeat(201) + "\"}")) {
            mvc.perform(post("/api/navigation-audit/visits/search").contentType("application/json").content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(visitQueries);
    }

    @Test
    void visitSearchUsesExistingErrorFormat() throws Exception {
        authenticate("ADMIN");
        when(visitQueries.findVisits(any())).thenThrow(new IllegalArgumentException("Invalid date range."));
        mvc.perform(post("/api/navigation-audit/visits/search").contentType("application/json")
                        .content("{\"dateFrom\":\"2026-10-04\",\"dateTo\":\"2026-10-03\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
