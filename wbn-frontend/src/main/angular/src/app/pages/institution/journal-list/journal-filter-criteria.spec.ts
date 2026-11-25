import { JournalListQuery } from '../../../core/backend';
import { FEE_FILTERS, feeCriteriaFromQuery } from './journal-fee-criteria';
import { NON_FEE_FEATURES, nonFeeCriteriaFromQuery } from './journal-non-fee-criteria';
import {
  EMPTY_SURVEY_FILTERS,
  JournalFilters,
  journalCriteriaKey,
  journalFilterChips,
  normalizeFullRanges,
  removeJournalFilter,
} from './journal-filter-criteria';

const BOUNDS = { icvMin: 0, icvMax: 300, mniswPointClasses: [20, 40, 70, 200] };
function filters(): JournalFilters {
  return {
    ...EMPTY_SURVEY_FILTERS,
    nonFeeFilters: nonFeeCriteriaFromQuery(),
    feeFilters: feeCriteriaFromQuery(),
    journalTitle: '',
    issn: '',
    publisher: '',
    countryCodes: [],
    languageCodes: [],
    disciplineCodes: [],
    sortBy: JournalListQuery.SortByEnum.SortByTitleAsc,
    ratingStatus: '',
    icvFrom: '',
    icvTo: '',
    mniswOnly: false,
    mniswDisciplineCodes: [],
    mniswPointsFrom: '',
    mniswPointsTo: '',
  };
}

