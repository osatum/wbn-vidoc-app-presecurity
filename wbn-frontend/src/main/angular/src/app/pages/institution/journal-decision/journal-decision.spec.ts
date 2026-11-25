import { Location } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { NgbConfig } from '@ng-bootstrap/ng-bootstrap';
import { BehaviorSubject, filter, firstValueFrom, of, throwError } from 'rxjs';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import {
  ConfigurationService,
  OperatingContextDto,
  UserProfileResponse,
} from '../../../core/backend';
import { journalDecisionGuard } from '../../../core/guards/journal-decision.guard';
import { AuthService } from '../../../core/services/auth.service';
import {
  JournalDecisionApi,
  JournalDecisionView,
} from '../../../core/services/journal-decision-api';
import { JournalDecisionAnnual } from '../../../core/services/journal-decision-annual';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';
import { OperatingProfileService } from '../../../core/services/operating-profile.service';
import { routes as institutionRoutes } from '../institution-routes';
import { JournalDetails } from '../journal-details/journal-details';
import { JournalDecision } from './journal-decision';
import { decisionFee, decisionFeeCard } from './journal-decision-fees.fixtures';

@Component({ template: '' })
class OtherPage {}
class Loader implements TranslocoLoader {
  getTranslation(lang: string) {
    return of(lang === 'en' ? en : pl);
  }
}
const ROOT = '/institution/user/journals';
const CARD: JournalDecisionView = {
  surveyYear: 2024,
  annualStatus: 'AVAILABLE',
  availableSurveyYears: [2025, 2024],
  icv: 81.125,
  icvStatus: 'AVAILABLE',
  mnisw: { year: 2024, listed: false, points: null, disciplines: [], status: 'AVAILABLE' },
  icvHistory: [],
  annual: null,
  benchmarks: null,
  benchmarksVisible: true,
  identity: {
    id: 123,
    titleOriginal: 'Catalogue title',
    titleEnglish: 'English title',
    descriptions: [
      { type: 'PL', content: 'Polski opis' },
      { type: 'EN', content: 'English description' },
    ],
    cardinality: { numberOfPublications: 0, percentFullText: 0, percentAbstracts: 100 },
    country: { code: 'pl', namePl: 'Polska', nameEn: 'Poland' },
  },
};

