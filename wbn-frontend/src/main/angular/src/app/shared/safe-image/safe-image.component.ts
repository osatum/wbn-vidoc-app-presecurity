import {
  booleanAttribute,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { NgOptimizedImage } from '@angular/common';

type ImageLoadState = 'loading' | 'loaded' | 'error';

type ObjectFit = 'cover' | 'contain' | 'fill' | 'none' | 'scale-down';

@Component({
  selector: 'app-safe-image',
  standalone: true,
  imports: [NgOptimizedImage],
  templateUrl: './safe-image.component.html',
  styleUrl: './safe-image.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SafeImageComponent {
  readonly src = input.required<string | null | undefined>();

  /**
   * Jeżeli obrazek jest tylko dekoracyjny, przekaż alt="".
   * Jeżeli niesie informację, przekaż sensowny opis.
   */
  readonly alt = input('');

  /**
   * Używane do wyliczenia aspect-ratio kontenera.
   * Przy fill nie są przekazywane bezpośrednio do <img>.
   */
  readonly width = input(320, { transform: numberAttribute });
  readonly height = input(180, { transform: numberAttribute });

  /**
   * Ustaw true tylko dla obrazka istotnego dla LCP, np. hero image.
   * Nie ustawiaj priority=true dla 20 obrazków naraz.
   */
  readonly priority = input(false, { transform: booleanAttribute });

  /**
   * Dostosuj do layoutu listy/kafelków.
   */
  readonly sizes = input('100vw');

  readonly objectFit = input<ObjectFit>('fill');
  readonly objectPosition = input('center');

  /**
   * Opcjonalna etykieta dla pustego fallbacku.
   * Jeżeli null, fallback będzie ignorowany przez screen readery.
   */
  readonly fallbackAriaLabel = input<string | null>(null);

  readonly state = signal<ImageLoadState>('loading');

  readonly imageSrc = computed(() => {
    const value = this.src()?.trim();
    return value ? value : null;
  });

  readonly aspectRatio = computed(() => {
    const width = this.width();
    const height = this.height();

    return width > 0 && height > 0 ? `${width} / ${height}` : '1 / 1';
  });

  readonly normalizedFallbackAriaLabel = computed(() => {
    const value = this.fallbackAriaLabel()?.trim();
    return value ? value : null;
  });

  constructor() {
    effect(() => {
      this.state.set(this.imageSrc() ? 'loading' : 'error');
    });
  }

  onImageLoad(): void {
    this.state.set('loaded');
  }

  onImageError(): void {
    this.state.set('error');
  }
}
