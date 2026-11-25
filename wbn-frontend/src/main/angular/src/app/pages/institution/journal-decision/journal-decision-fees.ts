import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { formatDecisionPercentage } from './journal-decision-presentation';
import { presentDecisionFees } from './journal-decision-fee-presentation';

@Component({
  selector: 'app-journal-decision-fees',
  imports: [TranslocoPipe],
  templateUrl: './journal-decision-fees.html',
  styleUrl: './journal-decision-fees.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionFees {
  readonly card = input.required<JournalDecisionView>();
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly tiles = computed(() => {
    const card = this.card(),
      language = this.language();
    const ownFees = card.annual?.surveyYear === card.surveyYear ? card.annual.fees : [];
    const comparisons =
      card.benchmarksVisible === true && card.benchmarks?.surveyYear === card.surveyYear
        ? card.benchmarks.fees
        : [];
    return presentDecisionFees(ownFees, language).map(({ category, presentation }) => {
      const matches = comparisons?.filter((fee) => fee.category === category) ?? [];
      const comparison = matches.length === 1 ? matches[0].comparison : undefined;
      const percent =
        presentation.state !== 'unavailable' && comparison?.status === 'AVAILABLE'
          ? formatDecisionPercentage(comparison.value?.chargedPercent, language)
          : '—';
      return {
        category,
        presentation,
        percent: percent === '—' ? undefined : percent,
      };
    });
  });
}
