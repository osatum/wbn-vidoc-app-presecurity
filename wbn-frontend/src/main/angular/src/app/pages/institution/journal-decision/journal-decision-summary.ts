import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { presentDecisionFees } from './journal-decision-fee-presentation';
import { JournalDecisionSectionPreview } from './journal-decision-section-preview';
import {
  decisionDayTranslationKey,
  decisionTimeBenchmarkValue,
  decisionTimeRangeTranslationKey,
  decisionTimeValue,
  formatDecisionNumber,
  formatDecisionTimeRange,
} from './journal-decision-presentation';

@Component({
  selector: 'app-journal-decision-summary',
  imports: [TranslocoPipe, JournalDecisionSectionPreview],
  templateUrl: './journal-decision-summary.html',
  styleUrl: './journal-decision-summary.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionSummary {
  readonly card = input.required<JournalDecisionView>();
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly fees = computed(() => {
    const fees = presentDecisionFees(this.currentAnnual()?.fees, this.language(), {
      useGrouping: true,
    });
    return {
      entries: fees.filter(({ presentation }) =>
        presentation.state === 'variants'
          ? presentation.variants.some((variant) => variant.amount != null)
          : presentation.state !== 'noFee' && presentation.state !== 'unavailable',
      ),
      unavailable: fees.some(({ presentation }) => presentation.state === 'unavailable'),
    };
  });
  readonly publication = computed(() => {
    const card = this.card();
    const days = decisionTimeValue(this.currentAnnual()?.times.publication);
    const comparison =
      card.benchmarksVisible === true && card.benchmarks?.surveyYear === card.surveyYear
        ? card.benchmarks.times.publication
        : undefined;
    const range = decisionTimeBenchmarkValue(comparison);
    const typical =
      days != null && range
        ? formatDecisionTimeRange(range.q1, range.q3, this.language())
        : undefined;
    return {
      days: days == null ? undefined : formatDecisionNumber(days, this.language()),
      dayKey: decisionDayTranslationKey(days),
      typical,
      typicalDayKey: range ? decisionTimeRangeTranslationKey(range.q1, range.q3) : undefined,
    };
  });
  private currentAnnual() {
    const card = this.card();
    return card.annualStatus === 'AVAILABLE' && card.annual?.surveyYear === card.surveyYear
      ? card.annual
      : undefined;
  }
}
