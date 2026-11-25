import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MultiValueSelectOption } from '../multi-value-select/multi-value-select';

@Component({
  selector: 'app-filter-checkbox-list',
  standalone: true,
  templateUrl: './filter-checkbox-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterCheckboxList {
  readonly options = input<readonly MultiValueSelectOption[]>([]);
  readonly value = input<readonly string[]>([]);
  readonly label = input.required<string>();
  readonly hideLabel = input(false);
  readonly plain = input(false);
  readonly sortAlphabetically = input(true);
  readonly searchLabel = input.required<string>();
  readonly searchFontSize = input<string | null>(null);
  readonly showSelection = input(false);
  readonly removeLabel = input('');
  readonly locale = input('pl');
  readonly valueChange = output<string[]>();
  readonly search = signal('');
  readonly selectedOptions = computed(() =>
    this.value().map((code) => ({
      code,
      label: this.options().find((option) => option.code === code)?.label ?? code,
    })),
  );
  readonly filteredOptions = computed(() => {
    const options = [...this.options()];
    for (const code of this.value()) {
      if (!options.some((option) => option.code === code)) {
        options.push({ code, label: code });
      }
    }
    const normalize = (value: string) =>
      value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .toLocaleLowerCase(this.locale());
    const search = normalize(this.search().trim());
    const filtered = options.filter((option) => normalize(option.label).includes(search));
    return this.sortAlphabetically()
      ? filtered.sort(
          (left, right) =>
            left.label.localeCompare(right.label, this.locale()) ||
            left.code.localeCompare(right.code),
        )
      : filtered;
  });

  toggle(code: string): void {
    this.valueChange.emit(
      this.value().includes(code)
        ? this.value().filter((selected) => selected !== code)
        : [...this.value(), code],
    );
  }
}
