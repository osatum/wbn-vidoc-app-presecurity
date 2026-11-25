import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewEncapsulation,
  computed,
  effect,
  input,
  viewChild,
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { API, create } from 'nouislider';
import { merge } from 'rxjs';

@Component({
  selector: 'app-journal-range-filter',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './journal-range-filter.html',
  styleUrl: './journal-range-filter.css',
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalRangeFilter {
  readonly minimum = input.required<FormControl<string>>();
  readonly maximum = input.required<FormControl<string>>();
  readonly minimumId = input.required<string>();
  readonly maximumId = input.required<string>();
  readonly label = input.required<string>();
  readonly labelClass = input('fs-6');
  readonly showBounds = input(true);
  readonly minimumLabel = input.required<string>();
  readonly maximumLabel = input.required<string>();
  readonly unrestrictedLabel = input.required<string>();
  readonly unavailableLabel = input('');
  readonly discrete = input(false);
  readonly choices = input<readonly number[]>([]);
  readonly disabled = input(false);
  readonly invalid = input(false);
  readonly describedBy = input<string | null>(null);
  readonly unit = input('');
  readonly points = computed(() =>
    [...new Set(this.choices())]
      .filter((value) => Number.isFinite(value) && value >= 0)
      .sort((first, second) => first - second),
  );
  private readonly sliderElement = viewChild<ElementRef<HTMLElement>>('slider');

  constructor() {
    effect((onCleanup) => {
      const element = this.sliderElement()?.nativeElement;
      const minimum = this.minimum();
      const maximum = this.maximum();
      const discrete = this.discrete();
      const points = this.points();
      const disabled = this.disabled();
      const label = this.label();
      const minimumLabel = this.minimumLabel();
      const maximumLabel = this.maximumLabel();
      const unit = this.unit();
      const describedBy = this.describedBy();
      const invalid = String(this.invalid());
      const last = discrete ? points.length - 1 : 100;
      if (!element || last <= 0) return;
      const actualValue = (coordinate: number) =>
        discrete ? points[Math.round(coordinate)] : Number(coordinate.toFixed(2));
      const display = (coordinate: number) => `${actualValue(coordinate)}${unit}`;
      const coordinate = (raw: string, fallback: number) => {
        const value = raw.trim() ? Number(raw.trim().replace(',', '.')) : NaN;
        if (!Number.isFinite(value)) return fallback;
        if (discrete) return points.includes(value) ? points.indexOf(value) : fallback;
        return Math.min(100, Math.max(0, value));
      };
      const positions = () => {
        const lower = coordinate(minimum.value, 0);
        const upper = coordinate(maximum.value, last);
        return [Math.min(lower, upper), Math.max(lower, upper)];
      };
      const slider: API = create(element, {
        start: positions(),
        connect: true,
        animate: false,
        step: discrete ? 1 : 0.01,
        keyboardMultiplier: 1,
        range: { min: 0, max: last },
        tooltips: [{ to: display }, { to: display }],
        handleAttributes: [
          {
            'aria-label': `${label}: ${minimumLabel}`,
            'aria-invalid': invalid,
            'aria-disabled': String(disabled),
            ...(describedBy ? { 'aria-describedby': describedBy } : {}),
          },
          {
            'aria-label': `${label}: ${maximumLabel}`,
            'aria-invalid': invalid,
            'aria-disabled': String(disabled),
            ...(describedBy ? { 'aria-describedby': describedBy } : {}),
          },
        ],
      });
      slider.on('update', (_values, _handle, values) => {
        element.querySelectorAll('.noUi-handle').forEach((handle, index) => {
          handle.setAttribute('aria-valuenow', String(actualValue(values[index])));
          handle.setAttribute('aria-valuetext', display(values[index]));
          handle.setAttribute('aria-valuemin', String(actualValue(index === 0 ? 0 : values[0])));
          handle.setAttribute('aria-valuemax', String(actualValue(index === 0 ? values[1] : last)));
        });
      });
      slider.on('slide', (_values, handle, values) => {
        const control = handle === 0 ? minimum : maximum;
        control.setValue(String(actualValue(values[handle])));
        control.markAsDirty();
        control.markAsTouched();
      });
      if (disabled) slider.disable();
      else slider.enable();
      const subscription = merge(minimum.valueChanges, maximum.valueChanges).subscribe(() =>
        slider.set(positions()),
      );
      onCleanup(() => {
        subscription.unsubscribe();
        slider.destroy();
      });
    });
  }

  isUnavailable(value: string): boolean {
    return !!value.trim() && !this.points().includes(Number(value));
  }
}
