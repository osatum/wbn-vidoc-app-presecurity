import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { signal } from '@angular/core';
import { ViewportScroller } from '@angular/common';
import { provideRouter } from '@angular/router';
import { provideTransloco, TranslocoLoader, TranslocoService, Translation } from '@jsverse/transloco';
import { EMPTY, of, Subject, throwError } from 'rxjs';
import { NgbOffcanvasConfig, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';

import {
  ConfigurationService,
  DictionaryService,
  JournalListQuery,
  JournalSearchMetadata,
  JournalService,
  PageResultJournalListView,
  QuestionnaireReportsService,
  ScientificCategoryDictionaryView,
} from '../../../core/backend';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import { NavigationAuditService, NavigationAuditViewContext } from '../../../core/services/navigation-audit.service';
import { NAVIGATION_AUDIT_OPERATION } from '../../../core/interceptors/navigation-audit.interceptor';
import { JournalList } from './journal-list';
import { FilterCheckboxList } from '../../../shared/filter-checkbox-list/filter-checkbox-list';
import { MultiValueSelect } from '../../../shared/multi-value-select/multi-value-select';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation(language: string) {
    const labels = (language === 'en' ? en : pl).page.institution.journalList;
    return of({
      page: {
        institution: {
          journalList: {
            surveyYear: labels.surveyYear,
            description: labels.description,
            notIndexedJournalsTooltip: labels.notIndexedJournalsTooltip,
            notEvaluatedJournalsTooltip: labels.notEvaluatedJournalsTooltip,
            applyFilters: language === 'en' ? 'Apply filters' : 'Zastosuj filtry',
            pagination: {
              summary: language === 'en'
                ? 'Page {{page}} of {{pages}}. Results: {{total}}.'
                : 'Strona {{page}} z {{pages}}. Wyników: {{total}}.',
              pageSize: language === 'en' ? 'Records per page:' : 'Rekordów na stronie:',
            },
            icvRangeError: language === 'en'
              ? 'Enter ICV values of at least 1, with at most two decimal places.'
              : 'Podaj wartości ICV od 1, z maksymalnie dwoma miejscami po przecinku.',
            icvRangeOrderError: language === 'en'
              ? 'The minimum ICV must not exceed the maximum.'
              : 'Minimum ICV nie może przekraczać maksimum.',
            mniswDisciplines: language === 'en' ? 'MNiSW disciplines' : 'Dyscypliny MNiSW',
            unappliedChanges: language === 'en' ? 'Unapplied changes' : 'Niezastosowane zmiany',
            filtersApplied: language === 'en' ? 'Filters applied' : 'Filtry zastosowane',
            retrySearchMetadata: language === 'en' ? 'Try again' : 'Spróbuj ponownie',
            loadingSearchMetadata:
              language === 'en' ? 'Loading filter data…' : 'Pobieranie danych filtrów…',
            searchMetadataError:
              language === 'en'
                ? 'Could not load the available filter values.'
                : 'Nie udało się pobrać dostępnych wartości filtrów.',
            empty:
              language === 'en'
                ? 'No journals found for the selected criteria.'
                : 'Brak czasopism dla wybranych kryteriów.',
          },
        },
      },
    } as Translation);
  }
}

const DISCIPLINES: ScientificCategoryDictionaryView[] = [
  { id: 1, level: 0, namePl: 'Nauki' },
  { id: 2, parentId: 1, level: 1, namePl: 'Nauki ścisłe' },
  { id: 3, parentId: 2, level: 2, namePl: 'Matematyka' },
  { id: 4, parentId: 1, level: 1, namePl: 'Nauki społeczne' },
  { id: 5, parentId: 4, level: 2, namePl: 'Ekonomia' },
  { id: 6, parentId: 2, level: 2, namePl: 'Ekonomia' },
];

