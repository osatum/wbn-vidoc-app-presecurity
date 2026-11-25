package com.osatum.wbn.web.configuration.controller;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig({ConfigurationService.class, ConfigurationController.class})
@TestPropertySource(properties = "app.journal.issues-url-template=https://journals.example.test/issues?issueId=all&journalId={journalId}")
class JournalLinksConfigurationTest {
    @Autowired private ConfigurationController controller;
    @Autowired private ConfigurationService configuration;

    @Test
    void exposesTheConfiguredTemplateAndUsesItToResolveTheJournalDestination() throws Exception {
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/api/configuration/journalLinks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuesUrlTemplate").value(
                        "https://journals.example.test/issues?issueId=all&journalId={journalId}"));
        assertThat(configuration.getJournalIssuesUrl(456L))
                .isEqualTo("https://journals.example.test/issues?issueId=all&journalId=456");
    }
}
