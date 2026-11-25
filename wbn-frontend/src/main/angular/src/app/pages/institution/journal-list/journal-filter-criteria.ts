import {
  EMPTY_SURVEY_FILTERS,
  JournalFilters,
  SurveyFilterName,
} from '../../../core/services/journal-list-state';
export { EMPTY_SURVEY_FILTERS } from '../../../core/services/journal-list-state';
export type { JournalFilters, SurveyFilterName } from '../../../core/services/journal-list-state';
import { FEE_FILTERS, isValidFeeCriteria } from './journal-fee-criteria';
import {
  NonFeeFeature,
  NonFeeSelection,
  NON_FEE_FEATURES,
  isValidNonFeeCriteria,
} from './journal-non-fee-criteria';

export const SURVEY_FILTER_NAMES = Object.keys(EMPTY_SURVEY_FILTERS) as SurveyFilterName[];
export type MainSelection =
  | 'countryCodes'
  | 'languageCodes'
  | 'disciplineCodes'
  | 'mniswDisciplineCodes';
export type MainText = 'journalTitle' | 'issn' | 'publisher';
export type MainNumber =
  | SurveyFilterName
  | 'icvFrom'
  | 'icvTo'
  | 'mniswPointsFrom'
  | 'mniswPointsTo';
export interface CriteriaBounds {
  icvMin?: number | null;
  icvMax?: number | null;
  mniswPointClasses?: readonly number[];
}
type FeeFlag = (typeof FEE_FILTERS)[number]['flag'] | 'otherFeesFreeOnly';
type FeeMaximum = (typeof FEE_FILTERS)[number]['maximum'];
export type FilterRemoval =
  | { kind: 'text'; field: MainText }
  | { kind: 'selection'; field: MainSelection; code: string }
  | { kind: 'range'; fields: readonly MainNumber[] }
  | { kind: 'mnisw' }
  | { kind: 'icvStatus' }
  | { kind: 'nonFeeFlag'; field: NonFeeFeature }
  | { kind: 'nonFeeSelection'; field: NonFeeSelection; code: string }
  | { kind: 'year' }
  | { kind: 'fee'; flag: FeeFlag; maximum?: FeeMaximum };
export interface JournalFilterChip {
  id: string;
  labelKey: string;
  value?: string;
  selection?: MainSelection | NonFeeSelection;
  minimum?: string;
  maximum?: string;
  unit?: 'days' | 'percent' | 'pln';
  noFee?: boolean;
  removal: FilterRemoval;
}
export type FilterChipLabels = Partial<
  Record<MainSelection | NonFeeSelection, Readonly<Record<string, string>>>
>;

export function removeJournalFilter(
  values: JournalFilters,
  removal: FilterRemoval,
): JournalFilters {
  const updated = {
    ...values,
    nonFeeFilters: { ...values.nonFeeFilters },
    feeFilters: { ...values.feeFilters },
  };
  switch (removal.kind) {
    case 'text':
      updated[removal.field] = '';
      break;
    case 'selection':
      updated[removal.field] = values[removal.field].filter(
        (code) =>
          (removal.field === 'disciplineCodes' ? code.trim() : code.trim().toLowerCase()) !==
          removal.code,
      );
      break;
    case 'range':
      for (const field of removal.fields) updated[field] = '';
      break;
    case 'mnisw':
      updated.mniswOnly = false;
      updated.mniswDisciplineCodes = [];
      updated.mniswPointsFrom = '';
      updated.mniswPointsTo = '';
      break;
    case 'icvStatus':
      updated.ratingStatus = '';
      updated.icvFrom = '';
      updated.icvTo = '';
      break;
    case 'nonFeeFlag':
      updated.nonFeeFilters[removal.field] = false;
      break;
    case 'nonFeeSelection':
      updated.nonFeeFilters[removal.field] = values.nonFeeFilters[removal.field].filter(
        (code) => code.trim() !== removal.code,
      );
      break;
    case 'year':
      updated.nonFeeFilters.firstIssueYearFrom = '';
      updated.nonFeeFilters.firstIssueYearTo = '';
      break;
    case 'fee':
      updated.feeFilters[removal.flag] = false;
      if (removal.maximum) updated.feeFilters[removal.maximum] = '';
      break;
  }
  return updated;
}

const SELECTIONS = [
  'countryCodes',
  'languageCodes',
  'disciplineCodes',
  'mniswDisciplineCodes',
] as const;
const NON_FEE_SELECTIONS = [
  'repositoryIds',
  'licenseCodes',
  'accessModels',
  'frequencyCodes',
  'reviewModels',
] as const;
const PERCENT_RANGES = [
  ['rejectionRateFrom', 'rejectionRateTo', 'rejectionRate'],
  [
    'authorForeignAffiliationPercentageFrom',
    'authorForeignAffiliationPercentageTo',
    'authorForeignAffiliationPercentage',
  ],
  [
    'reviewerForeignAffiliationPercentageFrom',
    'reviewerForeignAffiliationPercentageTo',
    'reviewerForeignAffiliationPercentage',
  ],
] as const;