describe('JournalList', () => {
  it('translates the whole filter panel and dictionary options when switching to English', async () => {
    const transloco = TestBed.inject(TranslocoService);
    transloco.setTranslation(pl, 'pl');
    transloco.setTranslation(en, 'en');
    countryLoader.mockReturnValue(of([
      { code: 'pl', namePl: 'Polska', nameEn: 'Poland' },
    ]));
    languageLoader.mockReturnValue(of([
      { code: 'en', namePl: 'angielski', nameEn: 'English' },
    ]));
    disciplineLoader.mockReturnValue(of([
      { id: 3, namePl: 'Matematyka', nameEn: 'Mathematics' },
    ]));
    const fixture = await mountedList();
    transloco.setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect([...root.querySelectorAll('[data-advanced-group] > summary')].map(element => element.textContent?.trim())).toEqual([
      'Indexing databases', 'Processing time', 'Publication fees', 'Publication process',
      'Editorial standards', 'International cooperation',
    ]);
    const panelText = root.querySelector('.journal-advanced-filters')!.textContent!;
    for (const label of [
      'Manuscript submission', 'Article publication', 'Open Access publication', 'Rapid review',
      'No other fees', 'First issue year', 'Publication frequency', 'Review model',
      'Continuous', 'Weekly', 'Quarterly', 'Assigns DOI identifiers to articles',
      'Uses an anti-plagiarism system', 'Creative Commons licenses applied to articles',
      'Access model', 'Authors with foreign affiliation (%)', 'Reviewers with foreign affiliation (%)',
    ]) expect(panelText).toContain(label);
    expect(panelText).not.toMatch(/page\.institution|journal\.dictionary/);
    expect(root.querySelector<HTMLInputElement>('#journalTitle')?.placeholder).toBe('Journal title');
    expect(root.querySelector<HTMLInputElement>('#publisher')?.placeholder).toBe('Publisher');
    const list = fixture.componentInstance;
    expect(list.countrySelectOptions()[0].label).toBe('Poland');
    expect(list.languageSelectOptions()[0].label).toBe('English');
    expect(list.disciplineSelectOptions()[0].label).toBe('Mathematics');
    expect(list.mniswDisciplineSelectOptions().map(option => option.label)).toEqual(['Automation', 'Civil engineering']);
  });

  it('shows selected database names under local search while keeping changes as a draft', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const section = fixture.nativeElement.querySelector('[data-advanced-group="repositories"]') as HTMLElement;
    const requests = journalService.searchJournals.mock.calls.length;
    section.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const search = section.querySelector<HTMLInputElement>('input[type=search]')!;
    search.value = 'Database B';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(section.querySelector('ul button')?.textContent).toContain('Database A');
    expect(list.filterForm.controls.nonFeeFilters.controls.repositoryIds.value).toEqual(['10']);
    expect(list.query().repositoryIds).toBeUndefined();
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().repositoryIds).toEqual([10]);
    section.querySelector<HTMLButtonElement>('ul button')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(section.querySelector('ul')).toBeNull();
    expect(list.filterForm.controls.nonFeeFilters.controls.repositoryIds.value).toEqual([]);
    expect(list.query().repositoryIds).toEqual([10]);
    list.applyFilters();
    expect(list.query().repositoryIds).toBeUndefined();
  });

  it('shows the domain frequency order and applies review model checkboxes only on Search', async () => {
    const fixture = await mountedList();
    const lists = fixture.debugElement.queryAll(By.directive(FilterCheckboxList))
      .map((element) => element.componentInstance as FilterCheckboxList);
    const frequency = lists.find((list) => list.label() === 'page.institution.journalList.frequency')!;
    expect(frequency.filteredOptions().map((option) => option.code))
      .toEqual(['k', 'e', 'c', 'm', 'b', 'q', 't', 'f', 'a', 'g']);
    const process = fixture.nativeElement.querySelector('[data-advanced-group="process"]') as HTMLElement;
    expect(process.querySelector('input[type="search"]')).toBeNull();
    const groups = process.querySelectorAll('app-filter-checkbox-list');
    const models = groups[1].querySelectorAll<HTMLInputElement>('input[type="checkbox"]');
    expect(models).toHaveLength(3);
    models[0].click();
    fixture.detectChanges();
    await fixture.whenStable();
    models[2].click();
    fixture.detectChanges();
    await fixture.whenStable();
    const list = fixture.componentInstance;
    expect(list.query().reviewModels).toBeUndefined();
    expect(list.hasUnappliedChanges()).toBe(true);
    list.applyFilters();
    fixture.detectChanges();
    expect(list.query().reviewModels).toEqual(['DOUBLE_BLIND_REVIEW', 'OPEN_PEER_REVIEW']);
    expect(journalService.searchJournals).toHaveBeenLastCalledWith(
      expect.objectContaining({ reviewModels: ['DOUBLE_BLIND_REVIEW', 'OPEN_PEER_REVIEW'], surveyYear: 2024 }),
      undefined, false, expect.anything());
    const chip = list.appliedFilterChips().find((value) => value.id === 'reviewModels:DOUBLE_BLIND_REVIEW')!;
    list.removeAppliedFilter(chip);
    expect(list.query().reviewModels).toEqual(['OPEN_PEER_REVIEW']);
    list.resetFilters();
    expect(list.query().reviewModels).toBeUndefined();
  });

  it('hides the selector when only one year is published', async () => {
    const fixture = await mountedList();
    expect(fixture.componentInstance.query().surveyYear).toBe(2024);
    expect(fixture.nativeElement.querySelector('select#surveyYear')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="journal-search-year"]').textContent).toContain('2024');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="journal-search-year"] label')!.textContent!.trim()).toBe('Rok ICV');
    const year = root.querySelector('[data-testid="journal-search-year"] .form-control')!;
    const title = root.querySelector('#journalTitle')!;
    expect(getComputedStyle(year).fontSize).toBe(getComputedStyle(title).fontSize);
    expect(getComputedStyle(year).height).toBe(getComputedStyle(title).height);
  });

  it('selects a published historical year, reloads metadata and preserves filters when clearing', async () => {
    vi.spyOn(TestBed.inject(ConfigurationService), 'getSearchYears').mockReturnValue(
      of({ defaultYear: 2025, availableYears: [2025, 2024] }),
    );
    TestBed.inject(JournalListQueryStateService).surveyYear.set(undefined);
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    expect(list.query().surveyYear).toBe(2025);
    expect(fixture.nativeElement.querySelectorAll('select#surveyYear option')).toHaveLength(2);
    const form = fixture.nativeElement.querySelector('#journal-search-form') as HTMLFormElement;
    expect(form.querySelector('[app-journal-search-fields]')!.firstElementChild!.querySelector('[data-testid="journal-search-year"]')).toBeTruthy();
    expect(form.querySelector('#surveyYear')).toBeTruthy();
    expect(form.querySelector('[app-journal-search-fields]')!.children[1].querySelector('#journalTitle')).toBeTruthy();
    const year = form.querySelector('#surveyYear')!;
    const title = form.querySelector('#journalTitle')!;
    expect(getComputedStyle(year).fontSize).toBe(getComputedStyle(title).fontSize);
    expect(getComputedStyle(year).height).toBe(getComputedStyle(title).height);
    list.filterForm.controls.journalTitle.setValue('Applied title');
    list.applyFilters();
    list.goToPage(2);
    list.filterForm.controls.publisher.setValue('Unapplied publisher');

    list.selectSurveyYear('2024');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(list.query()).toMatchObject({ surveyYear: 2024, page: 0, journalTitle: 'Applied title' });
    expect(list.query().publisher).toBeUndefined();
    expect(list.filterForm.controls.publisher.value).toBe('Unapplied publisher');
    expect(journalService.getJournalSearchMetadata).toHaveBeenLastCalledWith('body', false,
      expect.objectContaining({ surveyYear: 2024 }));
    list.resetFilters();
    expect(list.query().surveyYear).toBe(2024);
    list.selectSurveyYear('2023');
    expect(list.query().surveyYear).toBe(2024);
  });

  it('blocks search until published years can be loaded and supports retry', async () => {
    await mountedList();
    vi.spyOn(TestBed.inject(ConfigurationService), 'getSearchYears').mockReturnValue(throwError(() => new Error('offline')));
    journalService.searchJournals.mockClear();
    const fixture = await mountedList();
    expect(fixture.componentInstance.hasSearchYearsError()).toBe(true);
    expect(journalService.searchJournals).not.toHaveBeenCalled();
    vi.mocked(TestBed.inject(ConfigurationService).getSearchYears).mockReturnValue(
      of({ defaultYear: 2024, availableYears: [2024] }),
    );
    fixture.componentInstance.loadSearchYears();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.hasSearchYearsError()).toBe(false);
    expect(journalService.searchJournals).toHaveBeenCalled();
  });
  let component: JournalList;
  let repositoryLoader: ReturnType<typeof vi.fn>;
  let journalService: {
    searchJournals: ReturnType<typeof vi.fn>;
    getJournalSearchMetadata: ReturnType<typeof vi.fn>;
    getJournalSearchCounts: ReturnType<typeof vi.fn>;
  };
  let audit: { track: ReturnType<typeof vi.fn>; captureView: ReturnType<typeof vi.fn> };
  const viewContext: NavigationAuditViewContext = {
    pageKey: 'institution.journals.list',
    routeTemplate: '/institution/user/journals',
    url: '/institution/user/journals',
    viewId: 'journal-list-view',
  };
  let questionnaireReportsService: {
    questionnaireReportAvailability: ReturnType<typeof vi.fn>;
    downloadQuestionnaireReport2024: ReturnType<typeof vi.fn>;
  };

  async function mountedList() {
    const fixture = TestBed.createComponent(JournalList);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  let countryLoader: ReturnType<typeof vi.fn>;
  let languageLoader: ReturnType<typeof vi.fn>;
  let disciplineLoader: ReturnType<typeof vi.fn>;

  function filterLayout(compact = true) {
    const media = {
      matches: compact,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    };
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => media),
    );
    TestBed.inject(NgbOffcanvasConfig).animation = false;
    return (matches: boolean) => {
      media.matches = matches;
      const listener = media.addEventListener.mock.calls.at(-1)![1] as () => void;
      listener();
    };
  }

  it.each(['pl', 'en'])(
    'keeps one panel apply action below the filters in normal page flow with a truthful state in %s',
    async (language) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await mountedList();
      const list = fixture.componentInstance;
      const root: HTMLElement = fixture.nativeElement;
      const aside = root.querySelector('aside')!;
      const actions = aside.querySelector('[data-testid="journal-filter-actions"]')!;
      const options = aside.querySelector('.journal-filter-options')!;
      const apply = actions.querySelector<HTMLButtonElement>(
        '[data-testid="journal-apply-filters"]',
      )!;
      expect(root.querySelectorAll('[data-testid="journal-apply-filters"]')).toHaveLength(1);
      expect(options.nextElementSibling).toBe(actions);
      expect(options.querySelector('button[data-testid="journal-apply-filters"]')).toBeNull();
      expect(getComputedStyle(aside).position).toBe('static');
      expect(getComputedStyle(aside).maxHeight).toBe('none');
      expect(getComputedStyle(options).overflowY).toBe('visible');
      expect(apply.type).toBe('button');
      expect(apply.disabled).toBe(true);
      expect(apply.textContent!.trim()).toBe(language === 'en' ? 'Apply filters' : 'Zastosuj filtry');
      expect(actions.querySelector('#journal-filter-state')!.textContent!.trim()).toBe(
        language === 'en' ? 'Filters applied' : 'Filtry zastosowane',
      );
      list.filterForm.patchValue({ countryCodes: ['pl', 'de'], languageCodes: ['en', 'pl'] });
      list.applyFilters();
      fixture.detectChanges();
      await fixture.whenStable();
      list.filterForm.patchValue({ countryCodes: ['de', 'pl'], languageCodes: ['pl', 'en'] });
      fixture.detectChanges();
      expect(apply.disabled).toBe(true);
      list.filterForm.controls.publisher.setValue('Pending publisher');
      fixture.detectChanges();
      expect(apply.disabled).toBe(false);
      expect(actions.querySelector('#journal-filter-state')!.textContent!.trim()).toBe(
        language === 'en' ? 'Unapplied changes' : 'Niezastosowane zmiany',
      );
      expect(apply.textContent).not.toMatch(/\d/);
      expect(root.querySelector('app-journal-applied-filters [role="status"] button')).toBeNull();
    },
  );

  it.each([
    { compact: false, language: 'pl' },
    { compact: false, language: 'en' },
    { compact: true, language: 'pl' },
    { compact: true, language: 'en' },
  ])(
    'shows metadata retry only after a failure beside panel actions in $language, compact=$compact',
    async ({ compact, language }) => {
      filterLayout(compact);
      TestBed.inject(TranslocoService).setActiveLang(language);
      const initial = new Subject<JournalSearchMetadata>();
      journalService.getJournalSearchMetadata.mockReturnValue(initial);
      const fixture = await mountedList();
      const list = fixture.componentInstance;
      const root: HTMLElement = fixture.nativeElement;
      if (compact) {
        list.openFilterPanel();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
      }
      const actions = root.querySelector('[data-testid="journal-filter-actions"]')!;
      const options = root.querySelector('.journal-filter-options')!;
      const retrySelector = '[data-testid="journal-search-metadata-retry"]';
      expect(root.querySelector(retrySelector)).toBeNull();
      expect(actions.querySelector('[role="status"]')!.textContent!.trim()).toBe(
        language === 'en' ? 'Loading filter data…' : 'Pobieranie danych filtrów…',
      );
      list.filterForm.patchValue({
        publisher: 'Pending publisher',
        mniswOnly: true,
        mniswPointsFrom: '40',
        ratingStatus: 'INDEXED',
        icvFrom: '20',
      });
      const draft = list.filterForm.getRawValue();
      const applied = list.query();
      const searches = journalService.searchJournals.mock.calls.length;
      const metadataRequests = journalService.getJournalSearchMetadata.mock.calls.length;
      const events = audit.track.mock.calls.length;
      initial.error(new Error('Metadata offline'));
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const retry = actions.querySelector<HTMLButtonElement>(retrySelector)!;
      expect(root.querySelectorAll(retrySelector)).toHaveLength(1);
      expect(options.querySelector(retrySelector)).toBeNull();
      expect(options.querySelector('#journal-search-metadata-error')).toBeNull();
      expect(actions.querySelector('#journal-search-metadata-error')!.getAttribute('role')).toBe(
        'alert',
      );
      expect(actions.querySelector('#journal-search-metadata-error')!.textContent!.trim()).toBe(
        language === 'en'
          ? 'Could not load the available filter values.'
          : 'Nie udało się pobrać dostępnych wartości filtrów.',
      );
      expect(retry.previousElementSibling).toBe(
        actions.querySelector('[data-testid="journal-panel-clear"]'),
      );
      expect(actions.querySelector('[data-testid="journal-apply-filters"]')).not.toBeNull();
      expect(retry.type).toBe('button');
      expect(retry.getAttribute('aria-describedby')).toBe('journal-search-metadata-error');
      expect(retry.textContent!.trim()).toBe(language === 'en' ? 'Try again' : 'Spróbuj ponownie');
      const pending = new Subject<JournalSearchMetadata>();
      journalService.getJournalSearchMetadata.mockReturnValue(pending);
      retry.click();
      retry.click();
      fixture.detectChanges();
      expect(journalService.getJournalSearchMetadata).toHaveBeenCalledTimes(metadataRequests + 1);
      expect(actions.querySelector(retrySelector)).toBeNull();
      expect(actions.querySelector('#journal-search-metadata-error')).toBeNull();
      expect(actions.querySelector('[role="status"]')).not.toBeNull();
      pending.error(new Error('Still offline'));
      fixture.detectChanges();
      expect(actions.querySelector(retrySelector)).not.toBeNull();
      expect(actions.querySelector('[role="status"]')).toBeNull();
      journalService.getJournalSearchMetadata.mockReturnValue(
        of({ mniswPointClasses: [40, 70], icvMin: 0, icvMax: 200 }),
      );
      actions.querySelector<HTMLButtonElement>(retrySelector)!.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(actions.querySelector(retrySelector)).toBeNull();
      expect(actions.querySelector('#journal-search-metadata-error')).toBeNull();
      expect(actions.querySelector('[role="status"]')).toBeNull();
      expect(list.mniswPointClasses()).toEqual([40, 70]);
      expect(list.filterForm.getRawValue()).toEqual(draft);
      expect(list.query()).toEqual(applied);
      expect(journalService.searchJournals).toHaveBeenCalledTimes(searches);
      expect(audit.track).toHaveBeenCalledTimes(events);
    },
  );

  it('blocks duplicate panel submissions during loading and exposes the existing retry after an error', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const pending = new Subject<PageResultJournalListView>();
    journalService.searchJournals.mockReturnValue(pending);
    const requests = journalService.searchJournals.mock.calls.length;
    list.filterForm.controls.publisher.setValue('Pending');
    fixture.detectChanges();
    const apply = root.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!;
    apply.click();
    apply.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests + 1);
    expect(apply.disabled).toBe(true);
    expect(apply.getAttribute('aria-busy')).toBe('true');
    expect(apply.querySelector('.spinner-border')).not.toBeNull();
    list.filterForm.controls.publisher.setValue('Later draft');
    fixture.detectChanges();
    expect(apply.disabled).toBe(true);
    pending.error(new Error('Offline'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(apply.disabled).toBe(false);
    expect(root.querySelector('[data-testid="journal-search-retry"]')).not.toBeNull();
    expect(list.query().publisher).toBe('Pending');
  });

  it('clears from the panel with the original secondary style and one CLEAR operation', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    list.filterForm.patchValue({ journalTitle: 'Draft', countryCodes: ['pl'], mniswOnly: true });
    const events = audit.track.mock.calls.length;
    const clear = root.querySelector<HTMLButtonElement>('[data-testid="journal-panel-clear"]')!;
    expect(clear.classList.contains('btn-soft-dark')).toBe(true);
    expect(clear.querySelector('.ri-filter-off-line')).not.toBeNull();
    clear.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.journalTitle.value).toBe('');
    expect(list.query().countryCodes).toEqual([]);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(
      audit.track.mock.calls
        .slice(events)
        .filter(([event]) => event.eventType === 'JOURNAL_FILTERS_CLEAR'),
    ).toHaveLength(1);
    expect(
      audit.track.mock.calls.slice(events).filter(([event]) => event.eventType === 'JOURNAL_SEARCH'),
    ).toHaveLength(0);
  });

  it.each(['close', 'escape', 'backdrop'])(
    'cancels only mobile-panel edits through %s and retains the previous draft, groups and scroll',
    async (action) => {
      filterLayout();
      const fixture = await mountedList();
      const list = fixture.componentInstance;
      const root: HTMLElement = fixture.nativeElement;
      list.filterForm.controls.journalTitle.setValue('Earlier draft');
      fixture.detectChanges();
      const draft = list.filterForm.getRawValue();
      const requests = journalService.searchJournals.mock.calls.length;
      const dictionaries = repositoryLoader.mock.calls.length;
      const events = audit.track.mock.calls.length;
      expect(root.querySelector('aside')).toBeNull();
      const trigger = root.querySelector<HTMLButtonElement>('[data-testid="journal-open-filters"]')!;
      trigger.focus();
      trigger.click();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const dialog = root.querySelector<HTMLElement>('ngb-offcanvas-panel')!;
      expect(dialog.getAttribute('role')).toBe('dialog');
      expect(dialog.getAttribute('aria-modal')).toBe('true');
      expect(dialog.getAttribute('aria-labelledby')).toBe('journal-filters-heading');
      expect(root.querySelectorAll('[data-testid="journal-header-icv"] .noUi-handle')).toHaveLength(2);
      const panel = root.querySelector<HTMLElement>('aside')!;
      panel.scrollTop = 185;
      root.querySelector<HTMLDetailsElement>('[data-advanced-group="cooperation"]')!.open = true;
      list.filterForm.patchValue({ publisher: 'Cancelled', mniswOnly: true });
      list.onMniswToggle();
      list.filterForm.controls.mniswPointsFrom.setValue('70');
      if (action === 'close')
        root.querySelector<HTMLButtonElement>('[data-testid="journal-close-filters"]')!.click();
      else if (action === 'escape')
        dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      else
        root
          .querySelector<HTMLElement>('ngb-offcanvas-backdrop')!
          .dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
      await vi.waitFor(() => expect(root.querySelector('ngb-offcanvas-panel')).toBeNull());
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      expect(root.querySelector('ngb-offcanvas-panel')).toBeNull();
      expect(list.filterForm.getRawValue()).toEqual(draft);
      expect(list.filterForm.controls.mniswPointsFrom.disabled).toBe(true);
      expect(journalService.searchJournals).toHaveBeenCalledTimes(requests);
      expect(audit.track.mock.calls).toHaveLength(events);
    await vi.waitFor(() => expect(document.activeElement).toBe(trigger));
    panel.scrollTop = 0;
    list.rememberForDetails(new MouseEvent('click', { button: 0 }), 1);
    expect(TestBed.inject(JournalListQueryStateService).rememberForDetails).toHaveBeenLastCalledWith(
      'test-origin',
      1,
      expect.objectContaining({ filterScroll: 185 }),
    );
      trigger.click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(
        root.querySelector<HTMLDetailsElement>('[data-advanced-group="cooperation"]')!.open,
      ).toBe(true);
      expect(root.querySelector<HTMLElement>('aside')!.scrollTop).toBe(185);
      expect(getComputedStyle(root.querySelector('.journal-filter-options')!).overflowY).toBe('visible');
      expect(repositoryLoader).toHaveBeenCalledTimes(dictionaries);
    },
  );

  it('applies the entire draft from mobile once, closes to results and keeps dictionary labels without recreating controls', async () => {
    filterLayout();
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const requests = journalService.searchJournals.mock.calls.length;
    const dictionaries = repositoryLoader.mock.calls.length;
    list.filterForm.controls.journalTitle.setValue('Main title draft');
    list.openFilterPanel();
    fixture.detectChanges();
    await fixture.whenStable();
    const input = root.querySelector('#publisher');
    list.filterForm.patchValue({ publisher: 'Publisher', nonFeeFilters: { repositoryIds: ['10'] } });
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('ngb-offcanvas-panel')).toBeNull();
    await vi.waitFor(() =>
      expect(document.activeElement).toBe(root.querySelector('.journal-search-content')),
    );
    expect(list.query()).toMatchObject({
      journalTitle: 'Main title draft',
      publisher: 'Publisher',
      repositoryIds: [10],
      page: 0,
    });
    expect(root.querySelector('app-journal-applied-filters')!.textContent).toContain('Database A');
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests + 1);
    list.openFilterPanel();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('#publisher')).toBe(input);
    expect(repositoryLoader).toHaveBeenCalledTimes(dictionaries);
  });

  it('retains mobile validation and focuses the opened group rather than closing or applying an invalid draft', async () => {
    filterLayout();
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const applied = list.query();
    list.openFilterPanel();
    fixture.detectChanges();
    await fixture.whenStable();
    root.querySelector<HTMLDetailsElement>('[data-advanced-group="cooperation"]')!.open = false;
    list.filterForm.controls.authorForeignAffiliationPercentageFrom.setValue('101');
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('ngb-offcanvas-panel')).not.toBeNull();
    expect(root.querySelector<HTMLDetailsElement>('[data-advanced-group="cooperation"]')!.open).toBe(
      true,
    );
    expect(
      root.querySelector('[data-advanced-group="cooperation"]')!.contains(document.activeElement),
    ).toBe(true);
    expect(list.query()).toEqual(applied);
    list.filterForm.controls.authorForeignAffiliationPercentageFrom.setValue('50');
    fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('ngb-offcanvas-panel')).toBeNull();
    expect(list.query().authorForeignAffiliationPercentageFrom).toBe(50);
  });

  it('preserves controls, drafts, group expansion and scroll when switching between desktop and mobile', async () => {
    const resize = filterLayout(false);
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const input = root.querySelector('#publisher');
    root.querySelector<HTMLDetailsElement>('[data-advanced-group="repositories"]')!.open = true;
    list.filterForm.controls.publisher.setValue('Desktop draft');
    resize(true);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('aside')).toBeNull();
    list.openFilterPanel();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('#publisher')).toBe(input);
    expect(root.querySelector<HTMLElement>('aside')!.scrollTop).toBe(0);
    root.querySelector<HTMLElement>('aside')!.scrollTop = 120;
    list.filterForm.controls.publisher.setValue('Mobile draft');
    resize(false);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(root.querySelector('ngb-offcanvas-panel')).toBeNull();
    expect(root.querySelector('#publisher')).toBe(input);
    expect(root.querySelector<HTMLDetailsElement>('[data-advanced-group="repositories"]')!.open).toBe(
      true,
    );
    expect(list.filterForm.controls.publisher.value).toBe('Mobile draft');
    expect(list.query().publisher).toBeUndefined();
    expect(root.querySelector<HTMLElement>('aside')!.scrollTop).toBe(0);
    resize(true);
    fixture.detectChanges();
    await fixture.whenStable();
    list.openFilterPanel();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector<HTMLElement>('aside')!.scrollTop).toBe(120);
  });

  it('leaves all expanded desktop filters in page flow at the top and bottom without viewport clipping', async () => {
    const fixture = await mountedList();
    const root: HTMLElement = fixture.nativeElement;
    const panel = root.querySelector<HTMLElement>('aside')!;
    const bounds = vi.spyOn(panel, 'getBoundingClientRect');
    bounds.mockReturnValue({ top: 260 } as DOMRect);
    window.dispatchEvent(new Event('scroll'));
    bounds.mockReturnValue({ top: -2000 } as DOMRect);
    window.dispatchEvent(new Event('scroll'));
    window.dispatchEvent(new Event('resize'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(bounds).not.toHaveBeenCalled();
    expect(panel.style.getPropertyValue('--journal-filters-visible-top')).toBe('');
    expect(getComputedStyle(panel).position).toBe('static');
    expect(getComputedStyle(panel).maxHeight).toBe('none');
    const options = root.querySelector<HTMLElement>('.journal-filter-options')!;
    for (const group of options.querySelectorAll('details')) group.open = true;
    expect(getComputedStyle(options).overflowY).toBe('visible');
    expect(options.hasAttribute('tabindex')).toBe(false);
    expect(options.getAttribute('aria-labelledby')).toBe('journal-filters-heading');
    const lastFilter = options.querySelector<HTMLInputElement>('#reviewerForeignAffiliationPercentageTo')!;
    lastFilter.focus();
    lastFilter.value = '50';
    lastFilter.dispatchEvent(new Event('input', { bubbles: true }));
    expect(document.activeElement).toBe(lastFilter);
    expect(fixture.componentInstance.applyFilters()).toBe(true);
    expect(fixture.componentInstance.query().reviewerForeignAffiliationPercentageTo).toBe(50);
  });

  it('clears immediately from mobile without rolling back to the draft captured when opening', async () => {
    filterLayout();
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    list.filterForm.patchValue({ countryCodes: ['pl'], journalTitle: 'Before opening' });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const events = audit.track.mock.calls.length;
    list.openFilterPanel();
    fixture.detectChanges();
    await fixture.whenStable();
    list.filterForm.controls.publisher.setValue('Unapplied edit');
    root.querySelector<HTMLButtonElement>('[data-testid="journal-panel-clear"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('ngb-offcanvas-panel')).toBeNull();
    await vi.waitFor(() =>
      expect(document.activeElement).toBe(root.querySelector('.journal-search-content')),
    );
    expect(list.filterForm.controls.journalTitle.value).toBe('');
    expect(list.filterForm.controls.publisher.value).toBe('');
    expect(list.query().countryCodes).toEqual([]);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(
      audit.track.mock.calls
        .slice(events)
        .filter(([event]) => event.eventType === 'JOURNAL_FILTERS_CLEAR'),
    ).toHaveLength(1);
  });

  it.each([
    ['pl', 'Brak czasopism dla wybranych kryteriów.'],
    ['en', 'No journals found for the selected criteria.'],
  ])(
    'shows an empty-results Bootstrap card without count, sorting or pagination in %s',
    async (language, message) => {
      TestBed.inject(TranslocoService).setActiveLang(language);
      const fixture = await mountedList();
      const element: HTMLElement = fixture.nativeElement;
      const card = element.querySelector('[data-testid="journal-search-empty"]')!;
      expect(card.classList.contains('card')).toBe(true);
      expect(card.querySelector('.card-body[role="status"]')!.textContent!.trim()).toBe(message);
      expect(element.querySelectorAll('[data-testid="journal-search-empty"]')).toHaveLength(1);
      expect(element.querySelector('.journal-results-toolbar')).toBeNull();
      expect(element.querySelector('#journalSort')).toBeNull();
      expect(element.querySelector('.journal-results-pagination')).toBeNull();
      expect(element.querySelector('app-journal-pagination')).toBeNull();
      expect(element.querySelector('#journal-target-page')).toBeNull();
      expect(element.querySelector('app-journal-result-card')).toBeNull();
      expect(element.querySelector('#journal-search-form')).not.toBeNull();
      expect(element.querySelector('aside')).not.toBeNull();
      expect(element.querySelector('app-journal-applied-filters')).not.toBeNull();
    },
  );

  it('restores result controls when matches appear and hides them again without resetting filters or sort', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    journalService.searchJournals.mockReturnValue(
      of({ data: [{ id: 1, title: 'Journal' }], totalElements: 1, totalPages: 1 }),
    );
    list.filterForm.patchValue({
      publisher: 'Existing publisher',
      sortBy: JournalListQuery.SortByEnum.SortByTitleDesc,
    });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="journal-search-empty"]')).toBeNull();
    expect(element.querySelector('.journal-results-toolbar')).not.toBeNull();
    expect(element.querySelector<HTMLSelectElement>('#journalSort')!.value).toBe(
      JournalListQuery.SortByEnum.SortByTitleDesc,
    );
    expect(element.querySelector('app-journal-pagination')).not.toBeNull();
    expect(element.querySelector('app-journal-result-card')).not.toBeNull();
    journalService.searchJournals.mockReturnValue(
      of({ data: [], totalElements: 0, totalPages: 0 }),
    );
    list.filterForm.controls.publisher.setValue('Missing publisher');
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(element.querySelector('[data-testid="journal-search-empty"]')).not.toBeNull();
    expect(element.querySelector('.journal-results-toolbar')).toBeNull();
    expect(element.querySelector('app-journal-pagination')).toBeNull();
    expect(list.query()).toMatchObject({
      publisher: 'Missing publisher',
      sortBy: JournalListQuery.SortByEnum.SortByTitleDesc,
    });
    expect(list.appliedFilterChips().find((chip) => chip.id === 'publisher')?.value).toBe(
      'Missing publisher',
    );
  });

  it('does not show an empty-results card while loading or after an error and shows it after a successful empty retry', async () => {
    const pending = new Subject<PageResultJournalListView>();
    journalService.searchJournals.mockReturnValue(pending);
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    expect(list.isLoading()).toBe(true);
    expect(element.querySelector('app-local-spinner')).not.toBeNull();
    expect(element.querySelector('[data-testid="journal-search-empty"]')).toBeNull();
    pending.error(new Error('Search offline'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(list.hasError()).toBe(true);
    expect(element.querySelector('[data-testid="journal-search-empty"]')).toBeNull();
    expect(element.querySelector('[data-testid="journal-search-retry"]')).not.toBeNull();
    journalService.searchJournals.mockReturnValue(
      of({ data: [], totalElements: 0, totalPages: 0 }),
    );
    element.querySelector<HTMLButtonElement>('[data-testid="journal-search-retry"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(list.hasError()).toBe(false);
    expect(element.querySelector('[data-testid="journal-search-empty"]')).not.toBeNull();
    expect(element.querySelector('.journal-results-toolbar')).toBeNull();
    expect(element.querySelector('app-journal-pagination')).toBeNull();
  });

  it('keeps pagination available on an empty page when the search still has matches on other pages', async () => {
    journalService.searchJournals.mockReturnValue(
      of({ data: [], totalElements: 10, totalPages: 1 }),
    );
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(
      of({ page: 1 }),
    );
    const fixture = await mountedList();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelector('.journal-results-toolbar')).not.toBeNull();
    expect(element.querySelector('app-journal-pagination')).not.toBeNull();
    expect(element.querySelector('#journal-target-page')).not.toBeNull();
    const previous = element.querySelector<HTMLButtonElement>('app-journal-pagination nav button')!;
    expect(previous.disabled).toBe(false);
    previous.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.page()).toBe(0);
  });

  it('applies draft criteria from the persistent panel action once, resets paging and records a Search operation', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    list.page.set(2);
    fixture.detectChanges();
    await fixture.whenStable();
    const requests = journalService.searchJournals.mock.calls.length;
    const searchEvents = audit.track.mock.calls.filter(
      ([event]) => event.eventType === 'JOURNAL_SEARCH',
    ).length;
    list.filterForm.controls.publisher.setValue('New publisher');
    fixture.detectChanges();
    await fixture.whenStable();
    const status = element.querySelector('app-journal-applied-filters [role="status"]')!;
    expect(status.classList.contains('mt-3')).toBe(false);
    expect(list.query().publisher).toBeUndefined();
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    expect(status.querySelector('button')).toBeNull();
    element.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(list.query()).toMatchObject({ publisher: 'New publisher', page: 0 });
    expect(list.appliedFilterChips().find((chip) => chip.id === 'publisher')?.value).toBe(
      'New publisher',
    );
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(element.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.disabled).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    expect(
      audit.track.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_SEARCH'),
    ).toHaveLength(searchEvents + 1);
  });

  it('uses the existing validation from the panel action and focuses an invalid draft without searching', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.setValue('-1');
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasFeeFilterError()).toBe(true);
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(list.query()).toEqual(applied);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    expect(document.activeElement).toBe(element.querySelector('#publicationFeeMaxPln'));
    expect(element.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.disabled).toBe(false);
    list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.setValue('100');
    fixture.detectChanges();
    element.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(list.hasFeeFilterError()).toBe(false);
    expect(list.query().publicationFeeMaxPln).toBe(100);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    expect(element.querySelector<HTMLButtonElement>('[data-testid="journal-apply-filters"]')!.disabled).toBe(true);
  });

  it('keeps the Bootstrap heading and removes the advanced introductory description', async () => {
    const fixture = await mountedList();
    const root: HTMLElement = fixture.nativeElement;
    const card = root.querySelector('.journal-search-header')!;
    const header = card.querySelector('header.card-header')!;
    for (const className of ['card-title', 'fs-4', 'fw-semibold'])
      expect(header.querySelector('h1')!.classList.contains(className)).toBe(true);
    expect(card.querySelector('[data-testid="journal-search-description"]')).toBeNull();
    expect(card.textContent).not.toContain(pl.page.institution.journalList.description);
    expect(header.nextElementSibling!.querySelector('#journal-search-form')).not.toBeNull();
  });

  it('gives controls a shared height and side-by-side buttons a matching minimum height as selections grow', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const form = element.querySelector<HTMLFormElement>('#journal-search-form')!;
    const actions = form.querySelector<HTMLElement>('.journal-main-actions')!;
    expect(getComputedStyle(form).getPropertyValue('--journal-search-control-height').trim()).toBe(
      '3rem',
    );
    const controls = [
      form.querySelector('#journalTitle')!,
      form.querySelector('#issn')!,
      form.querySelector('[data-testid="jml-disciplines"] .multi-value-toggle')!,
      form.querySelector('[data-testid="journal-search-submit"]')!,
      form.querySelector('[data-testid="journal-search-clear"]')!,
    ];
    const heights = controls.map((control, index) => {
      const style = getComputedStyle(control);
      return index < 3 ? style.height : style.minHeight;
    });
    expect(heights[0]).not.toBe('');
    expect(heights[0]).not.toBe('auto');
    expect(new Set(heights).size).toBe(1);
    expect(getComputedStyle(controls[3]).fontSize).toBe(getComputedStyle(controls[4]).fontSize);
    const year = form.querySelector('[data-testid="journal-search-year"]')!;
    expect(year.classList.contains('d-flex')).toBe(true);
    expect(year.querySelector('label')!.classList.contains('text-nowrap')).toBe(true);
    expect(form.querySelector('[app-journal-search-fields]')!.classList.contains('row')).toBe(true);
    expect(
      form
        .querySelector('[data-testid="journal-search-submit"] .ri-search-line')!
        .getAttribute('aria-hidden'),
    ).toBe('true');
    expect(
      form
        .querySelector('[data-testid="journal-search-clear"] .ri-filter-off-line')!
        .getAttribute('aria-hidden'),
    ).toBe('true');
    const row = getComputedStyle(actions).gridRow;
    const requests = journalService.searchJournals.mock.calls.length;
    list.setDisciplineCodes(['3', '5']);
    list.hasDisciplineOptionsError.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    const jml = form.querySelector('[data-testid="jml-disciplines"]')!;
    expect(jml.querySelectorAll('li')).toHaveLength(0);
    expect(element.querySelector('[data-testid="pending-jml-disciplines"]')).toBeNull();
    expect(jml.parentElement!.querySelector('[role="alert"]')).not.toBeNull();
    expect(getComputedStyle(actions).gridRow).toBe(row);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
  });

  it.each([
    ['notIndexedJournals', 'notIndexedJournalsTooltip'],
    ['notEvaluatedJournals', 'notEvaluatedJournalsTooltip'],
  ] as const)('explains %s on focus without changing the status or submitting Search', async (status, key) => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const switchElement = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#' + status)!;
    const button = switchElement.parentElement!.querySelector<HTMLButtonElement>('button')!;
    const tooltip = fixture.debugElement.queryAll(By.directive(NgbTooltip))
      .find((element) => element.nativeElement === button)!.injector.get(NgbTooltip);
    const requests = journalService.searchJournals.mock.calls.length;
    const before = list.filterForm.getRawValue();
    expect(button.type).toBe('button');
    expect(button.tabIndex).toBe(0);
    expect(button.closest('label')).toBeNull();
    expect(button.querySelector('.ri-information-line')!.getAttribute('aria-hidden')).toBe('true');
    expect(button.previousElementSibling).toBe(switchElement);
    expect(button.nextElementSibling).toBe(switchElement.parentElement!.querySelector('label[for="' + status + '"]'));
    expect(button.getAttribute('aria-label')).toBe(pl.page.institution.journalList[key]);
    button.focus();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(tooltip.isOpen()).toBe(true);
    expect(document.getElementById(button.getAttribute('aria-describedby')!)!.textContent!.trim())
      .toBe(pl.page.institution.journalList[key]);
    button.dispatchEvent(new MouseEvent('mouseenter'));
    button.dispatchEvent(new MouseEvent('mouseleave'));
    await fixture.whenStable();
    expect(tooltip.isOpen()).toBe(true);
    button.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.getRawValue()).toEqual(before);
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests);
    button.blur();
    await fixture.whenStable();
    expect(tooltip.isOpen()).toBe(false);
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(button.getAttribute('aria-label')).toBe(en.page.institution.journalList[key]);
    expect(tooltip.ngbTooltip).toBe(en.page.institution.journalList[key]);
  });

  it.each(['notIndexedJournals', 'notEvaluatedJournals'] as const)(
    'shows a visible tooltip on mouse hover over %s without an extra change detection pass',
    async (status) => {
      const fixture = await mountedList();
      const root = fixture.nativeElement as HTMLElement;
      const button = root.querySelector<HTMLInputElement>('#' + status)!.parentElement!
        .querySelector<HTMLButtonElement>('button')!;
      button.dispatchEvent(new MouseEvent('mouseenter'));
      await fixture.whenStable();
      const tooltip = document.getElementById(button.getAttribute('aria-describedby')!);
      expect(tooltip).not.toBeNull();
      expect(tooltip!.getAttribute('role')).toBe('tooltip');
      expect(tooltip!.parentElement).toBe(document.body);
      expect(getComputedStyle(tooltip!).position).toBe('fixed');
      expect(tooltip!.classList.contains('show')).toBe(true);
      expect(tooltip!.textContent!.trim()).toBe(button.getAttribute('aria-label'));
      button.dispatchEvent(new MouseEvent('mouseleave'));
      await fixture.whenStable();
      expect(button.hasAttribute('aria-describedby')).toBe(false);
    },
  );

  it('keeps both ICV information icons reachable and switches between their distinct tooltips', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const before = list.filterForm.getRawValue();
    const requests = journalService.searchJournals.mock.calls.length;
    const root = fixture.nativeElement as HTMLElement;
    for (const [status, key] of [
      ['notIndexedJournals', 'notIndexedJournalsTooltip'],
      ['notEvaluatedJournals', 'notEvaluatedJournalsTooltip'],
    ] as const) {
      const group = root.querySelector('#' + status)!.parentElement!;
      const button = group.querySelector<HTMLButtonElement>('button')!;
      expect(parseFloat(getComputedStyle(group).minWidth)).toBe(0);
      expect(parseFloat(getComputedStyle(group.querySelector('label')!).minWidth)).toBe(0);
      expect(getComputedStyle(group.querySelector('label')!).overflowWrap).toBe('anywhere');
      expect(getComputedStyle(button).width).toBe(getComputedStyle(button).height);
      expect(parseFloat(getComputedStyle(button).width)).toBeGreaterThan(0);
      button.dispatchEvent(new MouseEvent('mouseenter'));
      await fixture.whenStable();
      const tooltips = document.querySelectorAll('[role="tooltip"]');
      expect(tooltips).toHaveLength(1);
      expect(tooltips[0].textContent!.trim()).toBe(pl.page.institution.journalList[key]);
      button.dispatchEvent(new MouseEvent('mouseleave'));
      await fixture.whenStable();
      expect(document.querySelector('[role="tooltip"]')).toBeNull();
    }
    expect(list.filterForm.getRawValue()).toEqual(before);
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests);
  });

  it('clears all applied and draft criteria from the main form once without submitting Search', async () => {
    journalService.searchJournals.mockReturnValue(
      of({ data: [{ id: 1 }], totalElements: 27, totalPages: 3 }),
    );
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.filterForm.patchValue({
      journalTitle: 'Applied title',
      issn: '1234',
      publisher: 'Applied publisher',
      countryCodes: ['pl'],
      languageCodes: ['en'],
      disciplineCodes: ['3'],
      nonFeeFilters: { doiOnly: true, repositoryIds: ['10'] },
      feeFilters: { publicationFeeMaxPln: '100' },
      sortBy: JournalListQuery.SortByEnum.SortByTitleDesc,
    });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    list.goToPage(2);
    fixture.detectChanges();
    await fixture.whenStable();
    list.filterForm.controls.publisher.setValue('Unapplied publisher');
    fixture.detectChanges();
    const requests = journalService.searchJournals.mock.calls.length;
    const searches = audit.track.mock.calls.filter(
      ([event]) => event.eventType === 'JOURNAL_SEARCH',
    ).length;
    const clears = audit.track.mock.calls.filter(
      ([event]) => event.eventType === 'JOURNAL_FILTERS_CLEAR',
    ).length;
    const element: HTMLElement = fixture.nativeElement;
    const clear = element.querySelector<HTMLButtonElement>(
      '#journal-search-form [data-testid="journal-search-clear"]',
    )!;
    expect(clear.type).toBe('button');
    expect(clear.classList.contains('btn-soft-dark')).toBe(true);
    expect(clear.classList.contains('fs-5')).toBe(true);
    expect(clear.classList.contains('btn-outline-primary')).toBe(false);
    clear.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(list.query()).toMatchObject({
      page: 0,
      countryCodes: [],
      languageCodes: [],
      sortBy: JournalListQuery.SortByEnum.SortByTitleAsc,
    });
    for (const field of [
      'journalTitle',
      'issn',
      'publisher',
      'disciplineCodes',
      'doiOnly',
      'repositoryIds',
      'publicationFeeMaxPln',
    ] as const)
      expect(list.query()[field]).toBeUndefined();
    expect(list.filterForm.controls.publisher.value).toBe('');
    expect(list.appliedFilterChips()).toHaveLength(0);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(element.querySelectorAll('[data-testid="jml-disciplines"] li')).toHaveLength(0);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    expect(
      audit.track.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_SEARCH'),
    ).toHaveLength(searches);
    expect(
      audit.track.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_FILTERS_CLEAR'),
    ).toHaveLength(clears + 1);
  });

  it('submits the main Search icon button once and applies the draft', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;
    const requests = journalService.searchJournals.mock.calls.length;
    const searches = audit.track.mock.calls.filter(
      ([event]) => event.eventType === 'JOURNAL_SEARCH',
    ).length;
    list.filterForm.controls.journalTitle.setValue('New title');
    fixture.detectChanges();
    const search = element.querySelector<HTMLButtonElement>(
      '[data-testid="journal-search-submit"]',
    )!;
    expect(search.type).toBe('submit');
    search.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(list.query().journalTitle).toBe('New title');
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    expect(
      audit.track.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_SEARCH'),
    ).toHaveLength(searches + 1);
  });

  it.each([false, true])('keeps only additional filters in the sidebar, collapsed by default, compact=%s', async (compact) => {
    filterLayout(compact);
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    if (compact) {
      list.openFilterPanel();
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
    }
    const groups = [
      ...fixture.nativeElement.querySelectorAll('.journal-filter-options > details'),
    ] as HTMLDetailsElement[];
    expect(groups.map((group) => group.dataset['advancedGroup'])).toEqual([
      'repositories',
      'processing',
      'fees',
      'process',
      'standards',
      'cooperation',
    ]);
    expect(
      groups.filter((group) => group.open).map((group) => group.dataset['advancedGroup']),
    ).toEqual([]);
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    const repositoryRequests = repositoryLoader.mock.calls.length;
    for (const group of [groups[1], groups[3], groups[5]]) {
      group.open = true;
      group.dispatchEvent(new Event('toggle'));
    }
    fixture.detectChanges();
    await fixture.whenStable();
    expect([groups[1], groups[3], groups[5]].every((group) => group.open)).toBe(true);
    expect(list.query()).toEqual(applied);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    expect(repositoryLoader.mock.calls.length).toBe(repositoryRequests);
    expect(fixture.nativeElement.querySelectorAll('app-filter-checkbox-list')).toHaveLength(5);
    expect(
      fixture.nativeElement.querySelector('[formControlName="mniswDisciplineCodes"]'),
    ).toBeNull();
  });

  it('keeps indexing databases in an independent details section without applying edits or reloading the dictionary', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const scores = root.querySelector<HTMLElement>('[data-main-group="mnisw"]')!;
    const databases = root.querySelector<HTMLDetailsElement>('[data-advanced-group="repositories"]')!;
    expect(scores.querySelector('app-journal-non-fee-filters')).toBeNull();
    expect(databases.querySelector('app-journal-non-fee-filters[section="indexation"]')).not.toBeNull();
    expect(root.querySelectorAll('app-journal-non-fee-filters[section="indexation"]')).toHaveLength(1);
    expect(scores.closest('#journal-search-form')).not.toBeNull();
    expect(databases.open).toBe(false);
    const requests = journalService.searchJournals.mock.calls.length;
    const dictionaries = repositoryLoader.mock.calls.length;
    databases.open = true;

    list.filterForm.controls.nonFeeFilters.controls.repositoryIds.setValue(['10']);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(databases.open).toBe(true);
    expect(scores.closest('#journal-search-form')).not.toBeNull();
    expect(list.query().repositoryIds).toBeUndefined();
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    expect(repositoryLoader.mock.calls.length).toBe(dictionaries);
  });

  it('keeps ICV with JML and MNiSW in separate full-width rows without manual range bounds', async () => {
    const fixture = await mountedList();
    const root: HTMLElement = fixture.nativeElement;
    const mnisw = root.querySelector<HTMLElement>('[data-main-group="mnisw"]')!;
    const icv = root.querySelector<HTMLElement>('.journal-search-header [data-testid="journal-header-icv"]')!;
    expect(root.querySelector('[data-advanced-group="icv"]')).toBeNull();
    expect(root.querySelectorAll('#icvFrom, #icvTo, #mniswPointsFrom, #mniswPointsTo')).toHaveLength(0);
    expect(icv.querySelector('[data-testid="jml-disciplines"]')).not.toBeNull();
    expect(icv.parentElement).toBe(mnisw.parentElement);
    expect(icv.parentElement!.classList.contains('row')).toBe(true);
    expect(icv.className).toBe('col-12');
    expect(mnisw.className).toBe('col-12 border-0');
    expect(icv.nextElementSibling).toBe(mnisw);
    const columns = [...icv.querySelector(':scope > .row')!.children];
    expect(columns).toHaveLength(4);
    expect(columns.every(column => column.classList.contains('col-lg-3'))).toBe(true);
    expect(columns[0].querySelector('[data-testid="jml-disciplines"]')).not.toBeNull();
    expect(columns[1].querySelector('#indexedJournals')).not.toBeNull();
    expect(columns[2].matches('fieldset.icv-range')).toBe(true);
    expect(columns[3].classList.contains('flex-column')).toBe(true);
    expect([...columns[3].querySelectorAll('input')].map(input => input.id))
      .toEqual(['notIndexedJournals', 'notEvaluatedJournals']);
    const actions = root.querySelector<HTMLElement>('#journal-search-form .journal-main-actions')!;
    const mniswColumns = [...mnisw.querySelector(':scope > .row')!.children];
    expect(mniswColumns).toHaveLength(4);
    expect(mniswColumns.every(column => column.classList.contains('col-lg-3'))).toBe(true);
    expect(mniswColumns[0].querySelector('#mniswOnly')).not.toBeNull();
    expect(mniswColumns[1].querySelector('[data-testid="mnisw-disciplines"]')).not.toBeNull();
    expect(mniswColumns[2].querySelector('app-journal-range-filter')).not.toBeNull();
    expect(mniswColumns[3]).toBe(actions);
    expect(actions.classList.contains('flex-row')).toBe(true);
    const buttons = [...actions.querySelectorAll('button')];
    expect(buttons.map(button => button.dataset['testid']))
      .toEqual(['journal-search-submit', 'journal-search-clear']);
    expect(buttons[0].type).toBe('submit');
    expect(buttons[1].type).toBe('button');
    expect(buttons.every(button => button.form === root.querySelector('#journal-search-form'))).toBe(true);
    const legends = [...mnisw.querySelectorAll('legend')];
    expect(legends).toHaveLength(2);
    expect(legends.every(legend => legend.classList.contains('visually-hidden'))).toBe(true);
    expect(icv.querySelector('.journal-icv-slider strong')).not.toBeNull();
    expect(icv.querySelectorAll('.noUi-handle')).toHaveLength(2);
    expect(mnisw.querySelectorAll('.noUi-handle')).toHaveLength(2);
    expect(mnisw.querySelector('#mniswOnly')).not.toBeNull();
    expect(mnisw.querySelector('#icvFrom')).toBeNull();
    expect(icv.querySelector('#mniswOnly')).toBeNull();
    expect(icv.querySelector('#indexedJournals')).not.toBeNull();
    expect(icv.querySelector('#notIndexedJournals')).not.toBeNull();
    expect(icv.querySelector('#notEvaluatedJournals')).not.toBeNull();
    expect(mnisw.closest('#journal-search-form')).not.toBeNull();
  });

  it('keeps the ICV slider visible, grey and inert while disabled and reuses it when toggling the filter', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root = fixture.nativeElement as HTMLElement;
    const slider = root.querySelector<HTMLElement>('.icv-range .noUi-target')!;
    const handle = slider.querySelector<HTMLElement>('.noUi-handle-lower')!;
    const toggle = root.querySelector<HTMLInputElement>('#indexedJournals')!;
    const requests = journalService.searchJournals.mock.calls.length;
    expect(slider.hasAttribute('disabled')).toBe(true);
    expect(slider.getAttribute('aria-disabled')).toBe('true');
    expect(getComputedStyle(slider).getPropertyValue('--vz-primary-rgb')).not.toBe('');
    expect(handle.hasAttribute('tabindex')).toBe(false);
    handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(list.filterForm.controls.icvFrom.value).toBe('');
    toggle.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('.icv-range .noUi-target')).toBe(slider);
    expect(slider.hasAttribute('disabled')).toBe(false);
    expect(slider.getAttribute('aria-disabled')).toBe('false');
    handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(list.filterForm.controls.icvFrom.value).toBe('1.01');
    toggle.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(root.querySelector('.icv-range .noUi-target')).toBe(slider);
    expect(slider.hasAttribute('disabled')).toBe(true);
    expect(handle.hasAttribute('tabindex')).toBe(false);
    handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(list.filterForm.controls.icvFrom.value).toBe('');
    expect(list.query().icvFrom).toBeUndefined();
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests);
  });

  it.each([false, true])('focuses invalid ICV in the header without applying or discarding drafts in compact=%s', async (compact) => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.compactFilters.set(compact);
    list.toggleIcvStatus(JournalListQuery.RatingStatusEnum.Indexed);
    list.filterForm.patchValue({ icvFrom: '100', icvTo: '10', publisher: 'Draft publisher' });
    fixture.detectChanges();
    if (compact) {
      list.openFilterPanel();
      fixture.detectChanges();
      await fixture.whenStable();
    }
    const requests = journalService.searchJournals.mock.calls.length;
    const applied = list.query();
    expect(list.applyFilters()).toBe(false);
    fixture.detectChanges();
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(root.querySelector('[data-testid="journal-header-icv"] .icv-range'));
    expect(document.activeElement!.getAttribute('aria-describedby')).toBe('icvRangeError');
    expect(list.filterForm.controls.publisher.value).toBe('Draft publisher');
    expect(list.filterForm.controls.icvFrom.value).toBe('100');
    expect(list.query()).toEqual(applied);
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests);
  });

  it.each([
    ['notIndexedJournals', 'NOT_INDEXED'],
    ['notEvaluatedJournals', 'NOT_EVALUATED'],
  ] as const)(
    'applies %s only on Search and removes its chip immediately without applying unrelated drafts',
    async (id, status) => {
      const fixture = await mountedList();
      const list = fixture.componentInstance;
      const root: HTMLElement = fixture.nativeElement;
      list.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '80', icvTo: '90' });
      list.applyFilters();
      fixture.detectChanges();
      await fixture.whenStable();
      const requests = journalService.searchJournals.mock.calls.length;
      root.querySelector<HTMLInputElement>('#' + id)!.click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(list.filterForm.controls.ratingStatus.value).toBe(status);
      expect(list.filterForm.controls.icvFrom.disabled).toBe(true);
      expect(list.filterForm.controls.icvTo.disabled).toBe(true);
      expect(list.filterForm.controls.icvFrom.value).toBe('');
      expect(list.hasUnappliedChanges()).toBe(true);
      expect(list.query().ratingStatus).toBe('INDEXED');
      expect(list.query().icvFrom).toBe(80);
      expect(list.appliedFilterChips().some((chip) => chip.id === 'icv')).toBe(true);
      expect(journalService.searchJournals.mock.calls.length).toBe(requests);
      list.applyFilters();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(list.query().ratingStatus).toBe(status);
      expect(list.query().icvFrom).toBeUndefined();
      expect(list.query().icvTo).toBeUndefined();
      expect(list.hasUnappliedChanges()).toBe(false);
      expect(list.appliedFilterChips().some((chip) => chip.id === 'icv')).toBe(false);
      const chip = list.appliedFilterChips().find((entry) => entry.id === 'ratingStatus')!;
      expect(chip.labelKey).toBe('page.institution.journalList.' + id);
      const intent = audit.track.mock.calls
        .map(([event]) => event)
        .filter((event) => event.eventType === 'JOURNAL_SEARCH')
        .at(-1);
      expect(intent.properties.criteria.ratingStatus).toBe(status);
      list.filterForm.controls.publisher.setValue('Unapplied publisher');
      list.removeAppliedFilter(chip);
      fixture.detectChanges();
      await fixture.whenStable();
      expect(list.query().ratingStatus).toBeUndefined();
      expect(list.query().publisher).toBeUndefined();
      expect(list.filterForm.controls.publisher.value).toBe('Unapplied publisher');
      expect(list.filterForm.controls.icvFrom.enabled).toBe(false);
      expect(list.appliedFilterChips().some((entry) => entry.id === 'ratingStatus')).toBe(false);
      expect(journalService.searchJournals.mock.calls.length).toBe(requests + 2);
    },
  );

  it('applies the Indexed switch and points on Search and removes the status without applying another draft', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const indexed = root.querySelector<HTMLInputElement>('#indexedJournals')!;
    const requests = journalService.searchJournals.mock.calls.length;
    indexed.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.ratingStatus.value).toBe('INDEXED');
    expect(list.query().ratingStatus).toBeUndefined();
    expect(list.filterForm.controls.icvFrom.enabled).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    list.filterForm.patchValue({ icvFrom: '80', icvTo: '90' });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toMatchObject({ ratingStatus: 'INDEXED', icvFrom: 80, icvTo: 90 });
    expect(list.appliedFilterChips().find((chip) => chip.id === 'ratingStatus')?.labelKey)
      .toBe('page.institution.journalList.indexedJournals');
    expect(list.appliedFilterChips().some((chip) => chip.id === 'icv')).toBe(true);
    expect(audit.track.mock.calls.map(([event]) => event).filter((event) => event.eventType === 'JOURNAL_SEARCH').at(-1)
      .properties.criteria.ratingStatus).toBe('INDEXED');
    list.filterForm.controls.publisher.setValue('Draft publisher');
    list.removeAppliedFilter(list.appliedFilterChips().find((chip) => chip.id === 'ratingStatus')!);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().ratingStatus).toBeUndefined();
    expect(list.query().icvFrom).toBeUndefined();
    expect(list.query().icvTo).toBeUndefined();
    expect(list.filterForm.controls.publisher.value).toBe('Draft publisher');
    expect(list.query().publisher).toBeUndefined();
    expect(indexed.checked).toBe(false);
    expect(list.filterForm.controls.icvFrom.disabled).toBe(true);
    expect(list.hasUnappliedChanges()).toBe(true);
  });

  it('keeps restored ICV statuses mutually exclusive and resets them together with point controls', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const indexed = root.querySelector<HTMLInputElement>('#indexedJournals')!;
    const notIndexed = root.querySelector<HTMLInputElement>('#notIndexedJournals')!;
    const notEvaluated = root.querySelector<HTMLInputElement>('#notEvaluatedJournals')!;
    indexed.click();
    fixture.detectChanges();
    expect(indexed.checked).toBe(true);
    expect(list.filterForm.controls.icvFrom.enabled).toBe(true);
    notIndexed.click();
    fixture.detectChanges();
    expect(indexed.checked).toBe(false);
    expect(notIndexed.checked).toBe(true);
    expect(list.filterForm.controls.icvFrom.enabled).toBe(false);
    notEvaluated.click();
    fixture.detectChanges();
    expect(notIndexed.checked).toBe(false);
    expect(notEvaluated.checked).toBe(true);
    notEvaluated.click();
    fixture.detectChanges();
    expect(notEvaluated.checked).toBe(false);
    expect(list.filterForm.controls.ratingStatus.value).toBe('');
    expect(list.filterForm.controls.icvFrom.enabled).toBe(false);
    notIndexed.click();
    fixture.detectChanges();
    await fixture.whenStable();
    list.applyFilters();
    list.resetFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(notIndexed.checked).toBe(false);
    expect(notEvaluated.checked).toBe(false);
    expect(indexed.checked).toBe(false);
    expect(list.query().ratingStatus).toBeUndefined();
    expect(list.appliedFilterChips()).toHaveLength(0);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(list.filterForm.controls.icvFrom.enabled).toBe(false);
  });

  it('can search non-evaluated journals when there are no numeric ICV bounds in metadata', async () => {
    journalService.getJournalSearchMetadata.mockReturnValue(of({ icvMin: null, icvMax: null }));
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.toggleIcvStatus('NOT_EVALUATED');
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().ratingStatus).toBe('NOT_EVALUATED');
    expect(list.hasIcvRangeError()).toBe(false);
    expect(list.filterForm.controls.icvFrom.disabled).toBe(true);
  });

  it('restores an initial non-indexed query without keeping incompatible numeric bounds', async () => {
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(
      of({
        ratingStatus: JournalListQuery.RatingStatusEnum.NotIndexed,
        icvFrom: 80.25,
        icvTo: 90,
      }),
    );
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    expect(list.query().ratingStatus).toBe('NOT_INDEXED');
    expect(list.query().icvFrom).toBeUndefined();
    expect(list.query().icvTo).toBeUndefined();
    expect(list.filterForm.controls.icvFrom.value).toBe('');
    expect(list.filterForm.controls.icvFrom.disabled).toBe(true);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(
      (fixture.nativeElement.querySelector('#notIndexedJournals') as HTMLInputElement).checked,
    ).toBe(true);
  });

  it.each([false, true])('keeps all main filters visible exactly once outside the sidebar, compact=%s', async (compact) => {
    filterLayout(compact);
    const fixture = await mountedList();
    const root: HTMLElement = fixture.nativeElement;
    const form = root.querySelector('#journal-search-form')!;
    for (const control of ['journalTitle', 'issn', 'publisher', 'mniswOnly']) {
      expect(root.querySelectorAll('#' + control)).toHaveLength(1);
      expect(form.querySelector('#' + control)).not.toBeNull();
      expect(root.querySelector('aside #' + control)).toBeNull();
    }
    for (const control of ['jml-disciplines', 'journal-countries', 'journal-languages', 'mnisw-disciplines']) {
      expect(form.querySelector('[data-testid="' + control + '"]')).not.toBeNull();
    }
    expect(form.querySelectorAll('#icvFrom, #icvTo, #mniswPointsFrom, #mniswPointsTo')).toHaveLength(0);
    expect(form.querySelectorAll('.noUi-handle')).toHaveLength(4);
    expect(root.querySelector('[data-advanced-group="mnisw"], [data-advanced-group="language"]')).toBeNull();
    expect(root.querySelector('form form')).toBeNull();
  });

  it('keeps the main publisher and language edits in the draft until Search', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    const publisher = fixture.nativeElement.querySelector('#journal-search-form #publisher') as HTMLInputElement;
    publisher.value = 'New publisher';
    publisher.dispatchEvent(new Event('input', { bubbles: true }));
    list.setDictionaryCodes('languageCodes', ['en']);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.publisher.value).toBe('New publisher');
    expect(list.query().publisher).toBeUndefined();
    expect(list.query().languageCodes).toEqual(applied.languageCodes);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    fixture.nativeElement
      .querySelector('#journal-search-form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().publisher).toBe('New publisher');
    expect(list.query().languageCodes).toEqual(['en']);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
  });

  it.each(['icv', 'mnisw'] as const)(
    'opens the relocated indexing section on an invalid %s range without searching',
    async (rating) => {
      const fixture = await mountedList();
      const list = fixture.componentInstance;
      const section = fixture.nativeElement.querySelector(rating === 'icv'
        ? '[data-testid="journal-header-icv"]'
        : '[data-main-group="mnisw"]') as HTMLElement;

      const applied = list.query();
      const requests = journalService.searchJournals.mock.calls.length;
      if (rating === 'icv') list.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '20', icvTo: '10' });
      else {
        list.filterForm.controls.mniswOnly.setValue(true);
        list.onMniswToggle();
        list.filterForm.patchValue({ mniswPointsFrom: '50' });
      }
      list.applyFilters();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(section.closest('#journal-search-form')).not.toBeNull();
      expect(section.querySelector('[role="alert"]')).not.toBeNull();
      expect(list.query()).toEqual(applied);
      expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    },
  );

  it('focuses invalid main MNiSW controls without opening the mobile filter panel', async () => {
    filterLayout(true);
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const applied = list.query();
    list.filterForm.controls.mniswOnly.setValue(true);
    list.onMniswToggle();
    list.filterForm.controls.mniswPointsFrom.setValue('50');
    expect(list.applyFilters()).toBe(false);
    fixture.detectChanges();
    await fixture.whenStable();
    const main = root.querySelector('[data-main-group="mnisw"]')!;
    expect(root.querySelector('ngb-offcanvas-panel')).toBeNull();
    expect(main.contains(document.activeElement)).toBe(true);
    expect(list.query()).toEqual(applied);
  });

  it('clears applied filters through the new action next to the chips', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.filterForm.controls.publisher.setValue('Applied publisher');
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const requests = journalService.searchJournals.mock.calls.length;
    const clear = fixture.nativeElement.querySelector(
      'app-journal-applied-filters button.btn-link',
    ) as HTMLButtonElement;
    expect(clear.type).toBe('button');
    clear.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.publisher.value).toBe('');
    expect(list.query().publisher).toBeUndefined();
    expect(list.appliedFilterChips()).toHaveLength(0);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
  });

  it('keeps draft JML selections in the header and shows them above results only after Search', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const requests = journalService.searchJournals.mock.calls.length;
    const selector = fixture.debugElement.query(By.css('[data-testid="jml-disciplines"]'));
    const component = selector.componentInstance as MultiValueSelect;
    const toggle = selector.nativeElement.querySelector('.multi-value-toggle') as HTMLButtonElement;
    toggle.click();
    fixture.detectChanges();
    expect(component.options().some((option) => ['1', '2', '4'].includes(option.code))).toBe(false);
    expect(selector.nativeElement.querySelector('.multi-value-options').textContent).toContain('(0)');
    for (const code of ['3', '5']) {
      const index = component.filteredOptions().findIndex((option) => option.code === code);
      (selector.nativeElement.querySelectorAll('input[type="checkbox"]')[index] as HTMLInputElement).click();
      fixture.detectChanges();
      await fixture.whenStable();
    }
    expect(list.filterForm.controls.disciplineCodes.value).toEqual(['3', '5']);
    expect(toggle.textContent).toContain('(2)');
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="jml-disciplines"] ul')).toBeNull();
    expect(root.querySelector('[data-testid="pending-jml-disciplines"]')).toBeNull();
    expect(root.querySelector('.journal-search-content')!.textContent).not.toContain('Matematyka');
    expect(root.querySelector('.journal-search-content')!.textContent).not.toContain('Ekonomia');
    expect(root.querySelectorAll('app-journal-applied-filters [data-filter-id^="disciplineCodes:"]')).toHaveLength(0);
    expect(list.filterForm.controls.disciplineCodes.value).toEqual(['3', '5']);
    expect(list.query().disciplineCodes).toBeUndefined();
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    root.querySelector<HTMLFormElement>('#journal-search-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().disciplineCodes).toEqual(['3', '5']);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    expect(root.querySelector('[data-testid="pending-jml-disciplines"]')).toBeNull();
    expect(root.querySelectorAll('.journal-search-content [data-filter-id="disciplineCodes:3"]')).toHaveLength(1);
    expect(root.querySelectorAll('.journal-search-content [data-filter-id="disciplineCodes:5"]')).toHaveLength(1);
    expect(root.querySelector('[data-testid="jml-disciplines"] ul')).toBeNull();
  });

  it('edits a cooperation slider only in the draft, applies it on Search and clears handles with the form', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const requests = journalService.searchJournals.mock.calls.length;
    const handle = fixture.nativeElement.querySelector('[data-advanced-group="cooperation"] .noUi-handle-lower') as HTMLElement;
    handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.authorForeignAffiliationPercentageFrom.value).toBe('0.01');
    expect(list.filterForm.controls.authorForeignAffiliationPercentageTo.value).toBe('');
    expect(list.query().authorForeignAffiliationPercentageFrom).toBeUndefined();
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().authorForeignAffiliationPercentageFrom).toBe(0.01);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    list.resetFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.authorForeignAffiliationPercentageFrom.value).toBe('');
    expect(handle.getAttribute('aria-valuenow')).toBe('0');
    expect(list.hasUnappliedChanges()).toBe(false);
  });

  it('enables the point slider with MNiSW, steps only over real classes and clears it without applying when unchecked', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const checkbox = fixture.nativeElement.querySelector('#mniswOnly') as HTMLInputElement;
    checkbox.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const range = fixture.nativeElement.querySelector('[data-main-group="mnisw"] app-journal-range-filter') as HTMLElement;
    const handle = range.querySelector('.noUi-handle-lower') as HTMLElement;
    for (let count = 0; count < 2; count++) handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.mniswPointsFrom.value).toBe('70');
    expect(list.filterForm.controls.mniswPointsTo.value).toBe('');
    expect(list.query().mniswOnly).toBeUndefined();
    list.applyFilters();
    const applied = list.query();
    expect(applied.mniswPointsFrom).toBe(70);
    checkbox.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.mniswPointsFrom.value).toBe('');
    expect(list.filterForm.controls.mniswPointsFrom.disabled).toBe(true);
    expect(list.query()).toEqual(applied);
    expect(range.querySelector('.noUi-target')!.hasAttribute('disabled')).toBe(true);
  });

  it.each([
    ['fees', { feeFilters: { publicationFeeMaxPln: '-1' } }],
    ['process', { nonFeeFilters: { firstIssueYearFrom: '2020', firstIssueYearTo: '2000' } }],
    ['cooperation', { authorForeignAffiliationPercentageFrom: '101' }],
    ['processing', { reviewDaysTo: '-1' }],
  ] as const)('reveals validation in the relevant collapsed %s group without changing applied filters', async (group, values) => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const requests = journalService.searchJournals.mock.calls.length;
    const applied = list.query();
    list.filterForm.patchValue(values);
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const section = fixture.nativeElement.querySelector(`[data-advanced-group="${group}"]`) as HTMLDetailsElement;
    expect(section.open).toBe(true);
    expect(section.querySelector('[role="alert"]')).toBeTruthy();
    expect(list.query()).toEqual(applied);
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
  });

  it('removes an applied chip and immediately refreshes page one without a pending message', async () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    const initialRequests = journalService.searchJournals.mock.calls.length;
    list.filterForm.patchValue({ countryCodes: ['pl', 'de'], languageCodes: ['en'], journalTitle: 'Applied title' });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(list.appliedFilterChips()).toEqual([]);
    expect(journalService.searchJournals.mock.calls.length).toBe(initialRequests);
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    list.page.set(2);
    fixture.detectChanges();
    await fixture.whenStable();
    const afterPaging = journalService.searchJournals.mock.calls.length;
    const button = fixture.nativeElement.querySelector('[data-filter-id="countryCodes:pl"] button') as HTMLButtonElement;
    expect(button).toBeTruthy();
    button.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(list.filterForm.controls.countryCodes.value).toEqual(['de']);
    expect(list.query()).toEqual({ ...applied, countryCodes: ['de'], page: 0, first: 0 });
    expect(list.appliedFilterChips().some((chip) => chip.id === 'countryCodes:pl')).toBe(false);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-journal-applied-filters [role=status]')).toBeNull();
    expect(journalService.searchJournals.mock.calls.length).toBe(afterPaging + 1);
    expect(afterPaging).toBe(requests + 1);
    expect(list.query().countryCodes).toEqual(['de']);
    expect(list.page()).toBe(0);
    expect(list.appliedFilterChips().some((chip) => chip.id === 'countryCodes:pl')).toBe(false);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(journalService.searchJournals.mock.calls.length).toBe(afterPaging + 1);
  });

  it('refreshes only applied criteria while retaining unrelated invalid drafts and ignoring repeated stale chip clicks', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.filterForm.patchValue({ journalTitle: 'Applied title', countryCodes: ['pl', 'de'],
      reviewDaysTo: '10', feeFilters: { publicationFeeMaxPln: '100' },
      sortBy: JournalListQuery.SortByEnum.SortByTitleDesc });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const chip = list.appliedFilterChips().find((entry) => entry.id === 'countryCodes:pl')!;
    list.filterForm.patchValue({ journalTitle: 'Unapplied title', countryCodes: ['de', 'fr'],
      reviewDaysTo: '-1', feeFilters: { publicationFeeMaxPln: '-2' } });
    const draft = list.filterForm.getRawValue();
    const requests = journalService.searchJournals.mock.calls.length;
    const searches = audit.track.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_SEARCH').length;
    list.removeAppliedFilter(chip);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toMatchObject({ journalTitle: 'Applied title', countryCodes: ['de'],
      reviewDaysTo: 10, publicationFeeMaxPln: 100, sortBy: JournalListQuery.SortByEnum.SortByTitleDesc, page: 0 });
    expect(list.filterForm.getRawValue()).toEqual(draft);
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    const clears = audit.track.mock.calls.map(([event]) => event).filter((event) => event.eventType === 'JOURNAL_FILTERS_CLEAR');
    expect(clears).toHaveLength(1);
    expect(clears[0].properties.criteria).toMatchObject({ journalTitle: 'Applied title', countryCodes: ['de'], reviewDaysTo: 10, publicationFeeMaxPln: 100 });
    expect(audit.track.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_SEARCH')).toHaveLength(searches);
    list.removeAppliedFilter(chip);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    expect(audit.track.mock.calls.filter(([event]) => event.eventType === 'JOURNAL_FILTERS_CLEAR')).toHaveLength(1);
  });

  it('clears a removed fee validation error and refreshes without requiring a valid replacement draft', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.setValue('100');
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.setValue('-1');
    list.applyFilters();
    expect(list.hasFeeFilterError()).toBe(true);
    const requests = journalService.searchJournals.mock.calls.length;
    list.removeAppliedFilter(list.appliedFilterChips().find((entry) => entry.id === 'publicationFeeFreeOnly')!);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().publicationFeeMaxPln).toBeUndefined();
    expect(list.hasFeeFilterError()).toBe(false);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-journal-applied-filters [role=status]')).toBeNull();
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
  });

  it('compares reordered selections and equivalent decimal formats semantically and ignores local searches and sort', async () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', countryCodes: ['pl', 'de'], languageCodes: ['en', 'pl'], disciplineCodes: ['3', '5'], icvFrom: '80.25',
      nonFeeFilters: { repositoryIds: ['10', '20'], licenseCodes: ['CC_BY', 'CC_BY_NC'], accessModels: ['FREE_ARTICLES', 'PAID_ARTICLES'], frequencyCodes: ['m', 'q'] },
      feeFilters: { publicationFeeMaxPln: '100.50' } });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasUnappliedChanges()).toBe(false);
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', countryCodes: ['de', 'pl'], languageCodes: ['pl', 'en'], disciplineCodes: ['5', '3'], icvFrom: '080,25',
      nonFeeFilters: { repositoryIds: ['20', '10'], licenseCodes: ['CC_BY_NC', 'CC_BY'], accessModels: ['PAID_ARTICLES', 'FREE_ARTICLES'], frequencyCodes: ['q', 'm'] },
      feeFilters: { publicationFeeMaxPln: '100,500' } });
    list.disciplineSelectOptions();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(list.query()).toEqual(applied);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    list.filterForm.controls.sortBy.setValue(JournalListQuery.SortByEnum.SortByTitleDesc);
    list.changeSort();
    expect(list.hasUnappliedChanges()).toBe(false);
    list.filterForm.controls.countryCodes.setValue(['de']);
    expect(list.hasUnappliedChanges()).toBe(true);
    list.filterForm.controls.countryCodes.setValue(['de', 'pl']);
    expect(list.hasUnappliedChanges()).toBe(false);
  });

  it('omits full ICV, percentage and MNiSW point ranges from the request and chip bar without marking their raw values pending', () => {
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '1', icvTo: '300', mniswPointsFrom: '20', mniswPointsTo: '200',
      rejectionRateFrom: '0', rejectionRateTo: '100', authorForeignAffiliationPercentageFrom: '0', authorForeignAffiliationPercentageTo: '100',
      reviewerForeignAffiliationPercentageFrom: '0', reviewerForeignAffiliationPercentageTo: '100' });
    component.applyFilters();
    expect(component.query().mniswOnly).toBe(true);
    for (const field of ['icvFrom', 'icvTo', 'mniswPointsFrom', 'mniswPointsTo', 'rejectionRateFrom', 'rejectionRateTo',
      'authorForeignAffiliationPercentageFrom', 'authorForeignAffiliationPercentageTo', 'reviewerForeignAffiliationPercentageFrom', 'reviewerForeignAffiliationPercentageTo'] as const) {
      expect(component.query()[field]).toBeUndefined();
    }
    expect(component.appliedFilterChips().map((chip) => chip.id)).toEqual(['ratingStatus', 'mniswOnly']);
    expect(component.hasUnappliedChanges()).toBe(false);
    expect(component.filterForm.controls.rejectionRateTo.value).toBe('100');
    expect(component.filterForm.controls.icvTo.value).toBe('300');
  });

  it('immediately removes dependent MNiSW points or membership without changing independent JML disciplines', () => {
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.filterForm.patchValue({ mniswPointsFrom: '40', mniswPointsTo: '70' });
    component.setDisciplineCodes(['3']);
    component.applyFilters();
    const applied = component.query();
    component.removeAppliedFilter(component.appliedFilterChips().find((chip) => chip.id === 'mniswPoints')!);
    expect(component.filterForm.controls.mniswOnly.value).toBe(true);
    expect(component.filterForm.controls.mniswPointsFrom.value).toBe('');
    expect(component.filterForm.controls.mniswPointsTo.value).toBe('');
    expect(component.filterForm.controls.mniswPointsFrom.enabled).toBe(true);
    expect(component.query()).toEqual({ ...applied, mniswPointsFrom: undefined, mniswPointsTo: undefined });
    expect(component.hasUnappliedChanges()).toBe(false);
    const withoutPoints = component.query();
    component.filterForm.patchValue({ mniswPointsFrom: '40', mniswPointsTo: '70' });
    expect(component.hasUnappliedChanges()).toBe(true);
    expect(component.query()).toEqual(withoutPoints);
    component.removeAppliedFilter(component.appliedFilterChips().find((chip) => chip.id === 'mniswOnly')!);
    expect(component.filterForm.controls.mniswOnly.value).toBe(false);
    expect(component.filterForm.controls.mniswPointsFrom.value).toBe('');
    expect(component.filterForm.controls.mniswPointsTo.value).toBe('');
    expect(component.filterForm.controls.mniswPointsFrom.disabled).toBe(true);
    expect(component.filterForm.controls.mniswPointsTo.disabled).toBe(true);
    expect(component.filterForm.controls.disciplineCodes.value).toEqual(['3']);
    expect(component.query().mniswOnly).toBeUndefined();
    expect(component.query().mniswPointsFrom).toBeUndefined();
    expect(component.query().mniswPointsTo).toBeUndefined();
    expect(component.query().disciplineCodes).toEqual(['3']);
    expect(component.hasUnappliedChanges()).toBe(false);
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    expect(component.filterForm.controls.mniswPointsFrom.enabled).toBe(true);
    expect(component.filterForm.controls.mniswPointsFrom.value).toBe('');
    expect(component.filterForm.controls.mniswPointsTo.value).toBe('');
  });

  it('immediately refreshes each implemented chip removal without a pending message', async () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    list.filterForm.controls.mniswOnly.setValue(true);
    list.onMniswToggle();
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', journalTitle: 'Title', publisher: 'Publisher', issn: '1234', countryCodes: ['pl'], languageCodes: ['en'], disciplineCodes: ['3'],
      icvFrom: '80', mniswPointsFrom: '40', mniswPointsTo: '70', reviewDaysTo: '0', initialAssessmentDaysTo: '5', publicationDaysTo: '10',
      rejectionRateFrom: '20', authorForeignAffiliationPercentageTo: '50', reviewerForeignAffiliationPercentageFrom: '20',
      nonFeeFilters: { doiOnly: true, editorialSystemOnly: true, newsletterOnly: true, socialMediaOnly: true, antiPlagiarismOnly: true, aopOnly: true,
        repositoryIds: ['10'], licenseCodes: ['CC_BY'], accessModels: ['FREE_ARTICLES'], frequencyCodes: ['q'], firstIssueYearFrom: '2000' },
      feeFilters: { otherFeesFreeOnly: true, openAccessFeeFreeOnly: true, publicationFeeMaxPln: '0', rapidReviewFeeFreeOnly: true, submissionFeeMaxPln: '50' } });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const requests = journalService.searchJournals.mock.calls.length;
    const chips = [...list.appliedFilterChips()].sort(
      (first, second) => Number(['mniswOnly', 'ratingStatus'].includes(first.id)) - Number(['mniswOnly', 'ratingStatus'].includes(second.id)),
    );
    expect(chips).toHaveLength(32);
    for (const [index, chip] of chips.entries()) {
      list.removeAppliedFilter(chip);
      fixture.detectChanges();
      await fixture.whenStable();
      expect(list.appliedFilterChips().some((entry) => entry.id === chip.id)).toBe(false);
      expect(list.hasUnappliedChanges()).toBe(false);
      expect(fixture.nativeElement.querySelector('app-journal-applied-filters [role=status]')).toBeNull();
      expect(journalService.searchJournals.mock.calls.length).toBe(requests + index + 1);
    }
    expect(list.filterForm.controls.feeFilters.controls.openAccessFeeMaxPln.enabled).toBe(true);
    expect(list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.value).toBe('');
    expect(list.appliedFilterChips()).toEqual([]);
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(list.query()).toMatchObject({ countryCodes: [], languageCodes: [] });
    expect(list.query().mniswOnly).toBeUndefined();
    expect(list.query().publicationFeeMaxPln).toBeUndefined();
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + chips.length);
  });

  it('keeps the pending message and applied chips after invalid Search and resets both with immediate clear', async () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    list.filterForm.patchValue({ countryCodes: ['pl'], feeFilters: { publicationFeeMaxPln: '10' } });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.setValue('-1');
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasFeeFilterError()).toBe(true);
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(list.query()).toEqual(applied);
    expect(list.appliedFilterChips()).toHaveLength(2);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    list.resetFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasUnappliedChanges()).toBe(false);
    expect(list.appliedFilterChips()).toEqual([]);
    expect(list.query().countryCodes).toEqual([]);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
  });

  it('resolves delayed repository chip labels and preserves pending drafts through metadata error and retry without searching', async () => {
    const repositories = new Subject<import('../../../core/backend').RepositoryDictionaryView[]>();
    repositoryLoader.mockReturnValue(repositories);
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(of({ repositoryIds: [10], mniswOnly: true, mniswPointsFrom: 70 }));
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    const repositoryRequests = repositoryLoader.mock.calls.length;
    expect(list.filterChipLabels().repositoryIds?.['10']).toBeUndefined();
    expect(list.appliedFilterChips().some((chip) => chip.id === 'repositoryIds:10')).toBe(true);
    repositories.next([{ id: 10, name: 'Database A' }]);
    repositories.complete();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterChipLabels().repositoryIds?.['10']).toBe('Database A');
    expect(list.hasUnappliedChanges()).toBe(false);
    list.filterForm.controls.mniswPointsFrom.setValue('40');
    journalService.getJournalSearchMetadata.mockReturnValue(throwError(() => new Error('metadata offline')));
    list.loadSearchMetadata();
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(list.filterForm.controls.mniswPointsFrom.value).toBe('40');
    expect(list.query()).toEqual(applied);
    journalService.getJournalSearchMetadata.mockReturnValue(of({ icvMin: 0, icvMax: 300, mniswPointClasses: [20, 40, 70, 200] }));
    list.loadSearchMetadata();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(list.filterForm.controls.mniswPointsFrom.value).toBe('40');
    expect(list.query()).toEqual(applied);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    expect(repositoryLoader.mock.calls.length).toBe(repositoryRequests);
  });

  it('never reinterprets applied chip ranges after metadata changes until another Search', async () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '80', icvTo: '90' });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const chips = list.appliedFilterChips();
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    journalService.getJournalSearchMetadata.mockReturnValue(of({ icvMin: 80, icvMax: 90, mniswPointClasses: [20, 70] }));
    list.loadSearchMetadata();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.appliedFilterChips()).toBe(chips);
    expect(list.appliedFilterChips().find((chip) => chip.id === 'icv')).toMatchObject({ minimum: '80', maximum: '90' });
    expect(list.query()).toEqual(applied);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    list.applyFilters();
    expect(list.appliedFilterChips().map((chip) => chip.id)).toEqual(['ratingStatus']);
    expect(list.query().ratingStatus).toBe('INDEXED');
    expect(list.query().icvFrom).toBeUndefined();
    expect(list.hasUnappliedChanges()).toBe(false);
  });

  it('restores a supported page size and keeps applied filters and invalid drafts when selecting a page', async () => {
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(of({
      page: 2, size: 25, journalTitle: 'Applied title', publicationFeeMaxPln: 100,
    }));
    journalService.searchJournals.mockReturnValue(of({ data: [{ id: 1 }], totalElements: 12037, totalPages: 482, size: 25 }));
    const fixture = TestBed.createComponent(JournalList);
    fixture.detectChanges();
    await fixture.whenStable();
    const list = fixture.componentInstance;
    expect(list.query()).toMatchObject({ page: 2, size: 25, first: 50 });
    expect((fixture.nativeElement.querySelector('#journal-page-size') as HTMLSelectElement).value).toBe('25');
    list.filterForm.patchValue({ journalTitle: 'Draft title', feeFilters: { publicationFeeMaxPln: '-1' } });
    list.goToPage(401);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toMatchObject({ page: 401, size: 25, first: 10025, journalTitle: 'Applied title', publicationFeeMaxPln: 100 });
    expect(list.filterForm.controls.journalTitle.value).toBe('Draft title');
    expect(list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.value).toBe('-1');
    expect(journalService.searchJournals.mock.lastCall?.[0]).toMatchObject({ page: 401, size: 25 });
    list.changePageSizeValue(50);
    expect(list.query().size).toBe(50);
    expect(list.page()).toBe(0);
  });

  it.each(['pl', 'en'])('changes the visible page size without applying drafts and updates the result summary in %s', async (language) => {
    TestBed.inject(TranslocoService).setActiveLang(language);
    journalService.searchJournals.mockImplementation((query: JournalListQuery) => of({
      data: [{ id: 1 }], totalElements: 1199, totalPages: Math.ceil(1199 / query.size!), size: query.size,
    }));
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.goToPage(119);
    fixture.detectChanges();
    await fixture.whenStable();
    list.filterForm.patchValue({ journalTitle: 'Unapplied draft', feeFilters: { publicationFeeMaxPln: '-1' } });
    for (const size of [25, 50, 100, 10]) {
      const select = fixture.nativeElement.querySelector('#journal-page-size') as HTMLSelectElement;
      select.value = String(size);
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      await fixture.whenStable();
      expect(list.query()).toMatchObject({ page: 0, first: 0, size });
      expect((fixture.nativeElement.querySelector('#journal-page-size') as HTMLSelectElement).value).toBe(String(size));
      expect(list.query().journalTitle).toBeUndefined();
      expect(list.filterForm.controls.journalTitle.value).toBe('Unapplied draft');
      expect(list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.value).toBe('-1');
      const summary = fixture.nativeElement.querySelector('app-journal-pagination [aria-live="polite"]') as HTMLElement;
      expect(summary.textContent?.trim()).toBe(language === 'en'
        ? `Page 1 of ${Math.ceil(1199 / size)}. Results: 1199.`
        : `Strona 1 z ${Math.ceil(1199 / size)}. Wyników: 1199.`);
    }
    for (const size of [0, 15, 101, Number.NaN]) list.changePageSizeValue(size);
    expect(list.query().size).toBe(10);
  });

  it('connects numbered pagination to the search query and ignores invalid page selections', async () => {
    journalService.searchJournals.mockReturnValue(of({ data: [{ id: 1 }], totalElements: 27, totalPages: 3, size: 10 }));
    const fixture = TestBed.createComponent(JournalList);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const list = fixture.componentInstance;
    const buttons = [...fixture.nativeElement.querySelectorAll('app-journal-pagination button')] as HTMLButtonElement[];
    buttons.find((button) => button.textContent?.trim() === '3')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toMatchObject({ page: 2, size: 10, first: 20 });
    for (const invalid of [-1, 3, 1.5, Number.NaN]) {
      list.goToPage(invalid);
      expect(list.page()).toBe(2);
    }
    list.filterForm.controls.journalTitle.setValue('New title');
    list.applyFilters();
    expect(list.page()).toBe(0);
    expect(fixture.nativeElement.querySelector('app-list-paging')).toBeNull();
  });

  it('applies independent time limits and partial decimal percentages only on Search', () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    list.filterForm.patchValue({
      reviewDaysTo: '0',
      initialAssessmentDaysTo: '5',
      publicationDaysTo: '60',
      rejectionRateFrom: '33,33333',
      authorForeignAffiliationPercentageTo: '25,5',
      reviewerForeignAffiliationPercentageFrom: '20.25',
      reviewerForeignAffiliationPercentageTo: '20.25',
    });
    expect(list.query().reviewDaysTo).toBeUndefined();
    expect(list.query().rejectionRateFrom).toBeUndefined();

    list.applyFilters();

    expect(list.hasSurveyRangeError()).toBe(false);
    expect(list.query()).toMatchObject({
      reviewDaysTo: 0,
      initialAssessmentDaysTo: 5,
      publicationDaysTo: 60,
      rejectionRateFrom: 33.33333,
      authorForeignAffiliationPercentageTo: 25.5,
      reviewerForeignAffiliationPercentageFrom: 20.25,
      reviewerForeignAffiliationPercentageTo: 20.25,
    });
    expect(list.query().rejectionRateTo).toBeUndefined();
    expect(list.query().authorForeignAffiliationPercentageFrom).toBeUndefined();
  });

  it('renders Min and Max day inputs for each processing criterion and applies them only on Search', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root = fixture.nativeElement as HTMLElement;
    const section = root.querySelector('[data-advanced-group="processing"]')!;
    const requests = journalService.searchJournals.mock.calls.length;
    const ranges = {
      initialAssessmentDaysFrom: '2', initialAssessmentDaysTo: '5',
      reviewDaysFrom: '0', reviewDaysTo: '0',
      publicationDaysFrom: '30', publicationDaysTo: '60',
    };
    expect(section.querySelectorAll('fieldset')).toHaveLength(3);
    expect([...section.querySelectorAll('input')].map((input) => input.id)).toEqual(Object.keys(ranges));
    for (const [name, value] of Object.entries(ranges)) {
      expect(root.querySelectorAll('#' + name)).toHaveLength(1);
      expect(section.querySelector('label[for="' + name + '"]')).not.toBeNull();
      const input = section.querySelector<HTMLInputElement>('#' + name)!;
      expect(input.inputMode).toBe('numeric');
      expect(input.closest('.input-group')?.querySelector('.input-group-text')).not.toBeNull();
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().reviewDaysFrom).toBeUndefined();
    expect(list.query().initialAssessmentDaysFrom).toBeUndefined();
    expect(list.query().publicationDaysFrom).toBeUndefined();
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests);
    root.querySelector<HTMLFormElement>('#journal-search-form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toMatchObject({
      initialAssessmentDaysFrom: 2, initialAssessmentDaysTo: 5,
      reviewDaysFrom: 0, reviewDaysTo: 0,
      publicationDaysFrom: 30, publicationDaysTo: 60,
    });
    expect(list.hasSurveyRangeError()).toBe(false);
    expect(journalService.searchJournals).toHaveBeenCalledTimes(requests + 1);
  });

  it.each([
    { reviewDaysFrom: '-1' },
    { reviewDaysFrom: '1.5' },
    { initialAssessmentDaysFrom: '2147483648' },
    { publicationDaysFrom: 'NaN' },
    { publicationDaysFrom: '1e2' },
    { reviewDaysFrom: '11', reviewDaysTo: '10' },
    { initialAssessmentDaysFrom: '6', initialAssessmentDaysTo: '5' },
    { publicationDaysFrom: '61', publicationDaysTo: '60' },
    { reviewDaysTo: '-1' },
    { reviewDaysTo: '1.5' },
    { initialAssessmentDaysTo: '2147483648' },
    { publicationDaysTo: 'NaN' },
    { publicationDaysTo: '1e2' },
    { rejectionRateFrom: '-0.1' },
    { rejectionRateTo: '100.01' },
    { rejectionRateFrom: '60', rejectionRateTo: '50' },
    { authorForeignAffiliationPercentageFrom: '30', authorForeignAffiliationPercentageTo: '20' },
    { reviewerForeignAffiliationPercentageTo: 'bad' },
  ])('does not apply invalid annual ranges: %j', (invalid) => {
    const list = TestBed.createComponent(JournalList).componentInstance;
    list.filterForm.patchValue({ publicationDaysTo: '60' });
    list.applyFilters();
    const applied = list.query();

    list.filterForm.patchValue(invalid);
    list.applyFilters();

    expect(list.hasSurveyRangeError()).toBe(true);
    expect(list.query()).toEqual(applied);
    list.resetFilters();
    expect(list.hasSurveyRangeError()).toBe(false);
    expect(list.query().publicationDaysTo).toBeUndefined();
  });

  it('restores partial annual filters and clears every annual field', () => {
    const state = TestBed.inject(JournalListQueryStateService);
    vi.mocked(state.getInitialQuery).mockReturnValue(of({
      reviewDaysFrom: 0,
      reviewDaysTo: 0,
      initialAssessmentDaysFrom: 5,
      publicationDaysFrom: 30,
      publicationDaysTo: 60,
      rejectionRateTo: 50,
      authorForeignAffiliationPercentageFrom: 20.25,
      reviewerForeignAffiliationPercentageTo: 25,
    }));
    const list = TestBed.createComponent(JournalList).componentInstance;
    expect(list.filterForm.controls.reviewDaysTo.value).toBe('0');
    expect(list.filterForm.controls.reviewDaysFrom.value).toBe('0');
    expect(list.filterForm.controls.initialAssessmentDaysFrom.value).toBe('5');
    expect(list.filterForm.controls.initialAssessmentDaysTo.value).toBe('');
    expect(list.filterForm.controls.publicationDaysFrom.value).toBe('30');
    expect(list.query().initialAssessmentDaysFrom).toBe(5);
    expect(list.query().initialAssessmentDaysTo).toBeUndefined();
    expect(list.filterForm.controls.rejectionRateFrom.value).toBe('');
    expect(list.query().rejectionRateTo).toBe(50);
    expect(list.query().authorForeignAffiliationPercentageFrom).toBe(20.25);

    list.resetFilters();

    expect(list.filterForm.controls.rejectionRateTo.value).toBe('');
    expect(list.query().reviewDaysTo).toBeUndefined();
    for (const name of ['reviewDaysFrom', 'initialAssessmentDaysFrom', 'publicationDaysFrom'] as const) {
      expect(list.filterForm.controls[name].value).toBe('');
      expect(list.query()[name]).toBeUndefined();
    }
    expect(list.query().authorForeignAffiliationPercentageFrom).toBeUndefined();
    expect(list.query().reviewerForeignAffiliationPercentageTo).toBeUndefined();
  });

  it('renders annual filter inputs without applying drafts', async () => {
    const fixture = TestBed.createComponent(JournalList);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const service = TestBed.inject(JournalService);
    const requests = vi.mocked(service.searchJournals).mock.calls.length;
    const input = fixture.nativeElement.querySelector('#rejectionRateFrom') as HTMLInputElement;
    expect(input).toBeTruthy();
    input.value = '25,5';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(vi.mocked(service.searchJournals).mock.calls.length).toBe(requests);
    expect(fixture.componentInstance.query().rejectionRateFrom).toBeUndefined();
    fixture.componentInstance.applyFilters();
    expect(fixture.componentInstance.query().rejectionRateFrom).toBe(25.5);
  });

  beforeEach(async () => {
    audit = { track: vi.fn(), captureView: vi.fn(() => viewContext) };
    vi.stubGlobal('URL', Object.assign(class extends URL {}, {
      createObjectURL: vi.fn(() => 'blob:report-test'),
      revokeObjectURL: vi.fn(),
    }));
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    questionnaireReportsService = {
      questionnaireReportAvailability: vi.fn(() => of({ pl: true, en: false })),
      downloadQuestionnaireReport2024: vi.fn(() => EMPTY),
    };
    journalService = {
      getJournalSearchCounts: vi.fn(() => of({
        countryCounts: { pl: 2, de: 1 }, languageCounts: { en: 3, pl: 1 },
        disciplineCounts: { '3': 2, '5': 1, '6': 0 },
        mniswDisciplineCounts: { '2021': 2, '2061': 1 },
        repositoryCounts: { '10': 2, '20': 1 }, licenseCounts: { CC_BY: 2, CC_BY_NC: 1 },
        accessModelCounts: { FREE_ARTICLES: 1, PAID_ARTICLES: 2 }, frequencyCounts: { q: 1, m: 2 },
        featureCounts: { doiOnly: 2, editorialSystemOnly: 1, newsletterOnly: 1, socialMediaOnly: 2, antiPlagiarismOnly: 1, aopOnly: 1 },
      })),
      searchJournals: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
      getJournalSearchMetadata: vi.fn(() => of({
        mniswPointClasses: [20, 40, 70, 100, 140, 200], icvMin: 1, icvMax: 300,
        countryCounts: { pl: 2, de: 1 }, languageCounts: { en: 3, pl: 1 },
        disciplineCounts: { '3': 2, '5': 1, '6': 0 },
        mniswDisciplineCounts: { '2021': 2, '2061': 1 },
        repositoryCounts: { '10': 2, '20': 1 }, licenseCounts: { CC_BY: 2, CC_BY_NC: 1 },
        accessModelCounts: { FREE_ARTICLES: 1, PAID_ARTICLES: 2 }, frequencyCounts: { q: 1, m: 2 },
        featureCounts: { doiOnly: 2, editorialSystemOnly: 1, newsletterOnly: 1, socialMediaOnly: 2, antiPlagiarismOnly: 1, aopOnly: 1 },
      })),
    };
    repositoryLoader = vi.fn(() => of([{ id: 10, name: 'Database A' }, { id: 20, name: 'Database B' }]));
    countryLoader = vi.fn(() => of([]));
    languageLoader = vi.fn(() => of([]));
    disciplineLoader = vi.fn(() => of(DISCIPLINES));

    await TestBed.configureTestingModule({
      imports: [JournalList],
      providers: [
        provideRouter([]),
        { provide: ViewportScroller, useValue: { getScrollPosition: vi.fn(() => [0, 0]), scrollToPosition: vi.fn() } },
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            reRenderOnLangChange: true,
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        {
          provide: JournalService,
          useValue: journalService,
        },
        {
          provide: ConfigurationService,
          useValue: {
            ratingYears: vi.fn(() => of({})),
            getJournalLinks: vi.fn(() => of({ issuesUrlTemplate: 'https://journals.example.test/issues/{journalId}' })),
            getSearchYears: vi.fn(() => of({ defaultYear: 2024, availableYears: [2024] })),
          },
        },
        {
          provide: DictionaryService,
          useValue: {
            findCountries: countryLoader,
            findRepositories: repositoryLoader,
            findLanguages: languageLoader,
            findScientificCategories: disciplineLoader,
            findMniswDisciplines: vi.fn(() => of([
              { code: '2021', namePl: 'Automatyka', nameEn: 'Automation', nameRu: 'Автоматика' },
              { code: '2061', namePl: 'Inżynieria lądowa', nameEn: 'Civil engineering' },
            ])),
          },
        },
        {
          provide: QuestionnaireReportsService,
          useValue: questionnaireReportsService,
        },
        {
          provide: JournalListQueryStateService,
          useValue: {
            surveyYear: signal<number | undefined>(undefined),
            selectSurveyYear: vi.fn((year: number) => TestBed.inject(JournalListQueryStateService).surveyYear.set(year)),
            getInitialQuery: vi.fn(() =>
              of({
                page: 0,
                size: 10,
                first: 0,
                sortBy: JournalListQuery.SortByEnum.SortByTitleAsc,
              }),
            ),
            freshEntry$: EMPTY,
              restoreFromDetails: vi.fn(() => undefined),
              restoreView: vi.fn(() => undefined),
              rememberView: vi.fn(),
              getContextVersion: vi.fn(() => 0),
            createDetailsNavigationState: vi.fn(() => ({ journalListOrigin: 'test-origin' })),
            rememberForDetails: vi.fn(),
          },
        },
        {
          provide: NavigationAuditService,
          useValue: audit,
        },
      ],
    }).compileComponents();

    component = TestBed.createComponent(JournalList).componentInstance;
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('applies MNiSW membership and partial bounds only on Search', () => {
    expect(component.filterForm.controls.mniswPointsFrom.disabled).toBe(true);
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.filterForm.controls.mniswPointsFrom.setValue('70');
    expect(component.query().mniswOnly).toBeUndefined();
    expect(component.filterForm.controls.mniswPointsFrom.enabled).toBe(true);

    component.applyFilters();

    expect(component.query()).toMatchObject({ mniswOnly: true, mniswPointsFrom: 70 });
    expect(component.query().mniswPointsTo).toBeUndefined();
  });

  it('applies MNiSW disciplines only on Search and resets them with the parent without changing JML', () => {
    expect(component.filterForm.controls.mniswDisciplineCodes.disabled).toBe(true);
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.setMniswDisciplineCodes(['2021', '2061', '2021', 'unknown']);
    component.filterForm.controls.disciplineCodes.setValue(['3']);
    expect(component.query().mniswDisciplineCodes).toBeUndefined();
    expect(component.filterForm.controls.mniswDisciplineCodes.enabled).toBe(true);
    component.applyFilters();
    expect(component.query()).toMatchObject({ mniswOnly: true, mniswDisciplineCodes: ['2021', '2061'], disciplineCodes: ['3'] });
    component.filterForm.controls.mniswOnly.setValue(false);
    component.onMniswToggle();
    expect(component.filterForm.controls.mniswDisciplineCodes.value).toEqual([]);
    expect(component.filterForm.controls.mniswDisciplineCodes.disabled).toBe(true);
    expect(component.query().mniswDisciplineCodes).toEqual(['2021', '2061']);
    component.applyFilters();
    expect(component.query().mniswDisciplineCodes).toBeUndefined();
    expect(component.query().disciplineCodes).toEqual(['3']);
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    expect(component.filterForm.controls.mniswDisciplineCodes.value).toEqual([]);
  });

  it('keeps applied MNiSW and unrelated drafts when removing an individual discipline chip', () => {
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.setMniswDisciplineCodes(['2021', '2061']);
    component.applyFilters();
    component.filterForm.controls.journalTitle.setValue('Unapplied title');
    const chip = component.appliedFilterChips().find((value) => value.id === 'mniswDisciplineCodes:2021')!;
    component.removeAppliedFilter(chip);
    expect(component.query()).toMatchObject({ mniswOnly: true, mniswDisciplineCodes: ['2061'] });
    expect(component.query().journalTitle).toBeUndefined();
    expect(component.filterForm.controls.journalTitle.value).toBe('Unapplied title');
    expect(component.filterForm.controls.mniswDisciplineCodes.value).toEqual(['2061']);
    expect(component.filterChipLabels().mniswDisciplineCodes?.['2061']).toBe('Inżynieria lądowa');
  });

  it('keeps both MNiSW states on dictionary failure and retry without running a new search', () => {
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.setMniswDisciplineCodes(['2021']);
    component.applyFilters();
    component.setMniswDisciplineCodes(['2061']);
    const dictionary = TestBed.inject(DictionaryService);
    vi.spyOn(dictionary, 'findMniswDisciplines').mockReturnValueOnce(throwError(() => new Error('Offline')));
    component.loadMniswDisciplineOptions();
    expect(component.hasMniswDisciplineOptionsError()).toBe(true);
    expect(component.query().mniswDisciplineCodes).toEqual(['2021']);
    expect(component.filterForm.controls.mniswDisciplineCodes.value).toEqual(['2061']);
    component.loadMniswDisciplineOptions();
    expect(component.hasMniswDisciplineOptionsError()).toBe(false);
    expect(component.query().mniswDisciplineCodes).toEqual(['2021']);
    expect(component.filterForm.controls.mniswDisciplineCodes.value).toEqual(['2061']);
  });

  it('renders localized MNiSW choices with separate counts and disabled controls', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const root: HTMLElement = fixture.nativeElement;
    const selector = root.querySelector<HTMLElement>('[data-testid="mnisw-disciplines"]')!;
    const toggle = selector.querySelector<HTMLButtonElement>('.multi-value-toggle')!;
    expect(toggle.matches(':disabled')).toBe(true);
    expect(list.mniswDisciplineSelectOptions()).toEqual([
      { code: '2021', label: 'Automatyka', count: 2 },
      { code: '2061', label: 'Inżynieria lądowa', count: 1 },
    ]);
    list.filterForm.controls.mniswOnly.setValue(true);
    list.onMniswToggle();
    fixture.detectChanges();
    expect(toggle.matches(':disabled')).toBe(false);
    toggle.click();
    fixture.detectChanges();
    const search = selector.querySelector<HTMLInputElement>('input[type=search]')!;
    const requests = journalService.searchJournals.mock.calls.length;
    search.value = 'AUTOMATYKA';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(selector.querySelectorAll('input[type=checkbox]')).toHaveLength(1);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    selector.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    fixture.detectChanges();
    expect(list.filterForm.controls.mniswDisciplineCodes.value).toEqual(['2021']);
    expect(list.query().mniswDisciplineCodes).toBeUndefined();
    search.value = '';
    search.dispatchEvent(new Event('input'));
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.mniswDisciplineSelectOptions()[0].label).toBe('Automation');
    expect(root.querySelector('[data-testid="mnisw-disciplines"]')?.textContent).toContain('Civil engineering');
    expect(list.dictionaryCount('mniswDisciplineCounts', '2061')).toBeUndefined();
  });

  it('clears dependent MNiSW drafts without changing applied results until Search', () => {
    component.filterForm.patchValue({ mniswOnly: true });
    component.onMniswToggle();
    component.filterForm.patchValue({ mniswPointsFrom: '40', mniswPointsTo: '140' });
    component.applyFilters();
    component.filterForm.controls.mniswOnly.setValue(false);
    component.onMniswToggle();

    expect(component.filterForm.controls.mniswPointsFrom.value).toBe('');
    expect(component.filterForm.controls.mniswPointsTo.disabled).toBe(true);
    expect(component.query().mniswPointsFrom).toBe(40);
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    expect(component.filterForm.controls.mniswPointsTo.value).toBe('');
    component.applyFilters();
    expect(component.query().mniswOnly).toBe(true);
    expect(component.query().mniswPointsFrom).toBeUndefined();
    component.resetFilters();
    expect(component.query().mniswOnly).toBeUndefined();
    expect(component.filterForm.controls.mniswPointsFrom.disabled).toBe(true);
  });

  it.each([
    { mniswPointsFrom: '50' },
    { mniswPointsTo: '20.5' },
    { mniswPointsFrom: '-20' },
    { mniswPointsFrom: '140', mniswPointsTo: '70' },
  ])('does not apply MNiSW values outside real classes or reversed bounds: %j', (bounds) => {
    const applied = component.query();
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.filterForm.patchValue(bounds);
    component.applyFilters();
    expect(component.hasMniswRangeError()).toBe(true);
    expect(component.query()).toEqual(applied);
  });

  it('preserves restored MNiSW bounds while metadata is delayed and does not apply metadata as a search', () => {
    const pending = new Subject<JournalSearchMetadata>();
    journalService.getJournalSearchMetadata.mockReturnValue(pending);
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(of({
      mniswOnly: true, mniswPointsFrom: 40,
    }));
    const list = TestBed.createComponent(JournalList).componentInstance;
    expect(list.filterForm.controls.mniswPointsFrom.value).toBe('40');
    expect(list.query().mniswPointsTo).toBeUndefined();
    expect(list.isSearchMetadataLoading()).toBe(true);
    const applied = list.query();
    pending.next({ mniswPointClasses: [20, 40, 70] });
    pending.complete();
    expect(list.query()).toEqual(applied);
    expect(list.filterForm.controls.mniswPointsFrom.value).toBe('40');
    expect(list.isSearchMetadataLoading()).toBe(false);
  });

  it('reports metadata failures without inventing classes and supports retry without applying drafts', () => {
    journalService.getJournalSearchMetadata.mockReturnValue(throwError(() => new Error('offline')));
    component.loadSearchMetadata();
    expect(component.hasSearchMetadataError()).toBe(true);
    component.filterForm.controls.mniswOnly.setValue(true);
    component.onMniswToggle();
    component.filterForm.controls.mniswPointsFrom.setValue('40');
    component.applyFilters();
    expect(component.hasMniswRangeError()).toBe(true);
    expect(component.query().mniswOnly).toBeUndefined();

    journalService.getJournalSearchMetadata.mockReturnValue(of({ mniswPointClasses: [40, 70] }));
    component.loadSearchMetadata();
    expect(component.hasSearchMetadataError()).toBe(false);
    expect(component.query().mniswOnly).toBeUndefined();
    expect(component.filterForm.controls.mniswPointsFrom.value).toBe('40');
    component.applyFilters();
    expect(component.query().mniswPointsFrom).toBe(40);
  });

  it('renders real point classes and keeps the form available after a search failure', async () => {
    journalService.searchJournals.mockReturnValue(throwError(() => new Error('search failed')));
    const fixture = TestBed.createComponent(JournalList);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.hasError()).toBe(true);
    const checkbox = fixture.nativeElement.querySelector('#mniswOnly') as HTMLInputElement;
    expect(checkbox).toBeTruthy();
    checkbox.click();
    fixture.detectChanges();
    await fixture.whenStable();
    const range = fixture.nativeElement.querySelector('[data-main-group="mnisw"]') as HTMLElement;
    expect([...range.querySelectorAll('.journal-range-scale span')].map(point => point.textContent!.trim()))
      .toEqual(['20', '40', '70', '100', '140', '200']);
    const minimum = range.querySelector('.noUi-handle-lower') as HTMLElement;
    for (let step = 0; step < 2; step++)
      minimum.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(fixture.componentInstance.filterForm.controls.mniswPointsFrom.value).toBe('70');
    expect(fixture.componentInstance.query().mniswOnly).toBeUndefined();
  });

  it('should expose only leaf disciplines and add context to duplicate labels', () => {
    expect(component.disciplineOptions().map(({ id, label }) => ({ id, label }))).toEqual([
      { id: '5', label: 'Ekonomia — Nauki / Nauki społeczne' },
      { id: '6', label: 'Ekonomia — Nauki / Nauki ścisłe' },
      { id: '3', label: 'Matematyka' },
    ]);
  });

  it('applies multiple countries and languages only after submitting the filters', () => {
    const applied = component.query();
    component.setDictionaryCodes('countryCodes', ['PL', 'DE', ' pl ']);
    component.setDictionaryCodes('languageCodes', ['en', 'pl']);
    expect(component.query()).toEqual(applied);

    component.applyFilters();

    expect(component.query().countryCodes).toEqual(['pl', 'de']);
    expect(component.query().languageCodes).toEqual(['en', 'pl']);
    expect(component.query().countryCode).toBeUndefined();
    expect(component.query().languageCode).toBeUndefined();

    component.setDictionaryCodes('countryCodes', ['DE']);
    expect(component.query().countryCodes).toEqual(['pl', 'de']);
    component.applyFilters();
    expect(component.query().countryCodes).toEqual(['de']);
    expect(component.query().languageCodes).toEqual(['en', 'pl']);
  });

  it.each([
    { countryCode: 'PL', languageCode: 'EN', expectedCountries: ['pl'], expectedLanguages: ['en'] },
    { countryCodes: ['PL', 'DE'], languageCodes: ['en', 'pl'], expectedCountries: ['pl', 'de'], expectedLanguages: ['en', 'pl'] },
    { countryCode: 'PL', countryCodes: [], languageCode: 'en', languageCodes: [], expectedCountries: [], expectedLanguages: [] },
  ])('restores dictionary selections including legacy and explicitly empty criteria', (criteria) => {
    vi.spyOn(TestBed.inject(JournalListQueryStateService), 'getInitialQuery').mockReturnValue(of({
      ...criteria, page: 0, size: 10,
    }));

    component = TestBed.createComponent(JournalList).componentInstance;

    expect(component.filterForm.controls.countryCodes.value).toEqual(criteria.expectedCountries);
    expect(component.filterForm.controls.languageCodes.value).toEqual(criteria.expectedLanguages);
    expect(component.query().countryCodes).toEqual(criteria.expectedCountries);
    expect(component.query().languageCodes).toEqual(criteria.expectedLanguages);
  });

  it('clears all country and language selections on reset', () => {
    component.setDictionaryCodes('countryCodes', ['PL', 'DE']);
    component.setDictionaryCodes('languageCodes', ['en', 'pl']);
    component.applyFilters();

    component.resetFilters();

    expect(component.filterForm.controls.countryCodes.value).toEqual([]);
    expect(component.filterForm.controls.languageCodes.value).toEqual([]);
    expect(component.query().countryCodes).toEqual([]);
    expect(component.query().languageCodes).toEqual([]);
  });

  it.each([
    ['journal-countries', 'countryCodes', 'pl', 'de', 'Polska'],
    ['journal-languages', 'languageCodes', 'en', 'pl', 'Angielski'],
  ] as const)('supports searchable multiple selection without tags or automatic search in %s', async (testId, field, first, second, term) => {
    const fixture = TestBed.createComponent(JournalList);
    component = fixture.componentInstance;
    component.countryOptions.set([{ code: 'PL', namePl: 'Polska' }, { code: 'DE', namePl: 'Niemcy' }]);
    component.languageOptions.set([{ code: 'en', namePl: 'Angielski' }, { code: 'pl', namePl: 'Polski' }]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const journalService = TestBed.inject(JournalService);
    vi.mocked(journalService.searchJournals).mockClear();
    const widget = fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement;
    const toggle = widget.querySelector<HTMLButtonElement>('.multi-value-toggle')!;
    toggle.click();
    fixture.detectChanges();
    const search = widget.querySelector<HTMLInputElement>('input[type="search"]')!;
    search.value = term;
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(widget.querySelectorAll('.multi-value-option')).toHaveLength(1);
    widget.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    fixture.detectChanges();
    expect(component.filterForm.controls[field].value).toEqual([first]);
    expect(toggle.textContent).toContain('(1)');
    expect(widget.querySelector('ul')).toBeNull();

    search.value = '';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    widget.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')[1].click();
    fixture.detectChanges();
    expect(component.filterForm.controls[field].value).toEqual([first, second]);
    expect(toggle.textContent).toContain('(2)');
    expect(widget.querySelector('ul')).toBeNull();
    widget.querySelector<HTMLInputElement>('input[type="checkbox"]')!.click();
    fixture.detectChanges();
    expect(component.filterForm.controls[field].value).toEqual([second]);
    widget.querySelector<HTMLButtonElement>('.multi-value-panel button')!.click();
    fixture.detectChanges();
    expect(component.filterForm.controls[field].value).toEqual([]);
    expect(toggle.textContent).not.toContain('(');
    expect(component.query()[field]).toEqual([]);
    expect(journalService.searchJournals).not.toHaveBeenCalled();
  });

  it.each([
    ['1', '120.12'],
    ['1', '226.4'],
    ['1,01', '500,25'],
    ['300.01', ''],
  ])('accepts manually entered ICV bounds %s to %s independently of the observed data range', async (from, to) => {
    journalService.getJournalSearchMetadata.mockReturnValue(of({ icvMin: 19.77, icvMax: 226.4 }));
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: from, icvTo: to });
    list.syncIcvRange();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.icvSliderMin()).toBe(1);
    expect(list.filterForm.controls.icvFrom.value).toBe(from);
    expect(list.filterForm.controls.icvTo.value).toBe(to);
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasIcvRangeError()).toBe(false);
    expect(fixture.nativeElement.querySelector('#icvRangeError')).toBeNull();
    expect(list.query().icvFrom).toBe(Number(from.replace(',', '.')));
    expect(list.query().icvTo).toBe(to ? Number(to.replace(',', '.')) : undefined);
    expect(journalService.searchJournals).toHaveBeenLastCalledWith(
      expect.objectContaining({ icvFrom: Number(from.replace(',', '.')) }),
      undefined, false, expect.anything());
  });

  it.each(['pl', 'en'])('shows only the relevant ICV validation message in %s and clears it after correction', async (language) => {
    TestBed.inject(TranslocoService).setActiveLang(language);
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    (fixture.nativeElement.querySelector('#indexedJournals') as HTMLInputElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    const requests = journalService.searchJournals.mock.calls.length;
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '120.12', icvTo: '1' });
    list.applyFilters();
    fixture.detectChanges();
    expect(list.icvRangeErrorKey()).toBe('icvRangeOrderError');
    expect(fixture.nativeElement.querySelector('#icvRangeError').textContent).toContain(
      language === 'en' ? 'The minimum ICV must not exceed the maximum.' : 'Minimum ICV nie może przekraczać maksimum.');
    list.filterForm.patchValue({ icvFrom: '0', icvTo: '120.12' });
    list.applyFilters();
    fixture.detectChanges();
    const error = fixture.nativeElement.querySelector('#icvRangeError').textContent;
    expect(list.icvRangeErrorKey()).toBe('icvRangeError');
    expect(error).toContain(language === 'en' ? 'at least 1' : 'od 1');
    expect(error).not.toContain(language === 'en' ? 'minimum ICV' : 'Minimum ICV');
    list.syncIcvRange();
    const minimum = fixture.nativeElement.querySelector('.icv-range .noUi-handle-lower') as HTMLElement;
    minimum.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.hasIcvRangeError()).toBe(false);
    expect(fixture.nativeElement.querySelector('#icvRangeError')).toBeNull();
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    list.applyFilters();
    expect(list.query()).toMatchObject({ icvFrom: 1.01, icvTo: 120.12 });
  });

  it.each([
    ['80.25', '95.75', 80.25, 95.75],
    ['80,25', '95,75', 80.25, 95.75],
    ['80.25', '', 80.25, undefined],
    ['', '95.75', undefined, 95.75],
    ['1', '1', 1, 1],
    ['300.01', '', 300.01, undefined],
    ['80.25', '80.25', 80.25, 80.25],
  ])('preserves decimal or partial ICV bounds %s to %s', (from, to, expectedFrom, expectedTo) => {

    component.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: from, icvTo: to });

    component.syncIcvRange();
    component.applyFilters();

    expect(component.query().icvFrom).toBe(expectedFrom);
    expect(component.query().icvTo).toBe(expectedTo);
    expect(component.filterForm.controls.icvFrom.value).toBe(from);
    expect(component.filterForm.controls.icvTo.value).toBe(to);
    expect(component.hasIcvRangeError()).toBe(false);
  });

  it.each([
    ['95.75', '80.25'],
    ['-0.01', '80'],
    ['80.251', '90'],
    ['80', 'invalid'],
    ['Infinity', ''],
    ['0', ''],
    ['', '0.99'],
    ['9'.repeat(400), ''],
  ])('does not apply invalid ICV bounds %s to %s', (from, to) => {
    const appliedQuery = component.query();

    component.filterForm.patchValue({ icvFrom: from, icvTo: to });

    component.applyFilters();

    expect(component.hasIcvRangeError()).toBe(true);
    expect(component.query()).toEqual(appliedQuery);
    expect(component.filterForm.controls.icvFrom.value).toBe(from);
    expect(component.filterForm.controls.icvTo.value).toBe(to);
  });

  it.each([undefined, JournalListQuery.RatingStatusEnum.Indexed])('restores a decimal lower bound and the Indexed switch from status %s', async (ratingStatus) => {
    vi.spyOn(TestBed.inject(JournalListQueryStateService), 'getInitialQuery').mockReturnValue(of({
      page: 0, size: 10, ratingStatus, icvFrom: 80.25,
    }));

    const fixture = TestBed.createComponent(JournalList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.query().icvFrom).toBe(80.25);
    expect(component.query().icvTo).toBeUndefined();
    expect(component.query().ratingStatus).toBe('INDEXED');
    expect((fixture.nativeElement.querySelector('#indexedJournals') as HTMLInputElement).checked).toBe(true);
    expect(component.filterForm.controls.icvTo.value).toBe('');
  });

  it('moves the rendered ICV slider by 0.01 with the keyboard', async () => {
    const fixture = TestBed.createComponent(JournalList);
    component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('#indexedJournals') as HTMLInputElement).click();
    fixture.detectChanges();

    const handle = fixture.nativeElement.querySelector('.icv-range-slider .noUi-handle-lower') as HTMLElement;
    expect(handle).not.toBeNull();
    const requests = journalService.searchJournals.mock.calls.length;
    handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    expect(component.hasUnappliedChanges()).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    component.applyFilters();

    expect(component.filterForm.controls.icvFrom.value).toBe('1.01');
    expect(component.query().icvFrom).toBe(1.01);
  });

  it('clears ICV validation errors after a corrected range or reset', () => {

    component.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '95.75', icvTo: '80.25' });
    component.applyFilters();
    expect(component.hasIcvRangeError()).toBe(true);

    component.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '80.25', icvTo: '95.75' });
    component.applyFilters();
    expect(component.hasIcvRangeError()).toBe(false);
    expect(component.query().icvFrom).toBe(80.25);

    component.resetFilters();
    expect(component.query().icvFrom).toBeUndefined();
    expect(component.query().icvTo).toBeUndefined();
    expect(component.hasIcvRangeError()).toBe(false);
  });

  it('adds and removes JML dropdown values without selecting parent categories or applying the draft', () => {
    component.setDisciplineCodes(['3', '5', '1']);
    expect(component.filterForm.controls.disciplineCodes.value).toEqual(['3', '5']);
    expect(component.disciplineSelectOptions().every((option) => !['1', '2', '4'].includes(option.code))).toBe(true);
    component.setDisciplineCodes(['5']);
    expect(component.filterForm.controls.disciplineCodes.value).toEqual(['5']);
    expect(component.query().disciplineCodes).toBeUndefined();
  });
  it('should allow selecting multiple leaves and reject parent categories', () => {
    component.setDisciplineCodes(['3']);
    component.setDisciplineCodes([...component.filterForm.controls.disciplineCodes.value, '5']);
    component.setDisciplineCodes([...component.filterForm.controls.disciplineCodes.value, '1']);

    expect(component.filterForm.controls.disciplineCodes.value).toEqual(['3', '5']);
  });

  it('keeps all three original-style switches off initially and enables numeric bounds only for Indexed', async () => {
    journalService.getJournalSearchMetadata.mockReturnValue(of({ icvMin: 10.25, icvMax: 450.75 }));
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.icvRangeMin()).toBe(10.25);
    expect(list.icvRangeMax()).toBe(450.75);
    for (const id of ['indexedJournals', 'notIndexedJournals', 'notEvaluatedJournals']) {
      const input = fixture.nativeElement.querySelector('#' + id) as HTMLInputElement;
      expect(input.checked).toBe(false);
      expect(input.getAttribute('role')).toBe('switch');
      expect(input.closest('.form-check.form-switch.icv-toggle')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('label[for="' + id + '"]')).not.toBeNull();
    }
    expect(list.query().ratingStatus).toBeUndefined();
    expect(list.isIcvFilterEnabled()).toBe(false);
    expect(list.filterForm.controls.icvFrom.disabled).toBe(true);
    (fixture.nativeElement.querySelector('#indexedJournals') as HTMLInputElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.icvFrom.enabled).toBe(true);
    expect((fixture.nativeElement.querySelector('#notIndexedJournals') as HTMLInputElement).checked).toBe(false);
    expect((fixture.nativeElement.querySelector('#notEvaluatedJournals') as HTMLInputElement).checked).toBe(false);
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '400,25' });
    list.applyFilters();
    expect(list.hasIcvRangeError()).toBe(false);
    expect(list.query().icvFrom).toBe(400.25);
    expect(list.query().ratingStatus).toBe('INDEXED');
  });

  it('does not apply a full metadata range and preserves the editable bounds', () => {
    component.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '1', icvTo: '300' });
    component.applyFilters();
    expect(component.query().icvFrom).toBeUndefined();
    expect(component.query().icvTo).toBeUndefined();
    expect(component.filterForm.controls.icvTo.value).toBe('300');
  });

  it('keeps the slider at one when metadata contains only zero and permits a manual bound of one', async () => {
    journalService.getJournalSearchMetadata.mockReturnValue(of({ icvMin: 0, icvMax: 0 }));
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    (fixture.nativeElement.querySelector('#indexedJournals') as HTMLInputElement).click();
    fixture.detectChanges();
    expect(list.isIcvFilterEnabled()).toBe(true);
    expect(list.icvRange()).toEqual({ from: 1, to: 1 });
    expect(fixture.nativeElement.querySelector('.icv-range .noUi-target')).toBeNull();
    list.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '0', icvTo: '0' });
    list.applyFilters();
    expect(list.hasIcvRangeError()).toBe(true);
    list.filterForm.patchValue({ icvFrom: '1', icvTo: '1' });
    list.applyFilters();
    expect(list.hasIcvRangeError()).toBe(false);
    expect(list.query()).toMatchObject({ icvFrom: 1, icvTo: 1 });
  });

  it('does not invent an ICV range for an empty qualified set', async () => {
    journalService.getJournalSearchCounts.mockReturnValue(of({ countryCounts: {} }));
    journalService.getJournalSearchMetadata.mockReturnValue(of({ mniswPointClasses: [], countryCounts: {} }));
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.icvRangeMin()).toBeNull();
    expect(list.icvRangeMax()).toBeNull();
    expect(list.isIcvFilterEnabled()).toBe(false);
    expect(list.filterForm.controls.icvFrom.disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.icv-range .noUi-target')).toBeNull();
    list.applyFilters();
    expect(list.hasIcvRangeError()).toBe(false);
    expect(list.dictionaryCount('countryCounts', 'pl')).toBe(0);
  });

  it('preserves restored and edited ICV bounds while metadata arrives without searching again', async () => {
    const pending = new Subject<JournalSearchMetadata>();
    journalService.getJournalSearchMetadata.mockReturnValue(pending);
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(of({
      ratingStatus: JournalListQuery.RatingStatusEnum.Indexed, icvFrom: 80.25,
    }));
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    const requests = journalService.searchJournals.mock.calls.length;
    const applied = list.query();
    list.filterForm.patchValue({ icvTo: '95.75' });
    pending.next({ icvMin: 20, icvMax: 100 });
    pending.complete();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.icvFrom.value).toBe('80.25');
    expect(list.filterForm.controls.icvTo.value).toBe('95.75');
    expect(list.query()).toEqual(applied);
    expect(list.query().ratingStatus).toBe('INDEXED');
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    expect(fixture.nativeElement.querySelector('.icv-range .noUi-target')).toBeTruthy();
  });

  it('preserves drafts and applied ICV through metadata failure, retry and a changed range', () => {
    component.filterForm.patchValue({ ratingStatus: 'INDEXED', icvFrom: '80.25' });
    component.applyFilters();
    const applied = component.query();
    component.filterForm.patchValue({ icvTo: '90.25' });
    journalService.getJournalSearchMetadata.mockReturnValue(throwError(() => new Error('offline')));
    component.loadSearchMetadata();
    component.applyFilters();
    expect(component.hasIcvRangeError()).toBe(true);
    expect(component.query()).toEqual(applied);
    expect(component.filterForm.controls.icvTo.value).toBe('90.25');
    journalService.getJournalSearchMetadata.mockReturnValue(of({ icvMin: 10, icvMax: 70 }));
    component.loadSearchMetadata();
    expect(component.filterForm.controls.icvTo.value).toBe('90.25');
    expect(component.query()).toEqual(applied);
    component.applyFilters();
    expect(component.hasIcvRangeError()).toBe(false);
    expect(component.query()).toMatchObject({ icvFrom: 80.25, icvTo: 90.25 });
  });

  it('keeps static validation metadata while updating counts for applied filters', async () => {
    const fixture = await mountedList();
    const component = fixture.componentInstance;
    const before = component.searchMetadata();
    const metadataCalls = journalService.getJournalSearchMetadata.mock.calls.length;
    journalService.getJournalSearchCounts.mockReturnValue(of({ countryCounts: { pl: 1 }, languageCounts: { en: 1 } }));
    component.filterForm.patchValue({ countryCodes: ['pl'], languageCodes: ['en'] });
    fixture.detectChanges();
    expect(component.dictionaryCount('countryCounts', 'PL')).toBeUndefined();
    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.searchMetadata()).toBe(before);
    expect(component.dictionaryCount('countryCounts', 'PL')).toBe(1);
    expect(component.dictionaryCount('countryCounts', 'fr')).toBe(0);
    expect(component.dictionaryCount('languageCounts', 'en')).toBe(1);
    expect(journalService.getJournalSearchMetadata).toHaveBeenCalledTimes(metadataCalls);
  });

  it('does not reload counts on pagination, page size or sorting and hides them for draft changes', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const calls = journalService.getJournalSearchCounts.mock.calls.length;
    list.page.set(2);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(journalService.getJournalSearchCounts).toHaveBeenCalledTimes(calls);
    list.changePageSizeValue(25);
    list.filterForm.controls.sortBy.setValue('SORT_BY_TITLE_DESC');
    list.changeSort();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(journalService.getJournalSearchCounts).toHaveBeenCalledTimes(calls);
    expect(list.visibleSearchCounts()).not.toBeNull();
    list.filterForm.controls.nonFeeFilters.controls.licenseCodes.setValue(['CC_BY']);
    fixture.detectChanges();
    expect(list.visibleSearchCounts()).toBeNull();
    expect(journalService.getJournalSearchCounts).toHaveBeenCalledTimes(calls);
  });

  it('discards superseded count responses, preserves options at zero and can retry counts independently', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const first = new Subject<JournalSearchMetadata>();
    const second = new Subject<JournalSearchMetadata>();
    journalService.getJournalSearchCounts.mockReturnValueOnce(first).mockReturnValueOnce(second);
    list.filterForm.controls.languageCodes.setValue(['en']);
    list.applyFilters();
    fixture.detectChanges();
    expect(list.visibleSearchCounts()).toBeNull();
    list.filterForm.controls.languageCodes.setValue(['pl']);
    list.applyFilters();
    fixture.detectChanges();
    second.next({ countryCounts: {}, licenseCounts: {}, accessModelCounts: {} });
    second.complete();
    first.next({ countryCounts: { pl: 999 } });
    fixture.detectChanges();
    expect(list.dictionaryCount('countryCounts', 'pl')).toBe(0);
    const lists = fixture.debugElement.queryAll(By.directive(FilterCheckboxList));
    const licenses = lists.map((entry) => entry.componentInstance as FilterCheckboxList)
      .find((entry) => entry.label() === 'page.institution.journalList.licenses')!;
    expect(licenses.options().map((entry) => entry.code)).toContain('CC_BY');
    journalService.getJournalSearchCounts.mockReturnValue(throwError(() => new Error('offline')));
    list.loadSearchCounts(true);
    fixture.detectChanges();
    expect(list.visibleSearchCounts()).toBeNull();
    expect(list.hasSearchCountsError()).toBe(true);
    const searches = journalService.searchJournals.mock.calls.length;
    journalService.getJournalSearchCounts.mockReturnValue(of({ countryCounts: { pl: 1 } }));
    (fixture.nativeElement.querySelector('[data-testid="journal-counts-retry"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(list.dictionaryCount('countryCounts', 'pl')).toBe(1);
    expect(journalService.searchJournals).toHaveBeenCalledTimes(searches);
  });

  it('offers exactly eight non-relevance sorts and defaults to title ascending', () => {
    expect(component.sortOptions).toHaveLength(8);
    expect(component.sortOptions.map((option) => option.value)).toEqual(Object.values(JournalListQuery.SortByEnum));
    expect(component.query().sortBy).toBe(JournalListQuery.SortByEnum.SortByTitleAsc);
    expect(component.sortOptions.map((option) => option.value).join()).not.toMatch(/RELEVANCE|REJECTION/);
  });

  it('sorts and pages using applied criteria while preserving invalid drafts and resetting the page on sort', async () => {
    journalService.searchJournals.mockReturnValue(of({ data: [], totalElements: 27, totalPages: 3, size: 10 }));
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(of({
      journalTitle: 'Applied title', publisher: 'Applied publisher', issn: '1234',
      countryCodes: ['pl'], repositoryIds: [10], firstIssueYearFrom: 1960,
      publicationFeeMaxPln: 100, otherFeesFreeOnly: true,
    }));
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    list.filterForm.patchValue({ journalTitle: 'Draft title', publisher: 'Draft publisher', countryCodes: ['de'],
      nonFeeFilters: { repositoryIds: ['20'], firstIssueYearFrom: '1970', firstIssueYearTo: '1950' },
      feeFilters: { publicationFeeMaxPln: '-1', openAccessFeeFreeOnly: true } });
    list.page.set(3);
    const select = fixture.nativeElement.querySelector('#journalSort') as HTMLSelectElement;
    select.value = JournalListQuery.SortByEnum.SortByRatingMniswDesc;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toEqual({ ...applied, sortBy: JournalListQuery.SortByEnum.SortByRatingMniswDesc, page: 0, first: 0 });
    expect(list.filterForm.controls.journalTitle.value).toBe('Draft title');
    expect(list.filterForm.controls.publisher.value).toBe('Draft publisher');
    expect(list.filterForm.controls.countryCodes.value).toEqual(['de']);
    expect(list.filterForm.controls.nonFeeFilters.controls.repositoryIds.value).toEqual(['20']);
    expect(list.filterForm.controls.nonFeeFilters.controls.firstIssueYearTo.value).toBe('1950');
    expect(list.hasNonFeeFilterError()).toBe(false);
    expect(list.hasFeeFilterError()).toBe(false);
    expect(list.filterForm.controls.feeFilters.controls.publicationFeeMaxPln.value).toBe('-1');
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
    expect(journalService.searchJournals.mock.calls.at(-1)?.[0]).toEqual(list.query());
    list.nextPage();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toEqual({ ...applied, sortBy: JournalListQuery.SortByEnum.SortByRatingMniswDesc, page: 1, first: 10 });
    expect(list.filterForm.controls.journalTitle.value).toBe('Draft title');
    expect(list.filterForm.controls.nonFeeFilters.controls.firstIssueYearTo.value).toBe('1950');
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 2);
    list.previousPage();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()).toEqual({ ...applied, sortBy: JournalListQuery.SortByEnum.SortByRatingMniswDesc, page: 0, first: 0 });
    expect(list.filterForm.controls.countryCodes.value).toEqual(['de']);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 3);
  });

  it.each(['journalTitle', 'publisher', 'issn'] as const)('applies literal %s text only when the main form is submitted', async (field) => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    const requests = journalService.searchJournals.mock.calls.length;
    const input = fixture.nativeElement.querySelector(`#${field}`) as HTMLInputElement;
    input.value = '  Łódź * Study  ';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    expect(list.query()[field]).toBeUndefined();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query()[field]).toBe('Łódź * Study');
    expect(journalService.searchJournals.mock.calls.length).toBe(requests + 1);
  });

  it('applies independent fee criteria only on Search and clears all of them on reset', () => {
    component.filterForm.controls.feeFilters.patchValue({ otherFeesFreeOnly: true, openAccessFeeFreeOnly: true,
      publicationFeeMaxPln: '0', rapidReviewFeeMaxPln: '123,45', submissionFeeFreeOnly: true });
    expect(component.query().publicationFeeMaxPln).toBeUndefined();
    component.applyFilters();
    expect(component.query()).toMatchObject({ otherFeesFreeOnly: true, openAccessFeeFreeOnly: true,
      publicationFeeMaxPln: 0, rapidReviewFeeMaxPln: 123.45, submissionFeeFreeOnly: true });
    component.resetFilters();
    expect(component.query().publicationFeeMaxPln).toBeUndefined();
    expect(component.query().otherFeesFreeOnly).toBeUndefined();
    expect(component.filterForm.controls.feeFilters.controls.rapidReviewFeeMaxPln.value).toBe('');
  });

  it('does not replace applied fees with an invalid or contradictory draft', () => {
    const form = component.filterForm.controls.feeFilters;
    form.patchValue({ publicationFeeMaxPln: '100' });
    component.applyFilters();
    const applied = component.query();
    form.patchValue({ publicationFeeFreeOnly: true });
    component.applyFilters();
    expect(component.hasFeeFilterError()).toBe(true);
    expect(component.query()).toEqual(applied);
    component.resetFilters();
    expect(component.hasFeeFilterError()).toBe(false);
  });

  it('hides fee count parentheses while editing and restores counts including zero after applying', async () => {
    journalService.getJournalSearchCounts.mockReturnValue(of({ featureCounts: {
      submissionFeeFreeOnly: 12,
      publicationFeeFreeOnly: 0,
      openAccessFeeFreeOnly: 7,
      rapidReviewFeeFreeOnly: 3,
      otherFeesFreeOnly: 0,
    } }));
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    const fees = (fixture.nativeElement as HTMLElement).querySelector('app-journal-fee-filters')!;
    const checkboxes = [...fees.querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];
    const labels = checkboxes.map((checkbox) => checkbox.closest('label')!);
    const counts = ['(12)', '(0)', '(7)', '(3)', '(0)'];
    expect(checkboxes).toHaveLength(5);
    labels.forEach((label, index) => expect(label.textContent).toContain(counts[index]));
    for (const checkbox of checkboxes) {
      const requests = journalService.searchJournals.mock.calls.length;
      checkbox.click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(checkbox.checked).toBe(true);
      expect(list.visibleSearchCounts()).toBeNull();
      labels.forEach((label) => expect(label.textContent).not.toMatch(/[()]/));
      expect(journalService.searchJournals).toHaveBeenCalledTimes(requests);
      list.applyFilters();
      fixture.detectChanges();
      await fixture.whenStable();
      labels.forEach((label, index) => expect(label.textContent).toContain(counts[index]));
      expect(journalService.searchJournals).toHaveBeenCalledTimes(requests + 1);
    }
  });

  it('fee checkbox clears and disables only its own draft limit without searching and reset re-enables it', async () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    const requests = journalService.searchJournals.mock.calls.length;
    const controls = list.filterForm.controls.feeFilters.controls;
    const input = fixture.nativeElement.querySelector('#publicationFeeMaxPln') as HTMLInputElement;
    input.value = '100';
    input.dispatchEvent(new Event('input'));
    controls.submissionFeeMaxPln.setValue('200');
    const checkbox = input.closest('fieldset')?.querySelector('input[type="checkbox"]') as HTMLInputElement;
    checkbox.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(controls.publicationFeeMaxPln.value).toBe('');
    expect(input.disabled).toBe(true);
    expect(controls.submissionFeeMaxPln.value).toBe('200');
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.query().publicationFeeFreeOnly).toBe(true);
    expect(list.query().publicationFeeMaxPln).toBeUndefined();
    expect(list.query().submissionFeeMaxPln).toBe(200);
    list.resetFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(input.disabled).toBe(false);
    expect(checkbox.checked).toBe(false);
    expect(list.query().publicationFeeFreeOnly).toBeUndefined();
  });

  it('applies every non-fee criterion only after Search and clears them on reset', () => {
    const controls = component.filterForm.controls.nonFeeFilters;
    controls.patchValue({
      doiOnly: true, editorialSystemOnly: true, newsletterOnly: true, socialMediaOnly: true,
      antiPlagiarismOnly: true, aopOnly: true, repositoryIds: ['10', '20'], licenseCodes: ['CC_BY'],
      accessModels: ['FREE_ARTICLES'], frequencyCodes: ['q'], firstIssueYearFrom: '1960', firstIssueYearTo: '1970',
    });
    expect(component.query().doiOnly).toBeUndefined();
    expect(component.query().repositoryIds).toBeUndefined();
    component.applyFilters();
    expect(component.hasNonFeeFilterError()).toBe(false);
    expect(component.query()).toMatchObject({
      doiOnly: true, editorialSystemOnly: true, newsletterOnly: true, socialMediaOnly: true,
      antiPlagiarismOnly: true, aopOnly: true, repositoryIds: [10, 20], licenseCodes: ['CC_BY'],
      accessModels: ['FREE_ARTICLES'], frequencyCodes: ['q'], firstIssueYearFrom: 1960, firstIssueYearTo: 1970,
    });
    component.resetFilters();
    expect(component.query().doiOnly).toBeUndefined();
    expect(component.query().repositoryIds).toBeUndefined();
    expect(component.query().licenseCodes).toBeUndefined();
    expect(component.query().accessModels).toBeUndefined();
    expect(component.query().frequencyCodes).toBeUndefined();
    expect(component.query().firstIssueYearFrom).toBeUndefined();
    expect(component.query().firstIssueYearTo).toBeUndefined();
    expect(controls.controls.repositoryIds.value).toEqual([]);
  });

  it('does not replace applied non-fee criteria with invalid draft years', () => {
    const controls = component.filterForm.controls.nonFeeFilters;
    controls.patchValue({ doiOnly: true, firstIssueYearFrom: '1960' });
    component.applyFilters();
    const applied = component.query();
    controls.patchValue({ doiOnly: false, firstIssueYearTo: '1950' });
    component.applyFilters();
    expect(component.hasNonFeeFilterError()).toBe(true);
    expect(component.query()).toEqual(applied);
    expect(controls.controls.firstIssueYearTo.value).toBe('1950');
    component.resetFilters();
    expect(component.hasNonFeeFilterError()).toBe(false);
  });

  it('restores non-fee selections and partial year bounds without waiting for dictionaries', () => {
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(of({
      repositoryIds: [10, 20], licenseCodes: ['CC_BY'], accessModels: ['PAID_ARTICLES'],
      frequencyCodes: ['q'], firstIssueYearTo: 1970, socialMediaOnly: true,
    }));
    const list = TestBed.createComponent(JournalList).componentInstance;
    expect(list.filterForm.controls.nonFeeFilters.controls.repositoryIds.value).toEqual(['10', '20']);
    expect(list.filterForm.controls.nonFeeFilters.controls.firstIssueYearFrom.value).toBe('');
    expect(list.query()).toMatchObject({
      repositoryIds: [10, 20], licenseCodes: ['CC_BY'], accessModels: ['PAID_ARTICLES'],
      frequencyCodes: ['q'], firstIssueYearTo: 1970, socialMediaOnly: true,
    });
    expect(list.query().firstIssueYearFrom).toBeUndefined();
  });

  it('local database search and checkbox selection do not search journals', async () => {
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const count = journalService.searchJournals.mock.calls.length;
    const databaseList = fixture.nativeElement.querySelector('app-filter-checkbox-list') as HTMLElement;
    (databaseList.querySelector('input[type=checkbox]') as HTMLInputElement).click();
    const localSearch = databaseList.querySelector('input[type=search]') as HTMLInputElement;
    localSearch.value = 'Database B';
    localSearch.dispatchEvent(new Event('input'));
    localSearch.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    (databaseList.querySelector('input[type=checkbox]') as HTMLInputElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(list.filterForm.controls.nonFeeFilters.controls.repositoryIds.value).toEqual(['10', '20']);
    expect(journalService.searchJournals.mock.calls.length).toBe(count);
    expect(list.query().repositoryIds).toBeUndefined();
    list.applyFilters();
    expect(list.query().repositoryIds).toEqual([10, 20]);
    expect((databaseList.querySelector('input[type=checkbox]') as HTMLInputElement).checked).toBe(true);
    list.resetFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    expect((databaseList.querySelector('input[type=checkbox]') as HTMLInputElement).checked).toBe(false);
  });

  it('preserves selections through delayed database failure and retry without applying drafts', async () => {
    const pending = new Subject<import('../../../core/backend').RepositoryDictionaryView[]>();
    repositoryLoader.mockReturnValue(pending);
    vi.mocked(TestBed.inject(JournalListQueryStateService).getInitialQuery).mockReturnValue(of({ repositoryIds: [10] }));
    const fixture = TestBed.createComponent(JournalList);
    const list = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    const count = journalService.searchJournals.mock.calls.length;
    list.filterForm.controls.nonFeeFilters.controls.repositoryIds.setValue(['10', '20']);
    pending.error(new Error('offline'));
    fixture.detectChanges();
    expect(list.filterForm.controls.nonFeeFilters.controls.repositoryIds.value).toEqual(['10', '20']);
    repositoryLoader.mockReturnValue(of([{ id: 10, name: 'DOAJ' }, { id: 20, name: 'Scopus' }]));
    const retry = fixture.nativeElement.querySelector('app-journal-non-fee-filters button.btn-link') as HTMLButtonElement;
    expect(retry).toBeTruthy();
    const repositoryRequests = repositoryLoader.mock.calls.length;
    retry.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(repositoryLoader).toHaveBeenCalledTimes(repositoryRequests + 1);
    const selected = fixture.nativeElement.querySelector('[data-advanced-group="repositories"] ul') as HTMLElement;
    expect(selected.textContent).toContain('DOAJ');
    expect(selected.textContent).toContain('Scopus');
    expect(list.filterForm.controls.nonFeeFilters.controls.repositoryIds.value).toEqual(['10', '20']);
    expect(list.query().repositoryIds).toEqual([10]);
    expect(journalService.searchJournals.mock.calls.length).toBe(count);
  });

  it.each(['pl', 'en'])('downloads the available %s report from the visible button without applying the draft', async (language) => {
    questionnaireReportsService.questionnaireReportAvailability.mockReturnValue(of({ pl: true, en: true }));
    const response = new Subject<Blob>();
    questionnaireReportsService.downloadQuestionnaireReport2024.mockReturnValue(response);
    TestBed.inject(TranslocoService).setActiveLang(language);
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.filterForm.patchValue({ journalTitle: 'Unapplied title', reviewDaysTo: '-1' });
    fixture.detectChanges();
    const applied = list.query();
    const requests = journalService.searchJournals.mock.calls.length;
    const button = fixture.nativeElement.querySelector('[data-testid="questionnaire-report-download"]') as HTMLButtonElement;
    expect(button.type).toBe('button');
    expect(button.closest('form')).toBeNull();
    button.click();
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(button.querySelector('.spinner-border')).toBeTruthy();
    expect(questionnaireReportsService.downloadQuestionnaireReport2024).toHaveBeenCalledWith(language, 'body', false, expect.anything());
    button.click();
    expect(questionnaireReportsService.downloadQuestionnaireReport2024).toHaveBeenCalledOnce();
    response.next(new Blob(['report']));
    response.complete();
    fixture.detectChanges();
    expect(button.disabled).toBe(false);
    expect(audit.track).toHaveBeenCalledWith(expect.objectContaining({
      eventType: 'FILE_DOWNLOAD', target: `questionnaire-report-2024-${language}.xlsx`, success: true,
    }));
    expect(list.query()).toEqual(applied);
    expect(list.filterForm.controls.journalTitle.value).toBe('Unapplied title');
    expect(list.hasUnappliedChanges()).toBe(true);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
  });

  it('hides the report action when the active language has no file and restores it when switching back', async () => {
    const fixture = await mountedList();
    expect(fixture.nativeElement.querySelector('[data-testid="questionnaire-report-download"]')).toBeTruthy();
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[data-testid="questionnaire-report-download"]')).toBeNull();
    TestBed.inject(TranslocoService).setActiveLang('pl');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[data-testid="questionnaire-report-download"]')).toBeTruthy();
    expect(questionnaireReportsService.downloadQuestionnaireReport2024).not.toHaveBeenCalled();
  });

  it('shows an accessible report error and allows another download without reloading search results', async () => {
    const fixture = await mountedList();
    const requests = journalService.searchJournals.mock.calls.length;
    questionnaireReportsService.downloadQuestionnaireReport2024.mockReturnValue(throwError(() => new Error('offline')));
    const button = fixture.nativeElement.querySelector('[data-testid="questionnaire-report-download"]') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')!.textContent).toContain('downloadQuestionnaireReportError');
    expect(button.disabled).toBe(false);
    questionnaireReportsService.downloadQuestionnaireReport2024.mockReturnValue(of(new Blob(['report'])));
    button.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.hasQuestionnaireReportDownloadError()).toBe(false);
    expect(questionnaireReportsService.downloadQuestionnaireReport2024).toHaveBeenCalledTimes(2);
    expect(journalService.searchJournals.mock.calls.length).toBe(requests);
  });

  it('keeps search usable without a report action when availability cannot be fetched', async () => {
    questionnaireReportsService.questionnaireReportAvailability.mockReturnValue(throwError(() => new Error('offline')));
    const fixture = await mountedList();
    expect(fixture.nativeElement.querySelector('[data-testid="questionnaire-report-download"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('button[type="submit"]')).toBeTruthy();
    expect(fixture.componentInstance.hasError()).toBe(false);
  });

  it('counts a valid identical Search once per click with applied query values, never the later draft', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    audit.track.mockClear();
    list.filterForm.patchValue({ journalTitle: 'Applied', reviewDaysTo: '0', feeFilters: { publicationFeeMaxPln: '0' } });
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    list.filterForm.controls.journalTitle.setValue('Later draft');
    const events = audit.track.mock.calls.map(([event]) => event).filter((event) => event.eventType === 'JOURNAL_SEARCH');
    expect(events).toHaveLength(1);
    expect(events[0].properties.criteria).toEqual({ surveyYear: 2024, journalTitle: 'Applied', reviewDaysTo: 0, publicationFeeMaxPln: 0 });
    list.filterForm.controls.journalTitle.setValue('Applied');
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const searches = audit.track.mock.calls.map(([event]) => event).filter((event) => event.eventType === 'JOURNAL_SEARCH');
    expect(searches).toHaveLength(2);
    expect(searches[0].properties.operationId).not.toBe(searches[1].properties.operationId);
    expect(list.query().journalTitle).toBe('Applied');
  });

  it('does not count editing, invalid Search, sort, pagination or local dictionary search as Search', async () => {
    journalService.searchJournals.mockReturnValue(of({ data: [{ id: 28032, title: 'Journal' }], totalElements: 27, totalPages: 3 }));
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    audit.track.mockClear();
    list.filterForm.patchValue({ journalTitle: 'Draft', reviewDaysTo: '-1' });
    list.applyFilters();
    list.filterForm.controls.sortBy.setValue(JournalListQuery.SortByEnum.SortByTitleDesc);
    list.changeSort();
    fixture.detectChanges();
    await fixture.whenStable();
    list.goToPage(1);
    fixture.detectChanges();
    await fixture.whenStable();
    const input = fixture.nativeElement.querySelector('app-filter-checkbox-list input[type="search"]') as HTMLInputElement;
    input.value = 'local search';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(audit.track.mock.calls.map(([event]) => event.eventType)).not.toContain('JOURNAL_SEARCH');
    expect(audit.track.mock.calls.map(([event]) => event.eventType)).not.toContain('JOURNAL_FILTERS_CLEAR');
  });

  it('preserves the Search operation across error retries and reports zero only after a valid empty response', async () => {
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    audit.track.mockClear();
    journalService.searchJournals.mockReturnValue(throwError(() => new Error('offline')));
    list.filterForm.controls.journalTitle.setValue('Applied');
    list.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    list.filterForm.controls.journalTitle.setValue('Later draft');
    expect(audit.track.mock.calls.map(([event]) => event.eventType)).not.toContain('JOURNAL_SEARCH_EMPTY');
    journalService.searchJournals.mockReturnValue(of({ data: [], totalElements: 0, totalPages: 0 }));
    list.retrySearch();
    fixture.detectChanges();
    await fixture.whenStable();
    list.retrySearch();
    fixture.detectChanges();
    await fixture.whenStable();
    const events = audit.track.mock.calls.map(([event]) => event);
    expect(events.filter((event) => event.eventType === 'JOURNAL_SEARCH')).toHaveLength(1);
    expect(events.filter((event) => event.eventType === 'JOURNAL_SEARCH_EMPTY')).toHaveLength(1);
    expect(events.filter((event) => event.eventType === 'JOURNAL_SEARCH_RESULT').map((event) => event.success)).toEqual([false, true, true]);
    expect(events.filter((event) => event.eventType === 'JOURNAL_SEARCH_RESULT').every((event) => event.properties.criteria.journalTitle === 'Applied')).toBe(true);
    expect(list.filterForm.controls.journalTitle.value).toBe('Later draft');
  });

  it('logs Clear with empty applied criteria rather than as another Search', async () => {
    const fixture = await mountedList();
    audit.track.mockClear();
    fixture.componentInstance.filterForm.controls.journalTitle.setValue('Discarded draft');
    fixture.componentInstance.resetFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    const events = audit.track.mock.calls.map(([event]) => event);
    expect(events.filter((event) => event.eventType === 'JOURNAL_FILTERS_CLEAR')).toHaveLength(1);
    expect(events.find((event) => event.eventType === 'JOURNAL_FILTERS_CLEAR').properties.criteria).toEqual({ surveyYear: 2024 });
    expect(events.filter((event) => event.eventType === 'JOURNAL_SEARCH')).toHaveLength(0);
  });

  it('records modified and middle card-link clicks with applied criteria without consuming return navigation state', async () => {
    journalService.searchJournals.mockReturnValue(of({ data: [{ id: 28032, title: 'Journal' }], totalElements: 1, totalPages: 1 }));
    const fixture = await mountedList();
    const list = fixture.componentInstance;
    list.filterForm.controls.journalTitle.setValue('Unapplied');
    const remember = vi.mocked(TestBed.inject(JournalListQueryStateService).rememberForDetails);
    remember.mockClear();
    audit.track.mockClear();
    for (const selector of ['[data-testid="journal-result-title"]', '[data-testid="journal-result-details"]']) {
      for (const [type, button] of [['click', 0], ['auxclick', 1]] as const) {
        const event = new MouseEvent(type, { button, ctrlKey: true, bubbles: true, cancelable: true });
        fixture.nativeElement.querySelector(selector).dispatchEvent(event);
        expect(event.defaultPrevented).toBe(false);
      }
    }
    const cards = audit.track.mock.calls.map(([event]) => event).filter((event) => event.eventType === 'JOURNAL_CARD_OPEN');
    expect(cards).toHaveLength(4);
    expect(cards.every((event) => event.resource.id === '28032' && !event.properties.criteria.journalTitle)).toBe(true);
    expect(remember).not.toHaveBeenCalled();
  });

  it('should expose report only for an available active language', () => {
    expect(component.questionnaireReportLanguage()).toBe('pl');
    expect(component.isQuestionnaireReportAvailable()).toBe(true);

    TestBed.inject(TranslocoService).setActiveLang('en');

    expect(component.questionnaireReportLanguage()).toBe('en');
    expect(component.isQuestionnaireReportAvailable()).toBe(false);
  });

  it('should download the report for the active language', () => {
    component.downloadQuestionnaireReport2024();

    expect(questionnaireReportsService.downloadQuestionnaireReport2024).toHaveBeenCalledWith(
      'pl',
      'body',
      false,
      expect.objectContaining({ transferCache: false }),
    );
    expect(component.isQuestionnaireReportDownloading()).toBe(false);
    const options = questionnaireReportsService.downloadQuestionnaireReport2024.mock.calls[0][3];
    expect(options.context.get(NAVIGATION_AUDIT_OPERATION)).toEqual({
      name: 'QuestionnaireReportsService.downloadQuestionnaireReport2024', viewContext,
    });
  });

  it.each(['pl', 'en'])('audits a completed bulk download in %s', (language) => {
    questionnaireReportsService.questionnaireReportAvailability.mockReturnValue(of({ pl: true, en: true }));
    component = TestBed.createComponent(JournalList).componentInstance;
    TestBed.inject(TranslocoService).setActiveLang(language);
    questionnaireReportsService.downloadQuestionnaireReport2024.mockReturnValue(of(new Blob(['report'])));
    audit.track.mockClear();
    component.downloadQuestionnaireReport2024();

    expect(audit.track).toHaveBeenCalledExactlyOnceWith({
      ...viewContext,
      eventType: 'FILE_DOWNLOAD', action: 'questionnaire.report.bulk.download',
      target: `questionnaire-report-2024-${language}.xlsx`, resource: { type: 'questionnaire-report', id: '2024' },
      success: true, properties: { year: 2024, language, format: 'xlsx', sizeBytes: 6 },
    });
    expect(component.hasQuestionnaireReportDownloadError()).toBe(false);
  });

  it('keeps the original language and view and prevents concurrent downloads', () => {
    const response = new Subject<Blob>();
    questionnaireReportsService.downloadQuestionnaireReport2024.mockReturnValue(response);
    audit.track.mockClear();
    component.downloadQuestionnaireReport2024();
    component.downloadQuestionnaireReport2024();
    expect(questionnaireReportsService.downloadQuestionnaireReport2024).toHaveBeenCalledOnce();
    expect(audit.track).not.toHaveBeenCalled();
    TestBed.inject(TranslocoService).setActiveLang('en');
    audit.captureView.mockReturnValue({ pageKey: 'system.admin.dashboard' });
    response.next(new Blob(['report']));
    response.complete();

    expect(audit.track).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      ...viewContext, target: 'questionnaire-report-2024-pl.xlsx',
    }));
    expect(component.isQuestionnaireReportDownloading()).toBe(false);
  });

  it('does not start unavailable report downloads', () => {
    TestBed.inject(TranslocoService).setActiveLang('en');
    component.downloadQuestionnaireReport2024();
    expect(questionnaireReportsService.downloadQuestionnaireReport2024).not.toHaveBeenCalled();
  });

  it('shows request errors without recording a successful download', () => {
    questionnaireReportsService.downloadQuestionnaireReport2024.mockReturnValue(throwError(() => new Error('Report unavailable')));
    audit.track.mockClear();
    component.downloadQuestionnaireReport2024();

    expect(audit.track).not.toHaveBeenCalled();
    expect(component.hasQuestionnaireReportDownloadError()).toBe(true);
    expect(component.isQuestionnaireReportDownloading()).toBe(false);
  });

  it('does not audit success when browser download preparation fails', () => {
    questionnaireReportsService.downloadQuestionnaireReport2024.mockReturnValue(of(new Blob(['report'])));
    vi.stubGlobal('URL', {});
    audit.track.mockClear();
    component.downloadQuestionnaireReport2024();

    expect(audit.track).not.toHaveBeenCalled();
    expect(component.hasQuestionnaireReportDownloadError()).toBe(true);
  });
});
