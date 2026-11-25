import { feeCriteriaFromQuery, feeQuery, isValidFeeCriteria } from './journal-fee-criteria';

describe('journal fee criteria', () => {
  it('restores and emits independent fee categories including an actual zero', () => {
    const query = {
      otherFeesFreeOnly: true,
      openAccessFeeFreeOnly: true,
      publicationFeeMaxPln: 0,
      rapidReviewFeeMaxPln: 123.45,
      submissionFeeFreeOnly: true,
    };
    const criteria = feeCriteriaFromQuery(query);
    expect(criteria.publicationFeeMaxPln).toBe('0');
    expect(isValidFeeCriteria(criteria)).toBe(true);
    expect(feeQuery(criteria)).toMatchObject(query);
  });

  it('does not send empty or unchecked restrictions', () => {
    const query = feeQuery(feeCriteriaFromQuery());
    expect(Object.values(query).every((value) => value === undefined)).toBe(true);
  });

  it.each([
    '-1',
    'NaN',
    'Infinity',
    '1e2',
    '12,3,4',
    '1.2.3',
    'text',
    '9007199254740990.5',
    '0.10000000000000001',
    '0.' + '0'.repeat(323) + '1',
  ])('rejects invalid limits: %s', (value) => {
    expect(isValidFeeCriteria({ ...feeCriteriaFromQuery(), publicationFeeMaxPln: value })).toBe(
      false,
    );
  });

  it.each(['0', '123,45', '.5', '0.0001'])(
    'accepts non-negative literal PLN limits: %s',
    (value) => {
      const criteria = { ...feeCriteriaFromQuery(), publicationFeeMaxPln: value };
      expect(isValidFeeCriteria(criteria)).toBe(true);
      expect(feeQuery(criteria).publicationFeeMaxPln).toBe(Number(value.replace(',', '.')));
    },
  );

  it('rejects a no-fee checkbox together with a limit in the same category', () => {
    expect(
      isValidFeeCriteria({
        ...feeCriteriaFromQuery(),
        publicationFeeFreeOnly: true,
        publicationFeeMaxPln: '0',
      }),
    ).toBe(false);
  });
});
