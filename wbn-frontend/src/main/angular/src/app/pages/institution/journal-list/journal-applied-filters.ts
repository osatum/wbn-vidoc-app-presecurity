import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { FilterChipLabels, JournalFilterChip } from './journal-filter-criteria';

@Component({
  selector: 'app-journal-applied-filters',
  imports: [TranslocoPipe],
  templateUrl: './journal-applied-filters.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalAppliedFilters {
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  private readonly translations = toSignal(this.transloco.selectTranslation());
  readonly chips = input.required<readonly JournalFilterChip[]>();
  readonly labels = input<FilterChipLabels>({});
  readonly pendingChanges = input(false);
  readonly remove = output<JournalFilterChip>();
  readonly clear = output<void>();
  readonly displayedChips = computed(() => {
    this.translations();
    const language = this.language();
    const format = (value: string) =>
      language?.startsWith('pl') ? value.replace('.', ',') : value.replace(',', '.');
    return this.chips().map((chip) => {
      const label = this.transloco.translate(chip.labelKey);
      let value = chip.value;
      if (chip.selection && value) value = this.labels()[chip.selection]?.[value] ?? value;
      if (chip.noFee) value = this.transloco.translate('page.institution.journalList.chips.noFee');
      else if (chip.minimum !== undefined || chip.maximum !== undefined) {
        const range =
          chip.minimum !== undefined && chip.maximum !== undefined
            ? 'range'
            : chip.minimum !== undefined
              ? 'minimum'
              : 'maximum';
        value = this.transloco.translate(`page.institution.journalList.chips.${range}`, {
          minimum: chip.minimum === undefined ? '' : format(chip.minimum),
          maximum: chip.maximum === undefined ? '' : format(chip.maximum),
        });
        if (chip.unit)
          value += ` ${this.transloco.translate(`page.institution.journalList.chips.units.${chip.unit}`)}`;
      }
      return { chip, label: value === undefined ? label : `${label}: ${value}` };
    });
  });
}
