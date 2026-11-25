package com.osatum.wbn.application.navigationaudit.service;

import com.osatum.wbn.application.system.service.NavigationAuditPageRegistry;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationAuditPageRegistryTest {

    private final NavigationAuditPageRegistry registry = new NavigationAuditPageRegistry();

    @Test
    void allowsDecisionPageAuditOnlyForTheAuthenticatedDecisionRoute() {
        for (var actor : new NavigationAuditActorType[]{NavigationAuditActorType.USER, NavigationAuditActorType.INSTITUTION}) {
            assertThat(registry.isAllowed("institution.journals.decision", "/institution/user/journals/:id/decision",
                    NavigationAuditEventType.JOURNAL_DECISION_ACTION, actor)).isTrue();
            assertThat(registry.isAllowed("institution.journals.details", "/institution/user/journals/:id",
                    NavigationAuditEventType.JOURNAL_DECISION_ACTION, actor)).isFalse();
        }
        assertThat(registry.isAllowed("institution.journals.decision", "/institution/user/journals/:id/decision",
                NavigationAuditEventType.JOURNAL_DECISION_ACTION, NavigationAuditActorType.ANONYMOUS)).isFalse();
        assertThat(registry.isAllowed("institution.journals.decision", "/institution/user/journals/:id/decision",
                NavigationAuditEventType.PAGE_READY, NavigationAuditActorType.USER)).isTrue();
        assertThat(registry.isAllowed("institution.journals.decision", "/institution/user/journals/:id/decision",
                NavigationAuditEventType.PAGE_VIEW, NavigationAuditActorType.INSTITUTION)).isTrue();
        assertThat(registry.isAllowed("institution.journals.decision", "/institution/user/journals/:id/decision",
                NavigationAuditEventType.PAGE_VIEW, NavigationAuditActorType.ANONYMOUS)).isFalse();
        assertThat(registry.isAllowed("institution.journals.decision", "/institution/user/journals/:id",
                NavigationAuditEventType.PAGE_VIEW, NavigationAuditActorType.USER)).isFalse();
        assertThat(registry.isAllowed("institution.journals.decision", "/institution/user/journals/:id/decision",
                NavigationAuditEventType.JOURNAL_CARD_OPEN, NavigationAuditActorType.USER)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = NavigationAuditEventType.class, names = {"JOURNAL_SEARCH", "JOURNAL_FILTERS_CLEAR", "JOURNAL_SEARCH_RESULT", "JOURNAL_SEARCH_EMPTY", "JOURNAL_CARD_OPEN", "JOURNAL_ISSUES_CLICK"})
    void restrictsP1AnalyticsToTheAuthenticatedP1Route(NavigationAuditEventType type) {
        for (var actor : new NavigationAuditActorType[]{NavigationAuditActorType.USER, NavigationAuditActorType.INSTITUTION}) {
            assertThat(registry.isAllowed("institution.journals.list", "/institution/user/journals", type, actor)).isTrue();
            assertThat(registry.isAllowed("institution.journals.details", "/institution/user/journals/:id", type, actor)).isFalse();
            assertThat(registry.isAllowed("institution.journals.list", "/different", type, actor)).isFalse();
            assertThat(registry.isAllowed("public.home", "/", type, actor)).isFalse();
        }
        assertThat(registry.isAllowed("institution.journals.list", "/institution/user/journals", type, NavigationAuditActorType.ANONYMOUS)).isFalse();
    }

    @Test
    void allowsAnonymousEventsOnlyForPublicPages() {
        assertThat(registry.isAllowed(
                "public.home",
                "/",
                NavigationAuditEventType.PUBLIC_PAGE_VIEW,
                NavigationAuditActorType.ANONYMOUS
        )).isTrue();

        assertThat(registry.isAllowed(
                "institution.journals.list",
                "/institution/user/journals",
                NavigationAuditEventType.PAGE_VIEW,
                NavigationAuditActorType.ANONYMOUS
        )).isFalse();
    }

    @Test
    void allowsTimingEventsForProtectedAuthenticatedPages() {
        assertThat(registry.isAllowed(
                "institution.journals.details",
                "/institution/user/journals/:id",
                NavigationAuditEventType.PAGE_HEARTBEAT,
                NavigationAuditActorType.USER
        )).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = NavigationAuditActorType.class, names = {"USER", "INSTITUTION"})
    void allowsFileDownloadsForJournalPages(NavigationAuditActorType actorType) {
        assertThat(registry.isAllowed(
                "institution.journals.list",
                "/institution/user/journals",
                NavigationAuditEventType.FILE_DOWNLOAD,
                actorType
        )).isTrue();
        assertThat(registry.isAllowed(
                "institution.journals.details",
                "/institution/user/journals/:id",
                NavigationAuditEventType.FILE_DOWNLOAD,
                actorType
        )).isTrue();
    }

    @Test
    void rejectsAnonymousFileDownloadsAndDownloadsOnOtherPages() {
        assertThat(registry.isAllowed(
                "institution.journals.list",
                "/institution/user/journals",
                NavigationAuditEventType.FILE_DOWNLOAD,
                NavigationAuditActorType.ANONYMOUS
        )).isFalse();
        assertThat(registry.isAllowed(
                "institution.journals.details",
                "/institution/user/journals/:id",
                NavigationAuditEventType.FILE_DOWNLOAD,
                NavigationAuditActorType.ANONYMOUS
        )).isFalse();
        assertThat(registry.isAllowed(
                "public.home",
                "/",
                NavigationAuditEventType.FILE_DOWNLOAD,
                NavigationAuditActorType.USER
        )).isFalse();
        assertThat(registry.isAllowed(
                "system.admin.dashboard",
                "/system/admin/dashboard",
                NavigationAuditEventType.FILE_DOWNLOAD,
                NavigationAuditActorType.USER
        )).isFalse();
        assertThat(registry.isAllowed(
                "institution.journals.details",
                "/institution/user/journals",
                NavigationAuditEventType.FILE_DOWNLOAD,
                NavigationAuditActorType.USER
        )).isFalse();
    }
}
