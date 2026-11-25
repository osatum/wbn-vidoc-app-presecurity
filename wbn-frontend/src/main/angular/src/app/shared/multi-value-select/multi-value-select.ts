import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

export interface MultiValueSelectOption {
  code: string;
  label: string;
  count?: number;
}

@Component({
  selector: 'app-multi-value-select',
  standalone: true,
  templateUrl: './multi-value-select.html',
  styleUrl: './multi-value-select.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MultiValueSelect {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly selection =
    viewChild<ElementRef<HTMLSelectElement | HTMLButtonElement>>('selection');
  readonly options = input<readonly MultiValueSelectOption[]>([]);
  readonly value = input<readonly string[]>([]);
  readonly label = input.required<string>();
  readonly removeLabel = input.required<string>();
  readonly controlHeight = input<string | null>(null);
  readonly showSelection = input(true);
  readonly searchable = input(false);
  readonly searchLabel = input('');
  readonly clearLabel = input('');
  readonly emptyLabel = input('');
  readonly valueChange = output<string[]>();
  readonly search = signal('');
  readonly isOpen = signal(false);
  readonly panelId = 'multi-value-select-' + crypto.randomUUID();
  readonly filteredOptions = computed(() => {
    const search = this.search().trim().toLocaleLowerCase();
    return this.options().filter((option) => option.label.toLocaleLowerCase().includes(search));
  });

  @HostListener('document:click', ['$event'])
  closeOutside(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.isOpen.set(false);
  }

  readonly availableOptions = computed(() => {
    const selected = new Set(this.value().map((code) => code.toLowerCase()));
    return this.options().filter((option) => !selected.has(option.code.toLowerCase()));
  });

  readonly selectedOptions = computed(() =>
    this.value().map((code) => ({
      code,
      label:
        this.options().find((option) => option.code.toLowerCase() === code.toLowerCase())?.label ??
        code,
    })),
  );

  select(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const selected = this.availableOptions().find((option) => option.code === select.value);
    select.value = '';
    if (selected) {
      this.valueChange.emit([...this.value(), selected.code]);
    }
  }

  remove(code: string): void {
    this.valueChange.emit(this.value().filter((selected) => selected !== code));
  }

  isSelected(code: string): boolean {
    return this.value().some((selected) => selected.toLowerCase() === code.toLowerCase());
  }

  toggle(code: string): void {
    if (this.isSelected(code)) {
      this.valueChange.emit(
        this.value().filter((selected) => selected.toLowerCase() !== code.toLowerCase()),
      );
    } else if (this.options().some((option) => option.code === code)) {
      this.valueChange.emit([...this.value(), code]);
    }
  }

  focusControl(): void {
    this.selection()?.nativeElement.focus();
  }
}
