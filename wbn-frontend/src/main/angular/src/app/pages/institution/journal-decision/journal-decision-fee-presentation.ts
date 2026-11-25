import {
  DecisionFeeView,
  DecisionFeeVariant,
} from '../../../core/services/journal-decision-annual';
type VariantPresentation = { variant: DecisionFeeVariant['variant']; amount: string | null };
type FeeFormatOptions = Pick<Intl.NumberFormatOptions, 'useGrouping'>;
const FEE_CATEGORIES: readonly DecisionFeeView['category'][] = [
  'SUBMISSION',
  'PUBLICATION',
  'OPEN_ACCESS',
  'RAPID_REVIEW',
  'OTHER',
];
export type DecisionFeePresentation =
  | { state: 'unavailable' | 'noFee' | 'present' | 'multipleCurrencies' }
  | { state: 'amount'; amount: string }
  | { state: 'variants'; variants: VariantPresentation[] };

export function countDecisionFeeCategories(
  fees: DecisionFeeView[] | undefined,
): number | undefined {
  let count = 0;
  for (const category of FEE_CATEGORIES) {
    const matches = fees?.filter((fee) => fee.category === category) ?? [];
    if (!matches.length) return undefined;
    const declarations = matches.map((fee) =>
      fee.status !== 'AVAILABLE' || fee.type == null
        ? undefined
        : fee.type === 'NO_FEE'
          ? false
          : (fee.charged ?? undefined),
    );
    if (declarations.some((value) => value == null) || new Set(declarations).size !== 1)
      return undefined;
    if (declarations[0]) count++;
  }
  return count;
}

export function presentDecisionFees(
  fees: DecisionFeeView[] | undefined,
  language: string,
  options: FeeFormatOptions = {},
) {
  return FEE_CATEGORIES.map((category) => {
    const matches = fees?.filter((fee) => fee.category === category) ?? [];
    return {
      category,
      presentation: presentDecisionFee(
        matches.length === 1 ? matches[0] : undefined,
        language,
        options,
      ),
    };
  });
}

/** Prezentacja P2 zachowuje waluty oraz oba warianty, niezależnie od perspektywy P1. */
export function presentDecisionFee(
  fee: DecisionFeeView | undefined,
  language: string,
  options: FeeFormatOptions = {},
): DecisionFeePresentation {
  if (!fee || fee.type == null || fee.status !== 'AVAILABLE' || fee.charged == null)
    return { state: 'unavailable' };
  if (fee.category === 'OTHER') return { state: fee.charged ? 'present' : 'noFee' };
  if (fee.type === 'COUNTRY_SPECIFIC_FEE') {
    const source = fee.variants ?? [];
    const order = ['INTERNAL_AUTHORS', 'EXTERNAL_AUTHORS'] as const;
    if (
      source.length !== 2 ||
      order.some((key) => source.filter((v) => v.variant === key).length !== 1) ||
      source.some((v) => v.status !== 'AVAILABLE' || v.charged == null) ||
      source.some((v) => v.charged) !== fee.charged
    )
      return { state: 'unavailable' };
    const variants: VariantPresentation[] = [];
    for (const key of order) {
      const value = source.find((v) => v.variant === key)!;
      if (!value.charged) {
        if (value.minimum !== 0 || value.maximum !== 0) return { state: 'unavailable' };
        variants.push({ variant: key, amount: null });
      } else {
        const amount = formatAmount(
          value.minimum,
          value.maximum,
          value.currency,
          language,
          options,
        );
        if (!amount) return { state: 'unavailable' };
        variants.push({ variant: key, amount });
      }
    }
    return { state: 'variants', variants };
  }
  if (!fee.charged) return { state: 'noFee' };
  if (
    fee.type === 'VARIOUS_FEE' &&
    new Set((fee.sourceRanges ?? []).map((r) => r.currency).filter(Boolean)).size > 1
  ) {
    const valid = fee.sourceRanges.every(
      (r) =>
        Number.isFinite(r.minimum) &&
        Number.isFinite(r.maximum) &&
        r.minimum >= 0 &&
        r.maximum >= r.minimum &&
        (r.maximum === 0 || !!r.currency?.match(/^[A-Z]{3}$/)),
    );
    return { state: valid ? 'multipleCurrencies' : 'unavailable' };
  }
  const amount = formatAmount(fee.minimum, fee.maximum, fee.currency, language, options);
  return amount ? { state: 'amount', amount } : { state: 'unavailable' };
}

function formatAmount(
  minimum: number | null,
  maximum: number | null,
  currency: string | null,
  language: string,
  options: FeeFormatOptions,
): string | undefined {
  if (
    minimum == null ||
    maximum == null ||
    !Number.isFinite(minimum) ||
    !Number.isFinite(maximum) ||
    minimum < 0 ||
    maximum < minimum ||
    maximum <= 0 ||
    !currency ||
    !/^[A-Z]{3}$/.test(currency)
  )
    return undefined;
  const money = new Intl.NumberFormat(language, { ...options, maximumFractionDigits: 2 });
  // Dodatnia kwota ani różne granice nie mogą zniknąć wskutek zaokrąglenia prezentacji.
  const needsPrecision =
    (minimum > 0 && money.format(minimum) === money.format(0)) ||
    (maximum > 0 && money.format(maximum) === money.format(0)) ||
    (minimum !== maximum && money.format(minimum) === money.format(maximum));
  const precise = needsPrecision
    ? new Intl.NumberFormat(language, { ...options, maximumFractionDigits: 20 })
    : money;
  const format = (value: number) => precise.format(value);
  return `${minimum === maximum ? format(minimum) : `${format(minimum)}–${format(maximum)}`} ${currency}`;
}
