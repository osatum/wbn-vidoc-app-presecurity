package com.osatum.wbn.application.system.service;

import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditActorType;
import com.osatum.wbn.domain.model.navigationaudit.NavigationAuditEventType;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Przechowuje listę dozwolonych stron, szablonów tras i typów zdarzeń audytowych.
 * Ogranicza zgłoszenia anonimowe do stron publicznych, ale nie zastępuje autoryzacji dostępu do widoków aplikacji.
 * Nowa audytowana strona lub typ zdarzenia wymaga dodania odpowiedniego wpisu w tym rejestrze.
 */
@Service
public class NavigationAuditPageRegistry {

    private static final Set<NavigationAuditEventType> PUBLIC_PAGE_EVENTS = eventSet(
            NavigationAuditEventType.SERVICE_ENTRY,
            NavigationAuditEventType.PUBLIC_PAGE_VIEW,
            NavigationAuditEventType.PAGE_HEARTBEAT,
            NavigationAuditEventType.PAGE_LEAVE,
            NavigationAuditEventType.PAGE_VISIBLE,
            NavigationAuditEventType.PAGE_HIDDEN
    );

    private static final Set<NavigationAuditEventType> PROTECTED_PAGE_EVENTS = eventSet(
            NavigationAuditEventType.SERVICE_ENTRY,
            NavigationAuditEventType.PAGE_VIEW,
            NavigationAuditEventType.PAGE_HEARTBEAT,
            NavigationAuditEventType.PAGE_LEAVE,
            NavigationAuditEventType.PAGE_VISIBLE,
            NavigationAuditEventType.PAGE_HIDDEN
    );

    private final Map<String, AllowedNavigationAuditPage> pages = Map.ofEntries(
            Map.entry(
                    "public.home",
                    publicPage("public.home", "/")
            ),
            Map.entry(
                    "public.registration",
                    publicPage("public.registration", "/registration")
            ),
            Map.entry(
                    "public.registration.institution",
                    publicPage("public.registration.institution", "/registration/:id")
            ),
            Map.entry(
                    "public.denied",
                    publicPage("public.denied", "/denied")
            ),
            Map.entry(
                    "auth.login",
                    publicPage("auth.login", "/auth/login")
            ),
            Map.entry(
                    "auth.password-reset",
                    publicPage("auth.password-reset", "/auth/password-reset")
            ),
            Map.entry(
                    "auth.select-institutions",
                    publicPage("auth.select-institutions", "/auth/select-institutions")
            ),
            Map.entry(
                    "auth.select-membership",
                    publicPage("auth.select-membership", "/auth/select-membership")
            ),
            Map.entry(
                    "system.admin.dashboard",
                    protectedPage("system.admin.dashboard", "/system/admin/dashboard")
            ),
            Map.entry(
                    "institution.admin.dashboard",
                    protectedPage("institution.admin.dashboard", "/institution/admin/dashboard")
            ),
            Map.entry(
                    "institution.user.dashboard",
                    protectedPage("institution.user.dashboard", "/institution/user/dashboard")
            ),
            Map.entry(
                    "institution.journals.list",
                    new AllowedNavigationAuditPage(
                            "institution.journals.list",
                            "/institution/user/journals",
                            eventSet(
                                    NavigationAuditEventType.SERVICE_ENTRY,
                                    NavigationAuditEventType.PAGE_VIEW,
                                    NavigationAuditEventType.PAGE_READY,
                                    NavigationAuditEventType.API_RESULT,
                                    NavigationAuditEventType.FILE_DOWNLOAD,
                                    NavigationAuditEventType.JOURNAL_SEARCH,
                                    NavigationAuditEventType.JOURNAL_FILTERS_CLEAR,
                                    NavigationAuditEventType.JOURNAL_SEARCH_RESULT,
                                    NavigationAuditEventType.JOURNAL_SEARCH_EMPTY,
                                    NavigationAuditEventType.JOURNAL_CARD_OPEN,
                                    NavigationAuditEventType.JOURNAL_ISSUES_CLICK,
                                    NavigationAuditEventType.PAGE_HEARTBEAT,
                                    NavigationAuditEventType.PAGE_LEAVE,
                                    NavigationAuditEventType.PAGE_VISIBLE,
                                    NavigationAuditEventType.PAGE_HIDDEN
                            ),
                            false
                    )
            ),
            Map.entry(
                    "institution.journals.decision",
                    new AllowedNavigationAuditPage(
                            "institution.journals.decision", "/institution/user/journals/:id/decision",
                            eventSet(NavigationAuditEventType.SERVICE_ENTRY, NavigationAuditEventType.PAGE_VIEW,
                                    NavigationAuditEventType.PAGE_READY, NavigationAuditEventType.API_RESULT,
                                    NavigationAuditEventType.PAGE_HEARTBEAT, NavigationAuditEventType.PAGE_LEAVE,
                                      NavigationAuditEventType.PAGE_VISIBLE, NavigationAuditEventType.PAGE_HIDDEN,
                                      NavigationAuditEventType.JOURNAL_DECISION_ACTION),
                            false
                    )
            ),
            Map.entry(
                    "institution.journals.details",
                    new AllowedNavigationAuditPage(
                            "institution.journals.details",
                            "/institution/user/journals/:id",
                            eventSet(
                                    NavigationAuditEventType.SERVICE_ENTRY,
                                    NavigationAuditEventType.PAGE_VIEW,
                                    NavigationAuditEventType.PAGE_READY,
                                    NavigationAuditEventType.API_RESULT,
                                    NavigationAuditEventType.FILE_DOWNLOAD,
                                    NavigationAuditEventType.QUESTIONNAIRE_SHOWN,
                                    NavigationAuditEventType.TAB_CLICK,
                                    NavigationAuditEventType.PAGE_HEARTBEAT,
                                    NavigationAuditEventType.PAGE_LEAVE,
                                    NavigationAuditEventType.PAGE_VISIBLE,
                                    NavigationAuditEventType.PAGE_HIDDEN
                            ),
                            false
                    )
            )
    );

