-- P2 korzysta z tej samej polityki retencji co analityka P1.
DROP INDEX IF EXISTS idx_navigation_audit_journal_search_retention;
CREATE INDEX idx_navigation_audit_journal_search_retention
    ON navigation_audit_event (received_at, id)
    WHERE event_type IN ('JOURNAL_SEARCH', 'JOURNAL_FILTERS_CLEAR', 'JOURNAL_SEARCH_RESULT',
                         'JOURNAL_SEARCH_EMPTY', 'JOURNAL_CARD_OPEN', 'JOURNAL_DECISION_ACTION');