function numericKey(value: string, pattern = /^\d+(?:[.,]\d+)?$/): string {
  const raw = value.trim();
  if (!raw) return '';
  if (!pattern.test(raw)) return `invalid:${raw}`;
  const [whole = '', fraction = ''] = raw.replace(',', '.').split('.');
  const integer = whole.replace(/^0+/, '') || '0';
  const decimal = fraction.replace(/0+$/, '');
  return decimal ? `${integer}.${decimal}` : integer;
}

export function selectionCodes(values: readonly string[], lowerCase = false): string[] {
  return [
    ...new Set(
      values
        .map((value) => (lowerCase ? value.trim().toLowerCase() : value.trim()))
        .filter(Boolean),
    ),
  ].sort();
}

export function normalizeFullRanges(
  filters: JournalFilters,
  bounds: CriteriaBounds,
): JournalFilters {
  const normalized = {
    ...filters,
    mniswDisciplineCodes: filters.mniswOnly ? [...(filters.mniswDisciplineCodes ?? [])] : [],
  };
  if (filters.ratingStatus && filters.ratingStatus !== 'INDEXED') {
    normalized.icvFrom = '';
    normalized.icvTo = '';
  }
  const resetFull = (
    from: MainNumber,
    to: MainNumber,
    minimum: number | null | undefined,
    maximum: number | null | undefined,
    pattern?: RegExp,
  ) => {
    if (
      minimum != null &&
      maximum != null &&
      numericKey(filters[from], pattern) === String(minimum) &&
      numericKey(filters[to], pattern) === String(maximum)
    ) {
      normalized[from] = '';
      normalized[to] = '';
    }
  };
  resetFull('icvFrom', 'icvTo', bounds.icvMin, bounds.icvMax, /^\d+(?:[.,]\d{1,2})?$/);
  if (filters.mniswOnly && bounds.mniswPointClasses?.length) {
    resetFull(
      'mniswPointsFrom',
      'mniswPointsTo',
      Math.min(...bounds.mniswPointClasses),
      Math.max(...bounds.mniswPointClasses),
      /^\d+$/,
    );
  }
  for (const [from, to] of PERCENT_RANGES) resetFull(from, to, 0, 100);
  return normalized;
}

export function journalCriteriaKey(filters: JournalFilters, bounds: CriteriaBounds): string {
  const values = normalizeFullRanges(filters, bounds);
  const key: Record<string, unknown> = {};
  for (const field of ['journalTitle', 'publisher'] as const) {
    key[field] = values[field].trim().replace(/\s+/gu, ' ').toLowerCase();
  }
  key['issn'] = values.issn.replace(/[\p{Pd}\s\p{Z}]+/gu, '').toUpperCase();
  for (const field of SELECTIONS)
    key[field] = selectionCodes(values[field], field !== 'disciplineCodes');
  key['icvFrom'] = numericKey(values.icvFrom, /^\d+(?:[.,]\d{1,2})?$/);
  key['icvTo'] = numericKey(values.icvTo, /^\d+(?:[.,]\d{1,2})?$/);
  key['ratingStatus'] = values.ratingStatus;
  key['mniswOnly'] = values.mniswOnly;
  key['mniswPointsFrom'] = values.mniswOnly ? numericKey(values.mniswPointsFrom, /^\d+$/) : '';
  key['mniswPointsTo'] = values.mniswOnly ? numericKey(values.mniswPointsTo, /^\d+$/) : '';
  for (const field of SURVEY_FILTER_NAMES)
    key[field] = numericKey(values[field], /Days(?:From|To)$/.test(field) ? /^\d+$/ : undefined);
  for (const field of NON_FEE_FEATURES) key[field] = values.nonFeeFilters[field];
  key['validNonFee'] = isValidNonFeeCriteria(values.nonFeeFilters);
  for (const field of NON_FEE_SELECTIONS) key[field] = selectionCodes(values.nonFeeFilters[field]);
  for (const field of ['firstIssueYearFrom', 'firstIssueYearTo'] as const)
    key[field] = numericKey(values.nonFeeFilters[field], /^\d+$/);
  const fees = values.feeFilters;
  key['otherFeesFreeOnly'] = fees.otherFeesFreeOnly;
  key['validFees'] = isValidFeeCriteria(fees);
  for (const { flag, maximum } of FEE_FILTERS) {
    key[flag] = fees[flag];
    key[maximum] = numericKey(fees[maximum], /^(?:\d+(?:[.,]\d+)?|[.,]\d+)$/);
  }
  return JSON.stringify(key);
}