    /**
     * Sprawdza, czy dana strona, trasa i typ zdarzenia mogą zostać zapisane dla wskazanego typu aktora.
     * Nieznana strona, niezgodny szablon trasy lub niedozwolony typ zdarzenia powodują odrzucenie zgłoszenia.
     *
     * @return true, jeśli zgłoszenie pasuje do wpisu rejestru i jest dozwolone dla danego typu aktora
     */
    public boolean isAllowed(
            String pageKey,
            String routeTemplate,
            NavigationAuditEventType eventType,
            NavigationAuditActorType actorType
    ) {
        AllowedNavigationAuditPage page = pages.get(pageKey);

        if (page == null) {
            return false;
        }

        if (actorType == NavigationAuditActorType.ANONYMOUS && !page.anonymousAllowed()) {
            return false;
        }

        return page.routeTemplate().equals(routeTemplate)
                && page.allowedEvents().contains(eventType);
    }

    private static AllowedNavigationAuditPage publicPage(String pageKey, String routeTemplate) {
        return new AllowedNavigationAuditPage(pageKey, routeTemplate, PUBLIC_PAGE_EVENTS, true);
    }

    private static AllowedNavigationAuditPage protectedPage(String pageKey, String routeTemplate) {
        return new AllowedNavigationAuditPage(pageKey, routeTemplate, PROTECTED_PAGE_EVENTS, false);
    }

    private static Set<NavigationAuditEventType> eventSet(NavigationAuditEventType... events) {
        EnumSet<NavigationAuditEventType> eventSet = EnumSet.noneOf(NavigationAuditEventType.class);
        Collections.addAll(eventSet, events);
        return Set.copyOf(eventSet);
    }

    private record AllowedNavigationAuditPage(
            String pageKey,
            String routeTemplate,
            Set<NavigationAuditEventType> allowedEvents,
            boolean anonymousAllowed
    ) {
    }
}
