package com.osatum.wbn.web.migration;

import com.osatum.wbn.application.journal.service.DecisionAnalyticsService;
import com.osatum.wbn.application.journal.service.DecisionAnalyticsTestSupport;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.system.authorization.SystemAuthorization;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.infrastructure.time.JournalEventTaskCoordinator;
import com.osatum.wbn.migration.service.DecisionAnalyticsJobService;
import com.osatum.wbn.migration.service.MigrationJobCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.ExceptionTranslationFilter;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DecisionAnalyticsAuthorizationTest {
    @Configuration @EnableMethodSecurity
    static class Security { }

    @Test
    void onlySystemAdministratorCanReadOrStartPreparationAndBusyReturnsConflict() throws Exception {
        var actors = mock(CurrentActorProvider.class);
        var analytics = DecisionAnalyticsTestSupport.readyAnalytics();
        var coordinator = new MigrationJobCoordinator();
        coordinator.tryStart(MigrationJobCoordinator.Job.IMPORT);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("test", "test", "ROLE_TEST"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Security.class);
            context.registerBean("conversionService", org.springframework.boot.convert.ApplicationConversionService.class);
            context.registerBean(CurrentActorProvider.class, () -> actors);
            context.registerBean("systemAuthorization", SystemAuthorization.class);
            context.registerBean(DecisionAnalyticsService.class, () -> analytics);
            context.registerBean(MigrationJobCoordinator.class, () -> coordinator);
            context.registerBean(JournalEventTaskCoordinator.class);
            context.registerBean(DecisionAnalyticsJobService.class);
            context.refresh();
            var mvc = standaloneSetup(new DecisionAnalyticsController(context.getBean(DecisionAnalyticsJobService.class)))
                    .addFilters(new ExceptionTranslationFilter((request, response, exception) -> response.sendError(401))).build();
            for (var type : InstitutionType.values()) for (var role : InstitutionRole.values()) {
                when(actors.currentActor()).thenReturn(new InstitutionActor(1L, type, role, "test", 1L));
                boolean allowed = type == InstitutionType.SYSTEM_OPERATOR && role == InstitutionRole.INSTITUTION_ADMIN;
                clearInvocations(analytics);
                mvc.perform(get("/api/migration/decision-analytics/status"))
                        .andExpect(allowed ? status().isOk() : status().isForbidden());
                mvc.perform(post("/api/migration/decision-analytics/rebuild"))
                        .andExpect(allowed ? status().isConflict() : status().isForbidden());
                if (!allowed) verifyNoInteractions(analytics);
                verify(analytics, never()).begin();
            }
        } finally { SecurityContextHolder.clearContext(); }
    }
}
