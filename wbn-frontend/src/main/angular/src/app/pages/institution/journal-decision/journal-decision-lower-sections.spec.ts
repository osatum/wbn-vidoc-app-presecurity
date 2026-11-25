import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { of, Subject } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { DecisionValue } from '../../../core/services/journal-decision-annual';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { JournalDecisionProcess } from './journal-decision-process';
import { JournalDecisionInternational } from './journal-decision-international';
import { JournalDecisionStandards } from './journal-decision-standards';
import { decisionFeeCard } from './journal-decision-fees.fixtures';

class Loader implements TranslocoLoader {
  getTranslation(lang: string) {
    return of(lang === 'en' ? en : pl);
  }
}
const available = <T>(value: T): DecisionValue<T> => ({ status: 'AVAILABLE', value });
const missing = { status: 'MISSING', value: null } as const;

describe('Decision annual lower sections', () => {
  let source: JournalDecisionView;
  let process: ComponentFixture<JournalDecisionProcess>;
  let international: ComponentFixture<JournalDecisionInternational>;
  let standards: ComponentFixture<JournalDecisionStandards>;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalDecisionProcess, JournalDecisionInternational, JournalDecisionStandards],
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
    source = decisionFeeCard();
    process = TestBed.createComponent(JournalDecisionProcess);
    international = TestBed.createComponent(JournalDecisionInternational);
    standards = TestBed.createComponent(JournalDecisionStandards);
  });
  async function render(lang = 'pl') {
    TestBed.inject(TranslocoService).setActiveLang(lang);
    const snapshot = structuredClone(source);
    for (const fixture of [process, international, standards]) {
      fixture.componentRef.setInput('card', snapshot);
    }
    for (const fixture of [process, international, standards]) {
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    }
  }
  const p = (key: string): string =>
    process.nativeElement.querySelector(`[data-process-field="${key}"] dd`).textContent.trim();
  const s = (key: string): string =>
    standards.nativeElement.querySelector(`[data-standard-field="${key}"] dd`).textContent.trim();

  it.each(['pl', 'en'])(
    'keeps both source years and the five process rows in order (%s)',
    async (lang) => {
      source.annual!.process.firstIssueYear = available(1990);
      source.annual!.process.continuedYear = available(1990);
      await render(lang);
      expect(p('since')).toBe(lang === 'pl' ? '1990 (nieprzerwanie)' : '1990 (continuously)');
      source.annual!.process.continuedYear = available(2000);
      await render(lang);
      expect(p('since')).toBe(
        lang === 'pl' ? '1990 (nieprzerwanie od 2000)' : '1990 (continuously since 2000)',
      );
      expect(
        [...process.nativeElement.querySelectorAll('[data-process-field]')].map(
          (e: HTMLElement) => e.dataset['processField'],
        ),
      ).toEqual(['since', 'frequency', 'aheadOfPrint', 'rejection', 'regularity']);
      expect(process.nativeElement.querySelector('svg,canvas')).toBeNull();
    },
  );
  it('retains a valid year when its companion is missing without synthesizing it', async () => {
    source.annual!.process.firstIssueYear = available(1990);
    await render();
    expect(p('since')).toBe('1990 (brak roku nieprzerwanej działalności)');
    source.annual!.process.firstIssueYear = missing;
    source.annual!.process.continuedYear = available(2000);
    await render();
    expect(p('since')).toContain('Nieprzerwanie od 2000');
  });
  it.each(['pl', 'en'])(
    'uses the annual dictionary code and preserves explicit false and missing declarations (%s)',
    async (lang) => {
      source.annual!.process.frequency = available('q');
      source.annual!.process.aheadOfPrint = available(false);
      source.annual!.process.publishingBreak = available(false);
      await render(lang);
      expect(p('frequency')).toBe(lang === 'pl' ? 'Kwartalnik' : 'Quarterly');
      expect(p('aheadOfPrint')).toBe(lang === 'pl' ? 'Nie' : 'No');
      expect(p('regularity')).toBe(
        lang === 'pl' ? 'Brak opóźnień powyżej 6 miesięcy' : 'No delays exceeding 6 months',
      );
      source.annual!.process.publishingBreak = available(true);
      await render(lang);
      expect(p('regularity')).toBe(lang === 'pl' ? 'Opóźnienia > 6 mies.' : 'Delays > 6 months');
      source.annual!.process.frequency = missing;
      source.annual!.process.aheadOfPrint = missing;
      source.annual!.process.publishingBreak = missing;
      await render(lang);
      for (const key of ['frequency', 'aheadOfPrint', 'regularity'])
        expect(p(key)).toBe(lang === 'pl' ? 'Brak danych' : 'No data');
    },
  );
  it.each([
    [0, '0%'],
    [0.3, '0%'],
    [0.5, '1%'],
    [25.6, '26%'],
    [100, '100%'],
  ])('shows raw rejection %s as whole %s without changing the API', async (value, expected) => {
    source.annual!.process.rejectionRate = available(value as number);
    await render();
    expect(p('rejection')).toBe(expected);
    expect(source.annual!.process.rejectionRate.value).toBe(value);
  });
  it('shows only the text rejection benchmark and hides it locally when unavailable or stale', async () => {
    source.annual!.process.rejectionRate = available(0);
    source.benchmarks!.rejectionRate = { status: 'AVAILABLE', value: { q1: 9.0909, q3: 39.1453 } };
    await render();
    expect(p('rejection')).toContain('(typowy wskaźnik w grupie: 9–40%)');
    source.benchmarks!.rejectionRate.value = { q1: 12.5, q3: 12.5 };
    await render('en');
    expect(p('rejection')).toContain('(typical rate in the group: 13%)');
    source.benchmarks!.surveyYear = 2025;
    await render();
    expect(p('rejection')).toBe('0%');
    source.benchmarks!.surveyYear = 2024;
    source.annual!.process.rejectionRate = missing;
    await render();
    expect(
      process.nativeElement.querySelector('[data-testid="decision-rejection-benchmark"]'),
    ).toBeNull();
  });
  it.each(['pl', 'en'])(
    'shows exactly three foreign shares and independently available medians (%s)',
    async (lang) => {
      source.annual!.international = {
        authors: available(0),
        reviewers: available(0.5),
        scientificCouncil: available(100),
      };
      source.benchmarks!.international = {
        authors: { status: 'AVAILABLE', value: { median: 0 } },
        reviewers: { status: 'UNAVAILABLE', value: null },
        scientificCouncil: { status: 'AVAILABLE', value: { median: 100 } },
      };
      await render(lang);
      expect(
        [...international.nativeElement.querySelectorAll('[data-testid="decision-own-share"]')].map(
          (e: HTMLElement) => e.textContent.trim(),
        ),
      ).toEqual(['0%', '1%', '100%']);
      expect(international.nativeElement.querySelectorAll('svg')).toHaveLength(2);
      expect(international.nativeElement.textContent).not.toMatch(/zewnętrzn|external/);
      source.annual!.international.authors = missing;
      await render(lang);
      expect(international.nativeElement.querySelectorAll('svg')).toHaveLength(1);
    },
  );
  it.each(['pl', 'en'])(
    'distinguishes indicated, none, missing, unindicated and invalid standard declarations (%s)',
    async (lang) => {
      source.annual!.standards.antiPlagiarism = available('INDICATED');
      source.annual!.standards.publicCriteria = available('NONE');
      source.annual!.standards.editorialSystem = available('NOT_INDICATED');
      await render(lang);
      expect(s('antiPlagiarism')).toBe(
        lang === 'pl' ? 'Tak — zadeklarowano stosowanie' : 'Yes — use declared',
      );
      expect(s('publicCriteria')).toBe(
        lang === 'pl' ? 'Zadeklarowano: żadne z powyższych' : 'Declared: none of the above',
      );
      expect(s('editorialSystem')).toBe(
        lang === 'pl' ? 'Nie wskazano tego narzędzia' : 'This tool was not indicated',
      );
      source.annual!.standards.publicCriteria = available('INDICATED');
      source.annual!.standards.editorialSystem = missing;
      source.annual!.standards.antiPlagiarism = { status: 'INVALID', value: null };
      await render(lang);
      expect(s('publicCriteria')).toBe(
        lang === 'pl'
          ? 'Tak — zadeklarowano publiczne udostępnianie'
          : 'Yes — public disclosure declared',
      );
      expect(s('editorialSystem')).toBe(lang === 'pl' ? 'Brak deklaracji' : 'No declaration');
      expect(s('antiPlagiarism')).toBe(
        lang === 'pl' ? 'Nieprawidłowa deklaracja' : 'Invalid declaration',
      );
    },
  );
  it.each(['pl', 'en'])(
    'retains all confirmed review models with their original shares and missing share (%s)',
    async (lang) => {
      source.annual!.standards.reviewing = {
        status: 'AVAILABLE',
        none: false,
        models: [
          { code: 'DOUBLE_BLIND_REVIEW', confirmed: true, share: available(0.5) },
          { code: 'SINGLE_BLIND_REVIEW', confirmed: true, share: available(20) },
          { code: 'OPEN_PEER_REVIEW', confirmed: true, share: missing },
        ],
      };
      await render(lang);
      expect(s('reviewing')).toContain(
        lang === 'pl' ? 'Double blind (0,5%)' : 'Double blind (0.5%)',
      );
      expect(s('reviewing')).toContain('Single blind (20%)');
      expect(s('reviewing')).toContain(
        lang === 'pl'
          ? 'Open peer review (udział niepodany)'
          : 'Open peer review (share not provided)',
      );
      expect(s('reviewing')).not.toContain('100%');
    },
  );
  it('shows complete declarations and the share of a single review model in the original layout', async () => {
    for (const key of ['antiPlagiarism', 'publicCriteria', 'editorialSystem'] as const) {
      source.annual!.standards[key] = available('INDICATED');
    }
    source.annual!.standards.reviewing = {
      status: 'AVAILABLE',
      none: false,
      models: [{ code: 'DOUBLE_BLIND_REVIEW', confirmed: true, share: available(100) }],
    };
    source.annual!.standards.access = available('FREE_ARTICLES');
    await render();
    expect(s('antiPlagiarism')).toBe('Tak — zadeklarowano stosowanie');
    expect(s('publicCriteria')).toBe('Tak — zadeklarowano publiczne udostępnianie');
    expect(s('editorialSystem')).toBe('Tak — zadeklarowano stosowanie');
    expect(s('reviewing')).toBe('Double blind (100%)');
    expect(s('access')).toBe('Dostęp bezpłatny do wszystkich artykułów naukowych');
  });

  it('distinguishes none, no selection and conflicting review data without inventing models', async () => {
    source.annual!.standards.reviewing = { status: 'AVAILABLE', none: true, models: [] };
    await render();
    expect(s('reviewing')).toBe('Żadne z powyższych');
    source.annual!.standards.reviewing.none = false;
    await render();
    expect(s('reviewing')).toBe('Brak wskazania');
    source.annual!.standards.reviewing = {
      status: 'INVALID',
      none: false,
      models: [
        { code: 'DOUBLE_BLIND_REVIEW', confirmed: true, share: available(80) },
        { code: 'SINGLE_BLIND_REVIEW', confirmed: true, share: available(60) },
        { code: 'OPEN_PEER_REVIEW', confirmed: false, share: available(20) },
      ],
    };
    await render();
    expect(s('reviewing')).toContain('Double blind (80%)');
    expect(s('reviewing')).toContain('Single blind (60%)');
    expect(s('reviewing')).not.toContain('Open peer review');
    expect(s('reviewing')).toContain('Niespójne dane');
    source.annual!.standards.reviewing.models[0].share = { status: 'INVALID', value: null };
    await render();
    expect(s('reviewing')).toContain('nieprawidłowy udział');
  });
  it.each(['pl', 'en'])(
    'shows partial access and all selected conditions without inventing OA or subscription (%s)',
    async (lang) => {
      source.annual!.standards.access = available('PAID_ARTICLES');
      source.annual!.standards.accessConditions = available([
        'publication_date',
        'article',
        'language_version',
      ]);
      await render(lang);
      expect(s('access')).toContain(
        lang === 'pl' ? 'Dostęp częściowo płatny' : 'Partially paid access',
      );
      expect(
        standards.nativeElement.querySelectorAll('[data-standard-field="access"] li'),
      ).toHaveLength(3);
      expect(s('access')).not.toMatch(/Open Access|OA|hybrid|subskrypcj|subscription/);
      source.annual!.standards.access = missing;
      await render(lang);
      expect(s('access')).toBe(lang === 'pl' ? 'Brak deklaracji' : 'No declaration');
    },
  );
  it.each(['FREE_ARTICLES', 'ALL_ARTICLES_PAID'] as const)(
    'uses the annual access declaration %s and keeps it separate from licence/fees',
    async (code) => {
      source.annual!.standards.access = available(code);
      source.annual!.standards.licence = available('CC_BY');
      await render('en');
      expect(s('access')).toBe(
        code === 'FREE_ARTICLES'
          ? 'Free access to all scientific articles'
          : 'Paid access to all scientific articles',
      );
      expect(s('licence')).toBe('CC BY');
    },
  );
  it.each([
    ['CC_BY', 'CC BY'],
    ['CC_BY_SA', 'CC BY-SA'],
    ['CC_BY_ND', 'CC BY-ND'],
    ['CC_BY_NC', 'CC BY-NC'],
    ['CC_BY_NC_SA', 'CC BY-NC-SA'],
    ['CC_BY_NC_ND', 'CC BY-NC-ND'],
  ] as const)('preserves the specific licence %s', async (code, label) => {
    source.annual!.standards.licence = available(code);
    await render();
    expect(s('licence')).toBe(label);
  });
  it.each(['pl', 'en'])(
    'distinguishes licence NONE, author preference and missing declaration (%s)',
    async (lang) => {
      source.annual!.standards.licence = available('NONE');
      await render(lang);
      expect(s('licence')).toBe(
        lang === 'pl'
          ? 'Nie publikuje na licencjach Creative Commons'
          : 'Does not publish under Creative Commons licences',
      );
      source.annual!.standards.licence = available('AUTHOR_PREFERENCE');
      await render(lang);
      expect(s('licence')).toContain(
        lang === 'pl' ? 'preferencjami autora' : "author's preference",
      );
      source.annual!.standards.licence = missing;
      await render(lang);
      expect(s('licence')).toBe(lang === 'pl' ? 'Brak deklaracji' : 'No declaration');
    },
  );
  it('shows social icons without actions and exactly seven standards without information icons', async () => {
    source.annual!.standards.socialMedia = available({
      used: true,
      channels: ['FACEBOOK', 'INSTAGRAM', 'LINKEDIN', 'X', 'YOUTUBE'],
      other: 'https://example.test/mastodon',
    });
    await render();
    expect(
      [...standards.nativeElement.querySelectorAll('[data-standard-field]')].map(
        (e: HTMLElement) => e.dataset['standardField'],
      ),
    ).toEqual([
      'antiPlagiarism',
      'publicCriteria',
      'editorialSystem',
      'reviewing',
      'access',
      'licence',
      'socialMedia',
    ]);
    expect(
      [...standards.nativeElement.querySelectorAll('[role="img"]')].map((e: HTMLElement) =>
        e.getAttribute('aria-label'),
      ),
    ).toEqual(['Facebook', 'Instagram', 'LinkedIn', 'X', 'YouTube']);
    expect(
      standards.nativeElement.querySelector(
        'a,button,[tabindex],dt i,.ri-question-line,.ri-information-line',
      ),
    ).toBeNull();
    expect(s('socialMedia')).toContain('https://example.test/mastodon');
  });
  it.each(['pl', 'en'])(
    'keeps false social declaration, unspecified channels and missing state separate (%s)',
    async (lang) => {
      source.annual!.standards.socialMedia = available({ used: false, channels: [], other: null });
      await render(lang);
      expect(s('socialMedia')).toBe(
        lang === 'pl' ? 'Nie korzysta z mediów społecznościowych' : 'Does not use social media',
      );
      source.annual!.standards.socialMedia = available({ used: true, channels: [], other: null });
      await render(lang);
      expect(s('socialMedia')).toContain(
        lang === 'pl' ? 'kanały niepodane' : 'channels not provided',
      );
      source.annual!.standards.socialMedia = missing;
      await render(lang);
      expect(s('socialMedia')).toBe(lang === 'pl' ? 'Brak deklaracji' : 'No declaration');
    },
  );
  it('does not leak an old annual year into any lower section', async () => {
    source.annual!.process.firstIssueYear = available(1990);
    source.annual!.international.authors = available(90);
    source.annual!.standards.licence = available('CC_BY');
    source.surveyYear = 2025;
    await render();
    expect(p('since')).toBe('Brak danych');
    expect(international.nativeElement.querySelector('svg')).toBeNull();
    expect(s('licence')).toBe('Brak deklaracji');
  });

  it('distinguishes invalid and missing access conditions while preserving the access declaration', async () => {
    source.annual!.standards.access = available('PAID_ARTICLES');
    source.annual!.standards.accessConditions = { status: 'INVALID', value: null };
    await render('en');
    expect(s('access')).toContain('Partially paid access');
    expect(s('access')).toContain('Invalid access conditions');
    source.annual!.standards.accessConditions = missing;
    await render('en');
    expect(s('access')).toContain('Access conditions not provided');
  });
});

