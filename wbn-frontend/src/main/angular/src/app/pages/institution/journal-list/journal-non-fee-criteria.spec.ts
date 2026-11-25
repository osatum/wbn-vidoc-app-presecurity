import {
  isValidNonFeeCriteria,
  nonFeeCriteriaFromQuery,
  nonFeeQuery,
} from './journal-non-fee-criteria';

describe('Non-fee journal criteria', () => {
  it('round trips every criterion without treating access model as a fee', () => {
    const query = {
      doiOnly: true,
      editorialSystemOnly: true,
      newsletterOnly: true,
      socialMediaOnly: true,
      antiPlagiarismOnly: true,
      aopOnly: true,
      repositoryIds: [10, 20],
      licenseCodes: ['CC_BY', 'NONE'] as const,
      accessModels: ['FREE_ARTICLES'] as const,
      frequencyCodes: ['q'] as const,
      reviewModels: ['DOUBLE_BLIND_REVIEW', 'OPEN_PEER_REVIEW'] as const,
      firstIssueYearFrom: 1960,
      firstIssueYearTo: 2000,
    };
    const mutable = {
      ...query,
      licenseCodes: [...query.licenseCodes],
      accessModels: [...query.accessModels],
      frequencyCodes: [...query.frequencyCodes],
      reviewModels: [...query.reviewModels],
    };
    const criteria = nonFeeCriteriaFromQuery(mutable);
    expect(isValidNonFeeCriteria(criteria)).toBe(true);
    expect(nonFeeQuery(criteria)).toEqual(mutable);
  });

  it('empty checkboxes and lists do not restrict results', () => {
    expect(
      Object.values(nonFeeQuery(nonFeeCriteriaFromQuery())).every((value) => value === undefined),
    ).toBe(true);
  });

  it.each([
    { firstIssueYearFrom: '-1' },
    { firstIssueYearTo: '1960.5' },
    { firstIssueYearFrom: '2000', firstIssueYearTo: '1960' },
    { firstIssueYearTo: '2147483648' },
    { repositoryIds: ['0'] },
    { repositoryIds: ['10.5'] },
    { licenseCodes: ['FAKE'] },
    { accessModels: ['OA'] },
    { frequencyCodes: ['FAKE'] },
    { reviewModels: ['FAKE'] },
  ])('rejects invalid values %j', (invalid) => {
    expect(isValidNonFeeCriteria({ ...nonFeeCriteriaFromQuery(), ...invalid })).toBe(false);
  });

  it.each([
    { firstIssueYearFrom: '1960' },
    { firstIssueYearTo: '1960' },
    { firstIssueYearFrom: '0', firstIssueYearTo: '0' },
  ])('accepts partial and inclusive year bounds %j', (range) => {
    expect(isValidNonFeeCriteria({ ...nonFeeCriteriaFromQuery(), ...range })).toBe(true);
  });
});
