/** Akcje użytkownika na Karcie decyzji, w tym rozwinięcie opisu E02. */
export type DecisionAuditAction =
  | 'E01'
  | 'E02'
  | 'E03'
  | 'E04'
  | 'E05'
  | 'E06'
  | 'E07'
  | 'E08'
  | 'E09'
  | 'E10'
  | 'E11'
  | 'E12'
  | 'E13'
  | 'E14';

export const DECISION_AUDIT_LABELS: Record<DecisionAuditAction, string> = {
  E01: 'decisionCard.audit.open',
  E02: 'decisionCard.audit.description',
  E03: 'decisionCard.audit.history',
  E04: 'decisionCard.audit.indexing',
  E05: 'decisionCard.audit.wos',
  E06: 'decisionCard.audit.scopus',
  E07: 'decisionCard.audit.fullData',
  E08: 'decisionCard.audit.time',
  E09: 'decisionCard.audit.costs',
  E10: 'decisionCard.audit.standards',
  E11: 'decisionCard.audit.process',
  E12: 'decisionCard.audit.international',
  E13: 'decisionCard.audit.website',
  E14: 'decisionCard.audit.issues',
};
