import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { DecisionFeeView } from '../../../core/services/journal-decision-annual';
import { JournalDecisionFees } from './journal-decision-fees';
import { decisionFee, decisionFeeCard } from './journal-decision-fees.fixtures';

class Loader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(language === 'en' ? en : pl);
  }
}

describe('JournalDecisionFees', () => {
  let fixture: ComponentFixture<JournalDecisionFees>;
  let source: JournalDecisionView;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalDecisionFees],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
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
    fixture = TestBed.createComponent(JournalDecisionFees);
    source = decisionFeeCard();
  });
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  async function render(language = 'pl') {
    TestBed.inject(TranslocoService).setActiveLang(language);
    fixture.componentRef.setInput('card', { ...source });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  function tile(category: DecisionFeeView['category']): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-fee-category="${category}"]`);
  }
  function text(category: DecisionFeeView['category']): string {
    return tile(category).querySelector('[data-testid="decision-fee-value"]')!.textContent!.trim();
  }
  function setFee(fee: DecisionFeeView) {
    source.annual!.fees = source
      .annual!.fees.filter((f) => f.category !== fee.category)
      .concat(fee);
  }

  it.each(['pl', 'en'])(
    'shows five categories and the persistent explanation with independent shares (%s)',
    async (language) => {
      source.annual!.fees.reverse();
      source.benchmarks!.fees.reverse();
      const original = JSON.stringify(source);
      await render(language);
      expect(
        [...fixture.nativeElement.querySelectorAll('article')].map(
          (e: HTMLElement) => e.dataset['feeCategory'],
        ),
      ).toEqual(['SUBMISSION', 'PUBLICATION', 'OPEN_ACCESS', 'RAPID_REVIEW', 'OTHER']);
      expect(
        [...fixture.nativeElement.querySelectorAll('[data-testid="decision-fee-percent"]')].map(
          (e: HTMLElement) => e.textContent!.trim(),
        ),
      ).toEqual(['<1%', '50%', '>99%', '100%', '0%']);
      expect(text('SUBMISSION')).toBe(language === 'pl' ? 'Brak' : 'None');
      expect(
        [...fixture.nativeElement.querySelectorAll('[data-testid="decision-fee-value"]')].every(
          (value: HTMLElement) => value.classList.contains('text-success'),
        ),
      ).toBe(true);
      expect(tile('PUBLICATION').querySelector('h3')?.textContent).toContain('Publication fee');
      expect(tile('OTHER').querySelector('h3')?.textContent).toContain(
        language === 'pl' ? 'Inne APC' : 'Other APC',
      );
      expect(
        tile('SUBMISSION')
          .querySelector('[data-testid="decision-fee-percent"]')
          ?.getAttribute('aria-label'),
      ).toContain('<1%');
      expect(
        fixture.nativeElement.querySelector('[data-testid="decision-fees-explanation"]')
          .textContent,
      ).toContain(language === 'pl' ? 'w grupie porównawczej' : 'in the comparison group');
      expect(fixture.nativeElement.querySelectorAll('.fee-tile')).toHaveLength(6);
      expect(
        fixture.nativeElement.querySelector('svg, canvas, .progress, button, details'),
      ).toBeNull();
      expect(JSON.stringify(source)).toBe(original);
    },
  );

  it.each(['pl', 'en'])(
    'shows both country variants even for legacy selection and ignores catalogue country (%s)',
    async (language) => {
      setFee(
        decisionFee({
          type: 'COUNTRY_SPECIFIC_FEE',
          charged: true,
          selectionStatus: 'LEGACY',
          variants: [
            {
              variant: 'INTERNAL_AUTHORS',
              status: 'AVAILABLE',
              charged: false,
              minimum: 0,
              maximum: 0,
              currency: 'PLN',
            },
            {
              variant: 'EXTERNAL_AUTHORS',
              status: 'AVAILABLE',
              charged: true,
              minimum: 1000,
              maximum: 1000,
              currency: 'EUR',
            },
          ],
        }),
      );
      source.identity.country = { code: 'PL' };
      await render(language);
      const first = text('SUBMISSION').replace(/\s+/g, ' ');
      expect(first).toContain(language === 'pl' ? 'Krajowi: Brak' : 'Domestic: None');
      expect(first).toContain(language === 'pl' ? 'Zagraniczni: 1000 EUR' : 'Foreign: 1,000 EUR');
      expect(tile('SUBMISSION').querySelectorAll('[data-fee-variant]')).toHaveLength(2);
      expect(
        tile('SUBMISSION')
          .querySelector('[data-fee-variant="INTERNAL_AUTHORS"] .text-success')!
          .textContent!.trim(),
      ).toBe(language === 'pl' ? 'Brak' : 'None');
      expect(
        tile('SUBMISSION').querySelector('[data-fee-variant="EXTERNAL_AUTHORS"] .text-success'),
      ).toBeNull();
      source.identity.country = { code: 'GB' };
      await render(language);
      expect(text('SUBMISSION').replace(/\s+/g, ' ')).toBe(first);
    },
  );

  it.each(['LEGACY', 'UNRESOLVED', 'INCOMPLETE', 'INVALID'] as const)(
    'keeps an unresolved %s variant local and hides only its dependent share',
    async (selectionStatus) => {
      setFee(
        decisionFee({
          type: 'COUNTRY_SPECIFIC_FEE',
          status: selectionStatus === 'INVALID' ? 'INVALID' : 'UNRESOLVED',
          charged: null,
          selectionStatus,
          effectiveVariant: null,
        }),
      );
      await render();
      expect(text('SUBMISSION')).toBe('Nie można ustalić opłaty');
      expect(tile('SUBMISSION').querySelector('.text-success')).toBeNull();
      expect(tile('SUBMISSION').querySelector('[data-testid="decision-fee-percent"]')).toBeNull();
      expect(text('PUBLICATION')).toBe('Brak');
      expect(
        tile('PUBLICATION').querySelector('[data-testid="decision-fee-percent"]')?.textContent,
      ).toContain('50%');
      expect(fixture.nativeElement.querySelector('.alert')).toBeNull();
    },
  );

  it('shows a single source currency without requesting rates, and includes a positive range starting at zero', async () => {
    setFee(
      decisionFee({
        type: 'VARIOUS_FEE',
        charged: true,
        effectiveVariant: null,
        minimum: 50,
        maximum: 150,
        currency: 'EUR',
        sourceRanges: [{ minimum: 50, maximum: 150, currency: 'EUR' }],
      }),
    );
    setFee(
      decisionFee({
        category: 'PUBLICATION',
        type: 'VARIOUS_FEE',
        charged: true,
        minimum: 0,
        maximum: 100,
        currency: 'USD',
      }),
    );
    await render();
    expect(text('SUBMISSION')).toBe('50–150 EUR');
    expect(text('PUBLICATION')).toBe('0–100 USD');
    expect(tile('SUBMISSION').querySelector('.text-success')).toBeNull();
    expect(tile('PUBLICATION').querySelector('.text-success')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="decision-fee-rates"]')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('shows the mixed currency label without rates even if an older payload contains converted bounds', async () => {
    setFee(
      decisionFee({
        type: 'VARIOUS_FEE',
        charged: true,
        minimum: 20.126,
        maximum: 120.999,
        currency: 'PLN',
        sourceRanges: [
          { minimum: 5, maximum: 20, currency: 'EUR' },
          { minimum: 10, maximum: 40, currency: 'USD' },
        ],
        exchangeRates: [
          {
            currency: 'USD',
            mid: 3,
            tableType: 'A',
            tableNumber: '001/A/2026',
            effectiveDate: '2026-10-28',
          },
          {
            currency: 'EUR',
            mid: 4.0252,
            tableType: 'B',
            tableNumber: '002/B/2026',
            effectiveDate: '2026-10-29',
          },
        ],
      }),
    );
    const original = JSON.stringify(source);
    await render('en');
    expect(text('SUBMISSION')).toBe('Different rates and currencies');
    expect(tile('SUBMISSION').querySelector('[data-testid="decision-fee-rates"]')).toBeNull();
    expect(tile('SUBMISSION').textContent).not.toContain('PLN');
    expect(JSON.stringify(source)).toBe(original);
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it.each(['pl', 'en'])(
    'does not treat an unavailable legacy conversion as a valid new fee (%s)',
    async (language) => {
      setFee(
        decisionFee({
          type: 'VARIOUS_FEE',
          status: 'RATES_UNAVAILABLE',
          charged: true,
          sourceRanges: [
            { minimum: 1, maximum: 100, currency: 'USD' },
            { minimum: 1, maximum: 100, currency: 'EUR' },
          ],
        }),
      );
      await render(language);
      expect(text('SUBMISSION')).toBe(
        language === 'pl' ? 'Nie można ustalić opłaty' : 'The fee cannot be determined',
      );
      expect(tile('SUBMISSION').querySelector('[data-testid="decision-fee-percent"]')).toBeNull();
      expect(tile('SUBMISSION').querySelector('[data-testid="decision-fee-rates"]')).toBeNull();
      expect(text('PUBLICATION')).toBe(language === 'pl' ? 'Brak' : 'None');
    },
  );

  it.each(['pl', 'en'])(
    'shows only declaration for Other APC even if the payload contains amounts (%s)',
    async (language) => {
      setFee(
        decisionFee({
          category: 'OTHER',
          type: 'VARIOUS_FEE',
          charged: true,
          minimum: 20,
          maximum: 100,
          currency: 'EUR',
          exchangeRates: [
            {
              currency: 'EUR',
              mid: 4,
              tableType: 'A',
              tableNumber: '001/A/2026',
              effectiveDate: '2026-10-29',
            },
          ],
        }),
      );
      await render(language);
      expect(text('OTHER')).toBe(language === 'pl' ? 'Występują' : 'Present');
      expect(tile('OTHER').textContent).not.toContain('EUR');
      expect(tile('OTHER').querySelector('[data-testid="decision-fee-rates"]')).toBeNull();
      setFee(
        decisionFee({
          category: 'OTHER',
          type: 'COUNTRY_SPECIFIC_FEE',
          effectiveVariant: 'INTERNAL_AUTHORS',
        }),
      );
      await render(language);
      expect(text('OTHER')).toBe(language === 'pl' ? 'Brak' : 'None');
      setFee(decisionFee({ category: 'OTHER', status: 'INVALID', charged: null }));
      await render(language);
      expect(text('OTHER')).toBe(
        language === 'pl' ? 'Nie można ustalić opłaty' : 'The fee cannot be determined',
      );
    },
  );

  it('keeps own values and explanation with no benchmark, old benchmark year or one unavailable comparison', async () => {
    source.benchmarks!.fees[1].comparison = { status: 'UNAVAILABLE', value: null };
    await render();
    expect(tile('PUBLICATION').querySelector('[data-testid="decision-fee-percent"]')).toBeNull();
    expect(
      fixture.nativeElement.querySelectorAll('[data-testid="decision-fee-percent"]'),
    ).toHaveLength(4);
    source.benchmarks!.surveyYear = 2025;
    await render();
    expect(fixture.nativeElement.querySelector('[data-testid="decision-fee-percent"]')).toBeNull();
    source.benchmarks = null;
    await render();
    expect(fixture.nativeElement.querySelectorAll('article')).toHaveLength(5);
    expect(text('SUBMISSION')).toBe('Brak');
    expect(
      fixture.nativeElement.querySelector('[data-testid="decision-fees-explanation"]'),
    ).not.toBeNull();
  });

  it('does not silently choose the first duplicate fee, category benchmark or wrong annual year', async () => {
    source.annual!.fees.push(decisionFee());
    source.benchmarks!.fees.push(source.benchmarks!.fees[1]);
    await render();
    expect(text('SUBMISSION')).toBe('Nie można ustalić opłaty');
    expect(tile('SUBMISSION').querySelector('[data-testid="decision-fee-percent"]')).toBeNull();
    expect(tile('PUBLICATION').querySelector('[data-testid="decision-fee-percent"]')).toBeNull();
    source.annual!.surveyYear = 2025;
    await render();
    expect(
      [...fixture.nativeElement.querySelectorAll('[data-testid="decision-fee-value"]')].every(
        (e: HTMLElement) => e.textContent!.trim() === 'Nie można ustalić opłaty',
      ),
    ).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="decision-fee-percent"]')).toBeNull();
  });
});