describe('Journal filter criteria', () => {
  it('tracks MNiSW selections independently and clears only the requested group', () => {
    const selected = {
      ...filters(),
      mniswOnly: true,
      mniswDisciplineCodes: ['2021', '2061'],
      disciplineCodes: ['42'],
      mniswPointsFrom: '70',
    };
    const chip = journalFilterChips(selected).find(
      (entry) => entry.id === 'mniswDisciplineCodes:2021',
    )!;
    expect(chip.labelKey).toBe('page.institution.journalList.mniswDisciplines');
    expect(removeJournalFilter(selected, chip.removal)).toMatchObject({
      mniswOnly: true,
      mniswDisciplineCodes: ['2061'],
      disciplineCodes: ['42'],
      mniswPointsFrom: '70',
    });
    expect(removeJournalFilter(selected, { kind: 'mnisw' })).toMatchObject({
      mniswOnly: false,
      mniswDisciplineCodes: [],
      disciplineCodes: ['42'],
      mniswPointsFrom: '',
    });
    expect(selected.mniswDisciplineCodes).toEqual(['2021', '2061']);
    expect(journalCriteriaKey(selected, BOUNDS)).toBe(
      journalCriteriaKey(
        {
          ...selected,
          mniswDisciplineCodes: ['2061', '2021', '2021'],
        },
        BOUNDS,
      ),
    );
    expect(journalCriteriaKey(selected, BOUNDS)).not.toBe(
      journalCriteriaKey(
        {
          ...selected,
          mniswDisciplineCodes: ['101'],
        },
        BOUNDS,
      ),
    );
  });

  it('ignores inactive MNiSW selections in criteria and chips', () => {
    const original = filters();
    const inactive = { ...original, mniswDisciplineCodes: ['2021'] };
    expect(journalCriteriaKey(inactive, BOUNDS)).toBe(journalCriteriaKey(original, BOUNDS));
    expect(journalFilterChips(inactive)).toEqual([]);
    expect(normalizeFullRanges(inactive, BOUNDS).mniswDisciplineCodes).toEqual([]);
  });
  it('keeps Indexed and its numeric range as separate chips and clears both only when removing the status', () => {
    const original = filters();
    const selected = {
      ...original,
      ratingStatus: JournalListQuery.RatingStatusEnum.Indexed,
      icvFrom: '80',
      icvTo: '90',
    };
    expect(normalizeFullRanges(selected, BOUNDS)).toMatchObject({
      ratingStatus: 'INDEXED',
      icvFrom: '80',
      icvTo: '90',
    });
    expect(journalCriteriaKey(selected, BOUNDS)).not.toBe(journalCriteriaKey(original, BOUNDS));
    const chips = journalFilterChips(selected);
    const status = chips.find((chip) => chip.id === 'ratingStatus')!;
    const range = chips.find((chip) => chip.id === 'icv')!;
    expect(status.labelKey).toBe('page.institution.journalList.indexedJournals');
    expect(removeJournalFilter(selected, range.removal)).toMatchObject({
      ratingStatus: 'INDEXED',
      icvFrom: '',
      icvTo: '',
    });
    expect(removeJournalFilter(selected, status.removal)).toMatchObject({
      ratingStatus: '',
      icvFrom: '',
      icvTo: '',
    });
  });

  it.each(['NOT_INDEXED', 'NOT_EVALUATED'] as const)(
    'tracks %s independently of disabled bounds and removes only this criterion',
    (ratingStatus) => {
      const original = filters();
      const selected = {
        ...original,
        ratingStatus,
        icvFrom: '80',
        icvTo: '90',
        publisher: 'Applied',
      };
      expect(journalCriteriaKey(selected, BOUNDS)).not.toBe(journalCriteriaKey(original, BOUNDS));
      expect(journalCriteriaKey(selected, BOUNDS)).toBe(
        journalCriteriaKey({ ...selected, icvFrom: '', icvTo: '' }, BOUNDS),
      );
      expect(normalizeFullRanges(selected, BOUNDS)).toMatchObject({
        icvFrom: '',
        icvTo: '',
        ratingStatus,
      });
      const chip = journalFilterChips(selected).find((entry) => entry.id === 'ratingStatus')!;
      expect(journalFilterChips(selected).some((entry) => entry.id === 'icv')).toBe(false);
      expect(removeJournalFilter(selected, chip.removal)).toMatchObject({
        ratingStatus: '',
        icvFrom: '',
        icvTo: '',
        publisher: 'Applied',
      });
      expect(selected).toMatchObject({ ratingStatus, icvFrom: '80', icvTo: '90' });
    },
  );

  it('has no chips for unrestricted criteria and does not count sorting as a filter change', () => {
    const original = filters();
    const changed = { ...original, sortBy: JournalListQuery.SortByEnum.SortByPublicationDaysDesc };
    expect(journalFilterChips(original)).toEqual([]);
    expect(journalCriteriaKey(original, BOUNDS)).toBe(journalCriteriaKey(changed, BOUNDS));
  });

  it('covers all 31 implemented filter parameters without inventing a MNiSW discipline source', () => {
    const values = filters();
    Object.assign(values, {
      journalTitle: 'Title',
      issn: '1234',
      publisher: 'Publisher',
      countryCodes: ['pl'],
      languageCodes: ['en'],
      disciplineCodes: ['3'],
      icvFrom: '80',
      icvTo: '90',
      mniswOnly: true,
      mniswPointsFrom: '40',
      mniswPointsTo: '70',
      reviewDaysFrom: '0',
      reviewDaysTo: '0',
      initialAssessmentDaysFrom: '2',
      initialAssessmentDaysTo: '10',
      publicationDaysFrom: '5',
      publicationDaysTo: '20',
      rejectionRateFrom: '10',
      rejectionRateTo: '20',
      authorForeignAffiliationPercentageTo: '50',
      reviewerForeignAffiliationPercentageFrom: '20',
    });
    for (const field of NON_FEE_FEATURES) values.nonFeeFilters[field] = true;
    Object.assign(values.nonFeeFilters, {
      repositoryIds: ['10'],
      licenseCodes: ['CC_BY'],
      accessModels: ['FREE_ARTICLES'],
      frequencyCodes: ['q'],
      reviewModels: ['DOUBLE_BLIND_REVIEW'],
      firstIssueYearFrom: '2000',
    });
    values.feeFilters.otherFeesFreeOnly = true;
    for (const { flag } of FEE_FILTERS) values.feeFilters[flag] = true;
    const chips = journalFilterChips(values);
    expect(chips).toHaveLength(32);
    expect(new Set(chips.map((chip) => chip.id)).size).toBe(32);
    expect(chips.find((chip) => chip.id === 'reviewDaysTo')?.maximum).toBe('0');
    expect(chips.find((chip) => chip.id === 'reviewDaysTo')).toMatchObject({
      minimum: '0', removal: { kind: 'range', fields: ['reviewDaysFrom', 'reviewDaysTo'] },
    });
    expect(chips.find((chip) => chip.id === 'initialAssessmentDaysTo')).toMatchObject({ minimum: '2', maximum: '10' });
    expect(chips.find((chip) => chip.id === 'publicationDaysTo')).toMatchObject({ minimum: '5', maximum: '20' });
    const original = structuredClone(values);
    for (const chip of chips) {
      const updated = removeJournalFilter(values, chip.removal);
      const removed = chip.id === 'mniswOnly' ? ['mniswOnly', 'mniswPoints'] : [chip.id];
      expect(journalFilterChips(updated).map((entry) => entry.id)).toEqual(
        chips.filter((entry) => !removed.includes(entry.id)).map((entry) => entry.id),
      );
      expect(values).toEqual(original);
    }
  });

  it('compares selections as sets without changing the raw order or discarding selected values', () => {
    const applied = filters();
    Object.assign(applied, {
      countryCodes: ['pl', 'de'],
      languageCodes: ['en', 'pl'],
      disciplineCodes: ['3', '5'],
    });
    Object.assign(applied.nonFeeFilters, {
      repositoryIds: ['10', '20'],
      licenseCodes: ['CC_BY', 'CC_BY_NC'],
      accessModels: ['FREE_ARTICLES', 'PAID_ARTICLES'],
      frequencyCodes: ['q', 'm'],
      reviewModels: ['DOUBLE_BLIND_REVIEW', 'OPEN_PEER_REVIEW'],
    });
    const draft = structuredClone(applied);
    draft.countryCodes = [' DE ', 'PL', 'pl'];
    draft.languageCodes.reverse();
    draft.disciplineCodes.reverse();
    draft.nonFeeFilters.repositoryIds.reverse();
    draft.nonFeeFilters.licenseCodes.reverse();
    draft.nonFeeFilters.accessModels.reverse();
    draft.nonFeeFilters.frequencyCodes.reverse();
    draft.nonFeeFilters.reviewModels.reverse();
    expect(journalCriteriaKey(draft, BOUNDS)).toBe(journalCriteriaKey(applied, BOUNDS));
    expect(draft.countryCodes).toEqual([' DE ', 'PL', 'pl']);
    expect(
      journalFilterChips(draft).filter((chip) => chip.selection === 'countryCodes'),
    ).toHaveLength(2);
    draft.languageCodes.push('fr');
    expect(journalCriteriaKey(draft, BOUNDS)).not.toBe(journalCriteriaKey(applied, BOUNDS));
  });

  it('normalizes equivalent decimal formats, whitespace, text case and ISSN separators', () => {
    const applied = filters();
    Object.assign(applied, {
      journalTitle: 'Journal title',
      publisher: 'Publisher name',
      issn: '1234567X',
      icvFrom: '80.25',
      publicationDaysTo: '10',
    });
    applied.feeFilters.publicationFeeMaxPln = '0.5';
    const draft = structuredClone(applied);
    Object.assign(draft, {
      journalTitle: ' JOURNAL   title ',
      publisher: 'Publisher  name ',
      issn: '1234–567x',
      icvFrom: '080,25',
      publicationDaysTo: '0010',
    });
    draft.feeFilters.publicationFeeMaxPln = ',50';
    expect(journalCriteriaKey(draft, BOUNDS)).toBe(journalCriteriaKey(applied, BOUNDS));
  });

  it('removes full ICV, MNiSW and percentage ranges but keeps the MNiSW membership filter', () => {
    const values = filters();
    Object.assign(values, {
      icvFrom: '0,00',
      icvTo: '300.00',
      mniswOnly: true,
      mniswPointsFrom: '20',
      mniswPointsTo: '200',
      rejectionRateFrom: '0',
      rejectionRateTo: '100',
      authorForeignAffiliationPercentageFrom: '0',
      authorForeignAffiliationPercentageTo: '100',
      reviewerForeignAffiliationPercentageFrom: '0',
      reviewerForeignAffiliationPercentageTo: '100',
    });
    const normalized = normalizeFullRanges(values, {
      ...BOUNDS,
      mniswPointClasses: [200, 20, 70, 40],
    });
    expect(normalized.icvFrom).toBe('');
    expect(normalized.icvTo).toBe('');
    expect(normalized.mniswPointsFrom).toBe('');
    expect(normalized.mniswPointsTo).toBe('');
    expect(normalized.rejectionRateFrom).toBe('');
    expect(normalized.authorForeignAffiliationPercentageTo).toBe('');
    expect(normalized.reviewerForeignAffiliationPercentageFrom).toBe('');
    expect(journalFilterChips(normalized).map((chip) => chip.id)).toEqual(['mniswOnly']);
    const unrestricted = { ...filters(), mniswOnly: true };
    expect(journalCriteriaKey(values, BOUNDS)).toBe(journalCriteriaKey(unrestricted, BOUNDS));
    expect(values.icvFrom).toBe('0,00');
    expect(values.rejectionRateTo).toBe('100');
  });

  it('keeps partial ranges and zero limits as real filters, including fee zero', () => {
    const values = filters();
    values.rejectionRateFrom = '0';
    values.reviewDaysTo = '0';
    values.feeFilters.publicationFeeMaxPln = '0';
    const chips = journalFilterChips(values);
    expect(chips.map((chip) => chip.id)).toEqual([
      'reviewDaysTo',
      'rejectionRate',
      'publicationFeeFreeOnly',
    ]);
    expect(chips.at(-1)?.maximum).toBe('0');
    expect(chips.at(-1)?.noFee).toBe(false);
    expect(journalCriteriaKey(values, BOUNDS)).not.toBe(journalCriteriaKey(filters(), BOUNDS));
  });

  it.each([
    { icvFrom: '100.000' },
    { publicationDaysTo: '10.0' },
    { reviewDaysFrom: '10.0' },
    { initialAssessmentDaysFrom: '1e2' },
    { publicationDaysFrom: '1.5' },
    { rejectionRateTo: 'NaN' },
    { rejectionRateFrom: '-1' },
  ])('does not hide invalid numeric drafts by coercing them: %j', (invalid) => {
    const applied = { ...filters(), icvFrom: '100', publicationDaysTo: '10' };
    expect(journalCriteriaKey({ ...applied, ...invalid }, BOUNDS)).not.toBe(
      journalCriteriaKey(applied, BOUNDS),
    );
  });

  it('does not hide monetary precision loss or contradictory no-fee and amount criteria', () => {
    const applied = filters();
    applied.feeFilters.publicationFeeMaxPln = '0.1';
    const draft = structuredClone(applied);
    draft.feeFilters.publicationFeeMaxPln = '0.10000000000000001';
    expect(journalCriteriaKey(draft, BOUNDS)).not.toBe(journalCriteriaKey(applied, BOUNDS));
    draft.feeFilters.publicationFeeMaxPln = '0.1';
    draft.feeFilters.publicationFeeFreeOnly = true;
    expect(journalCriteriaKey(draft, BOUNDS)).not.toBe(journalCriteriaKey(applied, BOUNDS));
  });

  it('ignores inactive MNiSW point fields but never ignores independent JML disciplines', () => {
    const values = filters();
    values.mniswPointsFrom = '70';
    expect(journalCriteriaKey(values, BOUNDS)).toBe(journalCriteriaKey(filters(), BOUNDS));
    expect(journalFilterChips(values)).toEqual([]);
    values.disciplineCodes = ['3'];
    expect(journalFilterChips(values).map((chip) => chip.id)).toEqual(['disciplineCodes:3']);
  });

  it('does not drop an ICV range when metadata is absent or invent full year/day/fee bounds', () => {
    const values = filters();
    Object.assign(values, { icvFrom: '0', icvTo: '300', publicationDaysTo: '100' });
    Object.assign(values.nonFeeFilters, { firstIssueYearFrom: '0', firstIssueYearTo: '2026' });
    values.feeFilters.openAccessFeeMaxPln = '100';
    expect(journalFilterChips(values).map((chip) => chip.id)).toEqual([
      'icv',
      'publicationDaysTo',
      'firstIssueYear',
      'openAccessFeeFreeOnly',
    ]);
  });
});
