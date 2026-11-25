import { DecisionValue } from '../../../core/services/journal-decision-annual';
import {
  DecisionComparison,
  DecisionTimeBenchmark,
} from '../../../core/services/journal-decision-benchmarks';

export function availableDecisionValue<T>(
  source: DecisionValue<T> | null | undefined,
): T | undefined {
  return source?.status === 'AVAILABLE' && source.value != null ? source.value : undefined;
}

export function decisionPercentageValue(
  source: DecisionValue<number> | null | undefined,
): number | undefined {
  const value = availableDecisionValue(source);
  return value != null && Number.isFinite(value) && value >= 0 && value <= 100 ? value : undefined;
}

export function formatDecisionWholePercentage(value: number | undefined, language: string): string {
  return value == null || !Number.isFinite(value) || value < 0 || value > 100
    ? '—'
    : `${formatDecisionNumber(Math.round(value), language)}%`;
}

export function formatDecisionNumber(value: number | null | undefined, language: string): string {
  return value == null || !Number.isFinite(value)
    ? '—'
    : new Intl.NumberFormat(language, { maximumFractionDigits: 20 }).format(value);
}

export function formatDecisionPercentage(
  value: number | null | undefined,
  language: string,
): string {
  if (value == null || !Number.isFinite(value) || value < 0 || value > 100) return '—';
  const rounded = Math.round(value);
  if (value > 0 && rounded === 0) return '<1%';
  if (value < 100 && rounded === 100) return '>99%';
  return `${formatDecisionNumber(rounded, language)}%`;
}

export function formatDecisionTimeRange(q1: number, q3: number, language: string): string {
  return q1 === q3
    ? formatDecisionNumber(Math.round(q1), language)
    : `${formatDecisionNumber(Math.floor(q1), language)}–${formatDecisionNumber(Math.ceil(q3), language)}`;
}

export function decisionDayTranslationKey(value: number | undefined): string {
  return value === 1 ? 'decisionCard.time.day' : 'decisionCard.time.days';
}

export function decisionTimeRangeTranslationKey(q1: number, q3: number): string {
  return decisionDayTranslationKey(q1 === q3 ? Math.round(q1) : undefined);
}

export function decisionTimeValue(
  source: DecisionValue<number> | null | undefined,
): number | undefined {
  const value = availableDecisionValue(source);
  return value != null && Number.isFinite(value) && value >= 0 ? value : undefined;
}

export function decisionTimeBenchmarkValue(
  comparison: DecisionComparison<DecisionTimeBenchmark> | null | undefined,
): DecisionTimeBenchmark | undefined {
  const value = comparison?.status === 'AVAILABLE' ? comparison.value : null;
  if (!value?.axis) return undefined;
  const { q1, q3, axis } = value;
  return [q1, q3, axis.minimum, axis.maximum].every(Number.isFinite) &&
    axis.minimum >= 0 &&
    axis.maximum > axis.minimum &&
    q1 >= axis.minimum &&
    q3 <= axis.maximum &&
    q1 <= q3
    ? value
    : undefined;
}
