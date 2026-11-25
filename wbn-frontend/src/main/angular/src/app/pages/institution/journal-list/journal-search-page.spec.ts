import { Location, ViewportScroller } from '@angular/common';
import { provideLocationMocks } from '@angular/common/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import {
  NavigationEnd,
  NavigationSkipped,
  Router,
  RouterLink,
  provideRouter,
} from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { BehaviorSubject, Subject, filter, firstValueFrom, of, throwError } from 'rxjs';
import {
  ConfigurationService,
  DictionaryService,
  OperatingContextDto,
  OperatingProfile,
  QuestionnaireReportsService,
  RatingYearsDto,
  SearchView,
  UserProfileResponse,
} from '../../../core/backend';
import { AuthService } from '../../../core/services/auth.service';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';
import { OperatingProfileService } from '../../../core/services/operating-profile.service';
import { routes as institutionRoutes } from '../institution-routes';
import { JournalList } from './journal-list';
import { JournalSearchPage } from './journal-search-page';

const LIST = '/institution/user/journals';
const PAGE = { data: [{ id: 123, title: 'Journal' }], totalElements: 27, totalPages: 3, size: 10 };

@Component({ imports: [RouterLink], template: `<a [routerLink]="list" [state]="state">Return</a>` })
class DetailsPage {
  readonly list = LIST;
  readonly state = inject(JournalListQueryStateService).returnNavigationState();
}