export function journalFilterChips(values: JournalFilters): JournalFilterChip[] {
  const chips: JournalFilterChip[] = [];
  const label = (key: string) => `page.institution.journalList.${key}`;
  for (const field of ['journalTitle', 'issn', 'publisher'] as const) {
    if (values[field].trim())
      chips.push({
        id: field,
        labelKey: label(field),
        value: values[field].trim(),
        removal: { kind: 'text', field },
      });
  }
  const selectionLabels = {
    countryCodes: 'country',
    languageCodes: 'language',
    disciplineCodes: 'disciplines',
    mniswDisciplineCodes: 'mniswDisciplines',
  };
  for (const field of SELECTIONS) {
    if (field === 'mniswDisciplineCodes' && !values.mniswOnly) continue;
    for (const code of selectionCodes(values[field] ?? [], field !== 'disciplineCodes')) {
      chips.push({
        id: `${field}:${code}`,
        labelKey: label(selectionLabels[field]),
        value: code,
        selection: field,
        removal: { kind: 'selection', field, code },
      });
    }
  }
  const range = (
    id: string,
    fields: readonly MainNumber[],
    minimum: string,
    maximum: string,
    labelKey: string,
    unit?: JournalFilterChip['unit'],
  ) => {
    if (minimum.trim() || maximum.trim())
      chips.push({
        id,
        labelKey: label(labelKey),
        minimum: minimum.trim() || undefined,
        maximum: maximum.trim() || undefined,
        unit,
        removal: { kind: 'range', fields },
      });
  };
  if (values.ratingStatus) {
    chips.push({
      id: 'ratingStatus',
      labelKey: label(
        values.ratingStatus === 'INDEXED'
          ? 'indexedJournals'
          : values.ratingStatus === 'NOT_INDEXED'
            ? 'notIndexedJournals'
            : 'notEvaluatedJournals',
      ),
      removal: { kind: 'icvStatus' },
    });
  }
  if (!values.ratingStatus || values.ratingStatus === 'INDEXED') {
    range('icv', ['icvFrom', 'icvTo'], values.icvFrom, values.icvTo, 'icvRange');
  }
  if (values.mniswOnly) {
    chips.push({ id: 'mniswOnly', labelKey: label('mniswOnly'), removal: { kind: 'mnisw' } });
    range(
      'mniswPoints',
      ['mniswPointsFrom', 'mniswPointsTo'],
      values.mniswPointsFrom,
      values.mniswPointsTo,
      'mniswFilters',
    );
  }
  for (const [from, to] of [
    ['reviewDaysFrom', 'reviewDaysTo'],
    ['initialAssessmentDaysFrom', 'initialAssessmentDaysTo'],
    ['publicationDaysFrom', 'publicationDaysTo'],
  ] as const) {
    range(to, [from, to], values[from], values[to], to.slice(0, -2), 'days');
  }
  for (const [from, to, name] of PERCENT_RANGES)
    range(name, [from, to], values[from], values[to], name, 'percent');
  for (const field of NON_FEE_FEATURES) {
    if (values.nonFeeFilters[field])
      chips.push({ id: field, labelKey: label(field), removal: { kind: 'nonFeeFlag', field } });
  }
  const nonFeeLabels = {
    repositoryIds: 'repositories',
    licenseCodes: 'licenses',
    accessModels: 'accessModels',
    frequencyCodes: 'frequency',
    reviewModels: 'reviewModels',
  };
  for (const field of NON_FEE_SELECTIONS) {
    for (const code of selectionCodes(values.nonFeeFilters[field]))
      chips.push({
        id: `${field}:${code}`,
        labelKey: label(nonFeeLabels[field]),
        value: code,
        selection: field,
        removal: { kind: 'nonFeeSelection', field, code },
      });
  }
  const { firstIssueYearFrom, firstIssueYearTo } = values.nonFeeFilters;
  if (firstIssueYearFrom.trim() || firstIssueYearTo.trim())
    chips.push({
      id: 'firstIssueYear',
      labelKey: label('firstIssueYear'),
      minimum: firstIssueYearFrom.trim() || undefined,
      maximum: firstIssueYearTo.trim() || undefined,
      removal: { kind: 'year' },
    });
  if (values.feeFilters.otherFeesFreeOnly)
    chips.push({
      id: 'otherFeesFreeOnly',
      labelKey: label('feeLabels.OTHER'),
      noFee: true,
      removal: { kind: 'fee', flag: 'otherFeesFreeOnly' },
    });
  for (const { category, flag, maximum } of FEE_FILTERS) {
    if (values.feeFilters[flag] || values.feeFilters[maximum].trim())
      chips.push({
        id: flag,
        labelKey: label(`feeLabels.${category}`),
        noFee: values.feeFilters[flag],
        maximum: values.feeFilters[maximum].trim() || undefined,
        unit: 'pln',
        removal: { kind: 'fee', flag, maximum },
      });
  }
  return chips;
}
