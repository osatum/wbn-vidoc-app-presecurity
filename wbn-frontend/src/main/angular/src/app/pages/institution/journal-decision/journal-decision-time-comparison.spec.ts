import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoService, TranslocoLoader } from '@jsverse/transloco';
import { of } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { DecisionValue } from '../../../core/services/journal-decision-annual';
import {
  DecisionComparison,
  DecisionTimeBenchmark,
} from '../../../core/services/journal-decision-benchmarks';
import { JournalDecisionTimeComparison } from './journal-decision-time-comparison';

class Loader implements TranslocoLoader {
  getTranslation(lang: string) {
    return of(lang === 'en' ? en : pl);
  }
}

describe('Time comparison', () => {
  let fixture: ComponentFixture<JournalDecisionTimeComparison>;
  const benchmark: DecisionComparison<DecisionTimeBenchmark> = {
    status: 'AVAILABLE',
    value: { q1: 18.25, q3: 32.75, axis: { minimum: 13, maximum: 37 } },
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalDecisionTimeComparison],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            prodMode: true,
            reRenderOnLangChange: true,
          },
          loader: Loader,
        }),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(JournalDecisionTimeComparison);
    fixture.componentRef.setInput('label', 'Średni czas recenzji');
    fixture.componentRef.setInput('comparison', benchmark);
  });
  async function render(
    value: number | null,
    status: DecisionValue<number>['status'] = 'AVAILABLE',
  ) {
    fixture.componentRef.setInput('own', { status, value });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  const element = (selector: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(selector);

  it.each([
    [0, 'below'],
    [18, 'below'],
    [18.24999, 'below'],
    [18.25, 'within'],
    [25, 'within'],
    [32.75, 'within'],
    [32.75001, 'above'],
    [33, 'above'],
    [1000, 'above'],
  ])(
    'compares raw days %s to quartiles and preserves the shared axis (%s)',
    async (days, state) => {
      const original = JSON.stringify(benchmark);
      await render(days);
      expect(element('svg')?.getAttribute('data-time-state')).toBe(state);
      expect(element('[data-testid="decision-own-days"]')?.textContent).toContain(
        new Intl.NumberFormat('pl', { maximumFractionDigits: 20 }).format(days) + ' dni',
      );
      expect(element('[data-testid="decision-typical-time"]')?.textContent).toContain('18–33 dni');
      expect(
        [...fixture.nativeElement.querySelectorAll('.time-axis-labels span')].map(
          (e: HTMLElement) => e.textContent,
        ),
      ).toEqual(['13', '37']);
      expect(element('[data-testid="time-arrow-left"]') !== null).toBe(state === 'below');
      expect(element('[data-testid="time-dot"]') !== null).toBe(state === 'within');
      expect(element('[data-testid="time-arrow-right"]') !== null).toBe(state === 'above');
      expect(element('svg')?.getAttribute('aria-label')).toContain('dni');
      expect(JSON.stringify(benchmark)).toBe(original);
    },
  );
  it.each([null, { status: 'UNAVAILABLE', value: null }])(
    'omits only the comparison when unavailable (%s)',
    async (comparison) => {
      fixture.componentRef.setInput('comparison', comparison);
      await render(0);
      expect(element('[data-testid="decision-own-days"]')?.textContent).toContain('0 dni');
      expect(element('.time-label')?.textContent).toContain('Średni czas recenzji');
      expect(element('svg')).toBeNull();
      expect(element('[data-testid="decision-typical-time"]')).toBeNull();
      expect(element('.alert')).toBeNull();
    },
  );
  it.each(['MISSING', 'INVALID', 'UNRESOLVED'] as const)(
    'does not draw a marker for an unavailable own time (%s)',
    async (status) => {
      await render(null, status);
      expect(element('[data-testid="decision-own-days"]')?.textContent).toContain('Brak danych');
      expect(element('svg')).toBeNull();
      expect(element('[data-testid="decision-typical-time"]')).toBeNull();
    },
  );
  it.each([NaN, Infinity, -1])('does not draw invalid own days %s', async (days) => {
    await render(days);
    expect(element('svg')).toBeNull();
  });
  it('rounds a single typical value for display and relocalizes without changing raw geometry', async () => {
    fixture.componentRef.setInput('comparison', {
      status: 'AVAILABLE',
      value: { q1: 12.5, q3: 12.5, axis: { minimum: 11, maximum: 14 } },
    });
    await render(12.5);
    const position = element('circle')?.getAttribute('cx');
    expect(element('[data-testid="decision-typical-time"]')?.textContent).toContain('13 dni');
    expect(element('[data-testid="decision-typical-time"]')?.textContent).not.toContain('–');
    expect(element('.time-equal-range')).not.toBeNull();
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element('circle')?.getAttribute('cx')).toBe(position);
    expect(element('[data-testid="decision-typical-time"]')?.textContent).toContain('13 days');
    expect(element('svg')?.getAttribute('aria-label')).toContain('within');
  });
  it('keeps the zero-width typical interval visible for an all-zero group', async () => {
    fixture.componentRef.setInput('comparison', {
      status: 'AVAILABLE',
      value: { q1: 0, q3: 0, axis: { minimum: 0, maximum: 1 } },
    });
    await render(0);
    expect(element('circle')?.getAttribute('cx')).toBe('20');
    expect(element('.time-equal-range')?.getAttribute('x1')).toBe('20');
    expect(element('[data-testid="decision-typical-time"]')?.textContent).toContain('0 dni');
  });

  it('uses singular days for the own value and an equal rounded typical value', async () => {
    fixture.componentRef.setInput('comparison', {
      status: 'AVAILABLE',
      value: { q1: 1.2, q3: 1.2, axis: { minimum: 0, maximum: 3 } },
    });
    await render(1);
    expect(element('[data-testid="decision-own-days"]')?.textContent).toContain('1 dzień');
    expect(element('[data-testid="decision-typical-time"]')?.textContent).toContain('1 dzień');
  });
});
