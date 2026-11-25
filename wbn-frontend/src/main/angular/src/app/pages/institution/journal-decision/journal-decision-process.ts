import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asapScheduler, filter, observeOn } from 'rxjs';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { JournalNameService } from '../../../core/services/journal-name-service';
import {
  availableDecisionValue,
  decisionPercentageValue,
  formatDecisionNumber,
  formatDecisionWholePercentage,
} from './journal-decision-presentation';

@Component({
  selector: 'app-journal-decision-process',
  imports: [TranslocoPipe],
  templateUrl: './journal-decision-process.html',
  styleUrl: './journal-decision-fields.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionProcess {
  readonly card = input.required<JournalDecisionView>();
  private readonly names = inject(JournalNameService);
  private readonly transloco = inject(TranslocoService);
  private readonly translationsLoaded = toSignal(
    this.transloco.events$.pipe(
      filter((event) => event.type === 'translationLoadSuccess'),
      observeOn(asapScheduler),
    ),
    { initialValue: null },
  );
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly process = computed(() =>
    this.card().annual?.surveyYear === this.card().surveyYear
      ? this.card().annual?.process
      : undefined,
  );
  readonly since = computed(() => {
    this.translationsLoaded();
    this.language();
    const first = availableDecisionValue(this.process()?.firstIssueYear),
      continued = availableDecisionValue(this.process()?.continuedYear);
    if (first == null && continued == null) return this.transloco.translate('decisionCard.noData');
    return this.transloco.translate(
      `decisionCard.process.${first == null ? 'continuedOnly' : continued == null ? 'firstOnly' : first === continued ? 'continuous' : 'restarted'}`,
      { first, continued },
    );
  });
  readonly frequency = computed(() => {
    this.translationsLoaded();
    this.language();
    const code = availableDecisionValue(this.process()?.frequency);
    return code
      ? this.names.getJournalDictionaryItem(code)
      : this.transloco.translate('decisionCard.noData');
  });
  readonly aheadOfPrint = computed(() => availableDecisionValue(this.process()?.aheadOfPrint));
  readonly publishingBreak = computed(() =>
    availableDecisionValue(this.process()?.publishingBreak),
  );
  readonly rejection = computed(() => {
    this.language();
    const own = decisionPercentageValue(this.process()?.rejectionRate);
    return own == null ? undefined : formatDecisionWholePercentage(own, this.language());
  });
  readonly typicalRejection = computed(() => {
    const card = this.card(),
      comparison = card.benchmarks?.rejectionRate;
    const value =
      card.benchmarksVisible === true &&
      card.benchmarks?.surveyYear === card.surveyYear &&
      comparison?.status === 'AVAILABLE'
        ? comparison.value
        : null;
    if (
      !this.rejection() ||
      !value ||
      !Number.isFinite(value.q1) ||
      !Number.isFinite(value.q3) ||
      value.q1 < 0 ||
      value.q3 > 100 ||
      value.q1 > value.q3
    )
      return undefined;
    return value.q1 === value.q3
      ? formatDecisionWholePercentage(value.q1, this.language())
      : `${formatDecisionNumber(Math.floor(value.q1), this.language())}–${formatDecisionNumber(Math.ceil(value.q3), this.language())}%`;
  });
}
