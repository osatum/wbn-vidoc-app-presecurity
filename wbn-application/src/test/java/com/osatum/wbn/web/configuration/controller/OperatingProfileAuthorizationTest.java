package com.osatum.wbn.web.configuration.controller;

import com.osatum.wbn.application.configuration.service.ConfigurationService;
import com.osatum.wbn.application.configuration.service.OperatingProfileService;
import com.osatum.wbn.application.institution.authorization.InstitutionAuthorization;
import com.osatum.wbn.application.journal.authorization.JournalAuthorization;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatingProfileAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }

    @Test
    void appliesRealMethodSecurityToResearchAccessAndOperatorOnlyConfiguration() {
        var actors = mock(CurrentActorProvider.class);
        var repository = mock(InstitutionRepository.class);
        var unit = new Institution();
        unit.setId(10L);
        unit.setState(EntityState.ACTIVE);
        unit.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        when(repository.findById(10L)).thenReturn(Optional.of(unit));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("test", "test", "ROLE_TEST"));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(MethodSecurityConfiguration.class);
            context.registerBean(CurrentActorProvider.class, () -> actors);
            context.registerBean(InstitutionRepository.class, () -> repository);
            context.registerBean(ConfigurationService.class);
            context.registerBean("journalAuthorization", JournalAuthorization.class);
            context.registerBean("institutionAuthorization", InstitutionAuthorization.class);
            context.registerBean(OperatingProfileService.class);
            context.registerBean(OperatingProfileController.class);
            context.refresh();
            var controller = context.getBean(OperatingProfileController.class);
            for (InstitutionRole role : InstitutionRole.values()) {
                when(actors.currentActor()).thenReturn(new InstitutionActor(10L, InstitutionType.RESEARCH_UNIT, role, "Unit", 1L));
                if (role == InstitutionRole.INSTITUTION_ADMIN || role == InstitutionRole.INSTITUTION_USER
                        || role == InstitutionRole.INSTITUTION_VIEWER) {
                    assertThat(controller.currentContext().effectiveProfile()).isEqualTo(OperatingProfile.BOTH);
                } else {
                    assertThatThrownBy(controller::currentContext).isInstanceOf(AccessDeniedException.class);
                }
                assertThatThrownBy(controller::defaultProfile).isInstanceOf(AccessDeniedException.class);
            }
            when(actors.currentActor()).thenReturn(new InstitutionActor(1L, InstitutionType.SYSTEM_OPERATOR,
                    InstitutionRole.INSTITUTION_ADMIN, "Operator", 2L));
            assertThat(controller.defaultProfile()).isEqualTo(OperatingProfile.BOTH);
            assertThatThrownBy(controller::currentContext).isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
