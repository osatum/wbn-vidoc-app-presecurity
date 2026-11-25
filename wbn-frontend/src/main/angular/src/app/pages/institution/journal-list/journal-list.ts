import {
  ChangeDetectionStrategy,
  afterNextRender,
  AfterRenderRef,
  Component,
  DestroyRef,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  OnDestroy,
  signal,
  untracked,
  ViewChild,
} from '@angular/core';
import { CommonModule, DOCUMENT, ViewportScroller } from '@angular/common';
import { NgbOffcanvas, NgbOffcanvasRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { catchError, filter, finalize, first, map, of, startWith, Subscription, switchMap, tap, timer } from 'rxjs';
import { API as NoUiSliderApi, create as createSlider } from 'nouislider';
import {
  ConfigurationService,
  CountryView,
  DictionaryService,
  JournalListQuery,
  JournalListView,
  JournalSearchMetadata,
  JournalSearchCounts,
  JournalService,
  LanguageView,
  PageResultJournalListView,
  QuestionnaireReportAvailabilityDto,
  QuestionnaireReportsService,
  RatingYearsDto,
  ScientificCategoryDictionaryView,
  MniswDisciplineDictionaryView,
} from '../../../core/backend';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { JournalPagination } from './journal-pagination';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';
import { withNavigationAuditOperation } from '../../../core/interceptors/navigation-audit.interceptor';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import {
  JournalListNavigationState,
  JOURNAL_PAGE_SIZES,
  defaultJournalListQuery,
} from '../../../core/services/journal-list-state';
import { downloadFile } from '../../../shared/file-download/file-download';
import { MultiValueSelect } from '../../../shared/multi-value-select/multi-value-select';
import { JournalNonFeeFilters } from './journal-non-fee-filters';
import { FEE_FILTERS, feeCriteriaFromQuery, feeQuery, isValidFeeCriteria } from './journal-fee-criteria';
import { JournalFeeFilters } from './journal-fee-filters';
import { JournalResultCard } from './journal-result-card';
import { JournalSearchAnalytics } from './journal-search-analytics';
import { JournalRangeFilter } from './journal-range-filter';
import { JournalFilterPanel } from './journal-filter-panel';
import { JournalSearchFields } from './journal-search-fields';
import { JournalBasicResult } from './journal-basic-result';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { JOURNAL_SEARCH_VIEW, JOURNAL_DECISION_CARD_AVAILABLE } from '../../../core/services/operating-profile.service';
import { isValidNonFeeCriteria, nonFeeCriteriaFromQuery, nonFeeQuery } from './journal-non-fee-criteria';
import { JournalAppliedFilters } from './journal-applied-filters';
import {
  CriteriaBounds,
  EMPTY_SURVEY_FILTERS,
  FilterChipLabels,
  JournalFilterChip,
  JournalFilters,
  SURVEY_FILTER_NAMES,
  SurveyFilterName,
  journalCriteriaKey,
  journalFilterChips,
  normalizeFullRanges,
  removeJournalFilter,
} from './journal-filter-criteria';
type SurveyFilterGroup = {
  labelKey: string;
  minimum: SurveyFilterName;
  maximum: SurveyFilterName;
  wholeDays?: boolean;
};

type SortByFormValue = JournalListQuery.SortByEnum;


type IcvRange = {
  from: number;
  to: number;
};

type DisciplineTreeNode = {
  id: string;
  label: string;
  children: DisciplineTreeNode[];
};

type DisciplineOption = {
  id: string;
  label: string;
};

type DisciplineLeaf = {
  id: string;
  label: string;
  parentLabels: string[];
};

const EMPTY_JOURNAL_PAGE: PageResultJournalListView = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};


const DEFAULT_SORT_BY = JournalListQuery.SortByEnum.SortByTitleAsc;

