package com.osatum.wbn.domain.model.navigationaudit;

public enum NavigationAuditEventType {
    SERVICE_ENTRY,
    PUBLIC_PAGE_VIEW,
    PAGE_VIEW,
    PAGE_READY,
    API_RESULT,
    FILE_DOWNLOAD,
    QUESTIONNAIRE_SHOWN,
    TAB_CLICK,
    PAGE_HEARTBEAT,
    PAGE_LEAVE,
    PAGE_VISIBLE,
    PAGE_HIDDEN,
    JOURNAL_SEARCH,
    JOURNAL_FILTERS_CLEAR,
    JOURNAL_SEARCH_RESULT,
    JOURNAL_SEARCH_EMPTY,
    JOURNAL_CARD_OPEN,
    JOURNAL_ISSUES_CLICK,
    JOURNAL_DECISION_ACTION;

    public boolean isJournalAnalyticsEvent() {
        return isJournalSearchEvent() || this == JOURNAL_DECISION_ACTION;
    }

    public boolean isJournalSearchEvent() {
        return switch (this) {
            case JOURNAL_SEARCH, JOURNAL_FILTERS_CLEAR, JOURNAL_SEARCH_RESULT, JOURNAL_SEARCH_EMPTY, JOURNAL_CARD_OPEN, JOURNAL_ISSUES_CLICK -> true;
            default -> false;
        };
    }
}