describe('Lower sections with asynchronously loaded translations', () => {
  it('updates computed labels after a fast card response preceded the dictionary', async () => {
    const translations = new Subject<typeof pl>();
    class DelayedLoader implements TranslocoLoader {
      getTranslation() {
        return translations.asObservable();
      }
    }
    await TestBed.configureTestingModule({
      imports: [JournalDecisionProcess, JournalDecisionStandards],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            prodMode: true,
            reRenderOnLangChange: true,
          },
          loader: DelayedLoader,
        }),
      ],
    }).compileComponents();
    const source = decisionFeeCard();
    source.annual!.process.firstIssueYear = available(1990);
    source.annual!.process.continuedYear = available(2000);
    source.annual!.process.frequency = available('q');
    source.annual!.standards.antiPlagiarism = available('INDICATED');
    source.annual!.standards.access = available('PAID_ARTICLES');
    source.annual!.standards.accessConditions = available(['publication_date']);
    source.annual!.standards.licence = available('AUTHOR_PREFERENCE');
    const process = TestBed.createComponent(JournalDecisionProcess),
      standards = TestBed.createComponent(JournalDecisionStandards);
    process.componentRef.setInput('card', source);
    standards.componentRef.setInput('card', source);
    process.detectChanges();
    standards.detectChanges();
    translations.next(pl);
    translations.complete();
    await process.whenStable();
    await standards.whenStable();
    process.detectChanges();
    standards.detectChanges();
    expect(process.nativeElement.textContent).toContain('1990 (nieprzerwanie od 2000)');
    expect(process.nativeElement.textContent).toContain('Kwartalnik');
    expect(standards.nativeElement.textContent).toContain('Tak — zadeklarowano stosowanie');
    expect(standards.nativeElement.textContent).toContain(
      'Publikowanie na CC zgodnie z preferencjami autora',
    );
    expect(standards.nativeElement.textContent).not.toContain('decisionCard.');
  });
});
