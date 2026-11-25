import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { NgbConfig } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { DecisionCategory } from '../../../core/services/journal-decision-annual';
import { JournalDecisionReference } from './journal-decision-reference';
import { JournalDecisionBenchmarks } from '../../../core/services/journal-decision-benchmarks';
import { JournalDecisionTimes } from './journal-decision-times';

class Loader implements TranslocoLoader {
  getTranslation(language: string) {
    return of(language === 'en' ? en : pl);
  }
}
const missing = { status: 'MISSING', value: null } as const;
function category(id: number, namePl: string | null, nameEn: string | null): DecisionCategory {
  return { id, code: `C${id}`, main: false, namePl, nameEn, nameRu: null };
}
function card(id = 123, year = 2025): JournalDecisionView {
  return {
    benchmarks: null,
    benchmarksVisible: true,
    surveyYear: year,
    annualStatus: 'AVAILABLE',
    availableSurveyYears: [2025, 2024],
    identity: { id, scientificCategory: { nameEn: 'Catalogue category must not be used' } },
    icv: 81.125,
    icvStatus: 'AVAILABLE',
    mnisw: { year: 2024, listed: false, points: null, disciplines: [], status: 'AVAILABLE' },
    icvHistory: [
      { year: 2021, status: 'INDEXED', value: 0 },
      { year: 2025, status: 'INDEXED', value: 81.125 },
      { year: 2022, status: 'NOT_INDEXED', value: null },
    ],
    annual: {
      surveyYear: year,
      times: { initialAssessment: missing, review: missing, publication: missing },
      process: {
        firstIssueYear: missing,
        continuedYear: missing,
        frequency: missing,
        aheadOfPrint: missing,
        rejectionRate: missing,
        publishingBreak: missing,
      },
      international: { authors: missing, reviewers: missing, scientificCouncil: missing },
      standards: {
        antiPlagiarism: missing,
        publicCriteria: missing,
        editorialSystem: missing,
        reviewing: { models: [], none: null, status: 'MISSING' },
        access: missing,
        accessConditions: missing,
        licence: missing,
        socialMedia: missing,
      },
      profile: {
        mainGroup: {
          status: 'AVAILABLE',
          value: { id: 1008, namePl: 'Nauki medyczne', nameEn: 'Medical sciences', nameRu: null },
        },
        memberships: missing,
        scientificCategories: missing,
        wosCategories: {
          status: 'AVAILABLE',
          value: [
            category(1, 'Zoologia', 'Anthropology'),
            category(2, 'Algebra', 'Zoology'),
            category(3, 'Biologia', 'Biology'),
            category(4, 'Chemia', 'Chemistry'),
          ],
        },
        scopusCategories: {
          status: 'AVAILABLE',
          value: [
            category(5, 'Zoologia', 'Zoology'),
            category(6, 'Algebra', 'Algebra'),
            category(7, 'Biologia', 'Biology'),
            category(8, 'Chemia', 'Chemistry'),
            category(9, 'Medycyna', 'Medicine'),
          ],
        },
      },
      indexing: {
        status: 'AVAILABLE',
        value: ['Zeta', 'Alpha', 'Gamma', 'Beta', 'Eta', 'Delta', 'Epsilon'].map((name, index) => ({
          id: index + 1,
          name,
        })),
      },
      fees: [],
    },
  };
}