@Component({
  selector: 'app-journal-list',
  providers: [JournalSearchAnalytics],
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    AlertData,
    JournalPagination,
    LocalSpinner,
    TranslocoPipe,
    NgbTooltip,
    MultiValueSelect,
    JournalNonFeeFilters,
    JournalFeeFilters,
    JournalResultCard,
    JournalAppliedFilters,
    JournalRangeFilter,
    JournalFilterPanel,
    JournalSearchFields,
    JournalBasicResult,
    ListPaging,
  ],

  templateUrl: './journal-list.html',
  styleUrl: './journal-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalList
  extends PagedListBase<JournalListQuery, JournalListView, PageResultJournalListView>
  implements OnDestroy
{
  readonly searchView = inject(JOURNAL_SEARCH_VIEW);
  readonly decisionCardAvailable = inject(JOURNAL_DECISION_CARD_AVAILABLE);
  // The page root has a fixed height; position body tooltips relative to the viewport when scrolled.
  readonly tooltipPopperOptions: NgbTooltip['popperOptions'] = (options) => ({
    ...options,
    strategy: 'fixed',
  });
  readonly isBasic = this.searchView === 'BASIC';
  private readonly journalService = inject(JournalService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly configurationService = inject(ConfigurationService);
  private readonly dictionaryService = inject(DictionaryService);
  private readonly questionnaireReportsService = inject(QuestionnaireReportsService);
  private readonly injector = inject(Injector);
  private readonly document = inject(DOCUMENT);
  private readonly fb = inject(FormBuilder);
  private readonly navigationAudit = inject(NavigationAuditService);
  private readonly searchAnalytics = inject(JournalSearchAnalytics);
  private readonly transloco = inject(TranslocoService);
  private readonly journalListQueryState = inject(JournalListQueryStateService);
  readonly selectedSurveyYear = this.journalListQueryState.surveyYear;
  readonly availableSearchYears = signal<number[]>([]);
  readonly hasSearchYearsError = signal(false);
  readonly isSearchYearsLoading = signal(false);
  readonly isSearchYearsReady = signal(false);
  private defaultSearchYear?: number;
  private searchMetadataSubscription?: Subscription;
  private searchCountsSubscription?: Subscription;
  private requestedCountsKey?: string;
  private readonly loadedCountsKey = signal<string | null>(null);
  private readonly searchCounts = signal<JournalSearchCounts | null>(null);
  readonly isSearchCountsLoading = signal(false);
  readonly hasSearchCountsError = signal(false);
  readonly visibleSearchCounts = computed(() =>
    !this.hasUnappliedChanges() && this.loadedCountsKey() === this.countsKey(this.query())
      ? this.searchCounts() : null,
  );
  private contextVersion = this.journalListQueryState.getContextVersion();
  private readonly viewportScroller = inject(ViewportScroller);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly offcanvas = inject(NgbOffcanvas);
  private readonly filterMedia = this.document.defaultView?.matchMedia?.(
    '(max-width: 991.98px), (max-height: 499.98px)',
  );
  readonly compactFilters = signal(this.filterMedia?.matches ?? false);
  private filterPanel?: NgbOffcanvasRef;
  private filterPanelDraft?: JournalFilters;
  private filterScroll = 0;
  private expandedFilterDetails: number[] = [];
  private filterOptions?: HTMLElement;
  private validationRender?: AfterRenderRef;
  @ViewChild(JournalFilterPanel)
  private advancedFilterPanel!: JournalFilterPanel;
  @ViewChild('filterOptions')
  private set filterOptionsRef(element: ElementRef<HTMLElement> | undefined) {
    if (this.filterOptions === element?.nativeElement) return;
    this.filterOptions = element?.nativeElement;
    if (element) {
      afterNextRender(() => this.restoreFilterPanelState(), { injector: this.injector });
    }
  }
  readonly detailsNavigationState = this.journalListQueryState.createDetailsNavigationState();
  readonly showDefaultNotice = signal(false);
  private pendingRestore?: JournalListNavigationState;
  private scrollRender?: AfterRenderRef;
  private restoredFromDetails = false;
  protected readonly journalNameService = inject(JournalNameService);
  private readonly activeLang = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly questionnaireReportLanguage = computed<'pl' | 'en'>(() => {
    const activeLanguage = (this.activeLang() || 'pl').toLowerCase();
    return activeLanguage.startsWith('en') ? 'en' : 'pl';
  });
  private readonly questionnaireReportAvailability =
    signal<QuestionnaireReportAvailabilityDto | null>(null);
  readonly isQuestionnaireReportAvailable = computed(() => {
    if (this.selectedSurveyYear() !== undefined && this.selectedSurveyYear() !== 2024) return false;
    const availability = this.questionnaireReportAvailability();
    return this.questionnaireReportLanguage() === 'en'
      ? availability?.en === true
      : availability?.pl === true;
  });
  readonly isQuestionnaireReportDownloading = signal(false);
  readonly hasQuestionnaireReportDownloadError = signal(false);
  readonly searchMetadata = signal<JournalSearchMetadata | null>(null);
  readonly icvRangeMin = computed(() => this.searchMetadata()?.icvMin ?? null);
  readonly icvRangeMax = computed(() => this.searchMetadata()?.icvMax ?? null);
  readonly minimumIcv = 1;
  readonly icvSliderMin = computed(() => this.icvRangeMin() === null ? null : this.minimumIcv);
  readonly icvSliderMax = computed(() => this.icvRangeMax() === null ? null
    : Math.max(this.minimumIcv, this.icvRangeMax()!));
  readonly isIcvFilterEnabled = computed(() => this.icvRangeMin() !== null && this.icvRangeMax() !== null
    && !this.isSearchMetadataLoading() && !this.hasSearchMetadataError()
    && this.draftFilters().ratingStatus === 'INDEXED');
  readonly hasIcvSliderRange = computed(() => this.icvSliderMin() !== null && this.icvSliderMax() !== null
    && !this.isSearchMetadataLoading() && !this.hasSearchMetadataError()
    && this.icvSliderMin()! < this.icvSliderMax()!);
  readonly hasIcvRangeError = signal(false);
  readonly icvRangeErrorKey = signal<'icvRangeError' | 'icvRangeOrderError'>('icvRangeError');
  readonly mniswPointClasses = signal<number[]>([]);
  readonly isSearchMetadataLoading = signal(false);
  readonly hasSearchMetadataError = signal(false);
  readonly hasMniswRangeError = signal(false);
  readonly hasSurveyRangeError = signal(false);
  readonly hasNonFeeFilterError = signal(false);
  readonly hasFeeFilterError = signal(false);
  readonly surveyFilterGroups: SurveyFilterGroup[] = [
    { labelKey: 'initialAssessmentDays', minimum: 'initialAssessmentDaysFrom', maximum: 'initialAssessmentDaysTo', wholeDays: true },
    { labelKey: 'reviewDays', minimum: 'reviewDaysFrom', maximum: 'reviewDaysTo', wholeDays: true },
    { labelKey: 'publicationDays', minimum: 'publicationDaysFrom', maximum: 'publicationDaysTo', wholeDays: true },
    { labelKey: 'rejectionRate', minimum: 'rejectionRateFrom', maximum: 'rejectionRateTo' },
    {
      labelKey: 'authorForeignAffiliationPercentage',
      minimum: 'authorForeignAffiliationPercentageFrom',
      maximum: 'authorForeignAffiliationPercentageTo',
    },
    {
      labelKey: 'reviewerForeignAffiliationPercentage',
      minimum: 'reviewerForeignAffiliationPercentageFrom',
      maximum: 'reviewerForeignAffiliationPercentageTo',
    },
  ];
  readonly icvRange = signal<IcvRange>({ from: 0, to: 0 });
  readonly processingFilterGroups = this.surveyFilterGroups.filter((group) => group.wholeDays);
  readonly cooperationFilterGroups = this.surveyFilterGroups.filter((group) => !group.wholeDays && group.maximum !== 'rejectionRateTo');
  readonly processFilterGroups = this.surveyFilterGroups.filter((group) => group.maximum === 'rejectionRateTo');
  private readonly ratingYears = signal<RatingYearsDto>({});
  readonly countryOptions = signal<CountryView[]>([]);
  readonly languageOptions = signal<LanguageView[]>([]);
  readonly countrySelectOptions = computed(() => this.countryOptions()
    .filter((country): country is CountryView & { code: string } => !!country.code)
    .map((country) => ({ code: country.code, label: this.countryOptionLabel(country),
      count: this.dictionaryCount('countryCounts', country.code) })));
  readonly languageSelectOptions = computed(() => this.languageOptions()
    .filter((language): language is LanguageView & { code: string } => !!language.code)
    .map((language) => ({ code: language.code, label: this.languageOptionLabel(language),
      count: this.dictionaryCount('languageCounts', language.code) })));
  readonly isCountryOptionsLoading = signal(false);
  readonly isLanguageOptionsLoading = signal(false);
  readonly hasCountryOptionsError = signal(false);
  readonly hasLanguageOptionsError = signal(false);
  readonly sortOptions: { value: JournalListQuery.SortByEnum; labelKey: string }[] = [
    { value: JournalListQuery.SortByEnum.SortByTitleAsc, labelKey: 'page.institution.journalList.sortOptions.titleAsc' },
    { value: JournalListQuery.SortByEnum.SortByTitleDesc, labelKey: 'page.institution.journalList.sortOptions.titleDesc' },
    { value: JournalListQuery.SortByEnum.SortByRatingIcvDesc, labelKey: 'page.institution.journalList.sortOptions.icvDesc' },
    { value: JournalListQuery.SortByEnum.SortByRatingIcvAsc, labelKey: 'page.institution.journalList.sortOptions.icvAsc' },
    { value: JournalListQuery.SortByEnum.SortByRatingMniswDesc, labelKey: 'page.institution.journalList.sortOptions.mniswDesc' },
    { value: JournalListQuery.SortByEnum.SortByRatingMniswAsc, labelKey: 'page.institution.journalList.sortOptions.mniswAsc' },
    { value: JournalListQuery.SortByEnum.SortByPublicationDaysAsc, labelKey: 'page.institution.journalList.sortOptions.publicationDaysAsc' },
    { value: JournalListQuery.SortByEnum.SortByPublicationDaysDesc, labelKey: 'page.institution.journalList.sortOptions.publicationDaysDesc' },
  ];
  private readonly disciplineCategories = signal<ScientificCategoryDictionaryView[]>([]);
  readonly disciplineOptions = computed(() => {
    return this.leafDisciplineOptions(this.buildDisciplineTree(this.disciplineCategories()));
  });
  readonly disciplineSelectOptions = computed(() => this.disciplineOptions().map((option) => ({
    code: option.id, label: option.label, count: this.dictionaryCount('disciplineCounts', option.id),
  })));
  readonly isDisciplineOptionsLoading = signal(false);
  readonly hasDisciplineOptionsError = signal(false);
  private readonly mniswDisciplines = signal<MniswDisciplineDictionaryView[]>([]);
  readonly isMniswDisciplineOptionsLoading = signal(false);
  readonly hasMniswDisciplineOptionsError = signal(false);
  readonly mniswDisciplineSelectOptions = computed(() => this.mniswDisciplines()
    .filter((discipline): discipline is MniswDisciplineDictionaryView & { code: string } => !!discipline.code)
    .map((discipline) => ({ code: discipline.code, label: this.mniswDisciplineLabel(discipline),
      count: this.dictionaryCount('mniswDisciplineCounts', discipline.code) }))
    .sort((first, second) => first.label.localeCompare(second.label, this.activeLang())));
  private readonly isQueryInitialized = signal(false);
  private icvSlider?: NoUiSliderApi;
  private readonly nonFeeFilterComponent = signal<JournalNonFeeFilters | undefined>(undefined);
  @ViewChild(JournalNonFeeFilters)
  private set nonFeeFilterRef(component: JournalNonFeeFilters | undefined) {
    this.nonFeeFilterComponent.set(component);
  }

  @ViewChild('icvRangeSlider')
  private set icvRangeSliderRef(element: ElementRef<HTMLDivElement> | undefined) {
    if (!element) {
      this.destroyIcvSlider();
      return;
    }

    this.createIcvSlider(element.nativeElement);
  }

  readonly filterForm = this.fb.nonNullable.group({
    ...EMPTY_SURVEY_FILTERS,
    feeFilters: this.fb.nonNullable.group(feeCriteriaFromQuery()),
    nonFeeFilters: this.fb.nonNullable.group({
      ...nonFeeCriteriaFromQuery(),
      repositoryIds: this.fb.nonNullable.control<string[]>([]),
      licenseCodes: this.fb.nonNullable.control<string[]>([]),
      accessModels: this.fb.nonNullable.control<string[]>([]),
      frequencyCodes: this.fb.nonNullable.control<string[]>([]),
      reviewModels: this.fb.nonNullable.control<string[]>([]),
    }),
    journalTitle: [''],
    issn: [''],
    publisher: [''],
    countryCodes: [[] as string[]],
    languageCodes: [[] as string[]],
    sortBy: [DEFAULT_SORT_BY as SortByFormValue],
    ratingStatus: ['' as JournalFilters['ratingStatus']],
    icvFrom: [''],
    icvTo: [''],
    mniswOnly: [false],
    mniswDisciplineCodes: [[] as string[]],
    mniswPointsFrom: [''],
    mniswPointsTo: [''],
    disciplineCodes: [[] as string[]],
  });

  private readonly filters = signal<JournalFilters>(this.filterForm.getRawValue());
  private readonly draftFilters = toSignal(this.filterForm.valueChanges.pipe(map(() => this.filterForm.getRawValue())), {
    initialValue: this.filterForm.getRawValue(),
  });
  readonly hasUnappliedChanges = computed(() => journalCriteriaKey(this.draftFilters(), this.criteriaBounds())
    !== journalCriteriaKey(this.filters(), this.criteriaBounds()));
  readonly appliedFilterChips = computed(() => journalFilterChips(this.filters()));
  readonly hasProcessingRangeError = computed(() => this.hasSurveyRangeError()
    && !this.isValidSurveyRanges(this.draftFilters(), this.processingFilterGroups));
  readonly hasProcessRangeError = computed(() => this.hasSurveyRangeError()
    && !this.isValidSurveyRanges(this.draftFilters(), this.processFilterGroups));
  readonly hasCooperationRangeError = computed(() => this.hasSurveyRangeError()
    && !this.isValidSurveyRanges(this.draftFilters(), this.cooperationFilterGroups));
  readonly filterChipLabels = computed<FilterChipLabels>(() => {
    this.activeLang();
    const labels = (options: readonly { code: string; label: string }[]) => Object.fromEntries(options.map((option) => [option.code, option.label]));
    const nonFee = this.nonFeeFilterComponent();
    return {
      countryCodes: {
        pl: this.transloco.translate('page.institution.journalList.poland'),
        ...labels(this.countrySelectOptions()),
      },
      languageCodes: labels(this.languageSelectOptions()),
      disciplineCodes: Object.fromEntries(this.disciplineOptions().map((option) => [option.id, option.label])),
      mniswDisciplineCodes: labels(this.mniswDisciplineSelectOptions()),
      repositoryIds: labels(nonFee?.repositoryOptions() ?? []),
      licenseCodes: labels(nonFee?.licenseOptions() ?? []),
      accessModels: labels(nonFee?.accessOptions() ?? []),
      frequencyCodes: labels(nonFee?.frequencyOptions() ?? []),
      reviewModels: labels(nonFee?.reviewModelOptions() ?? []),
    };
  });
  readonly currentIcvYear = computed(() => {
    return this.selectedSurveyYear() ?? this.ratingYears().currentIcvYear;
  });
  readonly previousIcvYear = computed(() => this.selectedSurveyYear() === undefined
    ? this.ratingYears().previousIcvYear : this.selectedSurveyYear()! - 1);

  readonly query = computed<JournalListQuery>(() => {
    const filters = this.filters();

    const query: JournalListQuery = {
      ...this.buildPagingQuery(),
      searchView: this.searchView,
      surveyYear: this.selectedSurveyYear(),
      ...nonFeeQuery(filters.nonFeeFilters),
      ...feeQuery(filters.feeFilters),
      journalTitle: this.cleanText(filters.journalTitle),
      issn: this.cleanText(filters.issn),
      publisher: this.cleanText(filters.publisher),
      countryCodes: [...filters.countryCodes],
      languageCodes: [...filters.languageCodes],
      disciplineCodes: filters.disciplineCodes.length > 0 ? filters.disciplineCodes : undefined,

      sortBy: filters.sortBy || undefined,
      mniswOnly: filters.mniswOnly || undefined,
      ratingStatus: filters.ratingStatus || undefined,
    };

    if (filters.ratingStatus === 'INDEXED') {
      query.icvFrom = this.optionalIcvValue(filters.icvFrom);
      query.icvTo = this.optionalIcvValue(filters.icvTo);
    }
    if (filters.mniswOnly) {
      query.mniswDisciplineCodes = filters.mniswDisciplineCodes?.length ? [...filters.mniswDisciplineCodes] : undefined;
      query.mniswPointsFrom = this.nonNegativeInteger(filters.mniswPointsFrom);
      query.mniswPointsTo = this.nonNegativeInteger(filters.mniswPointsTo);
    }

    for (const name of SURVEY_FILTER_NAMES) {
      query[name] = this.optionalIcvValue(filters[name]);
    }

    if (this.isBasic) {
      return {
        ...this.buildPagingQuery(), searchView: this.searchView, surveyYear: query.surveyYear,
        journalTitle: query.journalTitle, issn: query.issn, publisher: query.publisher,
        countryCodes: query.countryCodes, languageCodes: query.languageCodes,
        disciplineCodes: query.disciplineCodes, ratingStatus: query.ratingStatus,
        icvFrom: query.icvFrom, icvTo: query.icvTo, sortBy: query.sortBy,
        mniswOnly: query.mniswOnly, mniswDisciplineCodes: query.mniswDisciplineCodes,
        mniswPointsFrom: query.mniswPointsFrom, mniswPointsTo: query.mniswPointsTo,
      };
    }
    return query;
  });

  constructor() {
    super();
    this.refreshIcvControls();
    this.syncMniswControls();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_JOURNAL_PAGE,
      injector: this.injector,
      enabled: computed(() => this.isQueryInitialized() && this.isSearchYearsReady()),
      loader: (query) => this.searchAnalytics.load(query, () =>
        this.journalService.searchJournals(query, undefined, false, {
          context: withNavigationAuditOperation({
            name: 'JournalService.searchJournals',
          }),
        })),
    });
    this.loadInitialQuery();
    effect(() => {
      if (!this.isQueryInitialized() || !this.isSearchYearsReady()) return;
      this.query();
      untracked(() => this.loadSearchCounts());
    });
    this.restoreScrollAfterResults();
    this.trackPageReady();
    this.loadCountryOptions();
    this.loadLanguageOptions();
    this.loadSearchYears();
    this.loadRatingYears();
    this.loadDisciplineOptions();
    this.loadMniswDisciplineOptions();
    this.loadQuestionnaireReportAvailability();
    const changeFilterLayout = () => {
      this.captureFilterPanelState();
      this.closeFilterPanel();
      this.compactFilters.set(this.filterMedia?.matches ?? false);
      afterNextRender(() => this.restoreFilterPanelState(), { injector: this.injector });
    };
    this.filterMedia?.addEventListener('change', changeFilterLayout);
    this.destroyRef.onDestroy(() =>
      this.filterMedia?.removeEventListener('change', changeFilterLayout),
    );
  }

  ngOnDestroy(): void {
    if (this.isQueryInitialized()) {
      this.journalListQueryState.rememberView(this.searchView, this.navigationSnapshot(), this.contextVersion);
    }
    this.validationRender?.destroy();
    this.closeFilterPanel();
    this.cancelScrollRestore();
    this.destroyIcvSlider();
  }

  private loadInitialQuery(): void {
    const returned = this.journalListQueryState.restoreFromDetails();
    const restored = returned && (returned.searchView ?? 'ADVANCED') === this.searchView
      ? returned : this.journalListQueryState.restoreView(this.searchView);
    if (restored) {
      if (this.selectedSurveyYear() === undefined) this.selectedSurveyYear.set(restored.surveyYear);
      this.restoredFromDetails = true;
      this.filterForm.reset(restored.draft);
      this.refreshIcvControls();
      this.syncMniswControls();
      this.filters.set(restored.applied);
      super.changePageSizeValue(this.normalizePageSize(restored.size));
      this.page.set(restored.page);
      this.showDefaultNotice.set(restored.showDefaultNotice);
      this.pendingRestore = restored;
      this.expandedFilterDetails = restored.expandedDetails;
      this.filterScroll = restored.filterScroll ?? 0;
      this.searchAnalytics.begin(this.query(), 'RETURN', restored.analytics);
      this.isQueryInitialized.set(true);
    }
    this.journalListQueryState.freshEntry$
      .pipe(
        map(() => true),
        startWith(!restored),
        filter((fresh) => fresh),
        tap(() => {
          this.contextVersion = this.journalListQueryState.getContextVersion();
          this.closeFilterPanel();
          this.cancelScrollRestore();
          this.restoredFromDetails = false;
          this.isQueryInitialized.set(false);
        }),
        switchMap(() => this.journalListQueryState.getInitialQuery(this.searchView).pipe(
          catchError(() => of(defaultJournalListQuery(this.searchView))),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((query) => {
        if (this.selectedSurveyYear() === undefined && this.defaultSearchYear !== undefined) {
          this.selectedSurveyYear.set(this.defaultSearchYear);
        }
        this.clearValidationErrors();
        this.applyInitialQuery(query);
        this.showDefaultNotice.set(!this.isBasic);
        this.isQueryInitialized.set(true);
        this.viewportScroller.scrollToPosition([0, 0]);
      });
  }

  private restoreScrollAfterResults(): void {
    effect(() => {
      if (this.result().status !== 'success' || !this.pendingRestore) return;
      const restored = this.pendingRestore;
      this.scrollRender?.destroy();
      this.scrollRender = afterNextRender(
        () => {
          if (this.pendingRestore !== restored || this.destroyRef.destroyed) return;
          this.host.nativeElement.querySelectorAll('details').forEach((element, index) => {
            element.open = restored.expandedDetails.includes(index);
          });
          this.restoreFilterPanelState();
          this.viewportScroller.scrollToPosition(restored.scroll);
          this.pendingRestore = undefined;
        },
        { injector: this.injector },
      );
    });
  }

  private cancelScrollRestore(): void {
    this.pendingRestore = undefined;
    this.scrollRender?.destroy();
    this.scrollRender = undefined;
  }

  rememberForDetails(event: MouseEvent, journalId: number | undefined): void {
    if (journalId !== undefined && (event.button === 0 || (event.type === 'auxclick' && event.button === 1)) && !event.altKey) {
      this.searchAnalytics.cardOpen(this.query(), journalId);
    }
    if (
      journalId === undefined || event.button !== 0 || event.ctrlKey ||
      event.metaKey || event.shiftKey || event.altKey
    ) return;
    this.journalListQueryState.rememberForDetails(this.detailsNavigationState.journalListOrigin, journalId, this.navigationSnapshot());
  }

  recordIssuesClick(event: MouseEvent, journalId: number | undefined): void {
    if (journalId === undefined || event.altKey ||
        (event.button !== 0 && !(event.type === 'auxclick' && event.button === 1))) return;
    this.searchAnalytics.issuesClick(this.query(), journalId);
  }

  private navigationSnapshot(): JournalListNavigationState {
    this.captureFilterPanelState();
    return {
      searchView: this.searchView,
      surveyYear: this.selectedSurveyYear(),
      analytics: this.searchAnalytics.snapshot(),
      applied: this.filters(),
      draft: this.filterForm.getRawValue(),
      page: this.page(),
      size: this.buildPagingQuery().size,
      scroll: this.viewportScroller.getScrollPosition(),
      filterScroll: this.filterScroll,
      expandedDetails: this.expandedFilterDetails,
      showDefaultNotice: this.showDefaultNotice(),
    };
  }

  retrySearch(): void {
    this.reloadPage();
  }

  openFilterPanel(): void {
    if (this.isBasic || !this.compactFilters() || this.filterPanel) return;
    this.filterPanelDraft = structuredClone(this.filterForm.getRawValue());
    const panel = this.offcanvas.open(this.advancedFilterPanel.content, {
      animation: false,
      container: this.host.nativeElement,
      position: 'start',
      panelClass: 'w-100',
      ariaLabelledBy: 'journal-filters-heading',
      scroll: false,
    });
    this.filterPanel = panel;
    afterNextRender(() => this.restoreFilterPanelState(), { injector: this.injector });
    panel.dismissed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.captureFilterPanelState();
      const draft = this.filterPanelDraft;
      if (draft) {
        this.filterForm.reset(draft);
        this.refreshIcvControls();
        this.syncMniswControls();
        this.clearValidationErrors();
      }
      this.filterPanel = undefined;
      this.filterPanelDraft = undefined;
    });
    void panel.result.catch(() => undefined);
  }

  cancelFilterPanel(): void {
    this.filterPanel?.dismiss();
  }

  applyPanelFilters(): void {
    if (this.isLoading() || !this.hasUnappliedChanges()) return;
    if (this.applyFilters()) this.closeFilterPanel(true);
  }

  clearPanelFilters(): void {
    this.resetFilters();
    this.closeFilterPanel(true);
  }

  private closeFilterPanel(showResults = false): void {
    const panel = this.filterPanel;
    if (!panel) return;
    this.captureFilterPanelState();
    this.filterPanel = undefined;
    this.filterPanelDraft = undefined;
    if (showResults) {
      panel.hidden
        .pipe(
          first(),
          switchMap(() => timer(0)),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(() => {
          if (this.filterPanel) return;
          const results =
            this.host.nativeElement.querySelector<HTMLElement>('.journal-search-content');
          results?.focus({ preventScroll: true });
          results?.scrollIntoView?.({ block: 'start' });
        });
    }
    panel.close();
  }

  private captureFilterPanelState(): void {
    if (!this.filterOptions?.isConnected) return;
    if (this.compactFilters()) this.filterScroll = this.filterOptions.closest('aside')?.scrollTop ?? 0;
    this.expandedFilterDetails = [...this.filterOptions.querySelectorAll('details')].flatMap(
      (element, index) => (element.open ? [index] : []),
    );
  }

  private restoreFilterPanelState(): void {
    if (!this.filterOptions?.isConnected) return;
    this.filterOptions.querySelectorAll('details').forEach((element, index) => {
      element.open = this.expandedFilterDetails.includes(index);
    });
    const panel = this.filterOptions.closest('aside');
    if (panel) panel.scrollTop = this.compactFilters() ? this.filterScroll : 0;
  }

  private clearValidationErrors(): void {
    this.hasFeeFilterError.set(false);
    this.hasNonFeeFilterError.set(false);
    this.hasIcvRangeError.set(false);
    this.hasSurveyRangeError.set(false);
    this.hasMniswRangeError.set(false);
  }

  private applyInitialQuery(query: JournalListQuery): void {
    const filters = normalizeFullRanges(this.filtersFromQuery(query), this.criteriaBounds());
    super.changePageSizeValue(this.normalizePageSize(query.size));

    this.page.set(this.nonNegativeInteger(query.page) ?? 0);
    this.filterForm.reset(filters);
    this.refreshIcvControls();
    this.syncMniswControls();

    if (this.disciplineOptions().length > 0) {
      this.sanitizeSelectedDisciplines();
    }

    this.filters.set(this.filterForm.getRawValue());
    this.searchAnalytics.begin(this.query(), 'INITIAL');
  }

  private normalizePageSize(size: number | undefined): number {
    return !this.isBasic && JOURNAL_PAGE_SIZES.some((allowed) => allowed === size) ? size! : 10;
  }

  override changePageSizeValue(requestedSize: number): void {
    if ((this.isBasic && requestedSize !== 10)
      || !JOURNAL_PAGE_SIZES.some((allowed) => allowed === requestedSize)
      || requestedSize === this.buildPagingQuery().size) return;
    this.cancelScrollRestore();
    super.changePageSizeValue(requestedSize);
  }

  goToPage(page: number): void {
    if (!this.isLoading() && Number.isSafeInteger(page) && page >= 0 && page < this.totalPages()) {
      this.cancelScrollRestore();
      this.page.set(page);
    }
  }

  private criteriaBounds(): CriteriaBounds {
    return { icvMin: this.icvRangeMin(), icvMax: this.icvRangeMax(), mniswPointClasses: this.mniswPointClasses() };
  }

  removeAppliedFilter(chip: JournalFilterChip): void {
    const current = this.appliedFilterChips().find((entry) => entry.id === chip.id);
    if (!current) return;
    const applied = removeJournalFilter(this.filters(), current.removal);
    const draft = removeJournalFilter(this.filterForm.getRawValue(), current.removal);
    const target = current.removal;
    const controls = this.filterForm.controls;
    this.filterForm.patchValue(draft);
    if (target.kind === 'range') {
      if (target.fields.includes('icvFrom')) {
        this.hasIcvRangeError.set(false);
        this.syncIcvRange();
      }
      if (target.fields.includes('mniswPointsFrom')) this.hasMniswRangeError.set(false);
    }
    if (target.kind === 'mnisw') this.syncMniswControls();
    if (target.kind === 'icvStatus') {
      this.hasIcvRangeError.set(false);
      this.refreshIcvControls();
    }
    if (target.kind === 'fee' && target.maximum) {
      controls.feeFilters.controls[target.maximum].enable({ emitEvent: false });
    }
    if (this.hasFeeFilterError() && isValidFeeCriteria(draft.feeFilters))
      this.hasFeeFilterError.set(false);
    if (this.hasNonFeeFilterError() && isValidNonFeeCriteria(draft.nonFeeFilters))
      this.hasNonFeeFilterError.set(false);
    if (this.hasSurveyRangeError() && this.isValidSurveyRanges(draft))
      this.hasSurveyRangeError.set(false);
    this.filterForm.updateValueAndValidity();
    this.cancelScrollRestore();
    this.showDefaultNotice.set(false);
    this.resetPaging();
    this.filters.set(applied);
    this.searchAnalytics.begin(this.query(), 'CLEAR');
  }

  private filtersFromQuery(query: JournalListQuery): JournalFilters {
    const ratingStatus = query.ratingStatus
      ?? (query.icvFrom != null || query.icvTo != null ? JournalListQuery.RatingStatusEnum.Indexed : '');
    return {
      nonFeeFilters: nonFeeCriteriaFromQuery(query),
      feeFilters: feeCriteriaFromQuery(query),
      journalTitle: query.journalTitle ?? '',
      issn: query.issn ?? '',
      publisher: query.publisher ?? '',
      countryCodes: this.codesFromQuery(query.countryCodes, query.countryCode),
      languageCodes: this.codesFromQuery(query.languageCodes, query.languageCode),
      sortBy: this.sortByFromQuery(query.sortBy),
      ratingStatus,
      icvFrom: ratingStatus === JournalListQuery.RatingStatusEnum.Indexed ? this.numberFilterValue(query.icvFrom) : '',
      icvTo: ratingStatus === JournalListQuery.RatingStatusEnum.Indexed ? this.numberFilterValue(query.icvTo) : '',
      mniswOnly: query.mniswOnly === true,
      mniswDisciplineCodes: query.mniswOnly ? this.disciplineCodesFromQuery(query.mniswDisciplineCodes) : [],
      mniswPointsFrom: query.mniswOnly ? this.numberFilterValue(query.mniswPointsFrom) : '',
      mniswPointsTo: query.mniswOnly ? this.numberFilterValue(query.mniswPointsTo) : '',
      reviewDaysFrom: this.numberFilterValue(query.reviewDaysFrom),
      reviewDaysTo: this.numberFilterValue(query.reviewDaysTo),
      initialAssessmentDaysFrom: this.numberFilterValue(query.initialAssessmentDaysFrom),
      initialAssessmentDaysTo: this.numberFilterValue(query.initialAssessmentDaysTo),
      publicationDaysFrom: this.numberFilterValue(query.publicationDaysFrom),
      publicationDaysTo: this.numberFilterValue(query.publicationDaysTo),
      rejectionRateFrom: this.numberFilterValue(query.rejectionRateFrom),
      rejectionRateTo: this.numberFilterValue(query.rejectionRateTo),
      authorForeignAffiliationPercentageFrom: this.numberFilterValue(query.authorForeignAffiliationPercentageFrom),
      authorForeignAffiliationPercentageTo: this.numberFilterValue(query.authorForeignAffiliationPercentageTo),
      reviewerForeignAffiliationPercentageFrom: this.numberFilterValue(query.reviewerForeignAffiliationPercentageFrom),
      reviewerForeignAffiliationPercentageTo: this.numberFilterValue(query.reviewerForeignAffiliationPercentageTo),
      disciplineCodes: this.disciplineCodesFromQuery(query.disciplineCodes),
    };
  }

  private sortByFromQuery(sortBy: JournalListQuery.SortByEnum | undefined): SortByFormValue {
    if (sortBy && this.visibleSortOptions.some((option) => option.value === sortBy)) {
      return sortBy;
    }

    return DEFAULT_SORT_BY;
  }

  get visibleSortOptions() {
    return this.isBasic ? this.sortOptions.filter((option) =>
      option.value.includes('TITLE') || option.value.includes('ICV')) : this.sortOptions;
  }

  private disciplineCodesFromQuery(disciplineCodes: string[] | undefined): string[] {
    return Array.isArray(disciplineCodes)
      ? disciplineCodes.filter((code): code is string => typeof code === 'string')
      : [];
  }

  private codesFromQuery(codes: string[] | undefined, legacyCode?: string): string[] {
    const values = Array.isArray(codes) ? codes : typeof legacyCode === 'string' ? [legacyCode] : [];
    return [...new Set(values
      .filter((code): code is string => typeof code === 'string')
      .map((code) => code.trim().toLowerCase())
      .filter((code) => !!code))];
  }

  setDictionaryCodes(control: 'countryCodes' | 'languageCodes', codes: string[]): void {
    this.filterForm.controls[control].setValue(this.codesFromQuery(codes));
  }

  private numberFilterValue(value: unknown): string {
    if (typeof value === 'number') {
      return Number.isFinite(value) ? value.toString() : '';
    }

    if (typeof value !== 'string') {
      return '';
    }

    const trimmed = value.trim();

    if (!trimmed) {
      return '';
    }

    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed.toString() : '';
  }

  private nonNegativeInteger(value: unknown): number | undefined {
    const parsed = this.integerValue(value);

    if (parsed === undefined || parsed < 0) {
      return undefined;
    }

    return parsed;
  }

  private integerValue(value: unknown): number | undefined {
    if (typeof value === 'number') {
      return Number.isInteger(value) ? value : undefined;
    }

    if (typeof value !== 'string') {
      return undefined;
    }

    const trimmed = value.trim();

    if (!trimmed) {
      return undefined;
    }

    const parsed = Number(trimmed);
    return Number.isInteger(parsed) ? parsed : undefined;
  }

  private trackPageReady(): void {
    effect(() => {
      const result = this.result();

      if (result.status !== 'success') {
        return;
      }

      this.navigationAudit.track({
        eventType: 'PAGE_READY',
        action: 'journals.list.ready',
        properties: {
          count: result.data.totalElements ?? result.data.data?.length ?? 0,
          size: result.data.size,
        },
      });
    });
  }

  changeSort(): void {
    this.cancelScrollRestore();
    const sortBy = this.sortByFromQuery(this.filterForm.controls.sortBy.value || undefined);
    this.filterForm.controls.sortBy.setValue(sortBy);
    this.resetPaging();
    this.filters.update((applied) => ({ ...applied, sortBy }));
  }

  applyFilters(): boolean {
    this.hasFeeFilterError.set(false);
    this.hasNonFeeFilterError.set(false);
    this.hasIcvRangeError.set(false);
    this.hasSurveyRangeError.set(false);
    this.hasMniswRangeError.set(false);

    const formValue = this.filterForm.getRawValue();
    if (!isValidFeeCriteria(formValue.feeFilters)) {
      this.hasFeeFilterError.set(true);
      const invalidFee = FEE_FILTERS.find(({ flag, maximum }) =>
        !isValidFeeCriteria({
          ...feeCriteriaFromQuery(),
          [flag]: formValue.feeFilters[flag],
          [maximum]: formValue.feeFilters[maximum],
        }),
      );
      this.openAdvancedGroup('fees', invalidFee?.maximum);
      return false;
    }
    if (!isValidNonFeeCriteria(formValue.nonFeeFilters)) {
      this.hasNonFeeFilterError.set(true);
      this.openAdvancedGroup('process');
      return false;
    }
    if (!this.isValidMniswRange(formValue)) {
      this.hasMniswRangeError.set(true);
      this.focusMainFilterError('mnisw');
      return false;
    }
    if (!this.isValidSurveyRanges(formValue)) {
      this.hasSurveyRangeError.set(true);
      if (this.hasProcessingRangeError()) this.openAdvancedGroup('processing');
      else if (this.hasProcessRangeError()) this.openAdvancedGroup('process');
      else if (this.hasCooperationRangeError()) this.openAdvancedGroup('cooperation');
      return false;
    }
    const icvError = this.validateIcvRange(formValue.icvFrom, formValue.icvTo);
    if (icvError) {
      this.icvRangeErrorKey.set(icvError);
      this.hasIcvRangeError.set(true);
      this.focusMainFilterError('icv');
      return false;
    }
    const disciplineCodes = this.selectableDisciplineCodes(formValue.disciplineCodes);

    if (disciplineCodes.length !== formValue.disciplineCodes.length) {
      this.filterForm.controls.disciplineCodes.setValue(disciplineCodes, { emitEvent: false });
    }

    this.resetPaging();
    this.filters.set(normalizeFullRanges({ ...formValue, disciplineCodes }, this.criteriaBounds()));
    this.searchAnalytics.begin(this.query(), 'SEARCH');
    this.cancelScrollRestore();
    this.showDefaultNotice.set(false);
    this.filterForm.updateValueAndValidity();
    this.loadSearchCounts(true);
    return true;
  }

  resetFilters(): void {
    this.cancelScrollRestore();
    this.showDefaultNotice.set(false);
    this.clearValidationErrors();
    this.filterForm.reset({
      ...EMPTY_SURVEY_FILTERS,
      nonFeeFilters: nonFeeCriteriaFromQuery(),
      feeFilters: feeCriteriaFromQuery(),
      journalTitle: '',
      issn: '',
      publisher: '',
      countryCodes: [],
      languageCodes: [],
      sortBy: DEFAULT_SORT_BY,
      ratingStatus: '',
      icvFrom: '',
      icvTo: '',
      mniswOnly: false,
      mniswDisciplineCodes: [],
      mniswPointsFrom: '',
      mniswPointsTo: '',
      disciplineCodes: [],
    });
    this.refreshIcvControls();
    this.syncMniswControls();

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
    this.searchAnalytics.begin(this.query(), 'CLEAR');
  }

  onMniswToggle(): void {
    this.syncMniswControls();
  }

  setMniswDisciplineCodes(codes: string[]): void {
    if (!this.filterForm.controls.mniswOnly.value) return;
    const available = new Set(this.mniswDisciplineSelectOptions().map((option) => option.code));
    this.filterForm.controls.mniswDisciplineCodes.setValue([...new Set(codes.filter((code) => available.has(code)))]);
  }

  loadMniswDisciplineOptions(): void {
    if (this.isMniswDisciplineOptionsLoading()) return;
    this.isMniswDisciplineOptionsLoading.set(true);
    this.hasMniswDisciplineOptionsError.set(false);
    this.dictionaryService.findMniswDisciplines().pipe(
      takeUntilDestroyed(this.destroyRef),
      catchError(() => {
        this.hasMniswDisciplineOptionsError.set(true);
        return of(null);
      }),
      finalize(() => this.isMniswDisciplineOptionsLoading.set(false)),
    ).subscribe((disciplines) => {
      if (disciplines) this.mniswDisciplines.set(disciplines);
    });
  }

  private mniswDisciplineLabel(discipline: MniswDisciplineDictionaryView): string {
    const language = this.activeLang().toLowerCase().split('-')[0];
    const translated = language === 'ru' ? discipline.nameRu
      : language === 'en' ? discipline.nameEn : discipline.namePl;
    return translated || discipline.namePl || discipline.nameEn || discipline.nameRu || discipline.code || '';
  }

  toggleIcvStatus(status: JournalListQuery.RatingStatusEnum): void {
    const selected = this.filterForm.controls.ratingStatus.value === status;
    this.filterForm.patchValue({ ratingStatus: selected ? '' : status, icvFrom: '', icvTo: '' });
    this.hasIcvRangeError.set(false);
    this.refreshIcvControls();
  }

  private focusMainFilterError(group: 'icv' | 'mnisw'): void {
    this.closeFilterPanel();
    this.validationRender?.destroy();
    this.validationRender = afterNextRender(() => {
      const selector = group === 'icv'
        ? '.icv-range[aria-invalid="true"], .icv-range [aria-invalid="true"]:not([disabled])'
        : '[data-main-group="mnisw"] [aria-invalid="true"]:not([disabled])';
      const invalid = this.host.nativeElement.querySelector<HTMLElement>(selector);
      invalid?.focus({ preventScroll: true });
      invalid?.scrollIntoView?.({ block: 'nearest' });
    }, { injector: this.injector });
  }

  private openAdvancedGroup(group: string, controlId?: string): void {
    this.openFilterPanel();
    const selector = `[data-advanced-group="${group}"]`;
    const element = this.host.nativeElement.querySelector<HTMLDetailsElement>(selector);
    if (element) element.open = true;
    this.validationRender?.destroy();
    this.validationRender = afterNextRender(
      () => {
        const section = this.host.nativeElement.querySelector<HTMLDetailsElement>(selector);
        if (!section) return;
        section.open = true;
        const invalid = section.querySelector<HTMLElement>(
          controlId ? `#${controlId}` : '[aria-invalid="true"]:not([disabled])',
        );
        invalid?.focus({ preventScroll: true });
        invalid?.scrollIntoView?.({ block: 'nearest' });
      },
      { injector: this.injector },
    );
  }

  private syncMniswControls(): void {
    this.hasMniswRangeError.set(false);
    const { mniswOnly, mniswPointsFrom, mniswPointsTo } = this.filterForm.controls;
    for (const control of [mniswPointsFrom, mniswPointsTo]) {
      if (mniswOnly.value) {
        control.enable({ emitEvent: false });
      } else {
        control.setValue('', { emitEvent: false });
        control.disable({ emitEvent: false });
      }
    }
    const disciplines = this.filterForm.controls.mniswDisciplineCodes;
    if (mniswOnly.value) {
      disciplines.enable({ emitEvent: false });
    } else {
      disciplines.setValue([], { emitEvent: false });
      disciplines.disable({ emitEvent: false });
    }
    this.filterForm.updateValueAndValidity();
  }

  loadSearchMetadata(force = false): void {
    if (!this.isSearchYearsReady()) return;
    if (this.isSearchMetadataLoading() && !force) return;
    this.searchMetadataSubscription?.unsubscribe();
    this.isSearchMetadataLoading.set(true);
    this.hasSearchMetadataError.set(false);
    this.refreshIcvControls();
    this.searchMetadataSubscription = this.journalService.getJournalSearchMetadata('body', false, {
      transferCache: false, searchView: this.searchView, surveyYear: this.selectedSurveyYear(),
    }).pipe(
      takeUntilDestroyed(this.destroyRef),
      catchError(() => {
        this.hasSearchMetadataError.set(true);
        return of(null);
      }),
      finalize(() => {
        this.isSearchMetadataLoading.set(false);
        this.refreshIcvControls();
      }),
    ).subscribe((metadata) => {
      if (metadata) {
        this.searchMetadata.set(metadata);
        this.mniswPointClasses.set(metadata.mniswPointClasses ?? []);
      }
    });
  }

  private countsKey(query: JournalListQuery): string {
    const { page, size, first, sortBy, ...criteria } = query;
    return JSON.stringify(criteria);
  }

  loadSearchCounts(force = false): void {
    if (!this.isQueryInitialized() || !this.isSearchYearsReady()) return;
    const { page, size, first, sortBy, ...criteria } = this.query();
    const key = this.countsKey(criteria);
    if (!force && key === this.requestedCountsKey) return;
    this.searchCountsSubscription?.unsubscribe();
    this.requestedCountsKey = key;
    this.loadedCountsKey.set(null);
    this.isSearchCountsLoading.set(true);
    this.hasSearchCountsError.set(false);
    this.searchCountsSubscription = this.journalService.getJournalSearchCounts(criteria).pipe(
      takeUntilDestroyed(this.destroyRef),
      catchError(() => {
        this.hasSearchCountsError.set(true);
        return of(null);
      }),
      finalize(() => this.isSearchCountsLoading.set(false)),
    ).subscribe((counts) => {
      this.searchCounts.set(counts);
      if (counts) this.loadedCountsKey.set(key);
    });
  }

  private isValidMniswRange(filters: JournalFilters): boolean {
    if (!filters.mniswOnly) {
      return true;
    }
    const isValidBound = (value: string) => !value.trim() || (
      !this.isSearchMetadataLoading() && !this.hasSearchMetadataError()
      && /^\d+$/.test(value.trim()) && this.mniswPointClasses().includes(Number(value))
    );
    const from = this.nonNegativeInteger(filters.mniswPointsFrom);
    const to = this.nonNegativeInteger(filters.mniswPointsTo);
    return isValidBound(filters.mniswPointsFrom) && isValidBound(filters.mniswPointsTo)
      && (from === undefined || to === undefined || from <= to);
  }



  trackByJournal(_: number, journal: JournalListView): string {
    return journal.id?.toString() ?? journal.title ?? '';
  }


  downloadQuestionnaireReport2024(): void {
    if (!this.isQuestionnaireReportAvailable() || this.isQuestionnaireReportDownloading()) {
      return;
    }

    const language = this.questionnaireReportLanguage();
    const filename = `questionnaire-report-2024-${language}.xlsx`;
    const viewContext = this.navigationAudit.captureView();
    this.isQuestionnaireReportDownloading.set(true);
    this.hasQuestionnaireReportDownloadError.set(false);
    this.questionnaireReportsService
      .downloadQuestionnaireReport2024(language, 'body', false, {
        context: withNavigationAuditOperation({
          name: 'QuestionnaireReportsService.downloadQuestionnaireReport2024',
          viewContext,
        }),
        transferCache: false,
      })
      .pipe(finalize(() => this.isQuestionnaireReportDownloading.set(false)))
      .subscribe({
        next: (report) => {
          if (!downloadFile(this.document, report, filename)) {
            this.hasQuestionnaireReportDownloadError.set(true);
            return;
          }

          this.navigationAudit.track({
            ...viewContext,
            eventType: 'FILE_DOWNLOAD',
            action: 'questionnaire.report.bulk.download',
            target: filename,
            resource: { type: 'questionnaire-report', id: '2024' },
            success: true,
            properties: { year: 2024, language, format: 'xlsx', sizeBytes: report.size },
          });
        },
        error: () => {
          this.hasQuestionnaireReportDownloadError.set(true);
          this.loadQuestionnaireReportAvailability();
        },
      });
  }

  private isDisciplineSelectable(code: string): boolean {
    return this.disciplineOptions().some((option) => option.id === code);
  }

  private loadCountryOptions(): void {
    this.isCountryOptionsLoading.set(true);
    this.hasCountryOptionsError.set(false);

    this.dictionaryService
      .findCountries()
      .pipe(
        catchError(() => {
          this.hasCountryOptionsError.set(true);
          return of([]);
        }),
        finalize(() => this.isCountryOptionsLoading.set(false)),
      )
      .subscribe((countries) => {
        this.countryOptions.set(countries);
      });
  }

  private loadLanguageOptions(): void {
    this.isLanguageOptionsLoading.set(true);
    this.hasLanguageOptionsError.set(false);

    this.dictionaryService
      .findLanguages()
      .pipe(
        catchError(() => {
          this.hasLanguageOptionsError.set(true);
          return of([]);
        }),
        finalize(() => this.isLanguageOptionsLoading.set(false)),
      )
      .subscribe((languages) => {
        this.languageOptions.set(languages);
      });
  }

  countryOptionLabel(country: CountryView): string {
    this.activeLang();
    return this.journalNameService.getCountryName(country) || country.code || '-';
  }

  countryName(country: CountryView | undefined): string {
    this.activeLang();
    return this.journalNameService.getCountryName(country) || country?.code || '-';
  }

  languageOptionLabel(language: LanguageView): string {
    this.activeLang();
    return this.journalNameService.getLanguageName(language) || language.code || '-';
  }

  private loadRatingYears(): void {
    this.configurationService
      .ratingYears()
      .pipe(
        catchError(() => {
          return of(undefined);
        })
      )
      .subscribe((ratingYears) => {
        if (!ratingYears) {
          this.ratingYears.set({});
          return;
        }

        this.ratingYears.set(ratingYears);
      });
  }

  loadSearchYears(): void {
    if (this.isSearchYearsLoading()) return;
    this.isSearchYearsLoading.set(true);
    this.hasSearchYearsError.set(false);
    this.configurationService.getSearchYears().pipe(
      takeUntilDestroyed(this.destroyRef),
      catchError(() => {
        this.hasSearchYearsError.set(true);
        return of(null);
      }),
      finalize(() => this.isSearchYearsLoading.set(false)),
    ).subscribe((configuration) => {
      if (!configuration) return;
      const years = configuration.availableYears;
      if (!years.length || !years.includes(configuration.defaultYear)) {
        this.hasSearchYearsError.set(true);
        return;
      }
      this.availableSearchYears.set(years);
      this.defaultSearchYear = configuration.defaultYear;
      const selected = this.selectedSurveyYear();
      this.selectedSurveyYear.set(selected !== undefined && years.includes(selected) ? selected : configuration.defaultYear);
      this.isSearchYearsReady.set(true);
      this.loadSearchMetadata(true);
    });
  }

  selectSurveyYear(value: string): void {
    const year = Number(value);
    if (!this.availableSearchYears().includes(year) || year === this.selectedSurveyYear()) return;
    this.cancelScrollRestore();
    this.resetPaging();
    this.journalListQueryState.selectSurveyYear(year);
    this.searchMetadata.set(null);
    this.mniswPointClasses.set([]);
    this.loadSearchMetadata(true);
    this.searchAnalytics.begin(this.query(), 'SEARCH');
  }

  private loadDisciplineOptions(): void {
    this.isDisciplineOptionsLoading.set(true);
    this.hasDisciplineOptionsError.set(false);

    this.dictionaryService
      .findScientificCategories()
      .pipe(
        catchError(() => {
          this.hasDisciplineOptionsError.set(true);
          return of([]);
        }),
        finalize(() => this.isDisciplineOptionsLoading.set(false)),
      )
      .subscribe((categories) => {
        this.disciplineCategories.set(categories);
        if (!this.restoredFromDetails && !this.hasDisciplineOptionsError()) this.sanitizeSelectedDisciplines();
      });
  }

  private buildDisciplineTree(
    categories: ScientificCategoryDictionaryView[],
  ): DisciplineTreeNode[] {
    const nodeById = new Map<string, DisciplineTreeNode>();
    const categoryById = new Map<string, ScientificCategoryDictionaryView>();

    categories
      .filter((category) => category.id !== undefined)
      .forEach((category) => {
        const id = category.id!.toString();
        categoryById.set(id, category);
        nodeById.set(id, {
          id,
          label: this.disciplineLabel(category),
          children: [],
        });
      });

    const roots: DisciplineTreeNode[] = [];

    categoryById.forEach((category, id) => {
      const node = nodeById.get(id);

      if (!node) {
        return;
      }

      const parentId = category.parentId?.toString();
      const parent = parentId === undefined ? undefined : nodeById.get(parentId);

      if (parent) {
        parent.children.push(node);
        return;
      }

      roots.push(node);
    });

    this.sortDisciplineNodes(roots);
    return roots;
  }

  private leafDisciplineOptions(nodes: DisciplineTreeNode[]): DisciplineOption[] {
    const leaves = this.disciplineLeaves(nodes);
    const labelCounts = new Map<string, number>();

    leaves.forEach((leaf) => {
      const label = this.normalizeDisciplineSearch(leaf.label);
      labelCounts.set(label, (labelCounts.get(label) ?? 0) + 1);
    });

    return leaves
      .map((leaf) => {
        const normalizedLabel = this.normalizeDisciplineSearch(leaf.label);
        const hasDuplicateLabel = (labelCounts.get(normalizedLabel) ?? 0) > 1;
        const label = hasDuplicateLabel && leaf.parentLabels.length > 0
          ? `${leaf.label} — ${leaf.parentLabels.join(' / ')}`
          : leaf.label;

        return {
          id: leaf.id,
          label,
        };
      })
      .sort((first, second) => first.label.localeCompare(second.label, this.activeLang()));
  }

  private loadQuestionnaireReportAvailability(): void {
    this.questionnaireReportsService
      .questionnaireReportAvailability('body', false, { transferCache: false })
      .pipe(catchError(() => of(null)))
      .subscribe((availability) => this.questionnaireReportAvailability.set(availability));
  }

  private disciplineLeaves(nodes: DisciplineTreeNode[], parentLabels: string[] = []): DisciplineLeaf[] {
    return nodes.flatMap((node) => {
      if (node.children.length === 0) {
        return [{ id: node.id, label: node.label, parentLabels }];
      }

      return this.disciplineLeaves(node.children, [...parentLabels, node.label]);
    });
  }

  private sanitizeSelectedDisciplines(): void {
    const selected = this.filterForm.controls.disciplineCodes.value;

    if (selected.length === 0) {
      return;
    }

    const next = this.selectableDisciplineCodes(selected);

    if (next.length === selected.length) {
      return;
    }

    this.filterForm.controls.disciplineCodes.setValue(next, { emitEvent: false });
    this.filterForm.updateValueAndValidity();
  }

  private selectableDisciplineCodes(codes: string[]): string[] {
    const selectable = new Set(
      this.disciplineOptions()
        .map((option) => option.id),
    );

    return codes.filter((code) => selectable.has(code));
  }

  private sortDisciplineNodes(nodes: DisciplineTreeNode[]): void {
    nodes.sort((a, b) => a.label.localeCompare(b.label, this.activeLang()));
    nodes.forEach((node) => this.sortDisciplineNodes(node.children));
  }

  private disciplineLabel(category: ScientificCategoryDictionaryView): string {
    const activeLanguage = this.activeLang().toLowerCase();

    if (activeLanguage === 'pl') {
      return category.namePl ?? category.nameEn ?? category.nameRu ?? category.id?.toString() ?? '-';
    }

    return category.nameEn ?? category.namePl ?? category.nameRu ?? category.id?.toString() ?? '-';
  }

  private normalizeDisciplineSearch(value: string): string {
    return value.trim().toLocaleLowerCase(this.activeLang());
  }

  private createIcvSlider(element: HTMLDivElement): void {
    if (this.icvSlider?.target === element || !this.hasIcvSliderRange()) {
      return;
    }
    this.destroyIcvSlider();
    const range = this.icvRange();
    this.icvSlider = createSlider(element, {
      start: [range.from, range.to],
      connect: true,
      step: 0.01,
      range: { min: this.icvSliderMin()!, max: this.icvSliderMax()! },
      tooltips: true,
      format: {
        to: (value) => value.toFixed(2),
        from: (value) => Number(value),
      },
    });
    this.icvSlider.on('slide', (_values, _handle, unencoded) => {
      if (!this.isIcvFilterEnabled()) return;
      this.hasIcvRangeError.set(false);
      const from = Number(unencoded[0].toFixed(2));
      const to = Number(unencoded[1].toFixed(2));
      this.icvRange.set({ from, to });
      this.filterForm.patchValue({ icvFrom: from.toString(), icvTo: to.toString() });
    });
    if (!this.isIcvFilterEnabled()) this.icvSlider.disable();
  }

  setDisciplineCodes(codes: string[]): void {
    const current = this.filterForm.controls.disciplineCodes.value;
    const valid = [...new Set(codes)].filter((code) => current.includes(code) || this.isDisciplineSelectable(code));
    this.filterForm.controls.disciplineCodes.setValue(valid);
  }

  private destroyIcvSlider(): void {
    this.icvSlider?.destroy();
    this.icvSlider = undefined;
  }

  syncIcvRange(): void {
    if (!this.isIcvFilterEnabled()) {
      if (this.hasIcvSliderRange()) {
        const range = { from: this.icvSliderMin()!, to: this.icvSliderMax()! };
        this.icvRange.set(range);
        this.icvSlider?.set([range.from, range.to]);
      }
      return;
    }
    const minimum = this.icvSliderMin()!;
    const maximum = this.icvSliderMax()!;
    const { icvFrom, icvTo } = this.filterForm.getRawValue();
    if (this.hasIcvRangeError()) {
      const error = this.validateIcvRange(icvFrom, icvTo);
      this.hasIcvRangeError.set(error !== null);
      if (error) this.icvRangeErrorKey.set(error);
    }
    const from = Math.min(maximum, Math.max(minimum, this.optionalIcvValue(icvFrom) ?? minimum));
    const to = Math.min(maximum, Math.max(minimum, this.optionalIcvValue(icvTo) ?? maximum));
    const range = { from: Math.min(from, to), to: Math.max(from, to) };
    this.icvRange.set(range);
    this.icvSlider?.set([range.from, range.to]);
  }

  private refreshIcvControls(): void {
    for (const control of [this.filterForm.controls.icvFrom, this.filterForm.controls.icvTo]) {
      if (this.isIcvFilterEnabled()) {
        control.enable({ emitEvent: false });
      } else {
        control.disable({ emitEvent: false });
      }
    }
    if (this.hasIcvSliderRange()) {
      this.icvSlider?.updateOptions({ range: { min: this.icvSliderMin()!, max: this.icvSliderMax()! } }, false);
    }
    if (this.hasIcvSliderRange() && this.isIcvFilterEnabled()) {
      this.icvSlider?.enable();
    } else {
      this.icvSlider?.disable();
    }
    this.syncIcvRange();
  }

  dictionaryCount(field: 'countryCounts' | 'languageCounts' | 'disciplineCounts' | 'mniswDisciplineCounts', code: string): number | undefined {
    const counts = this.visibleSearchCounts()?.[field];
    return counts ? counts[code.toLowerCase()] ?? 0 : undefined;
  }

  private cleanText(value: string): string | undefined {
    const trimmed = value.trim();
    return trimmed || undefined;
  }

  private validateIcvRange(from: string, to: string): 'icvRangeError' | 'icvRangeOrderError' | null {
    const isValidBound = (value: string) => {
      if (!value.trim()) {
        return true;
      }
      const parsed = this.optionalIcvValue(value);
      return this.isIcvFilterEnabled() && /^\d+(?:[.,]\d{1,2})?$/.test(value.trim()) && parsed !== undefined
        && parsed >= this.minimumIcv;
    };
    if (!isValidBound(from) || !isValidBound(to)) return 'icvRangeError';
    const minimum = this.optionalIcvValue(from);
    const maximum = this.optionalIcvValue(to);
    return minimum !== undefined && maximum !== undefined && minimum > maximum ? 'icvRangeOrderError' : null;
  }

  private isValidSurveyRanges(filters: JournalFilters, groups: readonly SurveyFilterGroup[] = this.surveyFilterGroups): boolean {
    return groups.every((group) => {
      const minimum = filters[group.minimum].trim();
      const maximum = filters[group.maximum].trim();
      const isValidBound = (value: string) => {
        if (!value) {
          return true;
        }
        const pattern = group.wholeDays ? /^\d+$/ : /^\d+(?:[.,]\d+)?$/;
        const number = this.optionalIcvValue(value);
        return pattern.test(value) && number !== undefined && number >= 0
          && number <= (group.wholeDays ? 2147483647 : 100);
      };
      const from = this.optionalIcvValue(minimum);
      const to = this.optionalIcvValue(maximum);
      return isValidBound(minimum) && isValidBound(maximum)
        && (from === undefined || to === undefined || from <= to);
    });
  }

  private optionalIcvValue(value: string): number | undefined {
    const trimmed = value.trim().replace(',', '.');

    if (!trimmed) {
      return undefined;
    }

    const parsed = Number(trimmed);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

}
