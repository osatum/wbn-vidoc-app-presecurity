package com.osatum.wbn.web.journal.controller;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.journal.assembler.JournalDecisionIdentityMapper;
import com.osatum.wbn.application.journal.authorization.JournalAuthorization;
import com.osatum.wbn.application.journal.service.JournalDecisionService;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.osatum.wbn.domain.model.journal.Journal;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import com.osatum.wbn.domain.repository.journal.JournalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class JournalDecisionAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @Test
    void directEndpointEnforcesEffectiveProfilesRolloutAndExistingJournalPermissions() throws Exception {
        var actors = mock(CurrentActorProvider.class);
        var institutions = mock(InstitutionRepository.class);
        var journals = mock(JournalRepository.class);
        var mapper = mock(JournalDecisionIdentityMapper.class);
        var configuration = new ConfigurationService();
        var unit = new Institution();
        unit.setId(10L);
        unit.setState(EntityState.ACTIVE);
        unit.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        when(institutions.findById(10L)).thenReturn(Optional.of(unit));
        when(journals.findById(123L)).thenReturn(Optional.of(new Journal()));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("test", "test", "ROLE_TEST"));
        try (var context = new AnnotationConfigApplicationContext()) {
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "app.decision-card.enabled=true");
            context.register(MethodSecurityConfiguration.class);
            context.registerBean(CurrentActorProvider.class, () -> actors);
            context.registerBean(InstitutionRepository.class, () -> institutions);
            context.registerBean(JournalRepository.class, () -> journals);
            context.registerBean(JournalDecisionIdentityMapper.class, () -> mapper);
            context.registerBean(ConfigurationService.class, () -> configuration);
            context.registerBean("journalAuthorization", JournalAuthorization.class);
            context.registerBean(OperatingProfileService.class);
            context.registerBean(com.osatum.wbn.application.journal.service.DecisionAnnualReader.class, () -> mock(com.osatum.wbn.application.journal.service.DecisionAnnualReader.class));
            context.registerBean(com.osatum.wbn.application.journal.service.DecisionRatingReader.class, () -> mock(com.osatum.wbn.application.journal.service.DecisionRatingReader.class));
            context.registerBean(JournalDecisionService.class);
            context.registerBean(com.osatum.wbn.application.journal.service.DecisionAnalyticsService.class,
                    com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport::readyAnalytics);
            context.registerBean(JournalDecisionController.class);
            context.refresh();
            var mvc = standaloneSetup(context.getBean(JournalDecisionController.class))
                    .addFilters(new ExceptionTranslationFilter((request, response, exception) -> response.sendError(401)))
                    .build();
            for (var profile : OperatingProfile.values()) {
                ReflectionTestUtils.setField(configuration, "defaultOperatingProfile", profile);
                for (boolean inherited : new boolean[]{true, false}) {
                    unit.setOperatingProfile(inherited ? null : profile);
                    for (var role : InstitutionRole.values()) {
                        when(actors.currentActor()).thenReturn(new InstitutionActor(10L,
                                InstitutionType.RESEARCH_UNIT, role, "Unit", 1L));
                        boolean allowed = profile != OperatingProfile.BASIC
                                && (role == InstitutionRole.INSTITUTION_USER || role == InstitutionRole.INSTITUTION_ADMIN
                                || role == InstitutionRole.INSTITUTION_VIEWER);
                        clearInvocations(journals);
                        mvc.perform(get("/api/journal/journal/123/decision")
                                        .param("surveyYear", "2024").param("profile", "BOTH"))
                                .andExpect(allowed ? status().isOk() : status().isForbidden());
                        if (!allowed) verifyNoInteractions(journals);
                    }
                }
            }
            when(actors.currentActor()).thenReturn(new InstitutionActor(10L,
                    InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, "Unit", 1L));
            unit.setOperatingProfile(OperatingProfile.BOTH);
            ReflectionTestUtils.setField(configuration, "decisionCardEnabled", false);
            clearInvocations(journals);
            mvc.perform(get("/api/journal/journal/123/decision")).andExpect(status().isForbidden());
            verifyNoInteractions(journals);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
