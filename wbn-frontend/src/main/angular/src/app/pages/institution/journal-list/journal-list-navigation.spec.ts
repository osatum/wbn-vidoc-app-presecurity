import { Location, ViewportScroller } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { NgbConfig } from '@ng-bootstrap/ng-bootstrap';
import {
  provideTransloco,
  TranslocoLoader,
  TranslocoService,
  Translation,
} from '@jsverse/transloco';
import { BehaviorSubject, Subject, filter, firstValueFrom, of, throwError } from 'rxjs';

import {
  ConfigurationService,
  DictionaryService,
  JournalListQuery,
  OperatingContextDto,
  QuestionnaireReportsService,
  ScientificCategoryDictionaryView,
  UserProfileResponse,
} from '../../../core/backend';
import { AuthService } from '../../../core/services/auth.service';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';
import { installSessionStorageMock } from '../../../testing/session-storage-testing';
import { JournalDecision } from '../journal-decision/journal-decision';
import { journalDecisionGuard } from '../../../core/guards/journal-decision.guard';
import { JOURNAL_DECISION_CARD_AVAILABLE, OperatingProfileService } from '../../../core/services/operating-profile.service';
import { JournalDetails } from '../journal-details/journal-details';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { JournalQuestionnaireChartsComponent } from '../journal-details/journal-questionnaire-charts/journal-questionnaire-charts-component';
import { JournalList } from './journal-list';

const LIST = '/institution/user/journals';
const SORT = JournalListQuery.SortByEnum;
const PAGE = {
  data: [{ id: 28032, title: 'Journal' }],
  totalElements: 27,
  totalPages: 3,
  size: 10,
};

@Component({ template: '' })
class OtherPage {}

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation(language: string) {
    return of({
      decisionCard: {
        icv: language === 'en' ? 'ICV indicator' : 'Wskaźnik ICV',
        history: language === 'en' ? 'ICV history' : 'Historia ICV',
        icvComparison: (language === 'en' ? en : pl).decisionCard.icvComparison,
      },
      page: {
        institution: {
          journalList: {
            poland: language === 'en' ? 'Poland' : 'Polska',
            viewCard: 'Zobacz kartę',
            retrySearch: 'Spróbuj ponownie',
            defaultNotice:
              'Domyślnie wyświetlamy czasopisma polskie. Aby przeglądać wszystkie czasopisma JML, usuń filtr «Polska» — lista odświeży się automatycznie.',
          },
          journalDetails: {
            missingQuestionnaire: 'Brak ankiety za rok {{year}}.',
            availableQuestionnaires: 'Dostępne ankiety z innych lat:',
          },
          questionnaire: {
            title: 'Kwestionariusz czasopisma',
            surveyYear: 'Rok ankiety:',
            availableStatus: 'Dostępna ankieta',
            unavailableStatus: 'Brak danych ankiety',
            hiddenStatus: 'Ankieta nie jest udostępniona',
            loadingShort: 'Ładowanie kwestionariusza...',
            emptyTitle: 'Brak danych kwestionariusza za rok {{year}}',
            emptyDescription: 'Dla wybranego roku nie są dostępne dane kwestionariusza czasopisma.',
            showYear: 'Wyświetl ankietę {{year}}',
            tabs: { publishingVersions: 'Wersje wydawnicze', stability: 'Stabilność' },
          },
        },
      },
    } as Translation);
  }
}

