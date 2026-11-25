import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { formatDecisionPercentage } from '../../institution/journal-decision/journal-decision-presentation';

export interface JournalIcvRating {
  year?: number | null;
  status?: string | null;
  value?: number | null;
}

@Component({
  selector: '[app-journal-icv-card]',
  imports: [TranslocoPipe, NgbAccordionModule],
  templateUrl: './journal-icv-card.html',
  styleUrls: [
    './journal-icv-card.css',
    '../../../shared/keyboard-focus.css',
    '../../../shared/journal-disclosure-accordion.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalIcvCard {
  readonly year = input<number | null>();
  readonly value = input<number | null>();
  readonly status = input<string | null | undefined>('INDEXED');
  readonly reason = input<string>();
  readonly prefix = input<'decision' | 'journal'>('decision');
  readonly history = input<readonly JournalIcvRating[]>([]);
  readonly historyOpen = input(false);
  readonly historyOpenChange = output<boolean>();
  readonly comparisonSource = input<JournalDecisionView | null>();
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly sortedHistory = computed(() =>
    [...(this.history() ?? [])].sort((first, second) => (second.year ?? 0) - (first.year ?? 0)),
  );
  readonly historyToggleId = computed(() =>
    this.prefix() === 'decision' ? 'decision-history-toggle' : 'journal-icv-history-toggle',
  );
  readonly comparison = computed(() => {
    const source = this.comparisonSource();
    const comparison = source?.benchmarks?.icv;
    const own = this.value();
    if (
      this.status() !== 'INDEXED' ||
      own == null ||
      !Number.isFinite(own) ||
      own < 0 ||
      source?.benchmarksVisible !== true ||
      source.icvStatus !== 'AVAILABLE' ||
      source.surveyYear !== this.year() ||
      source.icv !== own ||
      source.benchmarks?.surveyYear !== this.year() ||
      comparison?.status !== 'AVAILABLE' ||
      !comparison.value
    )
      return undefined;
    const value = comparison.value;
    return [value.lowerPercent, value.equalPercent].every(
      (percent) => Number.isFinite(percent) && percent >= 0 && percent <= 100,
    ) && value.lowerPercent + value.equalPercent <= 100 + 1e-9
      ? value
      : undefined;
  });
  readonly formatIcvNumber = (value: number | null | undefined) =>
    value == null || !Number.isFinite(value)
      ? '—'
      : new Intl.NumberFormat(this.language(), {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        }).format(value);
  readonly formatPercentage = (value: number) => formatDecisionPercentage(value, this.language());
}
