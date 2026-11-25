import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { decisionFee, decisionFeeCard } from './journal-decision-fees.fixtures';
import { JournalDecisionSummary } from './journal-decision-summary';

class Loader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(language === 'en' ? en : pl);
  }
}

describe('JournalDecisionSummary', () => {
  let fixture: ComponentFixture<JournalDecisionSummary>;
  let source: JournalDecisionView;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalDecisionSummary],
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
    fixture = TestBed.createComponent(JournalDecisionSummary);
    source = decisionFeeCard();
    source.annual!.times.publication = { status: 'AVAILABLE', value: 80 };
    source.benchmarks!.times.publication = {
      status: 'AVAILABLE',
      value: { q1: 45, q3: 95, axis: { minimum: 0, maximum: 100 } },
    };
    source.mnisw = { year: 2024, listed: true, points: 20, disciplines: [], status: 'AVAILABLE' };
    source.annual!.process.frequency = { status: 'AVAILABLE', value: 'q' };
    source.annual!.process.rejectionRate = { status: 'AVAILABLE', value: 25 };
  });
  async function render(language = 'pl') {
    TestBed.inject(TranslocoService).setActiveLang(language);
    fixture.componentRef.setInput('card', { ...source });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  const element = (selector: string): HTMLElement | null =>
    fixture.nativeElement.querySelector(selector);
  const text = (selector: string) => element(selector)!.textContent!;
  function chargePublication() {
    source.annual!.fees = source.annual!.fees.map((fee) =>
      fee.category === 'PUBLICATION'
        ? decisionFee({
            category: 'PUBLICATION',
            type: 'ONE_FEE',
            charged: true,
            minimum: 1500,
            maximum: 1500,
            currency: 'PLN',
          })
        : fee,
    );
  }

  it.each(['pl', 'en'])(
    'shows charged categories and country rates with publication time and process (%s)',
    async (language) => {
      source.annual!.fees = source
        .annual!.fees.map((fee) => {
          if (fee.category === 'PUBLICATION')
            return decisionFee({
              category: 'PUBLICATION',
              type: 'COUNTRY_SPECIFIC_FEE',
              charged: true,
              variants: [
                {
                  variant: 'EXTERNAL_AUTHORS',
                  status: 'AVAILABLE',
                  charged: true,
                  minimum: 350,
                  maximum: 350,
                  currency: 'EUR',
                },
                {
                  variant: 'INTERNAL_AUTHORS',
                  status: 'AVAILABLE',
                  charged: true,
                  minimum: 1500,
                  maximum: 1500,
                  currency: 'PLN',
                },
              ],
            });
          if (fee.category === 'SUBMISSION')
            return decisionFee({
              category: 'SUBMISSION',
              type: 'ONE_FEE',
              charged: true,
              minimum: 50,
              maximum: 100,
              currency: 'USD',
            });
          if (fee.category === 'OTHER')
            return decisionFee({ category: 'OTHER', type: 'ONE_FEE', charged: true });
          return fee;
        })
        .reverse();
      const before = JSON.stringify(source);
      await render(language);
      expect(
        [...fixture.nativeElement.querySelectorAll('[data-summary-fee-category]')].map(
          (el: HTMLElement) => el.dataset['summaryFeeCategory'],
        ),
      ).toEqual(['SUBMISSION', 'PUBLICATION', 'OTHER']);
      expect(text('[data-summary-fee-category="SUBMISSION"]')).toContain('50–100 USD');
      expect(text('[data-summary-fee-variant="INTERNAL_AUTHORS"]')).toContain(
        language === 'pl' ? '1\u00a0500 PLN' : '1,500 PLN',
      );
      expect(text('[data-summary-fee-variant="EXTERNAL_AUTHORS"]')).toContain('350 EUR');
      expect(text('[data-summary-fee-variant="INTERNAL_AUTHORS"]')).toContain(
        language === 'pl' ? 'Autorzy krajowi' : 'Domestic authors',
      );
      expect(text('[data-summary-fee-category="OTHER"]')).toContain(
        language === 'pl' ? 'Występują' : 'Present',
      );
      expect(text('[data-testid="decision-summary-publication"]')).toContain(
        language === 'pl' ? '80 dni' : '80 days',
      );
      expect(text('[data-testid="decision-summary-typical-time"]')).toContain('45–95');
      expect(text('[data-testid="decision-summary-process"]')).toContain(language === 'pl' ? 'Kwartalnik' : 'Quarterly');
      expect(text('[data-testid="decision-summary-process"]')).toContain('25%');
      expect(element('[data-testid="decision-mnisw"]')).toBeNull();
      expect(JSON.stringify(source)).toBe(before);
    },
  );

  it('distinguishes no fees from missing declarations and retains zero days and zero rejections', async () => {
    source.annual!.times.publication.value = 0;
    source.annual!.process.rejectionRate.value = 0;
    await render();
    expect(text('[data-testid="decision-summary-fees"]')).toContain('Brak opłat');
    expect(text('[data-testid="decision-summary-publication"]')).toContain('0 dni');
    expect(text('[data-testid="decision-summary-process"]')).toContain('0%');
    source.annual!.fees = [];
    await render();
    expect(text('[data-testid="decision-summary-fees"]')).toContain('Nie można ustalić opłaty');
    expect(text('[data-testid="decision-summary-fees"]')).not.toContain('Brak opłat');
  });

  it('keeps a known fee and reports other unresolved or duplicate fee categories', async () => {
    chargePublication();
    source.annual!.fees.push(decisionFee({ category: 'SUBMISSION' }));
    await render();
    expect(text('[data-summary-fee-category="PUBLICATION"]')).toContain('1\u00a0500 PLN');
    expect(text('[data-testid="decision-summary-fees"]')).toContain(
      'Nie można ustalić wszystkich opłat',
    );
  });

  it.each(['hidden', 'old', 'unavailable', 'invalid'] as const)(
    'omits the typical range for %s benchmarks without losing own data',
    async (reason) => {
      if (reason === 'hidden') source.benchmarksVisible = false;
      if (reason === 'old') source.benchmarks!.surveyYear = 2023;
      if (reason === 'unavailable') source.benchmarks!.times.publication.status = 'UNAVAILABLE';
      if (reason === 'invalid') source.benchmarks!.times.publication.value!.q1 = 96;
      await render();
      expect(element('[data-testid="decision-summary-typical-time"]')).toBeNull();
      expect(text('[data-testid="decision-summary-publication"]')).toContain('80 dni');
    },
  );

  it.each([null, -1, NaN, Infinity])(
    'does not display invalid publication days %s or a derived range',
    async (days) => {
      source.annual!.times.publication.value = days;
      await render();
      expect(text('[data-testid="decision-summary-publication"]')).toContain('Brak danych');
      expect(element('[data-testid="decision-summary-typical-time"]')).toBeNull();
    },
  );

  it.each(['missing', 'old', 'unavailable'] as const)(
    'does not expose %s annual fees, days or process data',
    async (reason) => {
      chargePublication();
      if (reason === 'missing') source.annual = null;
      if (reason === 'old') source.annual!.surveyYear = 2023;
      if (reason === 'unavailable') source.annualStatus = 'NOT_INDEXED';
      await render();
      expect(text('[data-testid="decision-summary-fees"]')).not.toContain('1500');
      expect(text('[data-testid="decision-summary-publication"]')).not.toContain('80');
      expect(element('[data-testid="decision-summary-typical-time"]')).toBeNull();
      expect(text('[data-testid="decision-summary-process"]')).toContain('Brak danych');
      expect(text('[data-testid="decision-summary-process"]')).not.toContain('25%');
    },
  );

  it('relocalizes process and fee data without rendering the old MNiSW tile', async () => {
    chargePublication();
    source.mnisw!.listed = false;
    await render();
    expect(text('[data-testid="decision-summary-process"]')).toContain('Kwartalnik');
    expect(element('[data-testid="decision-mnisw"]')).toBeNull();
    await render('en');
    expect(text('[data-testid="decision-summary-process"]')).toContain('Quarterly');
    expect(text('[data-testid="decision-summary-publication"]')).toContain('80 days');
    expect(text('[data-summary-fee-category="PUBLICATION"]')).toContain('Publication fee (APC)');
  });
});
