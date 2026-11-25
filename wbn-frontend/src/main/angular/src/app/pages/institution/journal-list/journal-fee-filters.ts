import { ChangeDetectionStrategy, Component, effect, input } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { JournalSearchCounts } from '../../../core/backend';
import { FEE_FILTERS, FeeControls } from './journal-fee-criteria';

@Component({
  selector: 'app-journal-fee-filters',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe],
  templateUrl: './journal-fee-filters.html',
  styleUrl: './journal-fee-filters.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalFeeFilters {
  readonly form = input.required<FormGroup<FeeControls>>();
  readonly counts = input<JournalSearchCounts | null>(null);
  readonly hasValidationError = input(false);
  readonly filters = [...FEE_FILTERS].sort(
    (first, second) =>
      ['SUBMISSION', 'PUBLICATION', 'OPEN_ACCESS', 'RAPID_REVIEW'].indexOf(first.category) -
      ['SUBMISSION', 'PUBLICATION', 'OPEN_ACCESS', 'RAPID_REVIEW'].indexOf(second.category),
  );

  constructor() {
    effect((onCleanup) => {
      const form = this.form();
      const sync = () => {
        for (const { flag, maximum } of this.filters) {
          const control = form.controls[maximum];
          if (form.controls[flag].value) {
            control.setValue('', { emitEvent: false });
            control.disable({ emitEvent: false });
          } else {
            control.enable({ emitEvent: false });
          }
        }
      };
      sync();
      const subscription = form.valueChanges.subscribe(sync);
      onCleanup(() => subscription.unsubscribe());
    });
  }
}
