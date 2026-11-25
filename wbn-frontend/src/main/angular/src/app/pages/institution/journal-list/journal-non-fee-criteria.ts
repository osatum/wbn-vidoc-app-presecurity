import { FormControl } from '@angular/forms';
import { JournalListQuery } from '../../../core/backend';
import { NonFeeCriteria } from '../../../core/services/journal-list-state';
export type { NonFeeCriteria } from '../../../core/services/journal-list-state';

export type NonFeeSelection =
  | 'repositoryIds'
  | 'licenseCodes'
  | 'accessModels'
  | 'frequencyCodes'
  | 'reviewModels';
export const NON_FEE_FEATURES = [
  'doiOnly',
  'editorialSystemOnly',
  'newsletterOnly',
  'socialMediaOnly',
  'antiPlagiarismOnly',
  'aopOnly',
] as const;
export type NonFeeFeature = (typeof NON_FEE_FEATURES)[number];
export type NonFeeControls = {
  [Field in keyof NonFeeCriteria]: FormControl<NonFeeCriteria[Field]>;
};

export function nonFeeCriteriaFromQuery(query: JournalListQuery = {}): NonFeeCriteria {
  return {
    doiOnly: query.doiOnly === true,
    editorialSystemOnly: query.editorialSystemOnly === true,
    newsletterOnly: query.newsletterOnly === true,
    socialMediaOnly: query.socialMediaOnly === true,
    antiPlagiarismOnly: query.antiPlagiarismOnly === true,
    aopOnly: query.aopOnly === true,
    repositoryIds: (query.repositoryIds ?? []).map(String),
    licenseCodes: [...(query.licenseCodes ?? [])],
    accessModels: [...(query.accessModels ?? [])],
    frequencyCodes: [...(query.frequencyCodes ?? [])],
    reviewModels: [...(query.reviewModels ?? [])],
    firstIssueYearFrom: query.firstIssueYearFrom?.toString() ?? '',
    firstIssueYearTo: query.firstIssueYearTo?.toString() ?? '',
  };
}

export function isValidNonFeeCriteria(criteria: NonFeeCriteria): boolean {
  const validYear = (value: string) =>
    !value.trim() ||
    (/^\d+$/.test(value.trim()) && Number(value) >= 0 && Number(value) <= 2147483647);
  const validCodes = (values: string[], allowed: readonly string[]) =>
    values.every((value) => allowed.includes(value));
  return (
    validYear(criteria.firstIssueYearFrom) &&
    validYear(criteria.firstIssueYearTo) &&
    (!criteria.firstIssueYearFrom.trim() ||
      !criteria.firstIssueYearTo.trim() ||
      Number(criteria.firstIssueYearFrom) <= Number(criteria.firstIssueYearTo)) &&
    criteria.repositoryIds.every(
      (id) => /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id)),
    ) &&
    validCodes(criteria.licenseCodes, Object.values(JournalListQuery.LicenseCodesEnum)) &&
    validCodes(criteria.accessModels, Object.values(JournalListQuery.AccessModelsEnum)) &&
    validCodes(criteria.frequencyCodes, Object.values(JournalListQuery.FrequencyCodesEnum)) &&
    validCodes(criteria.reviewModels, Object.values(JournalListQuery.ReviewModelsEnum))
  );
}

export function nonFeeQuery(criteria: NonFeeCriteria): Partial<JournalListQuery> {
  return {
    doiOnly: criteria.doiOnly || undefined,
    editorialSystemOnly: criteria.editorialSystemOnly || undefined,
    newsletterOnly: criteria.newsletterOnly || undefined,
    socialMediaOnly: criteria.socialMediaOnly || undefined,
    antiPlagiarismOnly: criteria.antiPlagiarismOnly || undefined,
    aopOnly: criteria.aopOnly || undefined,
    repositoryIds: criteria.repositoryIds.length ? criteria.repositoryIds.map(Number) : undefined,
    licenseCodes: criteria.licenseCodes.length
      ? ([...criteria.licenseCodes] as JournalListQuery.LicenseCodesEnum[])
      : undefined,
    accessModels: criteria.accessModels.length
      ? ([...criteria.accessModels] as JournalListQuery.AccessModelsEnum[])
      : undefined,
    frequencyCodes: criteria.frequencyCodes.length
      ? ([...criteria.frequencyCodes] as JournalListQuery.FrequencyCodesEnum[])
      : undefined,
    reviewModels: criteria.reviewModels.length
      ? ([...criteria.reviewModels] as JournalListQuery.ReviewModelsEnum[])
      : undefined,
    firstIssueYearFrom: criteria.firstIssueYearFrom.trim()
      ? Number(criteria.firstIssueYearFrom)
      : undefined,
    firstIssueYearTo: criteria.firstIssueYearTo.trim()
      ? Number(criteria.firstIssueYearTo)
      : undefined,
  };
}