class Loader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('Operating profiles and basic/advanced search', () => {
  let harness: RouterTestingHarness;
  let http: HttpTestingController;
  let router: Router;
  let profile: BehaviorSubject<UserProfileResponse | null>;
  let currentContext: ReturnType<typeof vi.fn>;
  let ratingYears: RatingYearsDto;
  let dictionaries: {
    findRepositories: ReturnType<typeof vi.fn>;
    findMniswDisciplines: ReturnType<typeof vi.fn>;
  };

  function context(mode: OperatingProfile = 'BOTH', institutionId = 10): OperatingContextDto {
    return {
      institutionId,
      configuredProfile: mode,
      defaultProfile: 'BOTH',
      effectiveProfile: mode,
      availableFeatures:
        mode === 'BOTH'
          ? ['BASIC_SEARCH', 'ADVANCED_SEARCH']
          : mode === 'BASIC'
            ? ['BASIC_SEARCH']
            : ['ADVANCED_SEARCH'],
      defaultSearchView: mode === 'BASIC' ? 'BASIC' : 'ADVANCED',
    };
  }

  beforeEach(async () => {
    profile = new BehaviorSubject<UserProfileResponse | null>({
      userId: 1,
      institutionId: 10,
      institutionRole: 'INSTITUTION_USER',
    });
    currentContext = vi.fn(() => of(context()));
    ratingYears = {};
    dictionaries = {
      findRepositories: vi.fn(() => of([])),
      findMniswDisciplines: vi.fn(() => of([])),
    };
    const userRoute = institutionRoutes.find((route) => route.path === 'user')!;
    await TestBed.configureTestingModule({
      imports: [JournalSearchPage, DetailsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideLocationMocks(),
        provideRouter([
          {
            path: 'institution',
            children: [
              {
                ...userRoute,
                children: userRoute.children!.map((route) =>
                  route.path === 'journals/:id' ? { ...route, component: DetailsPage } : route,
                ),
              },
            ],
          },
        ]),
        provideTransloco({
          config: { availableLangs: ['pl', 'en'], defaultLang: 'pl', prodMode: true },
          loader: Loader,
        }),
        { provide: AuthService, useValue: { getUserProfile: () => profile } },
        { provide: OperatingProfileService, useValue: { currentContext } },
        {
          provide: ViewportScroller,
          useValue: { getScrollPosition: () => [0, 720], scrollToPosition: vi.fn() },
        },
        { provide: ConfigurationService, useValue: { ratingYears: () => of(ratingYears),
          getJournalLinks: () => of({ issuesUrlTemplate: 'https://journals.example.test/issues/{journalId}' }),
          getSearchYears: () => of({ defaultYear: 2024, availableYears: [2024] }) } },
        {
          provide: DictionaryService,
          useValue: {
            ...dictionaries,
            findCountries: () =>
              of([
                { code: 'pl', namePl: 'Polska' },
                { code: 'de', namePl: 'Niemcy' },
              ]),
            findLanguages: () => of([{ code: 'en', namePl: 'Angielski' }]),
            findScientificCategories: () => of([{ id: 3, namePl: 'Matematyka' }]),
          },
        },
        {
          provide: QuestionnaireReportsService,
          useValue: { questionnaireReportAvailability: () => of({}) },
        },
        { provide: NavigationAuditService, useValue: { track: vi.fn(), captureView: () => ({}) } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    router.setUpLocationChangeListener();
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function settle() {
    harness.detectChanges();
    http.match((request) => request.url.endsWith('/api/journal/search-journals/counts'))
      .forEach((request) => request.flush({}));
    http
      .match((request) => request.url.endsWith('/metadata'))
      .forEach((request) => request.flush({ icvMin: 0, icvMax: 300, mniswPointClasses: [20, 40, 70, 100, 140, 200] }));
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function list(): JournalList {
    return harness.routeDebugElement!.query(By.directive(JournalList)).componentInstance;
  }

  function request() {
    return http.expectOne(
      (candidate) => candidate.method === 'POST' && candidate.url.endsWith('/search-journals'),
    );
  }

  async function finish() {
    request().flush(PAGE);
    await settle();
  }

  async function enter(url = LIST) {
    await harness.navigateByUrl(url, JournalSearchPage);
    await settle();
  }

  async function enterBasic() {
    await enter();
    await finish();
    await switchView('BASIC');
  }

  async function switchView(view: SearchView) {
    const address = router.url;
    harness
      .routeNativeElement!.querySelector<HTMLButtonElement>(`[data-testid="search-view-${view}"]`)!
      .click();
    await settle();
    expect(router.url).toBe(address);
    expect(TestBed.inject(Location).path(true)).toBe(address);
    expect(router.url).not.toContain('searchView=');
  }

  async function historyMove(direction: 'back' | 'forward') {
    const completed = firstValueFrom(
      router.events.pipe(
        filter((event) => event instanceof NavigationEnd || event instanceof NavigationSkipped),
      ),
    );
    TestBed.inject(Location)[direction]();
    await completed;
    await settle();
  }

  it.each(['BASIC', 'ADVANCED', 'BOTH'] as const)(
    'redirects the login dashboard to the canonical journal search for %s',
    async (mode) => {
      currentContext.mockReturnValue(of(context(mode)));
      await enter('/institution/user/dashboard');
      expect(router.url).toBe(LIST);
      expect(currentContext).toHaveBeenCalledTimes(1);
      expect(list().searchView).toBe(mode === 'BASIC' ? 'BASIC' : 'ADVANCED');
      expect(harness.routeNativeElement!.querySelectorAll('nav button')).toHaveLength(
        mode === 'BOTH' ? 2 : 0,
      );
      expect(harness.routeNativeElement!.querySelectorAll('app-journal-list')).toHaveLength(1);
      const searched = request();
      expect(searched.request.body.searchView).toBe(mode === 'BASIC' ? 'BASIC' : 'ADVANCED');
      searched.flush(PAGE);
      await settle();
    },
  );

  it.each(['BASIC', 'ADVANCED'] as const)(
    'displays the configured default year even when the first option is newer, starting in %s',
    async (initialView) => {
      currentContext.mockReturnValue(of({ ...context(), defaultSearchView: initialView }));
      vi.spyOn(TestBed.inject(ConfigurationService), 'getSearchYears').mockReturnValue(
        of({ defaultYear: 2024, availableYears: [2025, 2024] }),
      );
      await enter();
      const initial = request();
      expect(initial.request.body.surveyYear).toBe(2024);
      initial.flush(PAGE);
      await settle();
      expect(list().selectedSurveyYear()).toBe(2024);
      expect(harness.routeNativeElement!.querySelector<HTMLSelectElement>('#surveyYear')!.value).toBe('2024');

      await switchView(initialView === 'BASIC' ? 'ADVANCED' : 'BASIC');
      await finish();
      const selector = harness.routeNativeElement!.querySelector<HTMLSelectElement>('#surveyYear')!;
      expect(selector.value).toBe('2024');
      selector.value = '2025';
      selector.dispatchEvent(new Event('change'));
      await settle();
      const changed = request();
      expect(changed.request.body.surveyYear).toBe(2025);
      changed.flush(PAGE);
      await settle();
      expect(selector.value).toBe('2025');

      list().selectSurveyYear('2024');
      await settle();
      const historical = request();
      expect(historical.request.body.surveyYear).toBe(2024);
      historical.flush(PAGE);
      await settle();
      expect(selector.value).toBe('2024');
    },
  );

  it('removes the legacy view parameter at dashboard entry, keeping other parameters and the fragment', async () => {
    await enter('/institution/user/dashboard?searchView=BASIC&source=menu#results');
    expect(router.url).toBe(LIST + '?source=menu#results');
    expect(list().searchView).toBe('ADVANCED');
    await finish();
    await switchView('BASIC');
    expect(router.url).toBe(LIST + '?source=menu#results');
    expect(list().searchView).toBe('BASIC');
    await finish();
  });

  it('does not bypass profile loading when entering from the login dashboard', async () => {
    currentContext.mockReturnValue(throwError(() => new Error('Context unavailable')));
    await enter('/institution/user/dashboard');
    expect(router.url).toBe(LIST);
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeTruthy();
    expect(harness.routeNativeElement!.querySelector('app-journal-list')).toBeNull();
    http.expectNone((candidate) => candidate.method === 'POST');
    currentContext.mockReturnValue(of(context()));
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await settle();
    expect(harness.routeNativeElement!.querySelectorAll('nav button')).toHaveLength(2);
    await finish();
  });

  it.each(['BASIC', 'ADVANCED'] as const)(
    'only renders the allowed %s view even with a forbidden URL parameter',
    async (mode) => {
      currentContext.mockReturnValue(of(context(mode)));
      await enter(LIST + '?searchView=' + (mode === 'BASIC' ? 'ADVANCED' : 'BASIC'));
      expect(router.url).toBe(LIST);
      expect(list().searchView).toBe(mode);
      expect(harness.routeNativeElement!.querySelector('nav')).toBeNull();
      const search = request();
      expect(search.request.body.searchView).toBe(mode);
      expect(search.request.body.countryCodes).toEqual(mode === 'BASIC' ? [] : ['pl']);
      expect(!!harness.routeNativeElement!.querySelector('aside')).toBe(mode === 'ADVANCED');
      if (mode === 'BASIC') {
        expect(dictionaries.findRepositories).not.toHaveBeenCalled();
        expect(dictionaries.findMniswDisciplines).toHaveBeenCalledTimes(1);
        expect(harness.routeNativeElement!.querySelectorAll('input[role="switch"]')).toHaveLength(
          3,
        );
        expect(harness.routeNativeElement!.querySelector('[data-testid="journal-countries"] .multi-value-toggle')).toBeTruthy();
      }
      search.flush(PAGE);
      await settle();
    },
  );

  it.each(['BASIC', 'ADVANCED'] as const)(
    'uses a white Bootstrap button only for the unselected search view, initially %s',
    async (initialView) => {
      currentContext.mockReturnValue(of({ ...context(), defaultSearchView: initialView }));
      await enter();
      await finish();
      const root = harness.routeNativeElement!;
      function expectViewStyles(selected: SearchView) {
        for (const view of ['BASIC', 'ADVANCED'] as const) {
          const button = root.querySelector<HTMLButtonElement>(
            `[data-testid="search-view-${view}"]`,
          )!;
          expect(button.classList.contains('active')).toBe(view === selected);
          for (const className of ['btn', 'btn-light', 'bg-white', 'text-dark']) {
            expect(button.classList.contains(className)).toBe(view !== selected);
          }
          expect(button.getAttribute('aria-current')).toBe(view === selected ? 'page' : null);
          expect(button.disabled).toBe(false);
        }
      }
      expectViewStyles(initialView);
      const otherView = initialView === 'BASIC' ? 'ADVANCED' : 'BASIC';
      await switchView(otherView);
      await finish();
      expectViewStyles(otherView);
      await switchView(initialView);
      await finish();
      expectViewStyles(initialView);
    },
  );

  it('starts BOTH in advanced and preserves independent applied/draft/page state during switches', async () => {
    await enter();
    expect(list().searchView).toBe('ADVANCED');
    await finish();
    list().filterForm.patchValue({ journalTitle: 'Advanced applied', mniswOnly: true });
    list().applyFilters();
    await settle();
    await finish();
    list().goToPage(2);
    await settle();
    await finish();
    list().filterForm.patchValue({ journalTitle: 'Advanced draft', reviewDaysTo: 'invalid' });
    await switchView('BASIC');
    const basic = request();
    expect(basic.request.body).toMatchObject({ searchView: 'BASIC', countryCodes: [], page: 0 });
    basic.flush(PAGE);
    await settle();
    list().filterForm.patchValue({ journalTitle: 'Basic applied' });
    list().applyFilters();
    await settle();
    await finish();
    list().filterForm.patchValue({ journalTitle: 'Basic draft' });
    await switchView('ADVANCED');
    const advanced = request();
    expect(advanced.request.body).toMatchObject({
      journalTitle: 'Advanced applied',
      page: 2,
      mniswOnly: true,
    });
    expect(list().filterForm.controls.journalTitle.value).toBe('Advanced draft');
    expect(list().filterForm.controls.reviewDaysTo.value).toBe('invalid');
    advanced.flush(PAGE);
    await settle();
    await switchView('BASIC');
    const restored = request();
    expect(restored.request.body.journalTitle).toBe('Basic applied');
    expect(list().filterForm.controls.journalTitle.value).toBe('Basic draft');
    restored.flush(PAGE);
    await settle();
  });

  it('passes both configured ICV years into advanced result cards when a previous rating is absent', async () => {
    ratingYears = { currentIcvYear: 2024, previousIcvYear: 2023 };
    await enter();
    request().flush({
      ...PAGE,
      data: [
        {
          id: 123,
          title: 'Journal',
          currentStatus: 'INDEXED',
          currentIcvPoints: 45.67,
          previousStatus: 'NOT_EVALUATED',
        },
      ],
    });
    await settle();
    const root = harness.routeNativeElement!;
    const current = root.querySelector('[data-testid="journal-result-icv-current"]')!;
    const previous = root.querySelector('[data-testid="journal-result-icv-previous"]')!;
    expect(current.textContent).toContain('ICV 2024:');
    expect(current.textContent).toContain('45,67');
    expect(previous.textContent).toContain('ICV 2023:');
    expect(previous.textContent).toContain('N/E');
    expect(root.querySelectorAll('.journal-result-ratings .card')).toHaveLength(3);
  });

  it.each([false, true])(
    'keeps indexed journals before the slider and the remaining ICV statuses in compact=%s',
    async (compact) => {
      await enter();
      await finish();
      list().compactFilters.set(compact);
      await settle();
      if (compact) {
        list().openFilterPanel();
        await settle();
      }
      const section = harness.routeNativeElement!.querySelector('[data-testid="journal-header-icv"]')!;
      expect(section.closest('.journal-search-header')).not.toBeNull();
      if (compact) {
        expect(harness.routeNativeElement!.querySelector('[role="dialog"] #icvFrom')).toBeNull();
        list().cancelFilterPanel();
        await settle();
      }
      const order = () =>
        [...section.querySelectorAll<HTMLElement>('input[role="switch"], fieldset.icv-range')].map(
          (element) => (element.matches('fieldset') ? 'icv-range' : element.id),
        );
      const expected = [
        'indexedJournals',
        'icv-range',
        'notIndexedJournals',
        'notEvaluatedJournals',
      ];
      expect(order()).toEqual(expected);
      section.querySelector<HTMLInputElement>('#indexedJournals')!.click();
      await settle();
      expect(section.querySelector('.noUi-target')).toBeTruthy();
      expect(section.querySelector('.noUi-target')!.hasAttribute('disabled')).toBe(false);
      expect(order()).toEqual(expected);
      section.querySelector<HTMLInputElement>('#notIndexedJournals')!.click();
      await settle();
      expect(section.querySelector('.noUi-target')!.hasAttribute('disabled')).toBe(true);
      expect(list().filterForm.controls.ratingStatus.value).toBe('NOT_INDEXED');
      expect(order()).toEqual(expected);
      http.expectNone((candidate) => candidate.method === 'POST');
    },
  );

  it.each(['success', 'empty', 'error'] as const)(
    'preserves every expanded and collapsed filter group while applying a draft with %s results',
    async (result) => {
      await enter();
      await finish();
      const original = list();
      const root = harness.routeNativeElement!;
      const groups = () => [
        ...root.querySelectorAll<HTMLDetailsElement>('.journal-filter-options > details'),
      ];
      const expected = [false, true, true, false, false, true];
      expect(groups()).toHaveLength(expected.length);
      groups().forEach((group, index) => {
        group.open = expected[index];
      });
      const options = root.querySelector<HTMLElement>('.journal-filter-options')!;
      expect(getComputedStyle(options).overflowY).toBe('visible');
      original.filterForm.controls.publisher.setValue('Applied publisher');
      await settle();
      root.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.click();
      await settle();
      const searched = request();
      expect(list()).toBe(original);
      expect(groups().map((group) => group.open)).toEqual(expected);
      expect(options.scrollTop).toBe(0);
      expect(searched.request.body.publisher).toBe('Applied publisher');
      if (result === 'error') searched.flush({}, { status: 500, statusText: 'Search failed' });
      else
        searched.flush(
          result === 'empty' ? { data: [], totalElements: 0, totalPages: 0, size: 10 } : PAGE,
        );
      await settle();
      expect(list()).toBe(original);
      expect(groups().map((group) => group.open)).toEqual(expected);
      expect(options.scrollTop).toBe(0);
      if (result === 'error') {
        root.querySelector<HTMLButtonElement>('[data-testid="journal-search-retry"]')!.click();
        await settle();
        await finish();
        expect(groups().map((group) => group.open)).toEqual(expected);
      expect(options.scrollTop).toBe(0);
      }
    },
  );

  it('keeps all filter groups collapsed while paging, sorting and clearing applied filters', async () => {
    await enter();
    await finish();
    const original = list();
    original.filterForm.controls.publisher.setValue('Applied publisher');
    original.applyFilters();
    await settle();
    await finish();
    const root = harness.routeNativeElement!;
    const options = root.querySelector<HTMLElement>('.journal-filter-options')!;
    const groups = [...options.querySelectorAll<HTMLDetailsElement>('details')];
    groups.forEach((group) => {
      group.open = false;
    });
    expect(getComputedStyle(options).overflowY).toBe('visible');
    const actions = [
      () => original.goToPage(1),
      () => {
        original.filterForm.controls.sortBy.setValue('SORT_BY_TITLE_DESC');
        original.changeSort();
      },
      () => original.resetFilters(),
    ];
    for (const action of actions) {
      action();
      await settle();
      const searched = request();
      expect(groups.every((group) => !group.open)).toBe(true);
      searched.flush(PAGE);
      await settle();
      expect(root.querySelector('.journal-filter-options')).toBe(options);
      expect(groups.every((group) => !group.open)).toBe(true);
      expect(options.scrollTop).toBe(0);
      expect(list()).toBe(original);
    }
  });

  it('restores the complete full-width basic card and keeps the advanced layout separate', async () => {
    vi.spyOn(TestBed.inject(ConfigurationService), 'getSearchYears').mockReturnValue(
      of({ defaultYear: 2025, availableYears: [2025, 2024] }),
    );
    await enterBasic();
    await finish();
    const root = harness.routeNativeElement!;
    const layout = root.querySelector('[data-testid="journal-basic-layout"]')!;
    const card = layout.querySelector(':scope > .col-xl-12 > .card')!;
    expect(card).toBeTruthy();
    expect(card.querySelector('.card-header h1')).toBeTruthy();
    expect(card.querySelector('.card-body.bg-light #journal-search-form')).toBeTruthy();
    const fields = card.querySelector('[data-testid="journal-basic-form"] [app-journal-search-fields]')!;
    expect(fields.firstElementChild!.querySelector('#surveyYear')).toBeTruthy();
    expect(fields.children[1].querySelector('#journalTitle')).toBeTruthy();
    expect(root.querySelectorAll('#surveyYear')).toHaveLength(1);
    expect(card.querySelector('#journalTitle')!.closest('.col-xxl-2')).toBeTruthy();
    expect(card.querySelector('#issn')!.closest('.col-xxl-2')).toBeTruthy();
    expect(card.querySelector('#publisher')!.closest('.col-xxl-2')).toBeTruthy();
    expect(card.querySelector('.search-box .ri-search-line')).toBeTruthy();
    expect(card.querySelector('#journalSort')).toBeTruthy();
    expect(card.querySelector('[data-main-group="mnisw"]')).toBeTruthy();
    expect(card.querySelectorAll('#journal-search-form')).toHaveLength(1);
    expect(card.querySelector('app-journal-basic-result')).toBeTruthy();
    expect(card.querySelectorAll('[data-testid="journal-basic-icv"]')).toHaveLength(2);
    expect(card.querySelector('[data-testid="journal-basic-deposit"]')).toBeTruthy();
    expect(card.querySelector('app-list-paging')).toBeTruthy();
    expect(card.querySelectorAll('app-list-paging select option')).toHaveLength(1);
    for (const selector of [
      'aside',
      '.journal-search-layout',
      'app-journal-result-card',
      'app-journal-pagination',
      'app-journal-applied-filters',
    ]) {
      expect(root.querySelector(selector)).toBeNull();
    }
    const yearSelect = card.querySelector<HTMLSelectElement>('#surveyYear')!;
    yearSelect.value = '2024';
    yearSelect.dispatchEvent(new Event('change'));
    await settle();
    const changedYear = request();
    expect(changedYear.request.body).toMatchObject({ searchView: 'BASIC', surveyYear: 2024 });
    changedYear.flush(PAGE);
    await settle();
    await switchView('ADVANCED');
    await finish();
    expect(root.querySelector('[data-testid="journal-basic-layout"]')).toBeNull();
    expect(root.querySelector('.journal-search-layout aside')).toBeTruthy();
    expect(root.querySelector('app-journal-result-card')).toBeTruthy();
    expect(root.querySelector('app-journal-pagination')).toBeTruthy();
    expect(root.querySelector('app-list-paging')).toBeNull();
  });

  it.each(['BASIC', 'ADVANCED'] as const)('uses the same searchable JML dropdown without applying draft selections in %s', async (view) => {
    if (view === 'BASIC') await enterBasic();
    else await enter();
    await finish();
    const root = harness.routeNativeElement!;
    const selector = root.querySelector<HTMLElement>('app-multi-value-select[data-testid$="jml-disciplines"]')!;
    const toggle = selector.querySelector<HTMLButtonElement>('.multi-value-toggle')!;
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    await settle();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    const search = selector.querySelector<HTMLInputElement>('input[type="search"]')!;
    search.value = 'matem';
    search.dispatchEvent(new Event('input'));
    await settle();
    const option = selector.querySelector<HTMLInputElement>('.multi-value-options input')!;
    expect(option.parentElement!.textContent).toContain('Matematyka');
    option.click();
    await settle();
    expect(list().filterForm.controls.disciplineCodes.value).toEqual(['3']);
    expect(toggle.textContent).toContain('(1)');
    search.value = 'missing';
    search.dispatchEvent(new Event('input'));
    await settle();
    expect(list().filterForm.controls.disciplineCodes.value).toEqual(['3']);
    expect(selector.querySelectorAll('.multi-value-options input')).toHaveLength(0);
    const enterKey = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
    search.dispatchEvent(enterKey);
    expect(enterKey.defaultPrevented).toBe(true);
    selector.querySelector<HTMLButtonElement>('.multi-value-panel button')!.click();
    await settle();
    expect(list().filterForm.controls.disciplineCodes.value).toEqual([]);
    search.value = 'missing';
    search.dispatchEvent(new Event('input'));
    await settle();
    expect(selector.querySelectorAll('.multi-value-options input')).toHaveLength(0);
    search.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await settle();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    await settle();
    root.querySelector<HTMLElement>('#journal-basic-heading, #journal-search-heading')!.click();
    await settle();
    expect(selector.querySelector('.multi-value-panel')).toBeNull();
    http.expectNone((candidate) => candidate.method === 'POST');
  });

  it('uses applied filters with the original basic pager, disables it while loading and preserves drafts', async () => {
    await enterBasic();
    await finish();
    list().changePageSizeValue(25);
    expect(list().query().size).toBe(10);
    list().filterForm.controls.journalTitle.setValue('Applied title');
    list().applyFilters();
    await settle();
    await finish();
    list().filterForm.controls.journalTitle.setValue('Unapplied draft');
    const pager = () => harness.routeNativeElement!.querySelector('app-list-paging')!;
    expect(pager().querySelectorAll('button')[0].disabled).toBe(true);
    pager().querySelectorAll('button')[1].click();
    await settle();
    const next = request();
    expect(next.request.body).toMatchObject({
      searchView: 'BASIC',
      journalTitle: 'Applied title',
      page: 1,
      size: 10,
    });
    expect(harness.routeNativeElement!.querySelector('app-list-paging')).toBeNull();
    next.flush(PAGE);
    await settle();
    expect(list().filterForm.controls.journalTitle.value).toBe('Unapplied draft');
    pager().querySelectorAll('button')[0].click();
    await settle();
    const previous = request();
    expect(previous.request.body).toMatchObject({
      journalTitle: 'Applied title',
      page: 0,
      size: 10,
    });
    previous.flush(PAGE);
    await settle();
  });

  it('keeps basic sorting beside the results without applying other draft fields', async () => {
    await enterBasic();
    await finish();
    list().filterForm.controls.journalTitle.setValue('Unapplied draft');
    const sort = harness.routeNativeElement!.querySelector<HTMLSelectElement>('#journalSort')!;
    sort.value = 'SORT_BY_TITLE_DESC';
    sort.dispatchEvent(new Event('change'));
    await settle();
    const sorted = request();
    expect(sorted.request.body).toMatchObject({
      searchView: 'BASIC',
      sortBy: 'SORT_BY_TITLE_DESC',
      page: 0,
    });
    expect(sorted.request.body.journalTitle).toBeUndefined();
    expect(list().filterForm.controls.journalTitle.value).toBe('Unapplied draft');
    sorted.flush(PAGE);
    await settle();
  });

  it('sends shared main fields from basic, omits advanced sidebar fields and resets only the active view', async () => {
    await enterBasic();
    await finish();
    list().filterForm.patchValue({
      journalTitle: 'Basic',
      mniswOnly: true,
      reviewDaysFrom: '0',
      reviewDaysTo: '5',
      initialAssessmentDaysFrom: '2',
      publicationDaysFrom: '30',
      feeFilters: { publicationFeeMaxPln: '100' },
      nonFeeFilters: { doiOnly: true },
    });
    list().applyFilters();
    await settle();
    const basic = request();
    expect(basic.request.body.searchView).toBe('BASIC');
    expect(basic.request.body.mniswOnly).toBe(true);
    expect(basic.request.body.publicationFeeMaxPln).toBeUndefined();
    expect(basic.request.body.doiOnly).toBeUndefined();
    expect(basic.request.body.reviewDaysTo).toBeUndefined();
    expect(basic.request.body.reviewDaysFrom).toBeUndefined();
    expect(basic.request.body.initialAssessmentDaysFrom).toBeUndefined();
    expect(basic.request.body.publicationDaysFrom).toBeUndefined();
    basic.flush(PAGE);
    await settle();
    list().filterForm.patchValue({ journalTitle: 'Basic draft' });
    await switchView('ADVANCED');
    await finish();
    list().resetFilters();
    await settle();
    await finish();
    await switchView('BASIC');
    const restored = request();
    expect(restored.request.body.journalTitle).toBe('Basic');
    expect(list().filterForm.controls.journalTitle.value).toBe('Basic draft');
    restored.flush(PAGE);
    await settle();
  });

  it('fails closed while context is loading or unavailable and retries without a fallback profile', async () => {
    const delayed = new Subject<OperatingContextDto>();
    currentContext.mockReturnValue(delayed);
    await enter();
    expect(harness.routeDebugElement!.query(By.directive(JournalList))).toBeNull();
    http.expectNone((candidate) => candidate.method === 'POST');
    delayed.error(new Error('offline'));
    await settle();
    expect(harness.routeNativeElement!.querySelector('[role="alert"]')).toBeTruthy();
    expect(harness.routeDebugElement!.query(By.directive(JournalList))).toBeNull();
    currentContext.mockReturnValue(of(context('BASIC')));
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    await settle();
    expect(list().searchView).toBe('BASIC');
    await finish();
  });

  it('uses native basic controls, enables the ICV slider only for Indexed and keeps validation local', async () => {
    currentContext.mockReturnValue(of(context('BASIC')));
    await enter();
    await finish();
    const root = harness.routeNativeElement!;
    const country = root.querySelector<HTMLElement>('[data-testid="journal-countries"]')!;
    country.querySelector<HTMLButtonElement>('.multi-value-toggle')!.click();
    await settle();
    country.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')[1].click();
    expect(list().filterForm.controls.countryCodes.value).toEqual(['de']);
    root.querySelector<HTMLInputElement>('#indexedJournals')!.click();
    await settle();
    expect(list().isIcvFilterEnabled()).toBe(true);
    expect(root.querySelector('.noUi-target')).toBeTruthy();
    list().filterForm.controls.icvFrom.setValue('invalid');
    expect(list().applyFilters()).toBe(false);
    await settle();
    expect(root.querySelector('#icvRangeError')).toBeTruthy();
    expect(root.querySelector('aside')).toBeNull();
    expect(root.querySelector('.offcanvas')).toBeNull();
    http.expectNone((candidate) => candidate.method === 'POST');
    list().filterForm.controls.icvFrom.setValue('80.25');
    list().applyFilters();
    await settle();
    const searched = request();
    expect(searched.request.body).toMatchObject({
      searchView: 'BASIC',
      countryCodes: ['de'],
      ratingStatus: 'INDEXED',
      icvFrom: 80.25,
    });
    searched.flush(PAGE);
    await settle();
  });

  it.each(['BASIC', 'ADVANCED'] as const)('shows the same MNiSW slider and tag-free discipline selector in %s', async view => {
    dictionaries.findMniswDisciplines.mockReturnValue(of([{ code: '2021', namePl: 'Architektura' }]));
    if (view === 'BASIC') await enterBasic();
    else await enter();
    await finish();
    const root = harness.routeNativeElement!;
    const mnisw = root.querySelector<HTMLElement>('[data-main-group="mnisw"]')!;
    const slider = mnisw.querySelector<HTMLElement>('.noUi-target')!;
    expect(slider).not.toBeNull();
    expect(slider.querySelectorAll('[role="slider"]')).toHaveLength(2);
    expect(slider.hasAttribute('disabled')).toBe(true);
    expect([...mnisw.querySelectorAll('.journal-range-scale span')].map(point => point.textContent!.trim()))
      .toEqual(['20', '40', '70', '100', '140', '200']);
    mnisw.querySelector<HTMLInputElement>('#mniswOnly')!.click();
    await settle();
    expect(slider.hasAttribute('disabled')).toBe(false);
    const selector = mnisw.querySelector<HTMLElement>('[data-testid="mnisw-disciplines"]')!;
    selector.querySelector<HTMLButtonElement>('.multi-value-toggle')!.click();
    await settle();
    selector.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    await settle();
    expect(list().filterForm.controls.mniswDisciplineCodes.value).toEqual(['2021']);
    expect(selector.querySelector('.multi-value-toggle')!.textContent).toContain('(1)');
    expect(selector.querySelector('ul')).toBeNull();
    slider.querySelector('.noUi-handle-lower')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await settle();
    expect(list().filterForm.controls.mniswPointsFrom.value).toBe('40');
    expect(list().query().mniswPointsFrom).toBeUndefined();
    http.expectNone(candidate => candidate.method === 'POST' && candidate.url.endsWith('/search-journals'));
  });

  it('submits all shared main fields from basic search and clears them through the shared buttons', async () => {
    currentContext.mockReturnValue(of(context('BASIC')));
    dictionaries.findMniswDisciplines.mockReturnValue(of([{ code: '2021', namePl: 'Architektura' }]));
    await enter();
    await finish();
    list().languageOptions.set([{ code: 'en', namePl: 'Angielski' }, { code: 'pl', namePl: 'Polski' }]);
    await settle();
    const root = harness.routeNativeElement!;
    for (const id of ['journal-countries', 'journal-languages', 'jml-disciplines']) {
      const widget = root.querySelector<HTMLElement>(`[data-testid="${id}"]`)!;
      widget.querySelector<HTMLButtonElement>('.multi-value-toggle')!.click();
      await settle();
      for (const checkbox of widget.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')) {
        checkbox.click();
        await settle();
      }
      expect(widget.querySelector('ul')).toBeNull();
    }
    root.querySelector<HTMLInputElement>('#mniswOnly')!.click();
    await settle();
    const mnisw = root.querySelector<HTMLElement>('[data-main-group="mnisw"]')!;
    mnisw.querySelector<HTMLButtonElement>('[data-testid="mnisw-disciplines"] .multi-value-toggle')!.click();
    await settle();
    mnisw.querySelector<HTMLInputElement>('input[type="checkbox"]:not(#mniswOnly)')!.click();
    await settle();
    expect(mnisw.querySelector('[data-testid="mnisw-disciplines"] ul')).toBeNull();
    mnisw.querySelector('.noUi-handle-lower')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    mnisw.querySelector('.noUi-handle-upper')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
    await settle();
    http.expectNone(candidate => candidate.method === 'POST' && candidate.url.endsWith('/search-journals'));
    root.querySelector<HTMLButtonElement>('[data-testid="journal-search-submit"]')!.click();
    await settle();
    const searched = request();
    expect(searched.request.body).toMatchObject({
      searchView: 'BASIC', countryCodes: ['pl', 'de'], languageCodes: ['en', 'pl'], disciplineCodes: ['3'],
      mniswOnly: true, mniswDisciplineCodes: ['2021'], mniswPointsFrom: 40, mniswPointsTo: 140,
    });
    searched.flush(PAGE);
    await settle();
    root.querySelector<HTMLButtonElement>('[data-testid="journal-search-clear"]')!.click();
    await settle();
    const cleared = request();
    expect(cleared.request.body).toMatchObject({ searchView: 'BASIC', countryCodes: [], languageCodes: [] });
    for (const key of ['disciplineCodes', 'mniswOnly', 'mniswDisciplineCodes', 'mniswPointsFrom', 'mniswPointsTo'])
      expect(cleared.request.body[key]).toBeUndefined();
    cleared.flush(PAGE);
    await settle();
    expect(root.querySelector('aside')).toBeNull();
    expect(root.querySelector('app-journal-basic-result')).toBeTruthy();
  });

  it('handles browser Back between search views and cancels the outgoing in-flight search', async () => {
    await enter();
    await finish();
    list().filterForm.controls.publisher.setValue('Advanced draft');
    await switchView('BASIC');
    const pending = request();
    await historyMove('back');
    expect(pending.cancelled).toBe(true);
    expect(list().searchView).toBe('ADVANCED');
    expect(list().filterForm.controls.publisher.value).toBe('Advanced draft');
    await finish();
  });

  it('restores both views through repeated Back/Forward without changing the URL or mixing drafts and pages', async () => {
    await enter();
    await finish();
    list().filterForm.controls.journalTitle.setValue('Advanced applied');
    list().applyFilters();
    await settle();
    await finish();
    list().goToPage(2);
    await settle();
    await finish();
    list().filterForm.controls.journalTitle.setValue('Advanced draft');
    await switchView('BASIC');
    await finish();
    list().filterForm.controls.publisher.setValue('Basic applied');
    list().applyFilters();
    await settle();
    await finish();
    list().filterForm.controls.publisher.setValue('Basic draft');
    for (const direction of ['back', 'forward', 'back', 'forward'] as const) {
      await historyMove(direction);
      const advanced = direction === 'back';
      expect(router.url).toBe(LIST);
      expect(TestBed.inject(Location).path(true)).toBe(LIST);
      expect(list().searchView).toBe(advanced ? 'ADVANCED' : 'BASIC');
      const searched = request();
      expect(searched.request.body).toMatchObject({
        searchView: advanced ? 'ADVANCED' : 'BASIC',
        page: advanced ? 2 : 0,
      });
      expect(searched.request.body.journalTitle).toBe(advanced ? 'Advanced applied' : undefined);
      expect(searched.request.body.publisher).toBe(advanced ? undefined : 'Basic applied');
      expect(list().filterForm.controls.journalTitle.value).toBe(advanced ? 'Advanced draft' : '');
      expect(list().filterForm.controls.publisher.value).toBe(advanced ? '' : 'Basic draft');
      searched.flush(PAGE);
      await settle();
    }
  });

  it('restores the right same-URL entry when returning from details with browser history', async () => {
    await enter();
    await finish();
    list().filterForm.controls.publisher.setValue('Advanced draft');
    await switchView('BASIC');
    await finish();
    list().filterForm.controls.journalTitle.setValue('Basic draft');
    const opened = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    harness
      .routeNativeElement!.querySelector<HTMLAnchorElement>('[data-testid="journal-result-title"]')!
      .click();
    await opened;
    await settle();
    await historyMove('back');
    expect(list().searchView).toBe('BASIC');
    expect(list().filterForm.controls.journalTitle.value).toBe('Basic draft');
    await finish();
    await historyMove('forward');
    expect(router.url).toBe(LIST + '/123');
    await historyMove('back');
    expect(list().searchView).toBe('BASIC');
    expect(list().filterForm.controls.journalTitle.value).toBe('Basic draft');
    await finish();
    await historyMove('back');
    expect(list().searchView).toBe('ADVANCED');
    expect(list().filterForm.controls.publisher.value).toBe('Advanced draft');
    await finish();
  });

  it.each([LIST, '/institution/user/dashboard'])(
    'starts with the profile default after a fresh menu entry at %s and ignores previous history contexts',
    async (url) => {
      await enter();
      await finish();
      list().filterForm.controls.journalTitle.setValue('Old advanced draft');
      await switchView('BASIC');
      await finish();
      list().filterForm.controls.journalTitle.setValue('Old basic draft');
      await harness.navigateByUrl(url, JournalSearchPage);
      await settle();
      expect(list().searchView).toBe('ADVANCED');
      expect(list().filterForm.controls.journalTitle.value).toBe('');
      expect(router.url).toBe(LIST);
      await finish();
      await historyMove('back');
      expect(list().searchView).toBe('ADVANCED');
      expect(list().filterForm.controls.journalTitle.value).toBe('');
      await finish();
    },
  );

  it('cleans a legacy view parameter on an already open list without activating that view', async () => {
    await enter();
    await finish();
    list().filterForm.controls.journalTitle.setValue('Old draft');
    await harness.navigateByUrl(
      LIST + '?searchView=BASIC&source=legacy#results',
      JournalSearchPage,
    );
    await settle();
    expect(router.url).toBe(LIST + '?source=legacy#results');
    expect(TestBed.inject(Location).path(true)).toBe(router.url);
    expect(list().searchView).toBe('ADVANCED');
    expect(list().filterForm.controls.journalTitle.value).toBe('');
    await finish();
  });

  it('treats a same-URL menu entry as fresh rather than an internal tab switch', async () => {
    await enter();
    await finish();
    list().filterForm.controls.publisher.setValue('Old advanced draft');
    await switchView('BASIC');
    await finish();
    list().filterForm.controls.journalTitle.setValue('Old basic draft');
    await harness.navigateByUrl(LIST, JournalSearchPage);
    await settle();
    expect(list().searchView).toBe('ADVANCED');
    expect(list().filterForm.controls.publisher.value).toBe('');
    await finish();
    await switchView('BASIC');
    expect(list().filterForm.controls.journalTitle.value).toBe('');
    await finish();
  });

  it('clears both view states on institution change and cannot restore the old component during teardown', async () => {
    await enter();
    await finish();
    list().filterForm.patchValue({ journalTitle: 'Old institution draft' });
    await switchView('BASIC');
    await finish();
    list().filterForm.patchValue({ journalTitle: 'Old basic draft' });
    currentContext.mockReturnValue(of(context('BOTH', 20)));
    profile.next({ userId: 1, institutionId: 20, institutionRole: 'INSTITUTION_USER' });
    await settle();
    expect(list().filterForm.controls.journalTitle.value).toBe('');
    await finish();
    await switchView('BASIC');
    expect(list().filterForm.controls.journalTitle.value).toBe('');
    await finish();
  });

  it('restores basic from details after an asynchronous context response and preserves the advanced sibling state', async () => {
    await enter();
    await finish();
    list().filterForm.patchValue({ publisher: 'Advanced draft' });
    await switchView('BASIC');
    await finish();
    list().filterForm.patchValue({ journalTitle: 'Basic applied' });
    list().applyFilters();
    await settle();
    await finish();
    list().filterForm.patchValue({ journalTitle: 'Basic draft' });
    const completed = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    harness
      .routeNativeElement!.querySelector<HTMLAnchorElement>('[data-testid="journal-result-title"]')!
      .click();
    await completed;
    await settle();
    const delayed = new Subject<OperatingContextDto>();
    currentContext.mockReturnValue(delayed);
    const returned = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a')!.click();
    await returned;
    await settle();
    delayed.next(context());
    delayed.complete();
    await settle();
    expect(list().searchView).toBe('BASIC');
    expect(list().filterForm.controls.journalTitle.value).toBe('Basic draft');
    const restored = request();
    expect(restored.request.body.journalTitle).toBe('Basic applied');
    restored.flush(PAGE);
    await settle();
    currentContext.mockReturnValue(of(context()));
    await switchView('ADVANCED');
    expect(list().filterForm.controls.publisher.value).toBe('Advanced draft');
    await finish();
  });

  it.each(['ADVANCED', 'BOTH'] as const)(
    'uses decision links for the effective %s profile, including BOTH on BASIC',
    async (mode) => {
      const enabled = context(mode);
      enabled.availableFeatures.push('DECISION_CARD');
      currentContext.mockReturnValue(of(enabled));
      await enter();
      await finish();
      for (const link of harness.routeNativeElement!.querySelectorAll<HTMLAnchorElement>(
        '[data-testid="journal-result-title"], [data-testid="journal-result-details"]',
      )) {
        expect(link.getAttribute('href')).toBe(LIST + '/123/decision');
      }
      if (mode === 'BOTH') {
        await switchView('BASIC');
        await finish();
        for (const link of harness.routeNativeElement!.querySelectorAll<HTMLAnchorElement>(
          '[data-testid="journal-result-title"], [data-testid="journal-result-details"]',
        )) {
          expect(link.getAttribute('href')).toBe(LIST + '/123/decision');
        }
      }
    },
  );

  it('restores BOTH BASIC and its independent ADVANCED draft through decision and full data', async () => {
    const enabled = context();
    enabled.availableFeatures.push('DECISION_CARD');
    currentContext.mockReturnValue(of(enabled));
    await enter();
    await finish();
    list().filterForm.controls.publisher.setValue('Advanced draft');
    await switchView('BASIC');
    await finish();
    list().filterForm.controls.journalTitle.setValue('Basic applied');
    list().applyFilters();
    await settle();
    await finish();
    list().goToPage(2);
    await settle();
    await finish();
    list().filterForm.controls.journalTitle.setValue('Basic draft');
    const open = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    harness
      .routeNativeElement!.querySelector<HTMLAnchorElement>('[data-testid="journal-result-title"]')!
      .click();
    await open;
    await settle();
    const decision = http.expectOne((value) => value.url.endsWith('/123/decision'));
    expect(decision.request.params.get('surveyYear')).toBe('2024');
    decision.flush({
      surveyYear: 2024,
      annualStatus: 'MISSING_SURVEY',
      availableSurveyYears: [],
      identity: { id: 123, titleOriginal: 'Journal' },
    });
    await settle();
    const details = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    harness
      .routeNativeElement!.querySelector<HTMLAnchorElement>('[data-testid="decision-full-data"]')!
      .click();
    await details;
    await settle();
    const returning = firstValueFrom(
      router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    );
    harness.routeNativeElement!.querySelector<HTMLAnchorElement>('a')!.click();
    await returning;
    await settle();
    expect(list().searchView).toBe('BASIC');
    expect(list().filterForm.controls.journalTitle.value).toBe('Basic draft');
    const restored = request();
    expect(restored.request.body).toMatchObject({
      searchView: 'BASIC',
      journalTitle: 'Basic applied',
      page: 2,
      size: 10,
      surveyYear: 2024,
    });
    restored.flush(PAGE);
    await settle();
    await historyMove('back');
    expect(router.url).toBe(LIST + '/123');
    await historyMove('back');
    const back = http.expectOne((value) => value.url.endsWith('/123/decision'));
    expect(back.request.params.get('surveyYear')).toBe('2024');
    back.flush({
      surveyYear: 2024,
      annualStatus: 'MISSING_SURVEY',
      availableSurveyYears: [],
      identity: { id: 123 },
    });
    await settle();
    await historyMove('forward');
    await historyMove('forward');
    expect(list().searchView).toBe('BASIC');
    const forward = request();
    expect(forward.request.body).toMatchObject({ journalTitle: 'Basic applied', page: 2, size: 10 });
    forward.flush(PAGE);
    await settle();
    await switchView('ADVANCED');
    expect(list().filterForm.controls.publisher.value).toBe('Advanced draft');
    await finish();
  });

});