describe('JournalDecision routing and skeleton', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let currentContext: ReturnType<typeof vi.fn>;
  let context: OperatingContextDto;
  let profile: BehaviorSubject<UserProfileResponse | null>;
  let track: ReturnType<typeof vi.fn>;
  let flush: ReturnType<typeof vi.fn>;
  let getJournalLinks: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    context = {
      institutionId: 10,
      configuredProfile: null,
      defaultProfile: 'BOTH',
      effectiveProfile: 'BOTH',
      availableFeatures: ['BASIC_SEARCH', 'ADVANCED_SEARCH', 'DECISION_CARD'],
      defaultSearchView: 'ADVANCED',
    };
    currentContext = vi.fn(() => of(context));
    profile = new BehaviorSubject<UserProfileResponse | null>({ userId: 1, institutionId: 10 });
    track = vi.fn();
    flush = vi.fn();
    getJournalLinks = vi.fn(() =>
      of({
        issuesUrlTemplate: 'https://journals.example.test/issues?issueId=all&journalId={journalId}',
      }),
    );
    await TestBed.configureTestingModule({
      imports: [JournalDecision, OtherPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideLocationMocks(),
        provideRouter([
          {
            path: 'institution/user/journals/:id/decision',
            component: JournalDecision,
            canActivate: [journalDecisionGuard],
            runGuardsAndResolvers: 'always',
          },
          { path: 'institution/user/journals/:id', component: OtherPage },
          { path: 'institution/user/journals', component: OtherPage },
          { path: 'institution/user/denied', component: OtherPage },
        ]),
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            prodMode: true,
            reRenderOnLangChange: true,
          },
          loader: Loader,
        }),
        { provide: OperatingProfileService, useValue: { currentContext } },
        { provide: AuthService, useValue: { getUserProfile: () => profile } },
        { provide: NavigationAuditService, useValue: { track, flush } },
        { provide: ConfigurationService, useValue: { getJournalLinks } },
      ],
    }).compileComponents();
    TestBed.inject(NgbConfig).animation = false;
    router = TestBed.inject(Router);
    router.setUpLocationChangeListener();
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => http.verify());
  async function settle() {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }
  function toggleSection(section: string) {
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>(
        `[data-decision-section="${section}"] button`,
      )!
      .click();
  }
  function request(id = 123) {
    return http.expectOne((value) => value.url.endsWith(`/api/journal/journal/${id}/decision`));
  }
  async function enter(card: JournalDecisionView = CARD) {
    const page = await harness.navigateByUrl(
      `${ROOT}/${card.identity.id}/decision`,
      JournalDecision,
    );
    request(card.identity.id).flush(card);
    await settle();
    return page;
  }

  function actions() {
    return track.mock.calls
      .map(([event]) => event)
      .filter((event) => event.eventType === 'JOURNAL_DECISION_ACTION');
  }

  it.each([
    ['pl', 'pl'],
    ['pl', 'tr'],
    ['en', 'pl'],
    ['en', 'tr'],
  ])(
    'hides every comparison globally, preserves own values and restores comparisons on demand (%s/%s)',
    async (lang, country) => {
      TestBed.inject(TranslocoService).setActiveLang(lang);
      const source = decisionFeeCard();
      source.identity = { ...CARD.identity, country: { code: country } };
      source.icvHistory = [{ year: 2024, status: 'INDEXED', value: 81.125 }];
      const own = (value: number) => ({ status: 'AVAILABLE', value }) as const;
      source.annual!.times = { initialAssessment: own(0), review: own(15), publication: own(30) };
      source.annual!.process.rejectionRate = own(25);
      source.annual!.international = {
        authors: own(0),
        reviewers: own(45),
        scientificCouncil: own(100),
      };
      source.annual!.profile.mainGroup = {
        status: 'AVAILABLE',
        value: {
          id: 1008,
          namePl: 'Nauki medyczne',
          nameEn: 'Medical sciences',
          nameRu: null,
        },
      };
      source.annual!.fees[1] = decisionFee({
        category: 'PUBLICATION',
        type: 'ONE_FEE',
        charged: true,
        minimum: 150,
        maximum: 150,
        currency: 'EUR',
      });
      const comparison = {
        status: 'AVAILABLE',
        value: { q1: 10, q3: 20, axis: { minimum: 0, maximum: 40 } },
      } as const;
      source.benchmarks!.icv = {
        status: 'AVAILABLE',
        value: { lowerPercent: 80, equalPercent: 5, allEqual: false },
      };
      source.benchmarks!.times = {
        initialAssessment: comparison,
        review: comparison,
        publication: comparison,
      };
      source.benchmarks!.rejectionRate = { status: 'AVAILABLE', value: { q1: 20, q3: 40 } };
      source.benchmarks!.international = {
        authors: { status: 'AVAILABLE', value: { median: 60 } },
        reviewers: { status: 'AVAILABLE', value: { median: 60 } },
        scientificCouncil: { status: 'AVAILABLE', value: { median: 60 } },
      };
      source.benchmarks!.fees.forEach(
        (fee) => (fee.comparison = { status: 'AVAILABLE', value: { chargedPercent: 50 } }),
      );
      const page = await enter({ ...source, benchmarksVisible: false });
      const root = harness.routeNativeElement!;
      const comparisonSelector =
        '[data-testid="decision-icv-comparison"], [data-testid="decision-fee-percent"], [data-testid="decision-fees-explanation"], [data-testid="decision-rejection-benchmark"], app-journal-decision-times svg, app-journal-decision-international svg, [data-testid="decision-share-median"]';
      expect(root.querySelectorAll(comparisonSelector)).toHaveLength(0);
      expect(root.textContent).not.toContain(
        lang === 'pl' ? 'grupa odniesienia' : 'reference group',
      );
      for (const section of ['time', 'costs', 'process', 'international', 'standards'] as const)
        toggleSection(section);
      root.querySelector<HTMLButtonElement>('[data-testid="decision-history-toggle"]')!.click();
      await settle();
      expect(page.openSections().size).toBe(5);
      expect(root.querySelectorAll('[data-standard-field]')).toHaveLength(7);
      expect(root.querySelectorAll('.fee-tile')).toHaveLength(5);
      expect(root.querySelector('[data-testid="decision-fee-value"]')!.textContent).toContain(
        lang === 'pl' ? 'Brak' : 'None',
      );
      expect(root.querySelector('[data-fee-category="PUBLICATION"]')!.textContent).toContain('EUR');
      expect(root.querySelector('#decision-icv-history-collapse')!.classList.contains('show')).toBe(
        true,
      );
      const ownSelector =
        '[data-testid="decision-own-days"], [data-testid="decision-own-share"], [data-testid="decision-fee-value"], [data-process-field="rejection"] dd, [data-testid="decision-deposit"], [data-testid="decision-icv"] > .card-body > p';
      const ownValues = () =>
        [...root.querySelectorAll(ownSelector)].map((node) =>
          node.textContent!.replace(/\s+/g, ' ').trim(),
        );
      const hiddenValues = ownValues();
      expect(root.querySelectorAll('app-journal-decision-time-comparison.own-only')).toHaveLength(
        3,
      );
      root.querySelector<HTMLAnchorElement>('[data-testid="decision-issues"]')!.click();
      expect(actions().filter((event) => event.action === 'E14')).toHaveLength(1);

      page.retry();
      request().flush({ ...source, benchmarksVisible: true });
      await settle();
      expect(root.querySelectorAll('app-journal-decision-times svg')).toHaveLength(3);
      expect(root.querySelectorAll('app-journal-decision-international svg')).toHaveLength(3);
      expect(root.querySelectorAll('[data-testid="decision-fee-percent"]')).toHaveLength(5);
      expect(root.querySelector('[data-testid="decision-fees-explanation"]')).not.toBeNull();
      expect(root.querySelector('[data-testid="decision-icv-comparison"]')).not.toBeNull();
      expect(root.querySelector('[data-testid="decision-rejection-benchmark"]')).not.toBeNull();
      expect(root.textContent).toContain(lang === 'pl' ? 'grupa odniesienia' : 'reference group');
      page.retry();
      await settle();
      expect(root.querySelectorAll(comparisonSelector)).toHaveLength(0);
      request().flush({ ...source, benchmarksVisible: false });
      await settle();
      expect(root.querySelectorAll(comparisonSelector)).toHaveLength(0);
      expect(ownValues()).toEqual(hiddenValues);
      expect(root.querySelectorAll('.fee-tile')).toHaveLength(5);
      expect(page.openSections().size).toBe(5);
    },
  );

  it.each(['pl', 'en'])(
    'opens configured issues for the current journal and preserves all five deposit values: %s',
    async (lang) => {
      TestBed.inject(TranslocoService).setActiveLang(lang);
      await enter({
        ...CARD,
        identity: {
          ...CARD.identity,
          cardinality: {
            numberOfPublications: 69,
            percentFullText: 0,
            percentAbstracts: 96,
            percentKeywords: 95,
            percentReferences: 9,
          },
        },
      });
      const deposit = harness.routeNativeElement!.querySelector(
        '[data-testid="decision-deposit"]',
      )!;
      const link = deposit.querySelector<HTMLAnchorElement>('[data-testid="decision-issues"]')!;
      expect(link.href).toBe('https://journals.example.test/issues?issueId=all&journalId=123');
      expect(link.target).toBe('_blank');
      expect(link.rel).toBe('noopener noreferrer');
      expect(link.textContent).toContain(
        lang === 'pl' ? 'Wydania i zawartość' : 'Issues and contents',
      );
      expect(link.getAttribute('aria-label')).toContain(lang === 'pl' ? 'nowej karcie' : 'new tab');
      const publications = deposit.querySelector<HTMLAnchorElement>(
        '[data-testid="decision-publications-link"]',
      )!;
      expect(publications.textContent).toMatch(/:\s*69/);
      expect(publications.href).toBe(link.href);
      expect(publications.target).toBe('_blank');
      expect(publications.rel).toBe('noopener noreferrer');
      expect(publications.getAttribute('aria-label')).toBe(
        (lang === 'pl' ? pl : en).common.depositedPublicationsNewTab,
      );
      expect(deposit.querySelectorAll('a')).toHaveLength(2);
      for (const value of ['69', '0%', '96%', '95%', '9%'])
        expect(deposit.textContent).toContain(value);
      link.click();
      expect(router.url).toBe(ROOT + '/123/decision');
      expect(actions().map((event) => event.action)).toEqual(['E01', 'E14']);
      expect(actions()[1]).toMatchObject({
        pageKey: 'institution.journals.decision',
        resource: { type: 'journal', id: '123' },
        properties: { journalId: 123, surveyYear: 2024 },
      });
      expect(flush).toHaveBeenCalledTimes(1);
      await harness.navigateByUrl(ROOT + '/456/decision', JournalDecision);
      request(456).flush({ ...CARD, identity: { ...CARD.identity, id: 456 } });
      await settle();
      expect(
        harness.routeNativeElement!.querySelector<HTMLAnchorElement>(
          '[data-testid="decision-issues"]',
        )!.href,
      ).toBe('https://journals.example.test/issues?issueId=all&journalId=456');
      expect(
        harness.routeNativeElement!.querySelector<HTMLAnchorElement>(
          '[data-testid="decision-publications-link"]',
        )!.href,
      ).toBe('https://journals.example.test/issues?issueId=all&journalId=456');
    },
  );

  it.each(['decision-issues', 'decision-publications-link'])(
    'records one E14 for each click or middle click on %s and keeps E13 separate',
    async (testId) => {
      await enter({
        ...CARD,
        identity: { ...CARD.identity, website: 'https://journal.example.test/' },
      });
      const root = harness.routeNativeElement!;
      const link = root.querySelector<HTMLAnchorElement>(`[data-testid="${testId}"]`)!;
      link.click();
      link.dispatchEvent(new MouseEvent('click', { button: 0, ctrlKey: true }));
      link.dispatchEvent(new MouseEvent('auxclick', { button: 1 }));
      link.dispatchEvent(new MouseEvent('auxclick', { button: 2 }));
      root.querySelector<HTMLAnchorElement>('[data-testid="decision-website"]')!.click();
      expect(actions().map((event) => event.action)).toEqual(['E01', 'E14', 'E14', 'E14', 'E13']);
      expect(flush).toHaveBeenCalledTimes(3);
    },
  );

  it('keeps a disabled issues button when configuration fails and reloads it on retry', async () => {
    getJournalLinks.mockReturnValue(throwError(() => new Error('unavailable')));
    const page = await enter();
    expect(harness.routeNativeElement!.querySelector('[data-testid="decision-issues"]')).toBeNull();
    expect(
      harness.routeNativeElement!.querySelector('[data-testid="decision-publications-link"]'),
    ).toBeNull();
    expect(
      harness.routeNativeElement!.querySelector('[data-testid="decision-deposit"]')!.textContent,
    ).toMatch(/Zdeponowane publikacje:\s*0/);
    expect(
      harness.routeNativeElement!.querySelector<HTMLButtonElement>(
        '[data-testid="decision-issues-unavailable"]',
      )!.disabled,
    ).toBe(true);
    expect(harness.routeNativeElement!.textContent).toContain('Catalogue title');
    getJournalLinks.mockReturnValue(
      of({ issuesUrlTemplate: 'https://journals.example.test/issues/{journalId}' }),
    );
    page.retry();
    request().flush(CARD);
    await settle();
    expect(
      harness.routeNativeElement!.querySelector<HTMLAnchorElement>(
        '[data-testid="decision-issues"]',
      )!.href,
    ).toBe('https://journals.example.test/issues/123');
    expect(
      harness.routeNativeElement!.querySelector<HTMLAnchorElement>(
        '[data-testid="decision-publications-link"]',
      )!.href,
    ).toBe('https://journals.example.test/issues/123');
  });

  it.each(['decision-issues', 'decision-publications-link'])(
    'keeps %s usable when analytics fails',
    async (testId) => {
      await enter();
      track.mockImplementation(() => {
        throw new Error('audit unavailable');
      });
      const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>(
        `[data-testid="${testId}"]`,
      )!;
      expect(() => link.click()).not.toThrow();
      expect(link.target).toBe('_blank');
      expect(link.href).toContain('journalId=123');
    },
  );

  it('waits for completed navigation even when the API responds synchronously', async () => {
    const get = vi.spyOn(TestBed.inject(JournalDecisionApi), 'get').mockReturnValue(of(CARD));
    await harness.navigateByUrl(`${ROOT}/123/decision`, JournalDecision);
    await settle();
    expect(actions().map((event) => event.action)).toEqual(['E01']);
    expect(get).toHaveBeenCalledTimes(1);
  });

  it('records E01 after rendering with the returned year and verified source, never on loading/error or reactive changes/retry', async () => {
    vi.spyOn(TestBed.inject(JournalListQueryStateService), 'decisionAuditSource').mockReturnValue({
      sourceSearchView: 'BASIC',
      sourceSurveyYear: 2025,
    });
    const page = await harness.navigateByUrl(`${ROOT}/123/decision`, JournalDecision);
    expect(actions()).toHaveLength(0);
    request().flush({}, { status: 500, statusText: 'error' });
    await settle();
    expect(actions()).toHaveLength(0);
    page.retry();
    request().flush(CARD);
    await settle();
    expect(harness.routeNativeElement!.querySelector('#decision-title')).not.toBeNull();
    expect(actions()).toEqual([
      expect.objectContaining({
        action: 'E01',
        pageKey: 'institution.journals.decision',
        routeTemplate: '/institution/user/journals/:id/decision',
        resource: { type: 'journal', id: '123' },
        properties: {
          schemaVersion: 1,
          journalId: 123,
          surveyYear: 2024,
          sourceSearchView: 'BASIC',
          sourceSurveyYear: 2025,
        },
      }),
    ]);
    TestBed.inject(TranslocoService).setActiveLang('en');
    await settle();
    expect(harness.routeNativeElement!.getAttribute('lang')).toBe('en');
    harness.detectChanges();
    page.retry();
    request().flush(CARD);
    await settle();
    expect(actions()).toHaveLength(1);
  });

  it('records a fresh E01 for another navigation and actor without inheriting the source', async () => {
    const page = await enter();
    expect(actions()[0].properties.sourceSearchView).toBeUndefined();
    await enter({ ...CARD, identity: { id: 456 } });
    expect(actions().map((event) => event.resource.id)).toEqual(['123', '456']);
    profile.next({ userId: 2, institutionId: 10 });
    request(456).flush({ ...CARD, identity: { id: 456 } });
    await settle();
    expect(actions()).toHaveLength(3);
    expect(page.openSections().size).toBe(0);
  });

  it.each([
    ['time', 'E08'],
    ['costs', 'E09'],
    ['standards', 'E10'],
    ['process', 'E11'],
    ['international', 'E12'],
  ] as const)('records %s only on actual opening and reopening (%s)', async (section, code) => {
    await enter(decisionFeeCard());
    const button = harness.routeNativeElement!.querySelector<HTMLButtonElement>(
      `[data-decision-section="${section}"] button`,
    )!;
    expect(actions().map((event) => event.action)).toEqual(['E01']);
    button.click();
    await settle();
    button.click();
    await settle();
    expect(actions().map((event) => event.action)).toEqual(['E01', code]);
    TestBed.inject(TranslocoService).setActiveLang('en');
    await settle();
    button.click();
    await settle();
    expect(actions().map((event) => event.action)).toEqual(['E01', code, code]);
  });

  it('keeps reference expansion events independent of the collapsed journal details', async () => {
    await enter({ ...CARD, icvHistory: [{ year: 2024, status: 'INDEXED', value: 81.125 }] });
    const root = harness.routeNativeElement!;
    const button = root.querySelector<HTMLButtonElement>(
      '[data-testid="decision-history-toggle"]',
    )!;
    button.click();
    await settle();
    button.click();
    await settle();
    button.click();
    await settle();
    expect(root.querySelector('app-journal-information-tabs')).toBeNull();
    expect(root.querySelector('[data-testid="decision-description"]')!.classList.contains('show')).toBe(false);
    expect(actions().map((event) => event.action)).toEqual(['E01', 'E03', 'E03']);
  });

  it('shows the complete long description, audits user expansion and resets details for another journal', async () => {
    const description = 'Long description. '.repeat(100).trim();
    await enter({ ...CARD, identity: { ...CARD.identity, descriptions: [{ type: 'PL', content: description }] } });
    const root = harness.routeNativeElement!;
    const toggle = root.querySelector<HTMLButtonElement>('[data-testid="decision-description-toggle"]')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(actions().map(event => event.action)).toEqual(['E01']);
    toggle.click();
    await settle();
    expect(root.querySelector('[data-testid="decision-description"] p')!.textContent!.trim()).toBe(description);
    expect(root.querySelector('[data-testid="decision-description"]')!.classList.contains('show')).toBe(true);
    expect(root.querySelector('[data-testid="decision-deposit"]')).not.toBeNull();
    toggle.click();
    await settle();
    expect(actions().filter(event => event.action === 'E02')).toHaveLength(1);
    toggle.click();
    await settle();
    expect(actions().filter(event => event.action === 'E02')).toHaveLength(2);
    await enter({ ...CARD, identity: { ...CARD.identity, id: 456 } });
    expect(harness.routeNativeElement!.querySelector('[data-testid="decision-description-toggle"]')!.getAttribute('aria-expanded')).toBe('false');
    expect(actions().filter(event => event.action === 'E02')).toHaveLength(2);
  });

  it.each(['pl', 'en'])('renders journal details in the selected language (%s)', async language => {
    TestBed.inject(TranslocoService).setActiveLang(language);
    await enter();
    const root = harness.routeNativeElement!;
    const toggle = root.querySelector<HTMLButtonElement>('[data-testid="decision-description-toggle"]')!;
    expect(toggle.textContent).toContain(language === 'pl' ? pl.decisionCard.moreAboutJournal : en.decisionCard.moreAboutJournal);
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    await settle();
    const expected = CARD.identity.descriptions!.find(item => item.type === language.toUpperCase())!.content;
    expect(root.querySelector('[data-testid="decision-description"] p')!.textContent!.trim()).toBe(expected);
    expect(actions().map(event => event.action)).toEqual(['E01', 'E02']);
  });

  it.each(['pl', 'en'])(
    'keeps both navigation links in the original header and the reference group in the right column (%s)',
    async (lang) => {
      TestBed.inject(TranslocoService).setActiveLang(lang);
      const source = decisionFeeCard();
      source.annual!.profile.mainGroup = {
        status: 'AVAILABLE',
        value: { id: 1008, namePl: 'Nauki medyczne', nameEn: 'Medical sciences', nameRu: null },
      };
      await enter(source);
      const root = harness.routeNativeElement!;
      expect(
        [...root.querySelectorAll('.decision-identity > header nav a')].map((link) =>
          link.getAttribute('data-testid'),
        ),
      ).toEqual(['decision-return', 'decision-full-data']);
      expect(
        root.querySelector('.decision-reference [data-testid="decision-main-group"]')?.textContent,
      ).toContain(lang === 'pl' ? 'Nauki medyczne' : 'Medical sciences');
      expect(
        root.querySelector('.decision-navigation, [data-testid="decision-details-note"]'),
      ).toBeNull();
      expect(actions().map((event) => event.action)).toEqual(['E01']);
    },
  );

  it('localizes the original reference panel and replaces an invalid group with no data', async () => {
    const source = decisionFeeCard();
    source.annual!.profile.mainGroup = {
      status: 'AVAILABLE',
      value: { id: 1008, namePl: 'Nauki medyczne', nameEn: 'Medical sciences', nameRu: null },
    };
    const page = await enter(source);
    const root = harness.routeNativeElement!;
    const group = () => root.querySelector('[data-testid="decision-main-group"]')!;
    expect(group().textContent).toContain('Nauki medyczne');
    expect(group().querySelector('h3')?.textContent).toContain('grupa odniesienia');
    TestBed.inject(TranslocoService).setActiveLang('en');
    await settle();
    expect(group().textContent).toContain('Medical sciences');
    source.annual!.profile.mainGroup = { status: 'INVALID', value: null };
    page.retry();
    request().flush(source);
    await settle();
    expect(group().querySelector('p')?.textContent?.trim()).toBe('No data');
    expect(root.textContent).not.toContain('Medical sciences');
  });

  it.each(['decision-full-data', 'decision-website'] as const)(
    'records modified and middle link activation once without blocking the browser (%s)',
    async (testId) => {
      await enter({
        ...CARD,
        identity: { ...CARD.identity, website: 'https://example.test/journal' },
      });
      const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>(
        `[data-testid="${testId}"]`,
      )!;
      const code = testId === 'decision-full-data' ? 'E07' : 'E13';
      const click = new MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true });
      link.dispatchEvent(click);
      expect(click.defaultPrevented).toBe(false);
      const middle = new MouseEvent('auxclick', { bubbles: true, cancelable: true, button: 1 });
      link.dispatchEvent(middle);
      link.dispatchEvent(new MouseEvent('auxclick', { bubbles: true, button: 2 }));
      expect(middle.defaultPrevented).toBe(false);
      expect(actions().map((event) => event.action)).toEqual(['E01', code, code]);
      expect(router.url).toBe(`${ROOT}/123/decision`);
    },
  );

  it('keeps rendering, toggles and ordinary full-data navigation working if the audit throws', async () => {
    track.mockImplementation(() => {
      throw new Error('audit unavailable');
    });
    const page = await enter();
    toggleSection('time');
    await settle();
    expect(page.openSections().has('time')).toBe(true);
    const end = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    harness
      .routeNativeElement!.querySelector<HTMLAnchorElement>('[data-testid="decision-full-data"]')!
      .click();
    await end;
    await settle();
    expect(router.url).toBe(`${ROOT}/123`);
  });

  it.each(['load', 'error'])(
    'starts loading a non-empty cover and handles its %s event',
    async (event) => {
      await enter({
        ...CARD,
        identity: { ...CARD.identity, cover: 'https://example.test/cover.jpg' },
      });
      const root = harness.routeNativeElement!;
      const image = root.querySelector<HTMLImageElement>('app-safe-image img')!;
      expect(image.getAttribute('src')).toBe('https://example.test/cover.jpg');
      image.dispatchEvent(new Event(event));
      await settle();
      expect(root.querySelector('.safe-image__loader')).toBeNull();
      expect(
        root.querySelector(event === 'load' ? '.safe-image--loaded' : '.safe-image__fallback'),
      ).not.toBeNull();
    },
  );

  it.each(['pl', 'en'])(
    'keeps MNiSW points above ICV and assessed disciplines in its own accordion (%s)',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      await router.navigateByUrl(`${ROOT}/123/decision`, { state: { surveyYear: 2025 } });
      request().flush({
        ...CARD,
        surveyYear: 2025,
        mnisw: {
          year: 2024,
          listed: true,
          points: 70,
          status: 'AVAILABLE',
          disciplines: [
            { code: '2021', namePl: 'Medycyna', nameEn: 'Medicine' },
            { code: '2022', nameEn: 'English fallback' },
            { code: '2099' },
            { code: '2100', namePl: 'Ekonomia', nameEn: 'Economics' },
          ],
        },
      });
      await settle();
      const root = harness.routeNativeElement!;
      const mnisw = root.querySelector('[data-testid="decision-mnisw"]')!;
      expect(mnisw.getAttribute('aria-label')).toContain('2024');
      expect(mnisw.textContent).toContain(language === 'pl' ? '70 pkt' : '70 pts');
      expect(mnisw.textContent).toContain('MNiSW 2024:');
      const toggle = mnisw.querySelector<HTMLButtonElement>('[data-testid="decision-mnisw-toggle"]')!;
      expect(toggle.getAttribute('aria-expanded')).toBe('false');
      toggle.click();
      await settle();
      expect(toggle.getAttribute('aria-expanded')).toBe('true');
      const disciplines = root.querySelector('[data-testid="decision-mnisw-disciplines"]')!;
      expect(
        [...disciplines.querySelectorAll('li')].map((item) => item.textContent!.trim()),
      ).toEqual([
        language === 'pl' ? 'Medycyna' : 'Medicine',
        'English fallback',
        '—',
        language === 'pl' ? 'Ekonomia' : 'Economics',
      ]);
      expect(root.querySelector('.decision-identification')!.contains(disciplines)).toBe(false);
      const scientificProfile = root.querySelector('[data-testid="decision-scientific-profile"]')!;
      expect(root.querySelector('.decision-reference')!.contains(scientificProfile)).toBe(true);
      expect(
        [...scientificProfile.querySelectorAll('.card-body > [data-testid]')].map((section) =>
          section.getAttribute('data-testid'),
        ),
      ).toEqual([
        'decision-main-group',
        'decision-wos',
        'decision-scopus',
      ]);
      expect(mnisw.contains(disciplines)).toBe(true);
      expect(scientificProfile.contains(disciplines)).toBe(false);
      expect(mnisw.querySelector('h2')!.classList.contains('fs-4')).toBe(true);
      expect(mnisw.compareDocumentPosition(root.querySelector('[data-testid="decision-icv"]')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      const summary = root.querySelector('[data-testid="decision-summary"]')!;
      expect(summary.contains(mnisw)).toBe(false);
      expect(summary.querySelector('[data-testid="decision-summary-process"]')).not.toBeNull();
      expect(root.querySelectorAll('[data-testid="decision-mnisw"]')).toHaveLength(1);
      expect(
        root.querySelector('[data-testid="decision-deposit"]')!.compareDocumentPosition(summary) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();

      const fields = root.querySelector('.decision-identification dl')!;
      expect(
        [...fields.querySelectorAll('dt')].slice(-1).map((field) => field.textContent!.trim()),
      ).toEqual([language === 'pl' ? 'Języki' : 'Languages']);
      const heading = root.querySelector('#decision-title')!.parentElement!;
      expect(heading.querySelector('p')).toBeNull();
      expect(heading.textContent!.trim()).toBe(
        root.querySelector('#decision-title')!.textContent!.trim(),
      );
      expect(root.querySelector('[data-survey-year="2025"]')!.getAttribute('aria-pressed')).toBe(
        'true',
      );
      expect(root.querySelector('[data-testid="decision-mnisw-unlisted"]')).toBeNull();
      TestBed.inject(TranslocoService).setActiveLang(language === 'pl' ? 'en' : 'pl');
      await settle();
      expect(disciplines.textContent).toContain(language === 'pl' ? 'Medicine' : 'Medycyna');
    },
  );

  it.each([false, true])('uses an English fallback or a localized missing-description message, missing=%s', async missing => {
    await enter({
      ...CARD,
      identity: { ...CARD.identity, descriptions: missing ? [] : [{ type: 'EN', content: 'Fallback description' }] },
    });
    const root = harness.routeNativeElement!;
    root.querySelector<HTMLButtonElement>('[data-testid="decision-description-toggle"]')!.click();
    await settle();
    const description = root.querySelector('[data-testid="decision-description"] p')!;
    expect(description.textContent!.trim()).toBe(missing ? pl.decisionCard.noData : 'Fallback description');
    TestBed.inject(TranslocoService).setActiveLang('en');
    await settle();
    expect(description.textContent!.trim()).toBe(missing ? en.decisionCard.noData : 'Fallback description');
    expect(root.querySelector('[data-testid="decision-description-toggle"]')!.getAttribute('aria-expanded')).toBe('true');
    expect(actions().map(event => event.action)).toEqual(['E01', 'E02']);
  });

  it('relocalizes an open description without closing it or recording another expansion', async () => {
    await enter();
    const root = harness.routeNativeElement!;
    root.querySelector<HTMLButtonElement>('[data-testid="decision-description-toggle"]')!.click();
    await settle();
    TestBed.inject(TranslocoService).setActiveLang('en');
    await settle();
    expect(root.querySelector('[data-testid="decision-description"] p')!.textContent!.trim()).toBe('English description');
    expect(root.querySelector('[data-testid="decision-description-toggle"]')!.getAttribute('aria-expanded')).toBe('true');
    expect(actions().map(event => event.action)).toEqual(['E01', 'E02']);
  });

  it('uses the grey unlisted card and omits MNiSW disciplines regardless of stray assignments', async () => {
    await enter({
      ...CARD,
      mnisw: {
        year: 2024,
        listed: false,
        points: null,
        status: 'AVAILABLE',
        disciplines: [{ code: 'WRONG', nameEn: 'Must not be shown' }],
      },
    });
    const root = harness.routeNativeElement!;
    expect(
      root.querySelector('[data-testid="decision-mnisw"]')!.classList.contains('bg-light-subtle'),
    ).toBe(true);
    expect(root.querySelector('[data-testid="decision-mnisw"]')!.textContent).toContain(
      'Spoza wykazu',
    );
    expect(root.querySelector('[data-testid="decision-mnisw-disciplines"]')).toBeNull();
    expect(root.querySelector('[data-testid="decision-mnisw-points"]')).toBeNull();
  });

  it.each([70, 0, null])(
    'does not derive MNiSW listing from an empty discipline collection (points %s)',
    async (points) => {
      await enter({
        ...CARD,
        mnisw: {
          year: 2024,
          listed: true,
          points,
          disciplines: [],
          status: points === null ? 'INVALID' : 'AVAILABLE',
        },
      });
      const root = harness.routeNativeElement!;
      expect(root.querySelector('[data-testid="decision-mnisw-unlisted"]')).toBeNull();
      root.querySelector<HTMLButtonElement>('[data-testid="decision-mnisw-toggle"]')!.click();
      await settle();
      const text = root.querySelector('[data-testid="decision-mnisw-points"]')!.textContent!;
      expect(text).toContain(points === null ? 'Brak danych' : `${points} pkt`);
      if (points === null) expect(text).not.toContain('0 pkt');
      expect(
        root
          .querySelector(
            '[data-testid="decision-mnisw"] [data-testid="decision-mnisw-disciplines"] p',
          )!
          .textContent!.trim(),
      ).toBe('Brak danych');
    },
  );

  it.each(['MISSING_SURVEY', 'NOT_INDEXED'] as const)(
    'keeps assessed MNiSW disciplines in its own card without annual JML data (%s)',
    async (status) => {
      await enter({
        ...CARD,
        annualStatus: status,
        mnisw: {
          year: 2024,
          listed: true,
          points: 70,
          status: 'AVAILABLE',
          disciplines: [{ code: '2021', namePl: 'Medycyna', nameEn: 'Medicine' }],
        },
      });
      const root = harness.routeNativeElement!;
      root.querySelector<HTMLButtonElement>('[data-testid="decision-mnisw-toggle"]')!.click();
      await settle();
      expect(
        root.querySelector(
          '.decision-reference [data-testid="decision-mnisw"] [data-testid="decision-mnisw-disciplines"]',
        )!.textContent,
      ).toContain('Medycyna');
      expect(root.querySelector('[data-testid="decision-scientific-profile"]')).toBeNull();
      expect(root.querySelector('[data-testid="decision-annual-status"]')).not.toBeNull();
      expect(
        root.querySelector(
          '[data-testid="decision-icv"], [data-testid="decision-indexing"], [data-testid="decision-main-group"], [data-testid="decision-wos"], [data-testid="decision-scopus"]',
        ),
      ).toBeNull();
      expect(root.querySelectorAll('[data-decision-section]')).toHaveLength(0);
    },
  );

  it('shows all five deposit fields with zero and missing values independently and no benchmark', async () => {
    await enter({
      ...CARD,
      identity: {
        ...CARD.identity,
        cardinality: {
          numberOfPublications: 0,
          percentFullText: 0,
          percentAbstracts: 100,
          percentKeywords: 0,
          percentReferences: 85,
        },
      },
    });
    const root = harness.routeNativeElement!;
    const deposit = root.querySelector('[data-testid="decision-deposit"]')!;
    expect(deposit.textContent).toMatch(/Zdeponowane publikacje:\s*0/);
    for (const text of [
      'Pełna treść: 0%',
      'Abstrakt: 100%',
      'Słowa kluczowe: 0%',
      'Referencje: 85%',
    ]) {
      expect(deposit.textContent!.replace(/\s+/g, ' ')).toContain(text);
    }
    expect(deposit.querySelector('svg, canvas, button')).toBeNull();
  });

  it('keeps ICV history independent from the five section toggles', async () => {
    const page = await enter({
      ...CARD,
      icvHistory: [{ year: 2024, status: 'INDEXED', value: 81.125 }],
    });
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>(
        '[data-testid="decision-history-toggle"]',
      )!
      .click();
    toggleSection('time');
    toggleSection('costs');
    await settle();
    expect(
      harness
        .routeNativeElement!.querySelector<HTMLElement>('#decision-icv-history-collapse')!
        .classList.contains('show'),
    ).toBe(true);
    expect(page.openSections().size).toBe(2);
  });

  it('integrates five fee tiles in an initially closed section and resets it on a new journal', async () => {
    const source = decisionFeeCard();
    await enter(source);
    const root = harness.routeNativeElement!;
    const costs = root.querySelector<HTMLElement>('#decision-section-costs-collapse')!;
    expect(costs.classList.contains('show')).toBe(false);
    root.querySelector<HTMLButtonElement>('[data-decision-section="costs"] button')!.click();
    await settle();
    expect(costs.classList.contains('show')).toBe(true);
    expect(costs.querySelectorAll('[data-fee-category]')).toHaveLength(5);
    expect(costs.querySelectorAll('.fee-tile')).toHaveLength(6);
    expect(costs.querySelector('a, button, details, svg, canvas, .progress')).toBeNull();
    toggleSection('time');
    await settle();
    expect(costs.classList.contains('show')).toBe(true);
    expect(root.querySelector('#decision-section-time-collapse')!.classList.contains('show')).toBe(
      true,
    );
    const next = { ...source, identity: { id: 456 } };
    await enter(next);
    expect(
      harness
        .routeNativeElement!.querySelector('#decision-section-costs-collapse')!
        .classList.contains('show'),
    ).toBe(false);
    expect(
      harness
        .routeNativeElement!.querySelector('#decision-section-time-collapse')!
        .classList.contains('show'),
    ).toBe(false);
  });

  it.each([0, 1, 2, 3, 4, 5, 6, 7])(
    'keeps all lower sections independent for expansion combination %s',
    async (mask) => {
      await enter(decisionFeeCard());
      const root = harness.routeNativeElement!;
      const keys = ['process', 'international', 'standards'] as const;
      keys.forEach((key, i) => {
        if (mask & (1 << i)) toggleSection(key);
      });
      await settle();
      keys.forEach((key, i) =>
        expect(
          root.querySelector(`#decision-section-${key}-collapse`)!.classList.contains('show'),
        ).toBe(Boolean(mask & (1 << i))),
      );
      expect(root.querySelectorAll('[data-process-field]')).toHaveLength(5);
      expect(root.querySelectorAll('app-journal-decision-share-comparison')).toHaveLength(3);
      expect(root.querySelectorAll('[data-standard-field]')).toHaveLength(7);
      for (const key of keys) expect(root.querySelector(`#decision-section-${key} a`)).toBeNull();
      expect(
        root.querySelector('#decision-section-time-collapse')!.classList.contains('show'),
      ).toBe(false);
      expect(
        root.querySelector('#decision-section-costs-collapse')!.classList.contains('show'),
      ).toBe(false);
    },
  );

  it('keeps local parameter availability and all other annual values without replacing the year or catalogue', async () => {
    const missing = { status: 'MISSING', value: null } as const;
    const annual: JournalDecisionAnnual = {
      surveyYear: 2024,
      times: {
        initialAssessment: { status: 'INVALID', value: null },
        review: { status: 'AVAILABLE', value: 0 },
        publication: { status: 'AVAILABLE', value: 42 },
      },
      process: {
        firstIssueYear: missing,
        continuedYear: missing,
        frequency: missing,
        aheadOfPrint: { status: 'AVAILABLE', value: false },
        rejectionRate: missing,
        publishingBreak: missing,
      },
      international: {
        authors: { status: 'AVAILABLE', value: 0.5 },
        reviewers: missing,
        scientificCouncil: missing,
      },
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
        mainGroup: missing,
        memberships: missing,
        scientificCategories: missing,
        wosCategories: missing,
        scopusCategories: missing,
      },
      indexing: missing,
      fees: [
        {
          category: 'SUBMISSION',
          type: 'VARIOUS_FEE',
          status: 'RATES_UNAVAILABLE',
          charged: true,
          minimum: null,
          maximum: null,
          currency: null,
          selectionStatus: null,
          effectiveVariant: null,
          sourceRanges: [{ minimum: 0, maximum: 100, currency: 'EUR' }],
          exchangeRates: [],
          variants: [],
        },
      ],
    };
    const page = await enter({ ...CARD, annual });
    expect(page.state().status).toBe('success');
    expect(page.annualAvailable()).toBe(true);
    expect(page.data()?.annual).toEqual(annual);
    expect(page.identity()?.titleOriginal).toBe('Catalogue title');
    expect(page.data()?.surveyYear).toBe(2024);
    expect(
      harness.routeNativeElement!.querySelector('[data-testid="decision-annual-status"]'),
    ).toBeNull();
    expect(router.url).toBe(`${ROOT}/123/decision`);
    http.expectNone((value) => value.url.endsWith('/decision'));
    const section = harness.routeNativeElement!.querySelector<HTMLElement>(
      '#decision-section-time-collapse',
    )!;
    expect(section.classList.contains('show')).toBe(false);
    toggleSection('time');
    await settle();
    expect(section.classList.contains('show')).toBe(true);
    expect([...section.querySelectorAll('.time-label')].map((e) => e.textContent!.trim())).toEqual([
      'Średni czas wstępnej oceny',
      'Średni czas recenzji',
      'Średni czas publikacji',
    ]);
    expect(
      [...section.querySelectorAll('[data-testid="decision-own-days"]')].map((e) =>
        e.textContent!.trim(),
      ),
    ).toEqual(['Brak danych', '0 dni', '42 dni']);
    expect(section.querySelector('svg, a')).toBeNull();
    toggleSection('costs');
    await settle();
    expect(section.classList.contains('show')).toBe(true);
    expect(
      harness
        .routeNativeElement!.querySelector('#decision-section-costs-collapse')!
        .classList.contains('show'),
    ).toBe(true);
  });

  it('shows a read error instead of data returned for a different year and retries the requested year', async () => {
    await router.navigateByUrl(`${ROOT}/123/decision`, { state: { surveyYear: 2025 } });
    request().flush(CARD);
    await settle();
    const page = harness.routeDebugElement!.componentInstance as JournalDecision;
    expect(page.state().status).toBe('error');
    expect(page.data()).toBeUndefined();
    page.retry();
    const retry = request();
    expect(retry.request.params.get('surveyYear')).toBe('2025');
    retry.flush({ ...CARD, surveyYear: 2025 });
    await settle();
    expect(page.state().status).toBe('success');
    expect(page.data()?.surveyYear).toBe(2025);
  });

  it('registers a distinct lazy route and preserves the full-details route', async () => {
    const routes = institutionRoutes.find((value) => value.path === 'user')!.children!;
    const decision = routes.find((value) => value.path === 'journals/:id/decision')!;
    expect(decision.canActivate).toContain(journalDecisionGuard);
    expect(decision.data?.['analytics'].routeTemplate).toBe(ROOT + '/:id/decision');
    expect(await (decision.loadComponent! as () => Promise<typeof JournalDecision>)()).toBe(
      JournalDecision,
    );
    expect(routes.find((value) => value.path === 'journals/:id')?.component).toBe(JournalDetails);
  });

  it.each(['pl', 'en'])(
    'starts five sections and journal details collapsed without catalogue tabs (%s)',
    async (lang) => {
      TestBed.inject(TranslocoService).setActiveLang(lang);
      const page = await enter();
      const root = harness.routeNativeElement!;
      expect(
        [...root.querySelectorAll('header nav a')].map((value) =>
          value.getAttribute('data-testid'),
        ),
      ).toEqual(['decision-return', 'decision-full-data']);
      const buttons = root.querySelectorAll<HTMLButtonElement>('[data-decision-section] button');
      expect(buttons).toHaveLength(5);
      expect([...buttons].every((button) => button.getAttribute('aria-expanded') === 'false')).toBe(
        true,
      );
      buttons[0].click();
      buttons[1].click();
      await settle();
      expect(page.openSections().size).toBe(2);
      expect(buttons[0].getAttribute('aria-expanded')).toBe('true');
      expect(root.querySelector('app-journal-information-tabs, [role="tablist"]')).toBeNull();
      expect(root.querySelector('[data-testid="decision-description-toggle"]')!.getAttribute('aria-expanded')).toBe('false');
      expect(root.querySelector('[data-testid="decision-description"]')!.classList.contains('show')).toBe(false);
      expect(root.textContent).toContain(lang === 'pl' ? '81,13' : '81.13');
      expect(root.querySelector('[data-testid="decision-deposit"]')?.textContent).toContain('0%');
      expect(root.querySelector('[data-testid="decision-deposit"]')?.textContent).not.toContain(
        '—%',
      );
      expect(
        [...root.querySelectorAll('[data-decision-section]')].map((element) =>
          element.getAttribute('data-decision-section'),
        ),
      ).toEqual(['time', 'costs', 'process', 'international', 'standards']);
      expect(track.mock.calls.filter(([event]) => event.eventType === 'PAGE_READY')).toHaveLength(
        1,
      );
    },
  );

  it('keeps all five accordion sections inside the year context with accessible independent toggles and one audit per opening', async () => {
    const page = await enter(decisionFeeCard());
    const root = harness.routeNativeElement!;
    const context = root.querySelector('[data-testid="decision-context"]')!;
    const sections = [...context.querySelectorAll('[data-decision-section]')];
    expect(sections).toHaveLength(5);
    expect(root.querySelectorAll('[data-decision-section]')).toHaveLength(sections.length);
    const yearPicker = context.querySelector('[data-survey-year]')!;
    for (const section of sections) {
      expect(
        yearPicker.compareDocumentPosition(section) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
      expect(section.classList.contains('accordion-item')).toBe(true);
      expect(section.closest('.accordion')).not.toBeNull();
      const button = section.querySelector<HTMLButtonElement>('.accordion-button')!;
      const collapse = section.querySelector<HTMLElement>('.accordion-collapse')!;
      const headerTexts = [
        button,
        ...button.querySelectorAll('.decision-section-title, .decision-section-preview'),
      ];
      expect(button.getAttribute('aria-controls')).toBe(collapse.id);
      expect(collapse.getAttribute('aria-labelledby')).toBe(button.id);
      expect(button.classList.contains('collapsed')).toBe(true);
      expect(button.classList.contains('bg-light')).toBe(true);
      expect(button.classList.contains('bg-secondary-subtle')).toBe(false);
      expect(headerTexts.every((element) => !element.classList.contains('text-primary'))).toBe(
        true,
      );
      button.click();
      await settle();
      expect(button.getAttribute('aria-expanded')).toBe('true');
      expect(button.classList.contains('collapsed')).toBe(false);
      expect(button.classList.contains('bg-light')).toBe(true);
      expect(button.classList.contains('bg-secondary-subtle')).toBe(false);
      expect(headerTexts.every((element) => element.classList.contains('text-primary'))).toBe(true);
      expect(collapse.classList.contains('show')).toBe(true);
    }
    expect(page.openSections().size).toBe(5);
    expect(actions().map((event) => event.action)).toEqual([
      'E01',
      'E08',
      'E09',
      'E11',
      'E12',
      'E10',
    ]);
    const timeButton = sections[0].querySelector<HTMLButtonElement>('button')!;
    timeButton.click();
    await settle();
    expect(page.openSections().size).toBe(4);
    expect(timeButton.classList.contains('bg-light')).toBe(true);
    expect(timeButton.classList.contains('bg-secondary-subtle')).toBe(false);
    expect(timeButton.querySelector('.text-primary')).toBeNull();
    expect(timeButton.classList.contains('text-primary')).toBe(false);
    expect(actions()).toHaveLength(6);
    timeButton.click();
    await settle();
    expect(page.openSections().size).toBe(5);
    expect(actions().map((event) => event.action)).toEqual([
      'E01',
      'E08',
      'E09',
      'E11',
      'E12',
      'E10',
      'E08',
    ]);
  });

  it('keeps current annual previews visible independently of section expansion and updates them after navigation', async () => {
    const source = decisionFeeCard();
    source.annual!.times.review = { status: 'AVAILABLE', value: 40 };
    source.benchmarks!.times.review = {
      status: 'AVAILABLE',
      value: { q1: 17.25, q3: 44.75, axis: { minimum: 10, maximum: 50 } },
    };
    source.annual!.process.frequency = { status: 'AVAILABLE', value: 'b' };
    source.annual!.process.rejectionRate = { status: 'AVAILABLE', value: (100 * 20) / 70 };
    source.annual!.fees[1] = decisionFee({
      category: 'PUBLICATION',
      type: 'ONE_FEE',
      charged: true,
      minimum: 1500,
      maximum: 1500,
      currency: 'PLN',
    });
    await enter(source);
    const root = harness.routeNativeElement!;
    const preview = (key: string) =>
      root
        .querySelector(`[data-testid="decision-preview-${key}"]`)!
        .textContent!.replace(/\s+/g, ' ')
        .trim();
    for (const [key, expected] of [
      ['time', 'Recenzja 40 dni · typowo 17–45 dni'],
      ['costs', 'Jedna kategoria opłat'],
      ['process', 'Dwumiesięcznik · 29% odrzuceń'],
    ]) {
      const button = root.querySelector<HTMLButtonElement>(
        `[data-decision-section="${key}"] button`,
      )!;
      const body = root.querySelector<HTMLElement>(`#decision-section-${key}-collapse`)!;
      expect(body.classList.contains('show')).toBe(false);
      expect(preview(key)).toBe(expected);
      expect(
        root.querySelector(`[data-testid="decision-preview-${key}"]`)!.closest('[hidden]'),
      ).toBeNull();
      button.click();
      await settle();
      expect(body.classList.contains('show')).toBe(true);
      expect(preview(key)).toBe(expected);
      button.click();
      await settle();
      expect(body.classList.contains('show')).toBe(false);
      expect(preview(key)).toBe(expected);
    }
    source.identity.id = 456;
    source.surveyYear = source.annual!.surveyYear = source.benchmarks!.surveyYear = 2025;
    source.annual!.times.review.value = 1;
    source.benchmarks!.times.review.value = { q1: 1, q3: 1, axis: { minimum: 0, maximum: 3 } };
    source.annual!.process.frequency.value = 'a';
    source.annual!.process.rejectionRate.value = 0;
    source.annual!.fees = source.annual!.fees.map((fee) => decisionFee({ category: fee.category }));
    await enter({ ...source });
    expect(preview('time')).toBe('Recenzja 1 dzień · typowo 1 dzień');
    expect(preview('costs')).toBe('Brak opłat');
    expect(preview('process')).toBe('Rocznik · 0% odrzuceń');
  });

  it.each([
    { pissn: '2299-7164', eissn: '2353-3218' },
    { pissn: '2299-7164', eissn: null },
    { pissn: null, eissn: '2353-3218' },
    { pissn: null, eissn: null },
  ])('labels only the available ISSN identifiers: %j', async ({ pissn, eissn }) => {
    await enter({ ...CARD, identity: { ...CARD.identity, pissn, eissn } });
    const text = harness.routeNativeElement!.querySelector(
      '[data-testid="decision-issn"]',
    )!.textContent!;
    expect(text.includes('(pISSN)')).toBe(pissn !== null);
    expect(text.includes('(eISSN)')).toBe(eissn !== null);
    expect(text.includes('|')).toBe(pissn !== null && eissn !== null);
    if (pissn) expect(text).toContain(pissn);
    if (eissn) expect(text).toContain(eissn);
    if (!pissn && !eissn) expect(text.trim()).toBe('—');
  });

  it('resets local sections on another public ID and cancels old reads', async () => {
    const page = await enter();
    toggleSection('standards');
    await settle();
    await harness.navigateByUrl(ROOT + '/456/decision', JournalDecision);
    request(456).flush({
      ...CARD,
      identity: {
        ...CARD.identity,
        id: 456,
        descriptions: [{ type: 'EN', content: 'Next description' }],
      },
    });
    await settle();
    expect(page.openSections().size).toBe(0);
    expect(harness.routeNativeElement!.querySelector('[data-testid="decision-description"]')!.classList.contains('show')).toBe(false);
    await harness.navigateByUrl(ROOT + '/123/decision', JournalDecision);
    const stale = request();
    await harness.navigateByUrl(ROOT + '/789/decision', JournalDecision);
    expect(stale.cancelled).toBe(true);
    request(789).flush({ ...CARD, identity: { id: 789, titleOriginal: 'Latest' } });
    await settle();
    expect(harness.routeNativeElement!.textContent).toContain('Latest');
    expect(harness.routeNativeElement!.textContent).not.toContain('Next description');
  });

  it.each([
    'MISSING_SURVEY',
    'NOT_EVALUATED',
    'NOT_INDEXED',
    'UNAVAILABLE',
    'DATA_UPDATING',
    'DATA_NOT_READY',
  ] as const)('keeps basic data and the requested year for %s', async (status) => {
    await enter({
      ...CARD,
      surveyYear: 2025,
      annualStatus: status,
      availableSurveyYears: [2024],
    });
    const root = harness.routeNativeElement!;
    expect(root.textContent).toContain('Catalogue title');
    expect(
      root.querySelector('[data-testid="decision-annual-status"] h2')?.textContent?.trim(),
    ).toBe(
      TestBed.inject(TranslocoService).translate(`decisionCard.status.${status}`, { year: 2025 }),
    );
    expect(root.querySelectorAll('[data-decision-section]')).toHaveLength(0);
    expect(root.querySelector('app-journal-information-tabs, [role="tablist"]')).toBeNull();
    expect(root.querySelector('[data-testid="decision-description"]')!.classList.contains('show')).toBe(false);
    expect(router.url).toBe(ROOT + '/123/decision');
    expect(TestBed.inject(Location).getState()).toMatchObject({ surveyYear: 2025 });
    const end = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    root.querySelector<HTMLAnchorElement>('[data-testid="decision-full-data"]')!.click();
    await end;
    await settle();
    expect(router.url).toBe(ROOT + '/123');
    expect(TestBed.inject(Location).getState()).toMatchObject({ surveyYear: 2025 });
  });

  it('uses a fresh URL default instead of the global list year and preserves its own entry year', async () => {
    TestBed.inject(JournalListQueryStateService).surveyYear.set(2025);
    await harness.navigateByUrl(ROOT + '/123/decision', JournalDecision);
    expect(request().request.params.has('surveyYear')).toBe(false);
    // Recreate a history entry as it is restored by refresh/back, retaining the chosen year.
    const navigation = router.navigateByUrl(ROOT + '/456/decision', {
      state: { surveyYear: 2025 },
    });
    await navigation;
    await settle();
    const selected = request(456);
    expect(selected.request.params.get('surveyYear')).toBe('2025');
    selected.flush({ ...CARD, surveyYear: 2025, identity: { id: 456 } });
    await settle();
    expect(TestBed.inject(Location).getState()).toMatchObject({ surveyYear: 2025 });
  });

  it('keeps different years for identical card URLs when moving back and forward', async () => {
    await enter();
    await harness.navigateByUrl(ROOT, OtherPage);
    await router.navigateByUrl(ROOT + '/123/decision', { state: { surveyYear: 2025 } });
    await settle();
    const selected = request();
    expect(selected.request.params.get('surveyYear')).toBe('2025');
    selected.flush({ ...CARD, surveyYear: 2025, icv: 90 });
    await settle();
    const move = async (direction: 'back' | 'forward') => {
      const end = firstValueFrom(
        router.events.pipe(filter((event) => event instanceof NavigationEnd)),
      );
      TestBed.inject(Location)[direction]();
      await end;
      await settle();
    };
    await move('back');
    await move('back');
    const historical = request();
    expect(historical.request.params.get('surveyYear')).toBe('2024');
    historical.flush(CARD);
    await settle();
    expect(harness.routeNativeElement!.textContent).toContain('ICV 2024');
    await move('forward');
    await move('forward');
    const forward = request();
    expect(forward.request.params.get('surveyYear')).toBe('2025');
    forward.flush({ ...CARD, surveyYear: 2025, icv: 90 });
    await settle();
    expect(harness.routeNativeElement!.textContent).toContain('ICV 2025');
    expect(router.url).toBe(ROOT + '/123/decision');
  });

  it.each([400, 403, 404, 500])(
    'shows a retryable error for HTTP %s instead of pretending the survey is missing',
    async (status) => {
      await harness.navigateByUrl(ROOT + '/123/decision', JournalDecision);
      request().flush({}, { status, statusText: 'error' });
      await settle();
      expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeTruthy();
      expect(
        harness.routeNativeElement!.querySelector('[data-testid="decision-annual-status"]'),
      ).toBeNull();
      harness
        .routeNativeElement!.querySelector<HTMLButtonElement>('[role="alert"] button')!
        .click();
      await settle();
      request().flush(CARD);
      await settle();
      expect(harness.routeNativeElement!.textContent).toContain('Catalogue title');
    },
  );

  it('keeps the resolved entry year on actor reload and resets local disclosures', async () => {
    const page = await enter();
    toggleSection('time');
    profile.next({ userId: 2, institutionId: 10 });
    await settle();
    const selected = request();
    expect(selected.request.params.get('surveyYear')).toBe('2024');
    selected.flush(CARD);
    await settle();
    expect(page.openSections().size).toBe(0);
  });

  it('reads only its own history year after a fresh SPA start without restoring a copied list token', async () => {
    const location = TestBed.inject(Location);
    vi.spyOn(location, 'getState').mockReturnValue({
      surveyYear: 2025,
      journalListOrigin: 'old-spa-token',
    });
    await harness.navigateByUrl(ROOT + '/123/decision', JournalDecision);
    const selected = request();
    expect(selected.request.params.get('surveyYear')).toBe('2025');
    selected.flush({ ...CARD, surveyYear: 2025 });
    await settle();
    expect(TestBed.inject(JournalListQueryStateService).returnNavigationState()).toEqual({});
    expect(TestBed.inject(JournalListQueryStateService).restoreView('ADVANCED')).toBeUndefined();
  });

  it.each(['pl', 'en'])(
    'omits the fallback subtitle and alternate questionnaire link for a missing survey (%s)',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      await enter({
        ...CARD,
        surveyYear: 2025,
        annualStatus: 'MISSING_SURVEY',
        availableSurveyYears: [2024],
      });
      const root = harness.routeNativeElement!;
      expect(root.querySelector('[data-testid="decision-benchmark-discipline"]')).toBeNull();
      expect(root.textContent).not.toContain(
        language === 'pl'
          ? pl.page.institution.questionnaire.descriptionText
          : en.page.institution.questionnaire.descriptionText,
      );
      expect(root.querySelector('[data-testid="decision-annual-status"] a')).toBeNull();
      expect(root.textContent).not.toContain(
        language === 'pl' ? 'Zobacz ankietę za' : 'questionnaire in Full JML data',
      );
      expect(
        root.querySelector('[data-testid="decision-context"] [data-survey-year="2024"]'),
      ).not.toBeNull();
      expect(root.querySelector('[data-testid="decision-full-data"]')).not.toBeNull();
      expect(router.url).toBe(ROOT + '/123/decision');
      expect(TestBed.inject(Location).getState()).toMatchObject({ surveyYear: 2025 });
      expect(actions().map((event) => event.action)).toEqual(['E01']);
    },
  );

  it.each(['pl', 'en'])(
    'places the year picker next to the localized decision heading before publication time (%s)',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const source = decisionFeeCard();
      source.availableSurveyYears = [2024, 2025, 2024];
      source.annual!.profile.mainGroup = {
        status: 'AVAILABLE',
        value: {
          id: 1008,
          namePl: 'Nauki medyczne',
          nameEn: 'Medical sciences',
          nameRu: null,
        },
      };
      await enter(source);
      const root = harness.routeNativeElement!;
      const context = root.querySelector('[data-testid="decision-context"]')!;
      expect(context.querySelector('h2')!.textContent!.trim()).toBe(
        language === 'pl' ? 'Karta decyzji' : 'Decision card',
      );
      expect(
        context.querySelector('[data-testid="decision-benchmark-discipline"]')!.textContent!.trim(),
      ).toBe(
        language === 'pl'
          ? 'Benchmarki liczone względem Nauki medyczne (ICI JML)'
          : 'Benchmarks calculated against Medical sciences (ICI JML)',
      );
      const header = context.querySelector('header')!;
      const picker = header.querySelector('.decision-year-picker')!;
      expect(picker.previousElementSibling).toBe(header.querySelector('h2'));
      expect(root.querySelectorAll('.decision-year-picker')).toHaveLength(1);
      const years = [...picker.querySelectorAll<HTMLButtonElement>('[data-survey-year]')];
      expect(context.querySelector('#decision-year-label')!.textContent!.trim()).toBe(
        language === 'pl' ? 'Rok danych:' : 'Data year:',
      );
      expect(years.map((button) => button.textContent!.trim())).toEqual(['2025', '2024']);
      expect(years.map((button) => button.getAttribute('aria-pressed'))).toEqual(['false', 'true']);
      expect(years[1].classList.contains('btn-primary')).toBe(true);
      expect(years[0].classList.contains('btn-outline-primary')).toBe(true);
      expect(context.querySelector('[role="group"]')!.getAttribute('aria-labelledby')).toBe(
        'decision-year-label',
      );
      expect(
        context.compareDocumentPosition(root.querySelector('[data-decision-section="time"]')!) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    },
  );

  it('changes only the local card year and refreshes comparisons, previews, links and actor reloads', async () => {
    const listState = TestBed.inject(JournalListQueryStateService);
    const source = decisionFeeCard();
    source.annual!.times.review = { status: 'AVAILABLE', value: 40 };
    const page = await enter(source);
    listState.surveyYear.set(2024);
    toggleSection('time');
    await settle();
    const location = TestBed.inject(Location);
    location.replaceState(location.path(true), '', {
      ...(location.getState() as object),
      sourceHint: 'keep',
    });
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>(
        '[data-testid="decision-context"] [data-survey-year="2025"]',
      )!
      .click();
    await settle();
    const selected = request();
    expect(selected.request.params.get('surveyYear')).toBe('2025');
    expect(location.getState()).toMatchObject({ surveyYear: 2025, sourceHint: 'keep' });
    const next = decisionFeeCard();
    next.surveyYear = next.annual!.surveyYear = next.benchmarks!.surveyYear = 2025;
    next.icv = 90;
    next.annual!.times.review = { status: 'AVAILABLE', value: 20 };
    next.benchmarks!.times.review = {
      status: 'AVAILABLE',
      value: { q1: 10, q3: 30, axis: { minimum: 0, maximum: 40 } },
    };
    next.annual!.profile.mainGroup = {
      status: 'AVAILABLE',
      value: { id: 2000, namePl: 'Nauki techniczne', nameEn: 'Engineering', nameRu: null },
    };
    selected.flush(next);
    await settle();
    const root = harness.routeNativeElement!;
    expect(
      root.querySelector('[data-testid="decision-benchmark-discipline"]')!.textContent,
    ).toContain('Nauki techniczne');
    expect(root.querySelector('[data-testid="decision-icv"] p')!.textContent).toContain(
      'ICV 2025: 90,00',
    );
    expect(
      root
        .querySelector('[data-testid="decision-preview-time"]')!
        .textContent!.replace(/\s+/g, ' ')
        .trim(),
    ).toBe('Recenzja 20 dni · typowo 10–30 dni');
    expect(
      root
        .querySelector('[data-testid="decision-context"] [data-survey-year="2025"]')!
        .getAttribute('aria-pressed'),
    ).toBe('true');
    expect(page.openSections().size).toBe(0);
    expect(page.detailsState()).toMatchObject({ surveyYear: 2025 });
    expect(listState.surveyYear()).toBe(2024);
    expect(router.url).toBe(ROOT + '/123/decision');
    profile.next({ userId: 2, institutionId: 10 });
    await settle();
    const reload = request();
    expect(reload.request.params.get('surveyYear')).toBe('2025');
    reload.flush(next);
    await settle();
    expect(page.data()!.surveyYear).toBe(2025);
  });

  it('does not request the current year or a year outside the available choices', async () => {
    const page = await enter();
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>(
        '[data-testid="decision-context"] [data-survey-year="2024"]',
      )!
      .click();
    page.selectSurveyYear(1999);
    page.selectSurveyYear(NaN);
    await settle();
    http.expectNone((value) => value.url.endsWith('/decision'));
    expect(page.data()!.surveyYear).toBe(2024);
  });

  it('allows choosing an available year directly from an unavailable annual card', async () => {
    await enter({
      ...CARD,
      surveyYear: 2025,
      annualStatus: 'MISSING_SURVEY',
      availableSurveyYears: [2024],
    });
    const context = harness.routeNativeElement!.querySelector('[data-testid="decision-context"]')!;
    expect(
      [...context.querySelectorAll('[data-survey-year]')].map((button) =>
        button.textContent!.trim(),
      ),
    ).toEqual(['2025', '2024']);
    context.querySelector<HTMLButtonElement>('[data-survey-year="2024"]')!.click();
    await settle();
    const selected = request();
    expect(selected.request.params.get('surveyYear')).toBe('2024');
    selected.flush(decisionFeeCard());
    await settle();
    expect(
      harness.routeNativeElement!.querySelector('[data-testid="decision-annual-status"]'),
    ).toBeNull();
    expect(
      harness.routeNativeElement!.querySelector('[data-decision-section="time"]'),
    ).not.toBeNull();
    expect(router.url).toBe(ROOT + '/123/decision');
  });

  it('retries the newly selected year after a failed year change', async () => {
    await enter();
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>(
        '[data-testid="decision-context"] [data-survey-year="2025"]',
      )!
      .click();
    await settle();
    const selected = request();
    expect(selected.request.params.get('surveyYear')).toBe('2025');
    selected.flush({}, { status: 503, statusText: 'unavailable' });
    await settle();
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await settle();
    const retry = request();
    expect(retry.request.params.get('surveyYear')).toBe('2025');
    retry.flush({ ...CARD, surveyYear: 2025 });
    await settle();
    expect(
      harness
        .routeNativeElement!.querySelector(
          '[data-testid="decision-context"] [data-survey-year="2025"]',
        )!
        .getAttribute('aria-pressed'),
    ).toBe('true');
  });

  it.each([false, true])(
    'omits the subtitle when benchmarks are hidden or the discipline is unavailable (%s)',
    async (visible) => {
      const source = decisionFeeCard();
      source.benchmarksVisible = visible;
      if (!visible)
        source.annual!.profile.mainGroup = {
          status: 'AVAILABLE',
          value: { id: 1008, namePl: 'Nauki medyczne', nameEn: 'Medical sciences', nameRu: null },
        };
      await enter(source);
      const description = harness.routeNativeElement!.querySelector(
        '[data-testid="decision-benchmark-discipline"]',
      );
      expect(description).toBeNull();
      expect(harness.routeNativeElement!.textContent).not.toContain(
        pl.page.institution.questionnaire.descriptionText,
      );
    },
  );

  it.each(['BASIC', 'DISABLED', 'FAILED'] as const)(
    'denies direct access before fetching data: %s',
    async (mode) => {
      if (mode === 'BASIC') {
        context.effectiveProfile = 'BASIC';
        context.availableFeatures = ['BASIC_SEARCH'];
      }
      if (mode === 'DISABLED') context.availableFeatures = ['BASIC_SEARCH', 'ADVANCED_SEARCH'];
      if (mode === 'FAILED')
        currentContext.mockReturnValue(throwError(() => new Error('unavailable')));
      await harness.navigateByUrl(ROOT + '/123/decision');
      await settle();
      expect(router.url).toBe('/institution/user/denied');
      http.expectNone((value) => value.url.endsWith('/decision'));
    },
  );
});
