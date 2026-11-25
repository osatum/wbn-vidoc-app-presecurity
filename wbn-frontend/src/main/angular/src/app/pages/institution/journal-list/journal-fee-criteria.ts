import { FormControl } from '@angular/forms';
import { JournalListQuery } from '../../../core/backend';

import { FeeCriteria } from '../../../core/services/journal-list-state';
export type { FeeCriteria } from '../../../core/services/journal-list-state';

export type FeeControls = { [Field in keyof FeeCriteria]: FormControl<FeeCriteria[Field]> };
export const FEE_FILTERS = [
  { category: 'OPEN_ACCESS', flag: 'openAccessFeeFreeOnly', maximum: 'openAccessFeeMaxPln' },
  { category: 'PUBLICATION', flag: 'publicationFeeFreeOnly', maximum: 'publicationFeeMaxPln' },
  { category: 'RAPID_REVIEW', flag: 'rapidReviewFeeFreeOnly', maximum: 'rapidReviewFeeMaxPln' },
  { category: 'SUBMISSION', flag: 'submissionFeeFreeOnly', maximum: 'submissionFeeMaxPln' },
] as const;

export function feeCriteriaFromQuery(query: JournalListQuery = {}): FeeCriteria {
  return {
    otherFeesFreeOnly: query.otherFeesFreeOnly === true,
    openAccessFeeFreeOnly: query.openAccessFeeFreeOnly === true,
    openAccessFeeMaxPln: query.openAccessFeeMaxPln?.toString() ?? '',
    publicationFeeFreeOnly: query.publicationFeeFreeOnly === true,
    publicationFeeMaxPln: query.publicationFeeMaxPln?.toString() ?? '',
    rapidReviewFeeFreeOnly: query.rapidReviewFeeFreeOnly === true,
    rapidReviewFeeMaxPln: query.rapidReviewFeeMaxPln?.toString() ?? '',
    submissionFeeFreeOnly: query.submissionFeeFreeOnly === true,
    submissionFeeMaxPln: query.submissionFeeMaxPln?.toString() ?? '',
  };
}

function amount(value: string): number | undefined {
  return value.trim() ? Number(value.trim().replace(',', '.')) : undefined;
}

function decimalKey(value: string): string {
  const [coefficient, exponent = '0'] = value.toLowerCase().split('e');
  const [whole, fraction = ''] = coefficient.split('.');
  let digits = (whole + fraction).replace(/^0+/, '');
  let scale = fraction.length - Number(exponent);
  if (!digits) return '0';
  while (digits.endsWith('0')) {
    digits = digits.slice(0, -1);
    scale--;
  }
  return `${digits}:${scale}`;
}

export function isValidFeeCriteria(criteria: FeeCriteria): boolean {
  return FEE_FILTERS.every(({ flag, maximum }) => {
    const raw = criteria[maximum].trim();
    const value = amount(raw);
    return (
      !raw ||
      (!criteria[flag] &&
        /^(?:\d+(?:[.,]\d+)?|[.,]\d+)$/.test(raw) &&
        value !== undefined &&
        Number.isFinite(value) &&
        value >= 0 &&
        value <= Number.MAX_SAFE_INTEGER &&
        decimalKey(raw.replace(',', '.')) === decimalKey(value.toString()))
    );
  });
}

export function feeQuery(criteria: FeeCriteria): Partial<JournalListQuery> {
  const query: Partial<JournalListQuery> = {
    otherFeesFreeOnly: criteria.otherFeesFreeOnly || undefined,
  };
  for (const { flag, maximum } of FEE_FILTERS) {
    query[flag] = criteria[flag] || undefined;
    query[maximum] = amount(criteria[maximum]);
  }
  return query;
}