describe('JournalDecisionReference own values', () => {
  it.each([
    ['history', 'E03'],
    ['indexing', 'E04'],
    ['wos', 'E05'],
    ['scopus', 'E06'],
  ] as const)(
    'emits %s expansion (%s) only for user open/reopen, never collapse/language/context reset',
    async (key, code) => {
      const emitted = vi.fn();
      fixture.componentInstance.expanded.subscribe(emitted);
      const button = fixture.nativeElement.querySelector(
        `[data-testid="decision-${key}-toggle"]`,
      ) as HTMLButtonElement;
      expect(emitted).not.toHaveBeenCalled();
      button.click();
      await settle();
      button.click();
      await settle();
      expect(emitted.mock.calls).toEqual([[code]]);
      button.click();
      await settle();
      transloco.setActiveLang('en');
      await settle();
      fixture.componentRef.setInput('card', card(456, 2024));
      await settle();
      expect(emitted.mock.calls).toEqual([[code], [code]]);
    },
  );
  let fixture: ComponentFixture<JournalDecisionReference>;
  let source: JournalDecisionView;
  let transloco: TranslocoService;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JournalDecisionReference, JournalDecisionTimes],
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
    TestBed.inject(NgbConfig).animation = false;
    transloco = TestBed.inject(TranslocoService);
    fixture = TestBed.createComponent(JournalDecisionReference);
    source = card();
    fixture.componentRef.setInput('card', source);
    await settle();
  });
  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  function element<T extends HTMLElement = HTMLElement>(selector: string): T {
    return fixture.nativeElement.querySelector(selector);
  }
  function labels(key: string): string[] {
    return [...fixture.nativeElement.querySelectorAll(`#decision-${key}-list li`)].map(
      (item: unknown) => (item as HTMLElement).textContent!.trim(),
    );
  }
  async function click(selector: string) {
    element(selector).click();
    await settle();
  }

  it.each(['journal', 'year'] as const)('keeps MNiSW expansion across language changes and resets it for a new %s', async (context) => {
    source.mnisw = {
      year: 2024, listed: true, points: 0, status: 'AVAILABLE',
      disciplines: [{ code: '2021', namePl: 'Medycyna', nameEn: 'Medicine' }],
    };
    fixture.componentRef.setInput('card', { ...source });
    await settle();
    const emitted = vi.fn();
    fixture.componentInstance.expanded.subscribe(emitted);
    const mnisw = element('[data-testid="decision-mnisw"]');
    expect(mnisw.nextElementSibling).toBe(element('[data-testid="decision-icv"]'));
    expect(mnisw.querySelector('[data-testid="decision-mnisw-points"]')!.textContent).toContain('0 pkt');
    expect(element('[data-testid="decision-mnisw-toggle"]').getAttribute('aria-expanded')).toBe('false');
    await click('[data-testid="decision-mnisw-toggle"]');
    expect(element('#decision-mnisw-disciplines-collapse').classList.contains('show')).toBe(true);
    expect(element('[data-testid="decision-scientific-profile"]').contains(element('[data-testid="decision-mnisw-disciplines"]'))).toBe(false);
    transloco.setActiveLang('en');
    await settle();
    expect(element('[data-testid="decision-mnisw-disciplines"]').textContent).toContain('Medicine');
    expect(element('[data-testid="decision-mnisw-toggle"]').getAttribute('aria-expanded')).toBe('true');
    fixture.componentRef.setInput('card', {
      ...source,
      identity: { ...source.identity, id: context === 'journal' ? 456 : source.identity.id },
      surveyYear: context === 'year' ? 2024 : source.surveyYear,
    });
    await settle();
    expect(element('[data-testid="decision-mnisw-toggle"]').getAttribute('aria-expanded')).toBe('false');
    expect(emitted).not.toHaveBeenCalled();
  });

  function benchmarks(): JournalDecisionBenchmarks {
    const unavailable = { status: 'UNAVAILABLE', value: null } as const;
    return {
      surveyYear: 2025,
      importId: 'build-1',
      groupId: 1008,
      icv: {
        status: 'AVAILABLE',
        value: { lowerPercent: 78.4, equalPercent: 4.2, allEqual: false },
      },
      times: { initialAssessment: unavailable, review: unavailable, publication: unavailable },
      rejectionRate: unavailable,
      international: {
        authors: unavailable,
        reviewers: unavailable,
        scientificCouncil: unavailable,
      },
      fees: [],
    };
  }

  it('wires three independent annual time comparisons in order and rejects an old benchmark year', async () => {
    source.benchmarks = benchmarks();
    source.annual!.times = {
      initialAssessment: { status: 'AVAILABLE', value: 0 },
      review: { status: 'AVAILABLE', value: 20 },
      publication: { status: 'AVAILABLE', value: 50 },
    };
    const comparison = {
      status: 'AVAILABLE',
      value: { q1: 18.25, q3: 32.75, axis: { minimum: 13, maximum: 37 } },
    } as const;
    source.benchmarks.times = {
      initialAssessment: comparison,
      review: comparison,
      publication: comparison,
    };
    const times = TestBed.createComponent(JournalDecisionTimes);
    const render = async () => {
      times.componentRef.setInput('card', { ...source });
      times.detectChanges();
      await times.whenStable();
      times.detectChanges();
    };
    await render();
    const states = () =>
      [...times.nativeElement.querySelectorAll('svg[data-time-state]')].map((svg: SVGElement) =>
        svg.getAttribute('data-time-state'),
      );
    expect(states()).toEqual(['below', 'within', 'above']);
    source.benchmarks.times.review = { status: 'UNAVAILABLE', value: null };
    await render();
    expect(states()).toEqual(['below', 'above']);
    expect(times.nativeElement.textContent).toContain('20 dni');
    source.surveyYear = 2024;
    await render();
    expect(states()).toEqual([]);
    expect(times.nativeElement.textContent).toContain('0 dni');
    expect(times.nativeElement.textContent).toContain('50 dni');
  });

  it.each(['pl', 'en'])(
    'shows a graphical ICV comparison with rounded lower/tie shares and preserves ICV/history (%s)',
    async (language) => {
      source.benchmarks = benchmarks();
      fixture.componentRef.setInput('card', { ...source });
      transloco.setActiveLang(language);
      await settle();
      expect(element('[data-testid="decision-icv-lower"]').textContent).toContain('78%');
      expect(element('[data-testid="decision-icv-equal"]').textContent).toContain('4%');
      expect(element('[data-testid="decision-icv-indicator"] .progress-bar').style.width).toBe(
        '78.4%',
      );
      expect(
        element('[data-testid="decision-icv-indicator"]').getAttribute('aria-label'),
      ).toContain('78%');
      expect(element('[data-testid="decision-icv"] p').textContent).toContain(
        language === 'pl' ? '81,13' : '81.13',
      );
      await click('[data-testid="decision-history-toggle"]');
      expect(element('#decision-icv-history-collapse').classList.contains('show')).toBe(true);
      expect(element('[data-testid="decision-icv-lower"]').textContent).toContain('78%');
    },
  );

  it('uses bounded percentage labels without changing the raw indicator position', async () => {
    source.benchmarks = benchmarks();
    source.benchmarks.icv.value = { lowerPercent: 99.8, equalPercent: 0.2, allEqual: false };
    fixture.componentRef.setInput('card', { ...source });
    await settle();
    expect(element('[data-testid="decision-icv-lower"]').textContent).toContain('>99%');
    expect(element('[data-testid="decision-icv-equal"]').textContent).toContain('<1%');
    expect(element('[data-testid="decision-icv-indicator"] .progress-bar').style.width).toBe(
      '99.8%',
    );
  });

  it.each(['pl', 'en'])(
    'replaces all ordinary comparisons and the indicator only for available all-equal ICV (%s)',
    async (language) => {
      source.benchmarks = benchmarks();
      source.benchmarks.icv.value = { lowerPercent: 0, equalPercent: 100, allEqual: true };
      fixture.componentRef.setInput('card', { ...source });
      transloco.setActiveLang(language);
      await settle();
      expect(element('[data-testid="decision-icv-all-equal"]').textContent).toContain(
        language === 'pl'
          ? 'ICV identyczny jak u 100% czasopism w grupie porównawczej'
          : 'ICV identical to 100% of journals in the comparison group',
      );
      expect(element('[data-testid="decision-icv-indicator"]')).toBeNull();
      expect(element('[data-testid="decision-icv-lower"]')).toBeNull();
      expect(element('[data-testid="decision-icv-equal"]')).toBeNull();
      expect(element('[data-testid="decision-history-toggle"]')).not.toBeNull();
      source.benchmarks.icv.status = 'UNAVAILABLE';
      fixture.componentRef.setInput('card', { ...source });
      await settle();
      expect(element('[data-testid="decision-icv-comparison"]')).toBeNull();
      expect(element('[data-testid="decision-icv"] p').textContent).toContain(
        language === 'pl' ? '81,13' : '81.13',
      );
    },
  );

  it('omits comparisons for missing own ICV or benchmark year mismatch without suppressing history', async () => {
    source.benchmarks = benchmarks();
    source.benchmarks.surveyYear = 2024;
    fixture.componentRef.setInput('card', { ...source });
    await settle();
    expect(element('[data-testid="decision-icv-comparison"]')).toBeNull();
    source.benchmarks.surveyYear = 2025;
    source.icv = null;
    source.icvStatus = 'MISSING';
    fixture.componentRef.setInput('card', { ...source });
    await settle();
    expect(element('[data-testid="decision-icv-comparison"]')).toBeNull();
    expect(element('[data-testid="decision-history-toggle"]')).not.toBeNull();
  });

  it.each(['pl', 'en'])(
    'preserves indexing labels and keeps the accordion control below the same database tiles (%s)',
    async (language) => {
      transloco.setActiveLang(language);
      await settle();
      const list = element('#decision-indexing-list');
      const button = element<HTMLButtonElement>('[data-testid="decision-indexing-toggle"]');
      const body = element('#decision-indexing-more-collapse');
      const initialTiles = [...list.querySelectorAll('li')];
      expect(initialTiles).toHaveLength(5);
      expect(button.classList.contains('accordion-button')).toBe(true);
      expect(button.closest('.accordion-flush.lefticon-accordion')).not.toBeNull();
      expect(button.getAttribute('aria-controls')).toBe(body.id);
      expect(body.getAttribute('aria-labelledby')).toBe(button.id);
      expect(button.textContent!.trim()).toBe(
        transloco.translate('decisionCard.moreCount', { count: 2 }),
      );
      expect(body.classList.contains('show')).toBe(false);
      button.click();
      await settle();
      expect(body.classList.contains('show')).toBe(true);
      expect(button.textContent!.trim()).toBe(transloco.translate('decisionCard.less'));
      const tiles = [...list.querySelectorAll('li')];
      expect(tiles).toHaveLength(7);
      expect(tiles.slice(0, 5)).toEqual(initialTiles);
      for (const tile of tiles) {
        expect(tile.matches('.bg-light.rounded.p-2')).toBe(true);
        expect(
          tile.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING,
        ).toBeTruthy();
      }
      button.click();
      await settle();
      expect(labels('indexing')).toEqual(['Alpha', 'Beta', 'Delta', 'Epsilon', 'Eta']);
      expect(body.classList.contains('show')).toBe(false);
    },
  );

  it.each([0, 1, 5])(
    'omits the indexing accordion when all %s databases already fit',
    async (count) => {
      source.annual!.indexing.value = source.annual!.indexing.value!.slice(0, count);
      fixture.componentRef.setInput('card', { ...source });
      await settle();
      expect(labels('indexing')).toHaveLength(count);
      expect(element('[data-testid="decision-indexing-toggle"]')).toBeNull();
      expect(element('#decision-indexing-more')).toBeNull();
    },
  );

  it.each(['pl', 'en'])(
    'puts main categories first, sorts the others before applying limits and expands independently (%s)',
    async (language) => {
      source.annual!.profile.wosCategories.value![3].main = true;
      source.annual!.profile.scopusCategories.value![4].main = true;
      fixture.componentRef.setInput('card', { ...source });
      transloco.setActiveLang(language);
      await settle();
      const original = JSON.stringify(source);
      expect(labels('indexing')).toEqual(['Alpha', 'Beta', 'Delta', 'Epsilon', 'Eta']);
      expect(labels('wos')).toEqual(
        language === 'pl'
          ? ['Chemia', 'Algebra', 'Biologia']
          : ['Chemistry', 'Anthropology', 'Biology'],
      );
      expect(labels('scopus')).toEqual(
        language === 'pl'
          ? ['Medycyna', 'Algebra', 'Biologia']
          : ['Medicine', 'Algebra', 'Biology'],
      );
      expect(element('[data-testid="decision-indexing-toggle"]').textContent).toContain('+2');
      expect(element('[data-testid="decision-wos-toggle"]').textContent).toContain('+1');
      await click('[data-testid="decision-wos-toggle"]');
      expect(labels('wos')).toHaveLength(4);
      expect(labels('scopus')).toHaveLength(3);
      expect(labels('indexing')).toHaveLength(5);
      await click('[data-testid="decision-indexing-toggle"]');
      await click('[data-testid="decision-scopus-toggle"]');
      expect(labels('indexing')).toEqual([
        'Alpha',
        'Beta',
        'Delta',
        'Epsilon',
        'Eta',
        'Gamma',
        'Zeta',
      ]);
      expect(labels('scopus')).toHaveLength(5);
      expect(
        element('[data-testid="decision-indexing-toggle"]').getAttribute('aria-expanded'),
      ).toBe('true');
      await click('[data-testid="decision-indexing-toggle"]');
      expect(labels('indexing')).toHaveLength(5);
      expect(labels('wos')).toHaveLength(4);
      expect(
        element('[data-testid="decision-scientific-profile"]').querySelector('i, svg, img'),
      ).toBeNull();
      const profileText = element('[data-testid="decision-scientific-profile"]').textContent!;
      const group = language === 'pl' ? 'Nauki medyczne' : 'Medical sciences';
      expect(profileText.split(group)).toHaveLength(2);
      expect(profileText).not.toContain('Catalogue category');
      expect(profileText).not.toContain('1008');
      expect(JSON.stringify(source)).toBe(original);
    },
  );

  it.each(['pl', 'en'])(
    'formats current and historical ICV to two decimal places with status legends (%s)',
    async (language) => {
      transloco.setActiveLang(language);
      await settle();
      expect(element('[data-testid="decision-icv"] p').textContent).toContain(
        language === 'pl' ? 'ICV 2025: 81,13' : 'ICV 2025: 81.13',
      );
      const button = element('[data-testid="decision-history-toggle"]');
      const history = element('#decision-icv-history-collapse');
      expect(button.classList.contains('accordion-button')).toBe(true);
      expect(button.closest('.accordion-flush')).not.toBeNull();
      expect(button.getAttribute('aria-controls')).toBe(history.id);
      expect(history.getAttribute('aria-labelledby')).toBe(button.id);
      expect(history.classList.contains('show')).toBe(false);
      await click('[data-testid="decision-history-toggle"]');
      expect(element('#decision-icv-history-collapse').classList.contains('show')).toBe(true);
      const rows = [
        ...fixture.nativeElement.querySelectorAll('#decision-icv-history-collapse tbody tr'),
      ] as HTMLElement[];
      expect(rows.map((row) => row.querySelector('th')!.textContent!.trim())).toEqual([
        '2025',
        '2022',
        '2021',
      ]);
      expect(rows[0].querySelector('td')!.textContent!.trim()).toBe(
        language === 'pl' ? '81,13' : '81.13',
      );
      expect(rows[1].querySelector('td')!.textContent!.trim()).toBe('N/I');
      expect(rows[2].querySelector('td')!.textContent!.trim()).toBe(
        language === 'pl' ? '0,00' : '0.00',
      );
      const legend = element('[data-testid="decision-icv-history-legend"]').textContent!;
      expect(legend).toContain(
        language === 'pl'
          ? 'N/I - czasopismo nie spełniło kryteriów indeksacji'
          : 'N/I - the journal did not meet the indexation criteria',
      );
      expect(legend).toContain(
        language === 'pl'
          ? 'N/E - czasopismo nie zostało zgłoszone do oceny'
          : 'N/E - the journal was not submitted for evaluation',
      );
      expect(element('[data-testid="decision-icv"]').textContent).not.toContain('%');
      await click('[data-testid="decision-history-toggle"]');
      expect(element('#decision-icv-history-collapse').classList.contains('show')).toBe(false);
    },
  );

  it.each([
    [456, 2025],
    [123, 2024],
  ])(
    'resets history and all expanded lists when journal or year changes (%s/%s)',
    async (id, year) => {
      await click('[data-testid="decision-history-toggle"]');
      for (const key of ['indexing', 'wos', 'scopus'])
        await click(`[data-testid="decision-${key}-toggle"]`);
      fixture.componentRef.setInput('card', card(id, year));
      await settle();
      expect(element('#decision-icv-history-collapse').classList.contains('show')).toBe(false);
      expect(labels('indexing')).toHaveLength(5);
      expect(labels('wos')).toHaveLength(3);
      expect(labels('scopus')).toHaveLength(3);
    },
  );

  it('relocalizes and resorts the expanded categories without closing history or lists', async () => {
    await click('[data-testid="decision-wos-toggle"]');
    await click('[data-testid="decision-history-toggle"]');
    transloco.setActiveLang('en');
    await settle();
    expect(labels('wos')).toEqual(['Anthropology', 'Biology', 'Chemistry', 'Zoology']);
    expect(element('#decision-icv-history-collapse').classList.contains('show')).toBe(true);
    expect(element('[data-testid="decision-wos-toggle"]').getAttribute('aria-expanded')).toBe(
      'true',
    );
  });

  it('keeps valid local values when the group, a category collection or ICV is unavailable', async () => {
    source.icv = null;
    source.icvStatus = 'INVALID';
    source.annual!.profile.mainGroup = { status: 'INVALID', value: null };
    source.annual!.profile.scopusCategories = missing;
    source.annual!.indexing = { status: 'INVALID', value: [{ id: 1, name: 'Known database' }] };
    fixture.componentRef.setInput('card', { ...source });
    await settle();
    expect(element('[data-testid="decision-main-group"]').textContent).toContain('Brak danych');
    expect(element('[data-testid="decision-icv"] p').textContent).toContain('ICV 2025: —');
    expect(labels('indexing')).toEqual(['Known database']);
    expect(element('[data-testid="decision-indexing"]').textContent).toContain('Niepełne dane');
    expect(labels('wos')).toHaveLength(3);
    expect(element('[data-testid="decision-scopus"]').textContent).toContain('Brak danych');
    expect(element('[data-testid="decision-scopus-toggle"]')).toBeNull();
    expect(element('.alert')).toBeNull();
  });

  it.each([0, 100])(
    'shows integer ICV %s with two decimal places and omits history controls when there are no evaluated years',
    async (icv) => {
      source.icv = icv;
      source.icvHistory = [];
      source.annual!.profile.wosCategories = {
        status: 'AVAILABLE',
        value: [category(1, null, 'English fallback'), category(2, null, null)],
      };
      fixture.componentRef.setInput('card', { ...source });
      await settle();
      expect(element('[data-testid="decision-icv"] p').textContent!.trim()).toBe(
        `ICV 2025: ${icv},00`,
      );
      expect(element('[data-testid="decision-history-toggle"]')).toBeNull();
      expect(labels('wos')).toEqual(['C2', 'English fallback']);
    },
  );
});
