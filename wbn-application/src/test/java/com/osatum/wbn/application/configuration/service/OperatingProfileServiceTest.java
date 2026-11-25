package com.osatum.wbn.application.configuration.service;

import com.osatum.wbn.application.security.Actor;
import com.osatum.wbn.application.security.CurrentActorProvider;
import com.osatum.wbn.application.security.InstitutionActor;
import com.osatum.wbn.application.security.UserActor;
import com.osatum.wbn.domain.model.EntityState;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.institution.OperatingProfile;
import com.osatum.wbn.domain.model.institution.SearchView;
import com.osatum.wbn.domain.repository.institution.InstitutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperatingProfileServiceTest {
    private final InstitutionRepository repository = mock();
    private final CurrentActorProvider actors = mock();
    private final ConfigurationService configuration = mock();
    private final OperatingProfileService service = new OperatingProfileService(configuration, repository, actors);

    @Test
    void deniedAuditContextDoesNotRollbackOtherEventsInTheOuterTransaction() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:p2-audit-context;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table if not exists audit_context_test (id integer)");
        var manager = new DataSourceTransactionManager(source);
        var proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var proxy = (OperatingProfileService) proxyFactory.getProxy();
        when(actors.currentActor()).thenReturn(new UserActor(1L, "test@example.test", null,
                InstitutionType.SYSTEM_OPERATOR, InstitutionRole.INSTITUTION_USER, 2L));
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThatThrownBy(proxy::decisionAuditContext).isInstanceOf(AccessDeniedException.class);
            jdbc.update("insert into audit_context_test values (1)");
        });
        assertThat(jdbc.queryForObject("select count(*) from audit_context_test", Integer.class)).isEqualTo(1);
        jdbc.execute("drop table audit_context_test");
    }

    @ParameterizedTest
    @EnumSource(OperatingProfile.class)
    void inheritsEveryGlobalProfileForUserAndInstitutionSessions(OperatingProfile defaultProfile) {
        when(configuration.isDecisionCardEnabled()).thenReturn(true);
        when(configuration.getDefaultOperatingProfile()).thenReturn(defaultProfile);
        var institution = institution();
        when(repository.findById(10L)).thenReturn(Optional.of(institution));
        Actor[] sessions = {
                new UserActor(1L, "user@example.test", 10L, InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_USER, 2L),
                new InstitutionActor(10L, InstitutionType.RESEARCH_UNIT, InstitutionRole.INSTITUTION_VIEWER, "Unit", 3L)
        };
        for (Actor actor : sessions) {
            when(actors.currentActor()).thenReturn(actor);
            var context = service.currentContext();
            assertThat(context.institutionId()).isEqualTo(10L);
            assertThat(context.configuredProfile()).isNull();
            assertThat(context.effectiveProfile()).isEqualTo(defaultProfile);
            assertThat(context.availableFeatures()).isEqualTo(defaultProfile.getAvailableFeatures());
            assertThat(service.requireSearchView(null)).isEqualTo(defaultProfile.getDefaultSearchView());
            for (SearchView view : SearchView.values()) {
                if (defaultProfile.supports(view)) {
                    assertThat(service.requireSearchView(view)).isEqualTo(view);
                } else {
                    assertThatThrownBy(() -> service.requireSearchView(view)).isInstanceOf(AccessDeniedException.class);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(OperatingProfile.class)
    void decisionCardRequiresRolloutAndTheEffectiveProfile(OperatingProfile profile) {
        when(configuration.getDefaultOperatingProfile()).thenReturn(profile);
        when(actors.currentActor()).thenReturn(new InstitutionActor(10L, InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_VIEWER, "Unit", 3L));
        var institution = institution();
        when(repository.findById(10L)).thenReturn(Optional.of(institution));
        assertThatThrownBy(service::requireDecisionCard).isInstanceOf(AccessDeniedException.class);
        assertThat(service.currentContext().availableFeatures()).doesNotContain(
                com.osatum.wbn.domain.model.institution.SystemFeature.DECISION_CARD);
        when(configuration.isDecisionCardEnabled()).thenReturn(true);
        for (boolean inherited : new boolean[]{true, false}) {
            institution.setOperatingProfile(inherited ? null : profile);
            if (profile == OperatingProfile.BASIC) {
                assertThatThrownBy(service::requireDecisionCard).isInstanceOf(AccessDeniedException.class);
            } else {
                assertThatCode(service::requireDecisionCard).doesNotThrowAnyException();
            }
        }
        institution.setOperatingProfile(OperatingProfile.BASIC);
        assertThatThrownBy(service::requireDecisionCard).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @EnumSource(OperatingProfile.class)
    void usesTheCurrentDatabaseOverrideRatherThanTheLoginToken(OperatingProfile override) {
        when(configuration.getDefaultOperatingProfile()).thenReturn(OperatingProfile.BOTH);
        when(actors.currentActor()).thenReturn(new InstitutionActor(10L, InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_VIEWER, "Unit", 3L));
        var institution = institution();
        institution.setOperatingProfile(override);
        when(repository.findById(10L)).thenReturn(Optional.of(institution));
        assertThat(service.currentContext().configuredProfile()).isEqualTo(override);
        assertThat(service.currentContext().effectiveProfile()).isEqualTo(override);
        institution.setOperatingProfile(null);
        assertThat(service.currentContext().effectiveProfile()).isEqualTo(OperatingProfile.BOTH);
        institution.setOperatingProfile(OperatingProfile.BASIC);
        assertThatThrownBy(() -> service.requireSearchView(SearchView.ADVANCED)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesMissingInactiveOrNonResearchUnitsInsteadOfUsingTheGlobalDefault() {
        when(actors.currentActor()).thenReturn(new InstitutionActor(10L, InstitutionType.RESEARCH_UNIT,
                InstitutionRole.INSTITUTION_VIEWER, "Unit", 3L));
        when(repository.findById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(service::currentContext).isInstanceOf(AccessDeniedException.class);
        var institution = institution();
        when(repository.findById(10L)).thenReturn(Optional.of(institution));
        institution.setState(EntityState.INACTIVE);
        assertThatThrownBy(service::currentContext).isInstanceOf(AccessDeniedException.class);
        institution.setState(EntityState.ACTIVE);
        institution.setInstitutionType(InstitutionType.SYSTEM_OPERATOR);
        assertThatThrownBy(service::currentContext).isInstanceOf(AccessDeniedException.class);
        when(actors.currentActor()).thenReturn(new InstitutionActor(10L, InstitutionType.SYSTEM_OPERATOR,
                InstitutionRole.INSTITUTION_ADMIN, "Operator", 3L));
        assertThatThrownBy(service::currentContext).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void defaultsToBothWithAdvancedAsTheLandingView() {
        assertThat(new ConfigurationService().getDefaultOperatingProfile()).isEqualTo(OperatingProfile.BOTH);
        assertThat(OperatingProfile.BOTH.getDefaultSearchView()).isEqualTo(SearchView.ADVANCED);
        assertThatThrownBy(() -> OperatingProfile.BOTH.getAvailableFeatures().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bindsConfiguredEnumsAndRejectsUnknownGlobalProfilesAtStartup() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "app.operating-profile.default=BASIC");
            context.register(ConfigurationService.class);
            context.refresh();
            assertThat(context.getBean(ConfigurationService.class).getDefaultOperatingProfile()).isEqualTo(OperatingProfile.BASIC);
        }
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            org.springframework.test.context.support.TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "app.operating-profile.default=UNKNOWN");
            context.register(ConfigurationService.class);
            assertThatThrownBy(context::refresh).isInstanceOf(org.springframework.beans.BeansException.class);
        }
    }

    private static Institution institution() {
        var institution = new Institution();
        institution.setId(10L);
        institution.setInstitutionType(InstitutionType.RESEARCH_UNIT);
        institution.setState(EntityState.ACTIVE);
        return institution;
    }
}
