import { decisionFee } from './journal-decision-fees.fixtures';
import { presentDecisionFee } from './journal-decision-fee-presentation';

describe('Fee presentation from the validated annual API', () => {
  it.each(['NO_FEE', 'ONE_FEE', 'VARIOUS_FEE'] as const)(
    'keeps validated free declaration %s',
    (type) => {
      const result = presentDecisionFee(decisionFee({ type }), 'pl');
      expect(result.state).toBe('noFee');
    },
  );
  it.each(['ONE_FEE', 'VARIOUS_FEE'] as const)(
    'formats both selected bounds for %s without considering unselected source prices',
    (type) => {
      const fee = decisionFee({
        type,
        charged: true,
        minimum: 50,
        maximum: 150,
        currency: 'EUR',
        effectiveVariant: 'INTERNAL_AUTHORS',
        sourceRanges: [{ minimum: 50, maximum: 150, currency: 'EUR' }],
      });
      const before = JSON.stringify(fee);
      expect(presentDecisionFee(fee, 'en')).toEqual({ state: 'amount', amount: '50–150 EUR' });
      expect(JSON.stringify(fee)).toBe(before);
    },
  );
  it('keeps a single amount, a zero lower bound and rounding only at presentation', () => {
    expect(
      presentDecisionFee(
        decisionFee({
          type: 'ONE_FEE',
          charged: true,
          minimum: 1500,
          maximum: 1500,
          currency: 'PLN',
        }),
        'pl',
      ),
    ).toEqual({ state: 'amount', amount: '1500 PLN' });
    expect(
      presentDecisionFee(
        decisionFee({
          type: 'VARIOUS_FEE',
          charged: true,
          minimum: 0,
          maximum: 100,
          currency: 'USD',
        }),
        'en',
      ),
    ).toEqual({ state: 'amount', amount: '0–100 USD' });
    const fee = decisionFee({
      type: 'VARIOUS_FEE',
      charged: true,
      minimum: 20.126,
      maximum: 120.999,
      currency: 'PLN',
    });
    expect(presentDecisionFee(fee, 'pl')).toEqual({ state: 'amount', amount: '20,13–121 PLN' });
    expect(fee.minimum).toBe(20.126);
    expect(fee.maximum).toBe(120.999);
  });
  it('does not round tiny positive amounts into zero or distinct bounds into an identical range', () => {
    expect(
      presentDecisionFee(
        decisionFee({
          type: 'ONE_FEE',
          charged: true,
          minimum: 0.0005,
          maximum: 0.0005,
          currency: 'PLN',
        }),
        'en',
      ),
    ).toEqual({ state: 'amount', amount: '0.0005 PLN' });
    expect(
      presentDecisionFee(
        decisionFee({
          type: 'VARIOUS_FEE',
          charged: true,
          minimum: 1.001,
          maximum: 1.002,
          currency: 'PLN',
        }),
        'pl',
      ),
    ).toEqual({ state: 'amount', amount: '1,001–1,002 PLN' });
  });
  it.each(['MISSING', 'INVALID', 'UNRESOLVED'] as const)(
    'does not interpret %s as free or a partial positive declaration',
    (status) => {
      expect(
        presentDecisionFee(
          decisionFee({ status, type: 'COUNTRY_SPECIFIC_FEE', charged: null }),
          'pl',
        ),
      ).toEqual({ state: 'unavailable' });
    },
  );
  it('rejects an unavailable old conversion payload and keeps Other APC free from amounts', () => {
    expect(
      presentDecisionFee(
        decisionFee({ type: 'VARIOUS_FEE', status: 'RATES_UNAVAILABLE', charged: true }),
        'pl',
      ),
    ).toEqual({ state: 'unavailable' });
    const other = decisionFee({
      category: 'OTHER',
      type: 'COUNTRY_SPECIFIC_FEE',
      charged: true,
      minimum: 100,
      maximum: 200,
      currency: 'USD',
    });
    expect(presentDecisionFee(other, 'en')).toEqual({ state: 'present' });
    expect(presentDecisionFee({ ...other, charged: false }, 'en')).toEqual({ state: 'noFee' });
    expect(presentDecisionFee({ ...other, status: 'UNRESOLVED', charged: null }, 'en')).toEqual({
      state: 'unavailable',
    });
  });
  it.each([
    { minimum: null },
    { maximum: null },
    { minimum: -1 },
    { minimum: Infinity },
    { maximum: NaN },
    { minimum: 20, maximum: 10 },
    { currency: null },
    { currency: '???' },
  ])('rejects an unusable displayed amount %s', (override) => {
    expect(
      presentDecisionFee(
        decisionFee({
          type: 'ONE_FEE',
          charged: true,
          minimum: 10,
          maximum: 100,
          currency: 'EUR',
          ...override,
        }),
        'en',
      ),
    ).toEqual({ state: 'unavailable' });
  });
  it('does not invent an amount for missing data', () => {
    expect(presentDecisionFee(undefined, 'pl')).toEqual({ state: 'unavailable' });
  });
  it('shows both country variants in a stable order and rejects missing or inconsistent variants', () => {
    const fee = decisionFee({
      type: 'COUNTRY_SPECIFIC_FEE',
      charged: true,
      selectionStatus: 'LEGACY',
      variants: [
        {
          variant: 'EXTERNAL_AUTHORS',
          status: 'AVAILABLE',
          charged: true,
          minimum: 100,
          maximum: 200,
          currency: 'USD',
        },
        {
          variant: 'INTERNAL_AUTHORS',
          status: 'AVAILABLE',
          charged: false,
          minimum: 0,
          maximum: 0,
          currency: null,
        },
      ],
    });
    expect(presentDecisionFee(fee, 'en')).toEqual({
      state: 'variants',
      variants: [
        { variant: 'INTERNAL_AUTHORS', amount: null },
        { variant: 'EXTERNAL_AUTHORS', amount: '100–200 USD' },
      ],
    });
    expect(presentDecisionFee({ ...fee, variants: fee.variants.slice(0, 1) }, 'en')).toEqual({
      state: 'unavailable',
    });
    expect(
      presentDecisionFee({ ...fee, variants: [fee.variants[0], fee.variants[0]] }, 'en'),
    ).toEqual({ state: 'unavailable' });
    expect(presentDecisionFee({ ...fee, charged: false }, 'en')).toEqual({ state: 'unavailable' });
  });
  it('shows two explicit free variants and does not label them as the Polish author selection', () => {
    const variants = ['INTERNAL_AUTHORS', 'EXTERNAL_AUTHORS'].map((variant) => ({
      variant,
      status: 'AVAILABLE',
      charged: false,
      minimum: 0,
      maximum: 0,
      currency: null,
    })) as import('../../../core/services/journal-decision-annual').DecisionFeeVariant[];
    expect(
      presentDecisionFee(decisionFee({ type: 'COUNTRY_SPECIFIC_FEE', variants }), 'pl'),
    ).toEqual({
      state: 'variants',
      variants: [
        { variant: 'INTERNAL_AUTHORS', amount: null },
        { variant: 'EXTERNAL_AUTHORS', amount: null },
      ],
    });
  });
  it('uses source currencies instead of a converted amount left in an older payload', () => {
    expect(
      presentDecisionFee(
        decisionFee({
          type: 'VARIOUS_FEE',
          charged: true,
          minimum: 0,
          maximum: 614.82,
          currency: 'PLN',
          sourceRanges: [
            { minimum: 0, maximum: 100, currency: 'EUR' },
            { minimum: 0, maximum: 200, currency: 'TRY' },
          ],
        }),
        'pl',
      ),
    ).toEqual({ state: 'multipleCurrencies' });
  });
});
