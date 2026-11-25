import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { ARTICLE_PUBLISHED_FREQUENCY } from '../journal-ui-definitions';
import { countDecisionFeeCategories } from './journal-decision-fee-presentation';
import {
  availableDecisionValue,
  decisionDayTranslationKey,
  decisionPercentageValue,
  decisionTimeBenchmarkValue,
  decisionTimeRangeTranslationKey,
  decisionTimeValue,
  formatDecisionNumber,
  formatDecisionTimeRange,
  formatDecisionWholePercentage,
} from './journal-decision-presentation';

@Component({
  selector: 'app-journal-decision-section-preview',
  imports: [TranslocoPipe],
  templateUrl: './journal-decision-section-preview.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionSectionPreview {
  readonly card = input.required<JournalDecisionView>();
  readonly section = input.required<'time' | 'costs' | 'process'>();
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly review = computed(() => {
    const card = this.card();
    const days = decisionTimeValue(this.currentAnnual()?.times.review);
    if (days == null) return undefined;
    const comparison =
      card.benchmarksVisible === true && card.benchmarks?.surveyYear === card.surveyYear
        ? decisionTimeBenchmarkValue(card.benchmarks.times.review)
        : undefined;
    return {
      days: formatDecisionNumber(days, this.language()),
      dayKey: decisionDayTranslationKey(days),
      typical: comparison
        ? {
            range: formatDecisionTimeRange(comparison.q1, comparison.q3, this.language()),
            dayKey: decisionTimeRangeTranslationKey(comparison.q1, comparison.q3),
          }
        : undefined,
    };
  });
  readonly feeCount = computed(() => countDecisionFeeCategories(this.currentAnnual()?.fees));
  readonly feeCountKey = computed(() => {
    const count = this.feeCount();
    return count == null
      ? 'decisionCard.noData'
      : count === 0
        ? 'decisionCard.summary.noFees'
        : count === 1
          ? 'decisionCard.preview.oneFeeCategory'
          : count === 5
            ? 'decisionCard.preview.fiveFeeCategories'
            : 'decisionCard.preview.feeCategories';
  });
  readonly frequency = computed(() => {
    const code = availableDecisionValue(this.currentAnnual()?.process.frequency);
    return code ? ARTICLE_PUBLISHED_FREQUENCY[code] : undefined;
  });
  readonly rejection = computed(() => {
    const value = decisionPercentageValue(this.currentAnnual()?.process.rejectionRate);
    return value == null ? undefined : formatDecisionWholePercentage(value, this.language());
  });

  private currentAnnual() {
    const card = this.card();
    return card.annualStatus === 'AVAILABLE' && card.annual?.surveyYear === card.surveyYear
      ? card.annual
      : undefined;
  }
}
