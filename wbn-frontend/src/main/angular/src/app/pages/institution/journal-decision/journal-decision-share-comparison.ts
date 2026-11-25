import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DecisionValue } from '../../../core/services/journal-decision-annual';
import { DecisionComparison } from '../../../core/services/journal-decision-benchmarks';
import {
  decisionPercentageValue,
  formatDecisionWholePercentage,
} from './journal-decision-presentation';

@Component({
  selector: 'app-journal-decision-share-comparison',
  imports: [TranslocoPipe],
  templateUrl: './journal-decision-share-comparison.html',
  styleUrl: './journal-decision-share-comparison.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionShareComparison {
  readonly label = input.required<string>();
  readonly own = input<DecisionValue<number>>();
  readonly comparison = input<DecisionComparison<{ median: number }> | null>();
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly value = computed(() => decisionPercentageValue(this.own()));
  readonly formatted = computed(() =>
    this.value() == null ? undefined : formatDecisionWholePercentage(this.value(), this.language()),
  );
  readonly chart = computed(() => {
    const own = this.value(),
      comparison = this.comparison();
    const median = comparison?.status === 'AVAILABLE' ? comparison.value?.median : undefined;
    return own == null || median == null || !Number.isFinite(median) || median < 0 || median > 100
      ? undefined
      : {
          point: own,
          median,
          formattedMedian: formatDecisionWholePercentage(median, this.language()),
        };
  });
}
