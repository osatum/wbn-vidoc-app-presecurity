package com.osatum.wbn.web.journal.controller;

import com.osatum.wbn.application.journal.service.JournalDecisionService;
import com.osatum.wbn.application.journal.view.JournalDecisionView;
import com.osatum.wbn.web.error.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import com.osatum.wbn.application.shared.exception.ResourceNotFoundException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class JournalDecisionControllerTest {
    private final JournalDecisionService service = mock();
    private final org.springframework.test.web.servlet.MockMvc mvc = standaloneSetup(new JournalDecisionController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new org.springframework.security.web.access.ExceptionTranslationFilter(
                    (request, response, exception) -> response.sendError(401)))
            .build();

    @Test
    void forwardsExplicitAndDefaultYearAndKeepsLimitedCardsAsSuccessfulReads() throws Exception {
        when(service.getDecision(1L, 2025)).thenReturn(new JournalDecisionView(2025,
                JournalDecisionView.AnnualStatus.MISSING_SURVEY, List.of(2024), null, null, null, null, List.of(), null, null, false));
        mvc.perform(get("/api/journal/journal/1/decision").param("surveyYear", "2025"))
                .andExpect(status().isOk()).andExpect(jsonPath("surveyYear").value(2025))
                .andExpect(jsonPath("annualStatus").value("MISSING_SURVEY"))
                .andExpect(jsonPath("benchmarksVisible").value(false));
        mvc.perform(get("/api/journal/journal/1/decision")).andExpect(status().isOk());
        verify(service).getDecision(1L, null);
        mvc.perform(get("/api/journal/journal/1/decision").param("surveyYear", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void accessDenialAndUnknownJournalAreNotMissingSurveyStates() throws Exception {
        when(service.getDecision(1L, null)).thenThrow(new AccessDeniedException("denied"));
        mvc.perform(get("/api/journal/journal/1/decision")).andExpect(status().isForbidden());
        when(service.getDecision(2L, null)).thenThrow(new ResourceNotFoundException("missing"));
        mvc.perform(get("/api/journal/journal/2/decision")).andExpect(status().isNotFound());
    }
}
