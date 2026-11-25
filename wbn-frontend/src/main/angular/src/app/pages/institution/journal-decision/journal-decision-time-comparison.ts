import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DecisionValue } from '../../../core/services/journal-decision-annual';
import {
  DecisionComparison,
  DecisionTimeBenchmark,
} from '../../../core/services/journal-decision-benchmarks';
import {
  decisionDayTranslationKey,
  decisionTimeBenchmarkValue,
  decisionTimeRangeTranslationKey,
  decisionTimeValue,
  formatDecisionNumber,
  formatDecisionTimeRange,
} from './journal-decision-presentation';

@Component({
  selector: 'app-journal-decision-time-comparison',
  host: { '[class.own-only]': '!chart()' },
  imports: [TranslocoPipe],
  templateUrl: './journal-decision-time-comparison.html',
  styleUrl: './journal-decision-time-comparison.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionTimeComparison {
  readonly label = input.required<string>();
  readonly own = input<DecisionValue<number>>();
  readonly comparison = input<DecisionComparison<DecisionTimeBenchmark> | null>();
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly days = computed(() => decisionTimeValue(this.own()));
  readonly dayKey = computed(() => decisionDayTranslationKey(this.days()));
  readonly chart = computed(() => {
    const own = this.days(),
      comparison = this.comparison();
    const value = decisionTimeBenchmarkValue(comparison);
    if (own == null || !value) return undefined;
    const { q1, q3, axis } = value;
    const position = (days: number) =>
      20 + (280 * (days - axis.minimum)) / (axis.maximum - axis.minimum);
    const state = own < q1 ? 'below' : own > q3 ? 'above' : 'within';
    return {
      state,
      left: position(q1),
      right: position(q3),
      point: position(own),
      minimum: axis.minimum,
      maximum: axis.maximum,
      range: formatDecisionTimeRange(q1, q3, this.language()),
      dayKey: decisionTimeRangeTranslationKey(q1, q3),
    };
  });
  readonly formattedDays = computed(() => formatDecisionNumber(this.days(), this.language()));
  readonly formatNumber = (value: number) => formatDecisionNumber(value, this.language());
}
