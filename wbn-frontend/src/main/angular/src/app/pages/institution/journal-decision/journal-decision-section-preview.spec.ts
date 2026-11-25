import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { decisionFee, decisionFeeCard } from './journal-decision-fees.fixtures';
import { JournalDecisionSectionPreview } from './journal-decision-section-preview';

class Loader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(language === 'en' ? en : pl);
  }
}

describe('Decision section previews', () => {
  let fixture: ComponentFixture<JournalDecisionSectionPreview>;
  let source: JournalDecisionView;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalDecisionSectionPreview],
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
    fixture = TestBed.createComponent(JournalDecisionSectionPreview);
    source = decisionFeeCard();
    source.annual!.times.review = { status: 'AVAILABLE', value: 40 };
    source.benchmarks!.times.review = {
      status: 'AVAILABLE',
      value: { q1: 17.25, q3: 44.75, axis: { minimum: 10, maximum: 50 } },
    };
    source.annual!.process.frequency = { status: 'AVAILABLE', value: 'b' };
    source.annual!.process.rejectionRate = { status: 'AVAILABLE', value: (100 * 20) / 70 };
  });
  async function render(section: 'time' | 'costs' | 'process', language = 'pl') {
    TestBed.inject(TranslocoService).setActiveLang(language);
    fixture.componentRef.setInput('card', { ...source });
    fixture.componentRef.setInput('section', section);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim();
  }

  it('uses review time and outward-rounded review quartiles rather than publication time', async () => {
    source.annual!.times.publication = { status: 'AVAILABLE', value: 80 };
    expect(await render('time')).toBe('Recenzja 40 dni · typowo 17–45 dni');
  });

  it.each([
    [30.4, '30 dni'],
    [30.5, '31 dni'],
    [1.4, '1 dzień'],
    [0, '0 dni'],
  ])('rounds equal raw quartiles %s once and uses the right day form', async (value, expected) => {
    source.annual!.times.review.value = 1;
    source.benchmarks!.times.review.value = {
      q1: value,
      q3: value,
      axis: { minimum: 0, maximum: 50 },
    };
    expect(await render('time')).toBe(`Recenzja 1 dzień · typowo ${expected}`);
  });

  it.each(['hidden', 'old', 'missing', 'invalid', 'axis'] as const)(
    'preserves own review time without the typical range for %s comparison',
    async (reason) => {
      if (reason === 'hidden') source.benchmarksVisible = false;
      if (reason === 'old') source.benchmarks!.surveyYear = 2023;
      if (reason === 'missing')
        source.benchmarks!.times.review = { status: 'UNAVAILABLE', value: null };
      if (reason === 'invalid') source.benchmarks!.times.review.value!.q1 = 60;
      if (reason === 'axis') source.benchmarks!.times.review.value!.axis.maximum = 0;
      expect(await render('time')).toBe('Recenzja 40 dni');
    },
  );

  it.each([null, -1, NaN, Infinity])('does not invent a review time for %s', async (value) => {
    source.annual!.times.review.value = value;
    expect(await render('time')).toBe('Brak danych');
  });

  it('retains a real zero review time', async () => {
    source.annual!.times.review.value = 0;
    source.benchmarks = null;
    expect(await render('time')).toBe('Recenzja 0 dni');
  });

  it.each([
    [0, 'Brak opłat'],
    [1, 'Jedna kategoria opłat'],
    [2, '2 kategorie opłat'],
    [3, '3 kategorie opłat'],
    [4, '4 kategorie opłat'],
    [5, '5 kategorii opłat'],
  ])('counts %s fee categories using declarations including Other APC', async (count, expected) => {
    source.annual!.fees = source.annual!.fees.map((fee, index) =>
      index < count ? { ...fee, type: 'ONE_FEE', charged: true } : fee,
    );
    expect(await render('costs')).toBe(expected);
  });

  it('counts a country-specific APC once despite variants, duplicate entries, ranges and currencies', async () => {
    const apc = decisionFee({
      category: 'PUBLICATION',
      type: 'COUNTRY_SPECIFIC_FEE',
      charged: true,
      variants: [
        {
          variant: 'INTERNAL_AUTHORS',
          status: 'AVAILABLE',
          charged: true,
          minimum: 1500,
          maximum: 2000,
          currency: 'PLN',
        },
        {
          variant: 'EXTERNAL_AUTHORS',
          status: 'AVAILABLE',
          charged: true,
          minimum: 300,
          maximum: 350,
          currency: 'EUR',
        },
      ],
    });
    source.annual!.fees = source
      .annual!.fees.filter((fee) => fee.category !== 'PUBLICATION')
      .concat(apc, { ...apc });
    expect(await render('costs')).toBe('Jedna kategoria opłat');
  });

  it('does not infer payment from amounts or override an explicit NO_FEE declaration', async () => {
    source.annual!.fees = source.annual!.fees.map((fee) => ({
      ...fee,
      charged: true,
      minimum: 100,
      maximum: 200,
      currency: 'PLN',
    }));
    expect(await render('costs')).toBe('Brak opłat');
  });

  it('does not turn unresolved fee declarations into no fees', async () => {
    source.annual!.fees[0] = decisionFee({ status: 'UNRESOLVED', type: null, charged: null });
    expect(await render('costs')).toBe('Brak danych');
  });

  it.each([
    [(100 * 20) / 70, '29%'],
    [0, '0%'],
    [100, '100%'],
    [0.3, '0%'],
    [99.8, '100%'],
  ])('rounds the annual rejection rate %s only for display', async (value, expected) => {
    source.annual!.process.rejectionRate.value = value;
    expect(await render('process')).toBe(`Dwumiesięcznik · ${expected} odrzuceń`);
  });

  it.each([null, -1, NaN, Infinity, 100.1])(
    'keeps frequency alone for an unusable rejection rate %s',
    async (value) => {
      source.annual!.process.rejectionRate.value = value;
      expect(await render('process')).toBe('Dwumiesięcznik');
    },
  );

  it('honors the invalid denominator status returned by the annual API', async () => {
    source.annual!.process.rejectionRate = { status: 'INVALID', value: 0 };
    expect(await render('process')).toBe('Dwumiesięcznik');
  });

  it('can display a valid rate without an unavailable frequency', async () => {
    source.annual!.process.frequency = { status: 'MISSING', value: null };
    expect(await render('process')).toBe('29% odrzuceń');
  });

  it('recomputes all previews for another journal/year and relocalizes existing values', async () => {
    expect(await render('time')).toBe('Recenzja 40 dni · typowo 17–45 dni');
    expect(await render('costs')).toBe('Brak opłat');
    expect(await render('process')).toBe('Dwumiesięcznik · 29% odrzuceń');
    source.identity.id = 456;
    source.surveyYear = 2025;
    for (const section of ['time', 'costs', 'process'] as const)
      expect(await render(section)).toBe('Brak danych');
    source.annual!.surveyYear = 2025;
    source.annual!.times.review.value = 1;
    source.annual!.process.frequency.value = 'a';
    source.annual!.process.rejectionRate.value = 100;
    source.annual!.fees[1] = decisionFee({
      category: 'PUBLICATION',
      type: 'ONE_FEE',
      charged: true,
    });
    expect(await render('time')).toBe('Recenzja 1 dzień');
    expect(await render('costs')).toBe('Jedna kategoria opłat');
    expect(await render('process')).toBe('Rocznik · 100% odrzuceń');
    expect(await render('time', 'en')).toBe('Review 1 day');
    expect(await render('costs', 'en')).toBe('One fee category');
    expect(await render('process', 'en')).toBe('Annual · 100% rejection rate');
  });
});