describe('P1/P2 navigation and HTTP state', () => {
  it('keeps the historical year in navigation state, HTTP request and restored list without changing the address', async () => {
    vi.spyOn(TestBed.inject(ConfigurationService), 'getSearchYears').mockReturnValue(
      of({ defaultYear: 2025, availableYears: [2025, 2024] }),
    );
    const list = await enter();
    const initial = search();
    expect(initial.request.body.surveyYear).toBe(2025);
    await finish(initial);
    list.selectSurveyYear('2024');
    await stabilize();
    const historical = search();
    expect(historical.request.body.surveyYear).toBe(2024);
    await finish(historical);
    await clickLink('[data-testid="journal-result-title"]');
    expect(router.url).toBe(LIST + '/28032');
    expect(TestBed.inject(Location).getState()).toMatchObject({ surveyYear: 2024 });
    const request = http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'));
    expect(request.request.params.get('surveyYear')).toBe('2024');
    request.flush({ id: 28032, titleOriginal: 'Historical journal' });
    await stabilize();
    await browserBack();
    const restored = search();
    expect(restored.request.body.surveyYear).toBe(2024);
    await finish(restored);
  });
  let harness: RouterTestingHarness;
  let decisionEnabled = false;
  let http: HttpTestingController;
  let router: Router;
  let profile: BehaviorSubject<UserProfileResponse | null>;
  let viewport: {
    getScrollPosition: ReturnType<typeof vi.fn>;
    scrollToPosition: ReturnType<typeof vi.fn>;
  };
  let disciplineLoader: ReturnType<typeof vi.fn>;
  let autoIcvBenchmarks: boolean;

  beforeEach(async () => {
    decisionEnabled = false;
    autoIcvBenchmarks = true;
    installSessionStorageMock();
    profile = new BehaviorSubject<UserProfileResponse | null>({
      userId: 1,
      institutionId: 10,
      institutionRole: 'INSTITUTION_USER',
    });
    viewport = { getScrollPosition: vi.fn(() => [0, 1450]), scrollToPosition: vi.fn() };
    disciplineLoader = vi.fn(() => of([{ id: 3, namePl: 'Matematyka' }]));
    await TestBed.configureTestingModule({
      imports: [JournalList, JournalDetails],
      providers: [
        { provide: JOURNAL_DECISION_CARD_AVAILABLE, useFactory: () => decisionEnabled },
        { provide: OperatingProfileService, useValue: { currentContext: () => of({
          institutionId: 10, configuredProfile: 'BOTH', defaultProfile: 'BOTH', effectiveProfile: 'BOTH',
          defaultSearchView: 'ADVANCED', availableFeatures: ['BASIC_SEARCH', 'ADVANCED_SEARCH', 'DECISION_CARD'],
        }) } },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideLocationMocks(),
        provideRouter([
          { path: 'institution/user/journals', component: JournalList },
          { path: 'institution/user/journals/:id/decision', component: JournalDecision, canActivate: [journalDecisionGuard] },
          { path: 'institution/user/journals/:id', component: JournalDetails },
          { path: 'other', component: OtherPage },
        ]),
        provideTransloco({
          config: { availableLangs: ['pl', 'en'], defaultLang: 'pl', prodMode: true },
          loader: TestingTranslocoLoader,
        }),
        { provide: AuthService, useValue: { getUserProfile: () => profile } },
        { provide: ViewportScroller, useValue: viewport },
        { provide: ConfigurationService, useValue: { ratingYears: () => of({}),
          getJournalLinks: () => of({ issuesUrlTemplate: 'https://journals.example.test/issues/{journalId}' }),
          getSearchYears: () => of({ defaultYear: 2024, availableYears: [2024] }) } },
        {
          provide: DictionaryService,
          useValue: {
            findCountries: () => of([]),
            findLanguages: () => of([]),
            findRepositories: vi.fn(() => of([])),
            findScientificCategories: disciplineLoader,
            findMniswDisciplines: () =>
              of([
                { code: '2021', namePl: 'Automatyka', nameEn: 'Automation' },
                { code: '2061', namePl: 'Inżynieria lądowa', nameEn: 'Civil engineering' },
              ]),
          },
        },
        {
          provide: QuestionnaireReportsService,
          useValue: { questionnaireReportAvailability: () => of({}) },
        },
        {
          provide: NavigationAuditService,
          useValue: { track: vi.fn(), flush: vi.fn(), captureView: vi.fn(() => ({})) },
        },
      ],
    }).overrideComponent(JournalQuestionnaireChartsComponent, { set: { template: '' } }).compileComponents();
    TestBed.inject(NgbConfig).animation = false;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    router.setUpLocationChangeListener();
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  async function stabilize(): Promise<void> {
    harness.detectChanges();
    http.match((request) => request.url.endsWith('/api/journal/search-journals/counts'))
      .forEach((request) => request.flush({}));
    http
      .match((request) => request.url.endsWith('/api/journal/search-journals/metadata'))
      .forEach((request) =>
        request.flush({ icvMin: 0, icvMax: 300, mniswPointClasses: [20, 70, 200] }),
      );
    await harness.fixture.whenStable();
    harness.detectChanges();
    const details = harness.routeDebugElement?.componentInstance;
    if (autoIcvBenchmarks && details instanceof JournalDetails) {
      const journal = details.journal();
      const requests = http.match(request => request.url.endsWith(`/${journal?.id}/decision`));
      for (const request of requests) request.flush({
        identity: { id: journal?.id },
        surveyYear: Number(request.request.params.get('surveyYear')),
        annualStatus: 'AVAILABLE',
        icv: journal?.currentIcvPoints,
        icvStatus: 'AVAILABLE',
        icvHistory: [], annual: null, benchmarks: null, benchmarksVisible: false,
      });
      if (requests.length) {
        await harness.fixture.whenStable();
        harness.detectChanges();
      }
    }
  }

  function search(): TestRequest {
    return http.expectOne(
      (request) =>
        request.url.endsWith('/api/journal/search-journals') && request.method === 'POST',
    );
  }

  it('keeps the list and filters when clicking the publication count', async () => {
    const list = await enter();
    await finish();
    const query = structuredClone(list.query());
    list.filterForm.controls.journalTitle.setValue('Unapplied draft');
    const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('[data-testid="journal-publications-link"]')!;
    expect(link.href).toBe('https://journals.example.test/issues/28032');
    expect(link.target).toBe('_blank');
    link.querySelector('span')!.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    link.dispatchEvent(new MouseEvent('click', { ctrlKey: true }));
    link.dispatchEvent(new MouseEvent('auxclick', { button: 1 }));
    link.dispatchEvent(new MouseEvent('auxclick', { button: 2 }));
    expect(router.url).toBe(LIST);
    expect(list.query()).toEqual(query);
    expect(list.filterForm.controls.journalTitle.value).toBe('Unapplied draft');
    const audit = vi.mocked(TestBed.inject(NavigationAuditService));
    const actions = audit.track.mock.calls.map(([event]) => event);
    expect(actions.filter(event => event.eventType === 'JOURNAL_ISSUES_CLICK')).toHaveLength(3);
    expect(actions.filter(event => event.eventType === 'JOURNAL_CARD_OPEN' || event.eventType === 'JOURNAL_DECISION_ACTION')).toHaveLength(0);
    expect(audit.flush).toHaveBeenCalledTimes(3);
  });

  it('links the full JML publication count to the same journal without a P1 or P2 action', async () => {
    await harness.navigateByUrl(LIST + '/456', JournalDetails);
    http.expectOne(value => value.url.endsWith('/api/journal/journal/456')).flush({
      id: 456, titleOriginal: 'Journal', surveyYear: 2024, cardinality: { numberOfPublications: 0 },
    });
    await stabilize();
    const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>('[data-testid="journal-full-data-publications-link"]')!;
    expect(link.href).toBe('https://journals.example.test/issues/456');
    expect(link.target).toBe('_blank');
    expect(link.rel).toBe('noopener noreferrer');
    expect(link.querySelector('span')!.textContent).toBe('0');
    link.click();
    expect(router.url).toBe(LIST + '/456');
    expect(vi.mocked(TestBed.inject(NavigationAuditService).track).mock.calls
      .some(([event]) => event.eventType === 'JOURNAL_ISSUES_CLICK' || event.eventType === 'JOURNAL_DECISION_ACTION')).toBe(false);
  });

  async function enter(): Promise<JournalList> {
    const list = await harness.navigateByUrl(LIST, JournalList);
    await stabilize();
    return list;
  }

  async function finish(request: TestRequest = search()): Promise<void> {
    request.flush(PAGE);
    await stabilize();
  }

  async function clickLink(selector: string): Promise<void> {
    const completed = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    const link = harness.routeNativeElement!.querySelector<HTMLAnchorElement>(selector)!;
    expect(link).toBeTruthy();
    link.click();
    await completed;
    await stabilize();
  }

  async function details(selector = '[data-testid="journal-result-title"]'): Promise<void> {
    await clickLink(selector);
    expect(router.url).toBe(LIST + '/28032');
    http
      .expectOne((request) => request.url.endsWith('/api/journal/journal/28032'))
      .flush({ id: 28032, titleOriginal: 'Journal' });
    await stabilize();
  }

  async function browserBack(): Promise<void> {
    const completed = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    TestBed.inject(Location).back();
    await completed;
    await stabilize();
  }

  it('uses the current year on direct entry instead of a previously selected list year', async () => {
    TestBed.inject(JournalListQueryStateService).selectSurveyYear(2024);
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    const request = http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'));
    expect(request.request.params.has('surveyYear')).toBe(false);
    request.flush({ id: 28032, surveyYear: 2025, questionnaireAvailable: true,
      availableSurveyYears: [2025, 2024], currentStatus: 'NOT_EVALUATED', currentIcvYear: 2025 });
    await stabilize();
    const questionnaire = http.expectOne((value) => value.url.endsWith('/api/journal/28032/questionnaire'));
    expect(questionnaire.request.params.get('surveyYear')).toBe('2025');
    questionnaire.flush({ questionnaire: { year: 2025, detailedSectionEnabled: false } });
    await stabilize();
    expect(router.url).toBe(LIST + '/28032');
    expect(harness.routeNativeElement!.querySelector('[data-testid="missing-questionnaire"]')).toBeNull();
  });

  it.each(['pl', 'en'])('styles the full-details ICV like the decision card and collapses its complete history (%s)', async (language) => {
    TestBed.inject(TranslocoService).setActiveLang(language);
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    const ratings = [
      { year: 2020, ratingStatus: 'INDEXED', currentMethodPoint: 0 },
      { year: 2024, ratingStatus: 'INDEXED', currentMethodPoint: 100 },
      { year: 2021, ratingStatus: 'INDEXED', currentMethodPoint: 81.125 },
      { year: 2023, ratingStatus: 'NOT_INDEXED' },
      { year: 2022, ratingStatus: 'NOT_EVALUATED' },
    ];
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentIcvPoints: 100,
      currentStatus: 'INDEXED', questionnaireAvailable: false, icvRatings: ratings,
    });
    await stabilize();
    const root = harness.routeNativeElement!;
    const title = root.querySelector('[data-testid="journal-icv"] h2')!;
    expect(title.textContent).toContain(language === 'pl' ? 'Wskaźnik ICV' : 'ICV indicator');
    expect(title.classList.contains('text-success')).toBe(true);
    const value = root.querySelector('[data-testid="journal-icv-value"]')!;
    expect(value.classList.contains('text-primary-emphasis')).toBe(true);
    expect(value.textContent).toContain(language === 'pl' ? 'ICV 2024: 100,00' : 'ICV 2024: 100.00');
    const button = root.querySelector<HTMLButtonElement>('[data-testid="journal-icv-history-toggle"]')!;
    const history = root.querySelector<HTMLElement>('#journal-icv-history-collapse')!;
    expect(button.classList.contains('accordion-button')).toBe(true);
    expect(button.closest('.accordion-flush')).not.toBeNull();
    expect(history.getAttribute('aria-labelledby')).toBe(button.id);
    expect(history.classList.contains('show')).toBe(false);
    expect(button.getAttribute('aria-controls')).toBe(history.id);
    expect(button.getAttribute('aria-expanded')).toBe('false');
    button.click();
    await stabilize();
    expect(history.classList.contains('show')).toBe(true);
    expect(button.getAttribute('aria-expanded')).toBe('true');
    const rows = [...history.querySelectorAll('tbody tr')];
    expect(rows.map((row) => row.querySelector('th')!.textContent!.trim())).toEqual(['2024', '2023', '2022', '2021', '2020']);
    expect(rows.map((row) => row.querySelector('td')!.textContent!.trim())).toEqual(language === 'pl'
      ? ['100,00', 'N/I', 'N/E', '81,13', '0,00']
      : ['100.00', 'N/I', 'N/E', '81.13', '0.00']);
    expect(root.querySelector('[data-testid="journal-icv-history-legend"]')).not.toBeNull();
    expect(ratings.map((rating) => rating.year)).toEqual([2020, 2024, 2021, 2023, 2022]);
    button.click();
    await stabilize();
    expect(history.classList.contains('show')).toBe(false);
    http.expectNone((request) => request.url.endsWith('/decision'));
  });

  it.each(['pl', 'en'])('shows the same ICV comparison in full details (%s)', async language => {
    autoIcvBenchmarks = false;
    TestBed.inject(TranslocoService).setActiveLang(language);
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne(value => value.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentIcvPoints: 81.125,
      currentStatus: 'INDEXED', questionnaireAvailable: false,
    });
    await stabilize();
    const request = http.expectOne(value => value.url.endsWith('/28032/decision'));
    expect(request.request.params.get('surveyYear')).toBe('2024');
    request.flush(icvComparisonResponse());
    await stabilize();
    const root = harness.routeNativeElement!;
    expect(root.querySelector('[data-testid="journal-icv-indicator"] .progress-bar')?.getAttribute('style')).toContain('78.4%');
    expect(root.querySelector('[data-testid="journal-icv-lower"]')?.textContent).toContain('78%');
    expect(root.querySelector('[data-testid="journal-icv-equal"]')?.textContent).toContain('4%');
    expect(root.querySelector('[data-testid="journal-icv-value"]')?.textContent).toContain(language === 'pl' ? '81,13' : '81.13');
  });

  function icvComparisonResponse() {
    return {
      identity: { id: 28032 }, surveyYear: 2024, annualStatus: 'AVAILABLE',
      icv: 81.125, icvStatus: 'AVAILABLE', icvHistory: [], annual: null, benchmarksVisible: true,
      benchmarks: { surveyYear: 2024, icv: {
        status: 'AVAILABLE', value: { lowerPercent: 78.4, equalPercent: 4.2, allEqual: false },
      } },
    };
  }

  it.each(['hidden', 'unavailable', 'wrong-year', 'wrong-value', 'error', 'all-equal'])(
    'handles optional ICV comparison: %s', async mode => {
      autoIcvBenchmarks = false;
      await harness.navigateByUrl(LIST + '/28032', JournalDetails);
      http.expectOne(value => value.url.endsWith('/api/journal/journal/28032')).flush({
        id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentIcvPoints: 81.125,
        currentStatus: 'INDEXED', questionnaireAvailable: false,
        icvRatings: [{ year: 2024, ratingStatus: 'INDEXED', currentMethodPoint: 81.125 }],
      });
      await stabilize();
      const response = icvComparisonResponse();
      if (mode === 'hidden') response.benchmarksVisible = false;
      if (mode === 'unavailable') response.benchmarks.icv.status = 'UNAVAILABLE';
      if (mode === 'wrong-year') response.benchmarks.surveyYear = 2023;
      if (mode === 'wrong-value') response.icv = 81;
      if (mode === 'all-equal') response.benchmarks.icv.value = { lowerPercent: 0, equalPercent: 100, allEqual: true };
      const request = http.expectOne(value => value.url.endsWith('/28032/decision'));
      if (mode === 'error') request.flush({}, { status: 503, statusText: 'Unavailable' });
      else request.flush(response);
      await stabilize();
      const root = harness.routeNativeElement!;
      expect(!!root.querySelector('[data-testid="journal-icv-comparison"]')).toBe(mode === 'all-equal');
      expect(root.querySelector('[data-testid="journal-icv-indicator"]')).toBeNull();
      if (mode === 'all-equal') expect(root.querySelector('[data-testid="journal-icv-all-equal"]')?.textContent).toContain(pl.decisionCard.icvComparison.allEqual);
      expect(root.querySelector('[data-testid="journal-icv-value"]')?.textContent).toContain('81,13');
      root.querySelector<HTMLButtonElement>('[data-testid="journal-icv-history-toggle"]')!.click();
      await stabilize();
      expect(root.querySelector('#journal-icv-history-collapse tbody td')?.textContent).toContain('81,13');
    },
  );

  it('cancels the previous ICV comparison on a year change', async () => {
    autoIcvBenchmarks = false;
    const page = await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    const journal = {
      id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentIcvPoints: 81.125,
      currentStatus: 'INDEXED', questionnaireAvailable: false, availableSurveyYears: [2024, 2023],
    };
    http.expectOne(value => value.url.endsWith('/api/journal/journal/28032')).flush(journal);
    await stabilize();
    const previous = http.expectOne(value => value.url.endsWith('/28032/decision'));
    page.selectSurveyYear(2023);
    await stabilize();
    expect(previous.cancelled).toBe(true);
    http.expectOne(value => value.url.endsWith('/api/journal/journal/28032')).flush({
      ...journal, surveyYear: 2023, currentIcvYear: 2023,
    });
    await stabilize();
    const current = http.expectOne(value => value.url.endsWith('/28032/decision'));
    expect(current.request.params.get('surveyYear')).toBe('2023');
    const response = icvComparisonResponse();
    response.surveyYear = response.benchmarks.surveyYear = 2023;
    current.flush(response);
    await stabilize();
    expect(harness.routeNativeElement!.querySelector('[data-testid="journal-icv-comparison"]')).not.toBeNull();
  });

  it.each(['BASIC', 'DISABLED'] as const)('does not request optional ICV comparisons without access: %s', async mode => {
    autoIcvBenchmarks = false;
    vi.spyOn(TestBed.inject(OperatingProfileService), 'currentContext').mockReturnValue(of({
      institutionId: 10, configuredProfile: mode === 'BASIC' ? 'BASIC' : 'BOTH', defaultProfile: 'BOTH',
      effectiveProfile: mode === 'BASIC' ? 'BASIC' : 'BOTH', defaultSearchView: 'BASIC',
      availableFeatures: mode === 'BASIC' ? ['BASIC_SEARCH', 'DECISION_CARD'] : ['BASIC_SEARCH'],
    }));
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne(value => value.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentIcvPoints: 81.125,
      currentStatus: 'INDEXED', questionnaireAvailable: false,
    });
    await stabilize();
    http.expectNone(value => value.url.endsWith('/28032/decision'));
    expect(harness.routeNativeElement!.querySelector('[data-testid="journal-icv-value"]')?.textContent).toContain('81,13');
  });

  it.each([0, undefined, NaN])('preserves zero and handles missing/invalid ICV %s without an empty history toggle', async (points) => {
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentIcvPoints: points,
      currentStatus: 'INDEXED', questionnaireAvailable: false, icvRatings: [],
    });
    await stabilize();
    const root = harness.routeNativeElement!;
    expect(root.querySelector('[data-testid="journal-icv-value"]')!.textContent).toContain(points === 0 ? '0,00' : '—');
    expect(root.querySelector('[data-testid="journal-icv-history-toggle"]')).toBeNull();
  });

  it.each(['NOT_EVALUATED', 'NOT_INDEXED'])('retains the current %s status and historical status labels', async (status) => {
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentStatus: status,
      currentReasonNotIndexing: 'dec01', questionnaireAvailable: false,
      icvRatings: [{ year: 2024, ratingStatus: status }],
    });
    await stabilize();
    const root = harness.routeNativeElement!;
    expect(root.querySelector(`[data-testid="journal-icv-${status === 'NOT_EVALUATED' ? 'not-evaluated' : 'not-indexed'}"]`)).not.toBeNull();
    expect(root.querySelector('[data-testid="journal-icv-value"]')).toBeNull();
    root.querySelector<HTMLButtonElement>('[data-testid="journal-icv-history-toggle"]')!.click();
    await stabilize();
    expect(root.querySelector('#journal-icv-history-collapse tbody td')!.textContent!.trim()).toBe(status === 'NOT_EVALUATED' ? 'N/E' : 'N/I');
  });

  it('resets expanded ICV history after a year or journal change', async () => {
    const page = await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    const journal = {
      id: 28032, surveyYear: 2024, currentIcvYear: 2024, currentIcvPoints: 100,
      currentStatus: 'INDEXED', questionnaireAvailable: false, availableSurveyYears: [2025, 2024],
      icvRatings: [{ year: 2024, ratingStatus: 'INDEXED', currentMethodPoint: 100 }],
    };
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032')).flush(journal);
    await stabilize();
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[data-testid="journal-icv-history-toggle"]')!.click();
    await stabilize();
    expect(page.icvHistoryOpen()).toBe(true);
    page.selectSurveyYear(2025);
    const year = http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'));
    expect(year.request.params.get('surveyYear')).toBe('2025');
    year.flush({ ...journal, surveyYear: 2025, currentIcvYear: 2025, currentIcvPoints: 81.125 });
    await stabilize();
    expect(page.icvHistoryOpen()).toBe(false);
    expect(harness.routeNativeElement!.querySelector('[data-testid="journal-icv-value"]')!.textContent).toContain('ICV 2025: 81,13');
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[data-testid="journal-icv-history-toggle"]')!.click();
    await stabilize();
    await harness.navigateByUrl(LIST + '/28033', JournalDetails);
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28033')).flush({ ...journal, id: 28033 });
    await stabilize();
    expect(page.icvHistoryOpen()).toBe(false);
    expect(harness.routeNativeElement!.querySelector<HTMLElement>('#journal-icv-history-collapse')!.classList.contains('show')).toBe(false);
  });

  it('shows missing survey and switches to an available year without changing the URL or losing the return context', async () => {
    vi.spyOn(TestBed.inject(ConfigurationService), 'getSearchYears').mockReturnValue(
      of({ defaultYear: 2025, availableYears: [2025, 2024] }),
    );
    await enter();
    await finish();
    await clickLink('[data-testid="journal-result-title"]');
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'))
      .flush({ id: 28032, surveyYear: 2025, questionnaireAvailable: false, availableSurveyYears: [2024],
        currentStatus: 'NOT_EVALUATED', currentIcvYear: 2025 });
    await stabilize();
    expect(harness.routeNativeElement!.querySelector('[data-testid="missing-questionnaire"]')?.textContent)
      .toContain('Brak danych kwestionariusza za rok 2025');
    expect(harness.routeNativeElement!.querySelector('[data-testid="journal-survey-context"]')).toBeNull();
    expect(harness.routeNativeElement!.querySelector('[data-testid="questionnaire-latest-year"]')?.textContent)
      .toContain('Wyświetl ankietę 2024');
    http.expectNone((value) => value.url.endsWith('/questionnaire') || value.url.endsWith('/analytics'));
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[data-testid="questionnaire-latest-year"]')!.click();
    await stabilize();
    const request = http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'));
    expect(request.request.params.get('surveyYear')).toBe('2024');
    request.flush({ id: 28032, surveyYear: 2024, questionnaireAvailable: true,
      availableSurveyYears: [2024], currentStatus: 'NOT_EVALUATED', currentIcvYear: 2024 });
    await stabilize();
    const questionnaire = http.expectOne((value) => value.url.endsWith('/api/journal/28032/questionnaire'));
    expect(questionnaire.request.params.get('surveyYear')).toBe('2024');
    questionnaire.flush({ questionnaire: { year: 2024, detailedSectionEnabled: false } });
    await stabilize();
    expect(router.url).toBe(LIST + '/28032');
    expect(TestBed.inject(Location).getState()).toMatchObject({ surveyYear: 2024, journalListOrigin: expect.any(String) });
    expect(harness.routeNativeElement!.querySelector('[data-testid="missing-questionnaire"]')).toBeNull();
    expect(harness.routeNativeElement!.querySelector('[data-survey-year="2025"]')).toBeTruthy();
    await browserBack();
    const restored = search();
    expect(restored.request.body.surveyYear).toBe(2025);
    await finish(restored);
    const completed = firstValueFrom(router.events.pipe(filter((event) => event instanceof NavigationEnd)));
    TestBed.inject(Location).forward();
    await completed;
    await stabilize();
    const forward = http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'));
    expect(forward.request.params.get('surveyYear')).toBe('2024');
    forward.flush({ id: 28032, surveyYear: 2024, questionnaireAvailable: false, availableSurveyYears: [] });
    await stabilize();
  });

  it('keeps the selected year and hides the fallback action when no published questionnaire exists', async () => {
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'))
      .flush({ id: 28032, surveyYear: 2025, questionnaireAvailable: false, availableSurveyYears: [] });
    await stabilize();
    expect(harness.routeNativeElement!.querySelector('[data-testid="missing-questionnaire"]')?.textContent)
      .toContain('Brak danych kwestionariusza za rok 2025');
    expect(harness.routeNativeElement!.querySelectorAll('[data-survey-year]')).toHaveLength(1);
    expect(harness.routeNativeElement!.querySelector('[data-survey-year="2025"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(harness.routeNativeElement!.querySelector('[data-testid="questionnaire-latest-year"]')).toBeNull();
    expect(harness.routeNativeElement!.querySelector('[data-testid="questionnaire-report-download"]')).toBeNull();
    http.expectNone((value) => value.url.endsWith('/questionnaire'));
  });

  it('keeps the questionnaire card, tab and scroll while switching years and downloads the selected report', async () => {
    const details = await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2025, questionnaireAvailable: true, availableSurveyYears: [2025, 2024],
      currentStatus: 'NOT_EVALUATED', currentIcvYear: 2025,
    });
    await stabilize();
    http.expectOne((request) => request.url.endsWith('/questionnaire')).flush({ questionnaire: { year: 2025 } });
    await stabilize();
    const root = harness.routeNativeElement!;
    const card = root.querySelector('[data-testid="journal-questionnaire"]')!;
    expect(card.closest('.col-12')).toBeTruthy();
    expect(card.querySelector('#questionnaire-heading')?.textContent).toBe('Kwestionariusz czasopisma');
    expect(card.querySelector('[data-testid="questionnaire-status"]')?.textContent).toContain('Dostępna ankieta');
    const tabs = card.querySelectorAll<HTMLAnchorElement>('.nav-link');
    tabs[1].click();
    await stabilize();
    expect(card.querySelector('.nav-link.active')?.textContent).toContain('Stabilność');
    viewport.scrollToPosition.mockClear();
    card.querySelector<HTMLButtonElement>('[data-survey-year="2024"]')!.click();
    await stabilize();
    expect(root.querySelector('[data-testid="journal-questionnaire"]')).toBe(card);
    expect(card.querySelector('[data-testid="questionnaire-status"]')?.textContent).toContain('Ładowanie');
    expect(card.querySelector('[data-testid="missing-questionnaire"]')).toBeNull();
    expect(card.querySelector('[data-testid="questionnaire-report-download"]')).toBeNull();
    http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2024, questionnaireAvailable: true, availableSurveyYears: [2025, 2024],
      currentStatus: 'NOT_EVALUATED', currentIcvYear: 2024,
    });
    await stabilize();
    http.expectOne((request) => request.url.endsWith('/questionnaire')).flush({
      questionnaire: { year: 2024, detailedSectionEnabled: true, survey: { evaluationYear: 2024 } },
    });
    await stabilize();
    expect(root.querySelector('[data-testid="journal-questionnaire"]')).toBe(card);
    expect(card.querySelector('.nav-link.active')?.textContent).toContain('Stabilność');
    expect(card.querySelector('[data-testid="detailed-section"]')).toBeTruthy();
    expect(card.querySelector('[data-survey-year="2024"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(details.journal()?.currentIcvYear).toBe(2024);
    expect(viewport.scrollToPosition).not.toHaveBeenCalled();
    card.querySelector<HTMLButtonElement>('[data-testid="questionnaire-report-download"]')!.click();
    const report = http.expectOne((request) => request.url.endsWith('/questionnaire/report'));
    expect(report.request.params.get('surveyYear')).toBe('2024');
    report.flush(new Blob(['report']));
    await stabilize();
  });

  it('cancels old questionnaire and details requests when the year changes during loading', async () => {
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2025, questionnaireAvailable: true, availableSurveyYears: [2025, 2024],
      currentStatus: 'NOT_EVALUATED', currentIcvYear: 2025,
    });
    await stabilize();
    const oldQuestionnaire = http.expectOne((request) => request.url.endsWith('/questionnaire'));
    const root = harness.routeNativeElement!;
    root.querySelector<HTMLButtonElement>('[data-survey-year="2024"]')!.click();
    await stabilize();
    expect(oldQuestionnaire.cancelled).toBe(true);
    const oldDetails = http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032'));
    root.querySelector<HTMLButtonElement>('[data-survey-year="2025"]')!.click();
    await stabilize();
    expect(oldDetails.cancelled).toBe(true);
    const latestDetails = http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032'));
    expect(latestDetails.request.params.get('surveyYear')).toBe('2025');
    latestDetails.flush({
      id: 28032, surveyYear: 2025, questionnaireAvailable: true, availableSurveyYears: [2025, 2024],
      currentStatus: 'NOT_EVALUATED', currentIcvYear: 2025,
    });
    await stabilize();
    const latestQuestionnaire = http.expectOne((request) => request.url.endsWith('/questionnaire'));
    expect(latestQuestionnaire.request.params.get('surveyYear')).toBe('2025');
    latestQuestionnaire.flush({ questionnaire: { year: 2025 } });
    await stabilize();
    expect(root.querySelector('[data-survey-year="2025"]')?.getAttribute('aria-pressed')).toBe('true');
    expect(root.querySelector('[data-testid="questionnaire-status"]')?.textContent).toContain('Dostępna ankieta');
  });

  it('retries a failed year context inside the questionnaire card without showing an empty state', async () => {
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2025, questionnaireAvailable: false, availableSurveyYears: [2024],
      currentStatus: 'NOT_EVALUATED', currentIcvYear: 2025,
    });
    await stabilize();
    const root = harness.routeNativeElement!;
    root.querySelector<HTMLButtonElement>('[data-survey-year="2024"]')!.click();
    await stabilize();
    http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032'))
      .flush({}, { status: 500, statusText: 'Server error' });
    await stabilize();
    expect(root.querySelector('[data-testid="missing-questionnaire"]')).toBeNull();
    expect(root.querySelector('[data-testid="journal-questionnaire"] [role="alert"]')).toBeTruthy();
    root.querySelector<HTMLButtonElement>('[data-testid="questionnaire-retry"]')!.click();
    await stabilize();
    const retry = http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032'));
    expect(retry.request.params.get('surveyYear')).toBe('2024');
    retry.flush({ id: 28032, surveyYear: 2024, questionnaireAvailable: true, availableSurveyYears: [2024],
      currentStatus: 'NOT_EVALUATED', currentIcvYear: 2024 });
    await stabilize();
    http.expectOne((request) => request.url.endsWith('/questionnaire')).flush({ questionnaire: { year: 2024 } });
    await stabilize();
    expect(root.querySelector('[data-testid="questionnaire-status"]')?.textContent).toContain('Dostępna ankieta');
  });

  it.each([404, 500])('distinguishes missing questionnaire from a fetch error with HTTP %s', async (status) => {
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2025, questionnaireAvailable: true, availableSurveyYears: [2025, 2024],
      currentStatus: 'NOT_EVALUATED', currentIcvYear: 2025,
    });
    await stabilize();
    http.expectOne((request) => request.url.endsWith('/questionnaire'))
      .flush({}, { status, statusText: 'Questionnaire unavailable' });
    await stabilize();
    const root = harness.routeNativeElement!;
    expect(!!root.querySelector('[data-testid="missing-questionnaire"]')).toBe(status === 404);
    expect(!!root.querySelector('[data-testid="questionnaire-retry"]')).toBe(status === 500);
    expect(root.querySelector('[data-testid="questionnaire-report-download"]')).toBeNull();
    expect(root.querySelector('app-journal-questionnaire .nav')).toBeNull();
    if (status === 500) {
      root.querySelector<HTMLButtonElement>('[data-testid="questionnaire-retry"]')!.click();
      await stabilize();
      http.expectOne((request) => request.url.endsWith('/questionnaire')).flush({ questionnaire: { year: 2025 } });
      await stabilize();
      expect(root.querySelector('[data-testid="questionnaire-report-download"]')).toBeTruthy();
    }
  });

  it.each([true, false])('preserves the non-indexed questionnaire visibility rule: confirmation allowed %s', async (visible) => {
    await harness.navigateByUrl(LIST + '/28032', JournalDetails);
    http.expectOne((request) => request.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2025, questionnaireAvailable: true, availableSurveyYears: [2025],
      currentStatus: 'NOT_INDEXED', currentIcvYear: 2025,
      currentReasonNotIndexingFlag: visible, currentReasonNotIndexing: visible ? 'dec06' : 'dec01',
    });
    await stabilize();
    const root = harness.routeNativeElement!;
    const reveal = root.querySelector<HTMLButtonElement>('[data-testid="questionnaire-reveal"]');
    expect(!!reveal).toBe(visible);
    expect(root.querySelector('[data-testid="missing-questionnaire"]')).toBeNull();
    http.expectNone((request) => request.url.endsWith('/questionnaire'));
    if (reveal) {
      reveal.click();
      await stabilize();
      http.expectOne((request) => request.url.endsWith('/questionnaire')).flush({ questionnaire: { year: 2025 } });
      http.expectOne((request) => request.url.endsWith('/analytics')).flush({});
      await stabilize();
      expect(root.querySelector('[data-testid="questionnaire-report-download"]')).toBeTruthy();
    }
  });

  it('starts fresh with Poland, A-Z, page one, chip and exact notice despite legacy session storage', async () => {
    sessionStorage.setItem(
      'journalList.query',
      JSON.stringify({ journalTitle: 'Old', countryCodes: ['de'], page: 2 }),
    );
    const list = await enter();
    expect(search().request.body).toMatchObject({
      countryCodes: ['pl'],
      sortBy: SORT.SortByTitleAsc,
      page: 0,
      first: 0,
      size: 10,
    });
    expect(list.filterForm.controls.countryCodes.value).toEqual(['pl']);
    expect(TestBed.inject(DictionaryService).findRepositories).toHaveBeenCalledTimes(1);
    expect(list.showDefaultNotice()).toBe(true);
    expect(harness.routeNativeElement!.textContent).toContain(
      'Domyślnie wyświetlamy czasopisma polskie.',
    );
    expect(
      harness.routeNativeElement!.querySelector('[data-filter-id="countryCodes:pl"]')?.textContent,
    ).toContain('Polska');
    TestBed.inject(TranslocoService).setActiveLang('en');
    await stabilize();
    expect(
      harness.routeNativeElement!.querySelector('[data-filter-id="countryCodes:pl"]')?.textContent,
    ).toContain('Poland');
  });

  it('removes Poland immediately without a pending message and restores the cleared criteria after P2', async () => {
    const list = await enter();
    await finish();
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>(
        '[data-filter-id="countryCodes:pl"] button',
      )!
      .click();
    await stabilize();
    const removed = search();
    expect(removed.request.body).toMatchObject({
      countryCodes: [],
      page: 0,
      first: 0,
      sortBy: SORT.SortByTitleAsc,
    });
    expect(list.filterForm.controls.countryCodes.value).toEqual([]);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(list.showDefaultNotice()).toBe(false);
    expect(
      harness.routeNativeElement!.querySelector('[data-filter-id="countryCodes:pl"]'),
    ).toBeNull();
    expect(
      harness.routeNativeElement!.querySelector('app-journal-applied-filters [role=status]'),
    ).toBeNull();
    await finish(removed);
    await details();
    await browserBack();
    const returned = search();
    expect(returned.request.body.countryCodes).toEqual([]);
    await finish(returned);
    const restored = harness.routeDebugElement!.componentInstance as JournalList;
    expect(restored.filterForm.controls.countryCodes.value).toEqual([]);
    expect(restored.hasUnappliedChanges()).toBe(false);
    expect(restored.showDefaultNotice()).toBe(false);
    expect(
      vi
        .mocked(TestBed.inject(NavigationAuditService).track)
        .mock.calls.filter(([event]) => event.eventType === 'JOURNAL_FILTERS_CLEAR'),
    ).toHaveLength(1);
  });

  it('cancels older responses during consecutive chip removals and retries the new criteria without counting another clear', async () => {
    const list = await enter();
    await finish();
    list.filterForm.controls.journalTitle.setValue('Applied');
    list.applyFilters();
    await stabilize();
    const older = search();
    list.removeAppliedFilter(
      list.appliedFilterChips().find((chip) => chip.id === 'countryCodes:pl')!,
    );
    await stabilize();
    expect(older.cancelled).toBe(true);
    const firstRemoval = search();
    expect(firstRemoval.request.body).toMatchObject({ countryCodes: [], journalTitle: 'Applied' });
    list.removeAppliedFilter(list.appliedFilterChips().find((chip) => chip.id === 'journalTitle')!);
    await stabilize();
    expect(firstRemoval.cancelled).toBe(true);
    const latest = search();
    expect(latest.request.body.countryCodes).toEqual([]);
    expect(latest.request.body.journalTitle).toBeUndefined();
    latest.flush({}, { status: 503, statusText: 'Unavailable' });
    await stabilize();
    expect(list.hasError()).toBe(true);
    list.filterForm.patchValue({ journalTitle: 'Unapplied', publicationDaysTo: '-1' });
    list.retrySearch();
    await stabilize();
    const retry = search();
    expect(retry.request.body).toEqual(latest.request.body);
    await finish(retry);
    expect(list.hasError()).toBe(false);
    expect(list.items()[0].title).toBe('Journal');
    expect(list.filterForm.controls.journalTitle.value).toBe('Unapplied');
    expect(() => older.flush(PAGE)).toThrow();
    expect(() => firstRemoval.flush(PAGE)).toThrow();
    const events = vi
      .mocked(TestBed.inject(NavigationAuditService).track)
      .mock.calls.map(([event]) => event);
    expect(events.filter((event) => event.eventType === 'JOURNAL_FILTERS_CLEAR')).toHaveLength(2);
    expect(
      events.filter((event) => event.eventType === 'JOURNAL_SEARCH_RESULT').at(-1)?.properties,
    ).toMatchObject({ origin: 'CLEAR', attempt: 2 });
  });

  it('makes identical Search a new HTTP request; clear immediately removes Poland and the notice', async () => {
    const list = await enter();
    const initial = search();
    const body = initial.request.body;
    await finish(initial);
    list.applyFilters();
    await stabilize();
    const repeated = search();
    expect(repeated.request.body).toEqual(body);
    await finish(repeated);
    expect(list.showDefaultNotice()).toBe(false);
    list.resetFilters();
    await stabilize();
    expect(search().request.body).toMatchObject({
      countryCodes: [],
      page: 0,
      sortBy: SORT.SortByTitleAsc,
    });
    expect(list.appliedFilterChips()).toEqual([]);
    expect(list.hasUnappliedChanges()).toBe(false);
  });

  it.each(['[data-testid="journal-result-title"]', '[data-testid="journal-result-details"]'])(
    'restores applied and invalid draft, page 3, sort and scroll via the P2 back link: %s',
    async (selector) => {
      const list = await enter();
      await finish();
      list.filterForm.patchValue({
        journalTitle: 'Applied',
        countryCodes: ['de'],
        feeFilters: { publicationFeeMaxPln: '0' },
        reviewDaysFrom: '0', reviewDaysTo: '10',
        initialAssessmentDaysFrom: '2', initialAssessmentDaysTo: '5',
        publicationDaysFrom: '30', publicationDaysTo: '60',
        mniswOnly: true,
      });
      list.onMniswToggle();
      list.filterForm.patchValue({ mniswPointsFrom: '70', disciplineCodes: ['3'] });
      list.applyFilters();
      await stabilize();
      await finish();
      list.filterForm.controls.sortBy.setValue(SORT.SortByTitleDesc);
      list.changeSort();
      await stabilize();
      await finish();
      list.goToPage(2);
      await stabilize();
      const applied = search();
      const body = applied.request.body;
      await finish(applied);
      list.filterForm.patchValue({
        journalTitle: 'Unsaved',
        reviewDaysFrom: '-2',
        reviewDaysTo: '-1',
        feeFilters: { publicationFeeMaxPln: '-2' },
      });
      const draft = list.filterForm.getRawValue();
      const detailsElement = harness.routeNativeElement!.querySelector('details')!;
      detailsElement.open = true;
      await stabilize();
      await details(selector);
      const audit = vi.mocked(TestBed.inject(NavigationAuditService).track);
      const cardEvents = audit.mock.calls
        .map(([event]) => event)
        .filter((event) => event.eventType === 'JOURNAL_CARD_OPEN');
      expect(cardEvents).toHaveLength(1);
      expect(cardEvents[0]).toMatchObject({
        resource: { type: 'journal', id: '28032' },
        properties: {
          criteria: {
            journalTitle: 'Applied',
            countryCodes: ['de'],
            publicationFeeMaxPln: 0,
            mniswPointsFrom: 70,
          },
        },
      });
      const operationId = cardEvents[0].properties?.['operationId'];
      const searchEvents = audit.mock.calls.filter(
        ([event]) => event.eventType === 'JOURNAL_SEARCH',
      ).length;
      expect(viewport.scrollToPosition).toHaveBeenLastCalledWith([0, 0]);
      viewport.scrollToPosition.mockClear();
      await clickLink('a[href="/institution/user/journals"]');
      const restored = harness.routeDebugElement!.componentInstance as JournalList;
      const returned = search();
      expect(returned.request.body).toEqual(body);
      expect(restored.filterForm.getRawValue()).toEqual(draft);
      expect(restored.hasUnappliedChanges()).toBe(true);
      expect(restored.page()).toBe(2);
      expect(viewport.scrollToPosition).not.toHaveBeenCalled();
      returned.flush({
        ...PAGE,
        data: [{ id: 28032, title: 'Updated journal' }],
        totalElements: 21,
      });
      await stabilize();
      expect(restored.items()[0].title).toBe('Updated journal');
      expect(restored.filterForm.getRawValue()).toEqual(draft);
      expect(viewport.scrollToPosition).toHaveBeenCalledExactlyOnceWith([0, 1450]);
      const results = audit.mock.calls
        .map(([event]) => event)
        .filter((event) => event.eventType === 'JOURNAL_SEARCH_RESULT');
      expect(results.at(-1)?.properties?.['operationId']).toBe(operationId);
      expect(
        audit.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_SEARCH'),
      ).toHaveLength(searchEvents);
      expect(harness.routeNativeElement!.querySelector('details')?.open).toBe(true);
    },
  );

  it('restores expanded groups and full language lists before scrolling to fully rendered result cards', async () => {
    const list = await enter();
    const journals = Array.from({ length: 10 }, (_, index) => ({
      id: index === 0 ? 28032 : index,
      title: `Journal ${index}`,
      currentIcvYear: 2024,
      currentIcvPoints: 45.67,
      languages: [{ code: 'pl' }, { code: 'en' }, { code: 'de' }],
      publicationDays: 0,
      fees: { noFees: true, items: [] },
    }));
    await finish();
    list.goToPage(2);
    await stabilize();
    search().flush({ ...PAGE, data: journals });
    await stabilize();
    list.filterForm.controls.journalTitle.setValue('Unapplied');
    const element = harness.routeNativeElement!;
    element.querySelector<HTMLDetailsElement>('[data-advanced-group="fees"]')!.open = true;
    element.querySelector<HTMLDetailsElement>('[data-advanced-group="repositories"]')!.open = true;

    await details();
    viewport.scrollToPosition.mockClear();
    let renderedAtScroll:
      | {
          count: number;
          feesOpen: boolean;
          databasesOpen: boolean;
          scoresInMainForm: boolean;
          icvInHeader: boolean;
          languages: string;
          resultDisclosures: number;
          aspectRatio: string;
          publicationTime: string;
          filterScroll: number;
        }
      | undefined;
    viewport.scrollToPosition.mockImplementation(() => {
      const restored = harness.routeNativeElement!;
      renderedAtScroll = {
        filterScroll: restored.querySelector<HTMLElement>('.journal-filter-options')!.scrollTop,
        count: restored.querySelectorAll('article.journal-result-card').length,
        feesOpen: restored.querySelector<HTMLDetailsElement>('[data-advanced-group="fees"]')!.open,
        databasesOpen: restored.querySelector<HTMLDetailsElement>(
          '[data-advanced-group="repositories"]',
        )!.open,
        scoresInMainForm: !!restored.querySelector('#journal-search-form [data-main-group="mnisw"]'),
        icvInHeader: !!restored.querySelector('.journal-search-header [data-testid="journal-header-icv"] .icv-range'),
        languages:
          restored.querySelector('[data-testid="journal-result-languages"]')!.textContent ?? '',
        resultDisclosures: restored.querySelectorAll('.journal-result-card details').length,
        aspectRatio: restored.querySelector<HTMLElement>('.safe-image')!.style.aspectRatio,
        publicationTime:
          restored.querySelector('[data-testid="journal-result-publication-time"]')!.textContent ??
          '',
      };
    });
    await clickLink('a[href="/institution/user/journals"]');
    const returned = search();
    expect(returned.request.body.page).toBe(2);
    expect(returned.request.body.journalTitle).toBeUndefined();
    expect(viewport.scrollToPosition).not.toHaveBeenCalled();
    returned.flush({ ...PAGE, data: journals });
    await stabilize();
    expect(viewport.scrollToPosition).toHaveBeenCalledExactlyOnceWith([0, 1450]);
    expect(renderedAtScroll).toMatchObject({
      count: 10,
      filterScroll: 0,
      feesOpen: true,
      databasesOpen: true,
      scoresInMainForm: true,
      icvInHeader: true,
      languages: 'pl, en, de',
      resultDisclosures: 0,
      aspectRatio: '120 / 176',
    });
    expect(renderedAtScroll?.publicationTime).toContain('0');
    const restored = harness.routeDebugElement!.componentInstance as JournalList;
    expect(restored.filterForm.controls.journalTitle.value).toBe('Unapplied');
    expect(restored.hasUnappliedChanges()).toBe(true);
  });

  it.each(['INDEXED', 'NOT_INDEXED', 'NOT_EVALUATED'] as const)(
    'restores applied %s separately from later draft status after P2',
    async (status) => {
      const list = await enter();
      await finish();
      list.toggleIcvStatus(status);
      list.applyFilters();
      await stabilize();
      const applied = search();
      expect(applied.request.body.ratingStatus).toBe(status);
      expect(applied.request.body.icvFrom).toBeUndefined();
      await finish(applied);
      const draft = status === 'NOT_INDEXED' ? 'NOT_EVALUATED' : 'NOT_INDEXED';
      list.toggleIcvStatus(draft);
      await details();
      await clickLink('a[href="/institution/user/journals"]');
      const returned = search();
      expect(returned.request.body.ratingStatus).toBe(status);
      await finish(returned);
      const restored = harness.routeDebugElement!.componentInstance as JournalList;
      expect(restored.filterForm.controls.ratingStatus.value).toBe(draft);
      expect(restored.filterForm.controls.icvFrom.disabled).toBe(true);
      expect(restored.hasUnappliedChanges()).toBe(true);
      expect(
        restored.appliedFilterChips().find((chip) => chip.id === 'ratingStatus')?.labelKey,
      ).toBe(
        'page.institution.journalList.' +
          (status === 'INDEXED'
            ? 'indexedJournals'
            : status === 'NOT_INDEXED'
              ? 'notIndexedJournals'
              : 'notEvaluatedJournals'),
      );
      expect(viewport.scrollToPosition).toHaveBeenLastCalledWith([0, 1450]);
    },
  );

  it('restores on browser back and repeated forward/back without treating menu navigation as a return', async () => {
    const list = await enter();
    await finish();
    list.filterForm.controls.journalTitle.setValue('Applied');
    list.applyFilters();
    await stabilize();
    await finish();
    await details();
    await browserBack();
    const firstReturn = search();
    expect(firstReturn.request.body.journalTitle).toBe('Applied');
    const completed = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    TestBed.inject(Location).forward();
    await completed;
    await stabilize();
    expect(firstReturn.cancelled).toBe(true);
    http
      .expectOne((request) => request.url.endsWith('/api/journal/journal/28032'))
      .flush({ id: 28032 });
    await stabilize();
    await browserBack();
    const secondReturn = search();
    expect(secondReturn.request.body.journalTitle).toBe('Applied');
    await finish(secondReturn);
    await details();
    await harness.navigateByUrl(LIST, JournalList);
    await stabilize();
    expect(search().request.body).toMatchObject({ countryCodes: ['pl'], page: 0 });
    expect(
      (harness.routeDebugElement!.componentInstance as JournalList).filterForm.controls.journalTitle
        .value,
    ).toBe('');
  });

  it('same-URL menu and returning from another page are fresh entries', async () => {
    const list = await enter();
    await finish();
    list.resetFilters();
    await stabilize();
    await finish();
    list.filterForm.controls.journalTitle.setValue('Draft');
    await router.navigateByUrl(LIST);
    await stabilize();
    expect(search().request.body).toMatchObject({
      countryCodes: ['pl'],
      page: 0,
      sortBy: SORT.SortByTitleAsc,
    });
    expect(list.filterForm.controls.journalTitle.value).toBe('');
    expect(list.showDefaultNotice()).toBe(true);
    await harness.navigateByUrl('/other');
    const fresh = await enter();
    expect(search().request.body.countryCodes).toEqual(['pl']);
    expect(fresh.hasUnappliedChanges()).toBe(false);
  });

  it('submits the pagination jump independently of the search form without applying its draft', async () => {
    const list = await enter();
    await finish();
    const applied = list.query();
    list.filterForm.controls.journalTitle.setValue('Unapplied title');
    await stabilize();
    const root = harness.routeNativeElement!;
    expect(root.querySelector('form form')).toBeNull();
    const page = root.querySelector<HTMLInputElement>('#journal-target-page')!;
    page.value = '3';
    root
      .querySelector('app-journal-pagination form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await stabilize();
    const request = search();
    expect(request.request.body).toEqual({ ...applied, page: 2, first: 20 });
    await finish(request);
    expect(list.filterForm.controls.journalTitle.value).toBe('Unapplied title');
    expect(list.query().journalTitle).toBeUndefined();
    expect(list.hasUnappliedChanges()).toBe(true);
  });

  it('retries a failed page with applied criteria, not subsequent invalid draft edits, and restores scroll after retry', async () => {
    const list = await enter();
    await finish();
    list.goToPage(2);
    await stabilize();
    await finish();
    list.filterForm.controls.journalTitle.setValue('Draft before P2');
    await details();
    await browserBack();
    const failed = search();
    const body = failed.request.body;
    failed.flush({}, { status: 503, statusText: 'Unavailable' });
    await stabilize();
    const restored = harness.routeDebugElement!.componentInstance as JournalList;
    expect(restored.hasError()).toBe(true);
    restored.filterForm.patchValue({ journalTitle: 'Later draft', publicationDaysTo: '-5' });
    viewport.scrollToPosition.mockClear();
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>('[data-testid="journal-search-retry"]')!
      .click();
    await stabilize();
    const retry = search();
    expect(retry.request.body).toEqual(body);
    await finish(retry);
    expect(restored.filterForm.controls.journalTitle.value).toBe('Later draft');
    expect(restored.filterForm.controls.publicationDaysTo.value).toBe('-5');
    expect(restored.hasError()).toBe(false);
    expect(viewport.scrollToPosition).toHaveBeenCalledExactlyOnceWith([0, 1450]);
  });

  it('cancels earlier HTTP responses after Search, sort, clear and leaving P1; only the newest result wins', async () => {
    const list = await enter();
    const initial = search();
    list.filterForm.controls.journalTitle.setValue('New');
    list.applyFilters();
    await stabilize();
    expect(initial.cancelled).toBe(true);
    const searched = search();
    list.filterForm.controls.sortBy.setValue(SORT.SortByTitleDesc);
    list.changeSort();
    await stabilize();
    expect(searched.cancelled).toBe(true);
    const sorted = search();
    list.resetFilters();
    await stabilize();
    expect(sorted.cancelled).toBe(true);
    const latest = search();
    latest.flush({ ...PAGE, data: [{ id: 28032, title: 'Newest' }] });
    await stabilize();
    expect(list.items()[0].title).toBe('Newest');
    expect(() => initial.flush(PAGE)).toThrow();
    expect(list.filterForm.controls.journalTitle.value).toBe('');
    list.goToPage(2);
    await stabilize();
    const paging = search();
    await harness.navigateByUrl('/other');
    expect(paging.cancelled).toBe(true);
    const fresh = await enter();
    const freshRequest = search();
    await finish(freshRequest);
    expect(fresh.items()[0].title).toBe('Journal');
  });

  it('does not restore a forged/copied history token after reload/direct P2 entry', async () => {
    await router.navigateByUrl(LIST + '/28032', { state: { journalListOrigin: 'copied-origin' } });
    await stabilize();
    http
      .expectOne((request) => request.url.endsWith('/api/journal/journal/28032'))
      .flush({ id: 28032 });
    await stabilize();
    expect(TestBed.inject(JournalListQueryStateService).returnNavigationState()).toEqual({});
    await clickLink('a[href="/institution/user/journals"]');
    expect(search().request.body).toMatchObject({ countryCodes: ['pl'], page: 0 });
  });

  it('keeps restored selections and invalid draft through a delayed or failed discipline dictionary', async () => {
    const list = await enter();
    await finish();
    list.filterForm.controls.disciplineCodes.setValue(['3']);
    list.applyFilters();
    await stabilize();
    await finish();
    list.filterForm.patchValue({ disciplineCodes: ['999'], reviewDaysTo: '-2' });
    const draft = list.filterForm.getRawValue();
    await details();
    const dictionary = new Subject<ScientificCategoryDictionaryView[]>();
    disciplineLoader.mockReturnValue(dictionary);
    await browserBack();
    const restored = harness.routeDebugElement!.componentInstance as JournalList;
    const request = search();
    expect(request.request.body.disciplineCodes).toEqual(['3']);
    dictionary.error(new Error('Dictionary unavailable'));
    await finish(request);
    expect(restored.filterForm.getRawValue()).toEqual(draft);
    expect(restored.filterForm.controls.disciplineCodes.value).toEqual(['999']);
    expect(restored.appliedFilterChips().some((chip) => chip.id === 'disciplineCodes:3')).toBe(
      true,
    );
  });

  it('restores applied and draft MNiSW disciplines separately after P2 with sort and page changes', async () => {
    const list = await enter();
    await finish();
    const finishResized = async (request: TestRequest) => {
      request.flush({ ...PAGE, size: 50, totalElements: 137, totalPages: 3 });
      await stabilize();
    };
    list.filterForm.controls.mniswOnly.setValue(true);
    list.onMniswToggle();
    list.setMniswDisciplineCodes(['2021']);
    list.changePageSizeValue(50);
    list.applyFilters();
    await stabilize();
    const applied = search();
    expect(applied.request.body.mniswDisciplineCodes).toEqual(['2021']);
    await finishResized(applied);
    list.setMniswDisciplineCodes(['2061']);
    list.filterForm.controls.sortBy.setValue(SORT.SortByTitleDesc);
    list.changeSort();
    await stabilize();
    const sorted = search();
    expect(sorted.request.body.mniswDisciplineCodes).toEqual(['2021']);
    await finishResized(sorted);
    list.goToPage(2);
    await stabilize();
    const paged = search();
    expect(paged.request.body.mniswDisciplineCodes).toEqual(['2021']);
    expect(paged.request.body.page).toBe(2);
    expect(paged.request.body.size).toBe(50);
    await finishResized(paged);
    await details();
    await browserBack();
    const restored = harness.routeDebugElement!.componentInstance as JournalList;
    const returned = search();
    expect(returned.request.body.mniswDisciplineCodes).toEqual(['2021']);
    expect(returned.request.body.page).toBe(2);
    expect(returned.request.body.size).toBe(50);
    expect(returned.request.body.first).toBe(100);
    expect(returned.request.body.sortBy).toBe(SORT.SortByTitleDesc);
    await finishResized(returned);
    expect(restored.filterForm.controls.mniswDisciplineCodes.value).toEqual(['2061']);
    expect(restored.hasUnappliedChanges()).toBe(true);
    expect(
      restored.appliedFilterChips().some((chip) => chip.id === 'mniswDisciplineCodes:2021'),
    ).toBe(true);
  });

  it('clears a live P1 and cancels old-context HTTP after institution change', async () => {
    const list = await enter();
    const oldContext = search();
    list.filterForm.controls.journalTitle.setValue('Old context draft');
    profile.next({ userId: 1, institutionId: 20, institutionRole: 'INSTITUTION_USER' });
    await stabilize();
    expect(oldContext.cancelled).toBe(true);
    expect(search().request.body).toMatchObject({
      countryCodes: ['pl'],
      page: 0,
      sortBy: SORT.SortByTitleAsc,
    });
    expect(list.filterForm.controls.journalTitle.value).toBe('');
    expect(list.showDefaultNotice()).toBe(true);
  });

  it('cancels an in-flight request on P2 entry and restores the applied request without applying newer draft edits', async () => {
    const list = await enter();
    await finish();
    list.filterForm.controls.journalTitle.setValue('New applied');
    list.applyFilters();
    await stabilize();
    const pending = search();
    list.filterForm.controls.journalTitle.setValue('Later draft');
    list.rememberForDetails(new MouseEvent('click', { button: 0 }), 28032);
    await router.navigateByUrl(LIST + '/28032', { state: list.detailsNavigationState });
    await stabilize();
    expect(pending.cancelled).toBe(true);
    http
      .expectOne((request) => request.url.endsWith('/api/journal/journal/28032'))
      .flush({ id: 28032 });
    await stabilize();
    await browserBack();
    const restored = harness.routeDebugElement!.componentInstance as JournalList;
    const returned = search();
    expect(returned.request.body.journalTitle).toBe('New applied');
    await finish(returned);
    expect(restored.filterForm.controls.journalTitle.value).toBe('Later draft');
  });

  it.each(['history', 'link'] as const)(
    'restores page 2 / size 50, year and draft through decision/full data using %s',
    async (mode) => {
      decisionEnabled = true;
      const list = await enter();
      await finish();
      list.filterForm.patchValue({
        nonFeeFilters: { reviewModels: ['DOUBLE_BLIND_REVIEW'] },
        reviewDaysFrom: '0',
        reviewDaysTo: '10',
        initialAssessmentDaysFrom: '2',
        initialAssessmentDaysTo: '5',
        publicationDaysFrom: '30',
        publicationDaysTo: '60',
      });
      expect(list.applyFilters()).toBe(true);
      list.changePageSizeValue(50);
      await stabilize();
      const applied = search();
      expect(applied.request.body.reviewModels).toEqual(['DOUBLE_BLIND_REVIEW']);
      applied.flush({ ...PAGE, size: 50, totalElements: 137, totalPages: 3 });
      await stabilize();
      list.filterForm.patchValue({
        nonFeeFilters: { reviewModels: ['OPEN_PEER_REVIEW'] },
        reviewDaysFrom: '7',
        journalTitle: 'Unsaved draft',
      });
      list.goToPage(2);
      await stabilize();
      const paged = search();
      expect(paged.request.body.size).toBe(50);
      expect(paged.request.body.page).toBe(2);
      paged.flush({ ...PAGE, size: 50, totalElements: 137, totalPages: 3 });
      await stabilize();
      await clickLink('[data-testid="journal-result-title"]');
      expect(router.url).toBe(LIST + '/28032/decision');
      const decision = http.expectOne((value) => value.url.endsWith('/28032/decision'));
      expect(decision.request.params.get('surveyYear')).toBe('2024');
      decision.flush({
        surveyYear: 2024,
        annualStatus: 'AVAILABLE',
        availableSurveyYears: [2025, 2024],
        identity: { id: 28032, titleOriginal: 'Journal' },
      });
      await stabilize();
      await clickLink('[data-testid="decision-full-data"]');
      const full = http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032'));
      expect(full.request.params.get('surveyYear')).toBe('2024');
      full.flush({
        id: 28032,
        titleOriginal: 'Journal',
        surveyYear: 2024,
        questionnaireAvailable: false,
        availableSurveyYears: [2025, 2024],
      });
      await stabilize();
      (harness.routeDebugElement!.componentInstance as JournalDetails).selectSurveyYear(2025);
      http
        .expectOne((value) => value.url.endsWith('/api/journal/journal/28032'))
        .flush({
          id: 28032,
          titleOriginal: 'Journal',
          surveyYear: 2025,
          questionnaireAvailable: false,
          availableSurveyYears: [2025, 2024],
        });
      await stabilize();
      if (mode === 'history') {
        await browserBack();
        const back = http.expectOne((value) => value.url.endsWith('/28032/decision'));
        expect(back.request.params.get('surveyYear')).toBe('2024');
        back.flush({
          surveyYear: 2024,
          annualStatus: 'AVAILABLE',
          availableSurveyYears: [2024],
          identity: { id: 28032 },
        });
        await stabilize();
        await browserBack();
      } else await clickLink('a[href="/institution/user/journals"]');
      const returned = search();
      expect(returned.request.body).toMatchObject({
        page: 2,
        size: 50,
        first: 100,
        surveyYear: 2024,
        reviewDaysFrom: 0,
        reviewModels: ['DOUBLE_BLIND_REVIEW'],
        initialAssessmentDaysFrom: 2,
        publicationDaysFrom: 30,
      });
      returned.flush({ ...PAGE, size: 50, totalElements: 137, totalPages: 3 });
      await stabilize();
      const restored = harness.routeDebugElement!.componentInstance as JournalList;
      expect(restored.filterForm.controls.nonFeeFilters.controls.reviewModels.value).toEqual([
        'OPEN_PEER_REVIEW',
      ]);
      expect(restored.filterForm.controls.reviewDaysFrom.value).toBe('7');
      expect(restored.hasUnappliedChanges()).toBe(true);
    },
  );

  it.each(['BASIC', 'ADVANCED', 'BOTH', 'DISABLED', 'ERROR'] as const)(
    'shows the decision button in full details only for an available feature: %s',
    async (mode) => {
      const effective = mode === 'DISABLED' || mode === 'ERROR' ? 'BOTH' : mode;
      const context: OperatingContextDto = {
        institutionId: 10, configuredProfile: effective, defaultProfile: 'BOTH', effectiveProfile: effective,
        defaultSearchView: effective === 'BASIC' ? 'BASIC' : 'ADVANCED',
        availableFeatures: mode === 'DISABLED' ? ['BASIC_SEARCH', 'ADVANCED_SEARCH'] : ['BASIC_SEARCH', 'ADVANCED_SEARCH', 'DECISION_CARD'],
      };
      vi.spyOn(TestBed.inject(OperatingProfileService), 'currentContext').mockReturnValue(
        mode === 'ERROR' ? throwError(() => new Error('unavailable')) : of(context),
      );
      await enter(); await finish(); await details();
      const link = harness.routeNativeElement!.querySelector('[data-testid="journal-details-decision"]');
      expect(!!link).toBe(mode === 'ADVANCED' || mode === 'BOTH');
    },
  );

  it('opens decision from full details with its local year and preserves the original list snapshot', async () => {
    const list = await enter(); await finish();
    list.filterForm.controls.journalTitle.setValue('Unsaved draft');
    await clickLink('[data-testid="journal-result-title"]');
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2024, questionnaireAvailable: false, availableSurveyYears: [2025, 2024],
    });
    await stabilize();
    (harness.routeDebugElement!.componentInstance as JournalDetails).selectSurveyYear(2025);
    http.expectOne((value) => value.url.endsWith('/api/journal/journal/28032')).flush({
      id: 28032, surveyYear: 2025, questionnaireAvailable: false, availableSurveyYears: [2025, 2024],
    });
    await stabilize();
    const actions = harness.routeNativeElement!.querySelectorAll('header nav a');
    expect([...actions].map((value) => value.getAttribute('data-testid'))).toEqual(['journal-details-return', 'journal-details-decision']);
    await clickLink('[data-testid="journal-details-decision"]');
    const decision = http.expectOne((value) => value.url.endsWith('/28032/decision'));
    expect(decision.request.params.get('surveyYear')).toBe('2025');
    decision.flush({ surveyYear: 2025, annualStatus: 'MISSING_SURVEY', availableSurveyYears: [2024, 2023], identity: { id: 28032 } });
    await stabilize();
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[data-testid="decision-context"] [data-survey-year="2023"]')!.click();
    await stabilize();
    const changedYear = http.expectOne((value) => value.url.endsWith('/28032/decision'));
    expect(changedYear.request.params.get('surveyYear')).toBe('2023');
    changedYear.flush({ surveyYear: 2023, annualStatus: 'MISSING_SURVEY', availableSurveyYears: [2024, 2025], identity: { id: 28032 } });
    await stabilize();
    await clickLink('[data-testid="decision-return"]');
    const restored = search();
    expect(restored.request.body.surveyYear).toBe(2024);
    expect(restored.request.body.journalTitle).toBeUndefined();
    await finish(restored);
    expect((harness.routeDebugElement!.componentInstance as JournalList).filterForm.controls.journalTitle.value).toBe('Unsaved draft');
  });
});
