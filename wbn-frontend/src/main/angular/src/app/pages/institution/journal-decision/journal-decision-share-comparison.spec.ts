import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { JournalDecisionShareComparison } from './journal-decision-share-comparison';

class Loader implements TranslocoLoader {
  getTranslation(lang: string) {
    return of(lang === 'en' ? en : pl);
  }
}
describe('International share comparison', () => {
  let fixture: ComponentFixture<JournalDecisionShareComparison>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalDecisionShareComparison],
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
    fixture = TestBed.createComponent(JournalDecisionShareComparison);
    fixture.componentRef.setInput('label', 'Autorzy z zagraniczną afiliacją');
  });
  async function render(own: number | null, median: number | null, available = true) {
    fixture.componentRef.setInput('own', {
      status: own == null ? 'MISSING' : 'AVAILABLE',
      value: own,
    });
    fixture.componentRef.setInput('comparison', {
      status: available ? 'AVAILABLE' : 'UNAVAILABLE',
      value: median == null ? null : { median },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  const e = (selector: string): SVGElement | HTMLElement | null =>
    fixture.nativeElement.querySelector(selector);
  it.each([0, 0.3, 0.5, 50, 100])(
    'places own value %s at its raw coordinate and draws it above an equal median',
    async (own) => {
      await render(own, own);
      expect(e('circle')?.getAttribute('cx')).toBe(`${own}%`);
      expect(e('line.share-median')?.getAttribute('x1')).toBe(e('circle')?.getAttribute('cx'));
      expect(e('svg')?.lastElementChild?.tagName.toLowerCase()).toBe('circle');
      expect(e('[data-testid="decision-own-share"]')?.textContent?.trim()).toBe(
        `${Math.round(own)}%`,
      );
      expect(fixture.nativeElement.textContent).not.toMatch(/identyczn|równ|equal|tie/);
      expect(e('.share-axis-labels > span')?.textContent).toBe('0–100%');
      expect(e('.share-axis-labels [data-testid="decision-share-median"]')).not.toBeNull();
    },
  );
  it('does not treat rounded equality as raw equality or move points to avoid overlap', async () => {
    await render(0.3, 0.4);
    expect(parseFloat(e('circle')!.getAttribute('cx')!)).toBeCloseTo(0.3, 10);
    expect(parseFloat(e('line.share-median')!.getAttribute('x1')!)).toBeCloseTo(0.4, 10);
    expect(e('[data-testid="decision-own-share"]')?.textContent?.trim()).toBe('0%');
    expect(e('[data-testid="decision-share-median"]')?.textContent).toContain('0%');
  });
  it('hides only the graph and median when a comparison is unavailable', async () => {
    await render(0, 50, false);
    expect(e('svg')).toBeNull();
    expect(e('[data-testid="decision-share-median"]')).toBeNull();
    expect(e('[data-testid="decision-own-share"]')?.textContent?.trim()).toBe('0%');
    expect(fixture.nativeElement.textContent).toContain('Autorzy z zagraniczną afiliacją');
    expect(e('.alert')).toBeNull();
  });
  it.each([NaN, -1, 100.1, Infinity, null])(
    'does not draw invalid/missing own share %s',
    async (own) => {
      await render(own, 50);
      expect(e('svg')).toBeNull();
      expect(e('[data-testid="decision-own-share"]')?.textContent).toContain('Brak danych');
    },
  );
  it.each([NaN, -1, 100.1, Infinity, null])(
    'does not draw an unusable median %s and preserves own share',
    async (median) => {
      await render(50, median);
      expect(e('svg')).toBeNull();
      expect(e('[data-testid="decision-own-share"]')?.textContent?.trim()).toBe('50%');
    },
  );
  it('relocalizes accessible descriptions without changing geometry', async () => {
    await render(40.2, 50.7);
    const x = e('circle')?.getAttribute('cx');
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(e('circle')?.getAttribute('cx')).toBe(x);
    expect(e('svg')?.getAttribute('aria-label')).toBe(
      'Own share: 40%; group median: 51%; scale 0–100%',
    );
  });
});
