import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CommonModule, Location, ViewportScroller } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, distinctUntilChanged, filter, map, merge, of, scan, startWith, switchMap, tap } from 'rxjs';
import {
  ApiErrorResponse,
  JournalService,
  JournalView,
} from '../../../core/backend';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { JournalIssuesLinksService } from '../../../core/services/journal-issues-links.service';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import { OperatingProfileService, canOpenDecisionCard } from '../../../core/services/operating-profile.service';
import { JournalQuestionnaire } from './journal-questionnaire/journal-questionnaire';
import { JournalInformationTabs } from '../../shared/journal-information-tabs/journal-information-tabs';
import { SafeImageComponent } from '../../../shared/safe-image/safe-image.component';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';
import { withNavigationAuditOperation } from '../../../core/interceptors/navigation-audit.interceptor';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { JournalIcvCard } from '../../shared/journal-icv-card/journal-icv-card';
import { JournalDecisionApi } from '../../../core/services/journal-decision-api';
import {
  JournalQuestionnaireChartsComponent
} from './journal-questionnaire-charts/journal-questionnaire-charts-component';

type JournalDetailsState = {
  status: 'loading' | 'success' | 'error';
  data: JournalView | null;
  error: unknown | null;
  journalId: number | null;
  surveyYear?: number;
  selectableSurveyYears: number[];
};

const INITIAL_JOURNAL_DETAILS_STATE: JournalDetailsState = {
  status: 'loading',
  data: null,
  error: null,
  journalId: null,
  selectableSurveyYears: [],
};

@Component({
  selector: 'app-journal-details',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    JournalQuestionnaire,
    JournalInformationTabs,
    SafeImageComponent,
    LocalSpinner,
    TranslocoPipe,
    JournalIcvCard,
    JournalQuestionnaireChartsComponent,
  ],
  templateUrl: './journal-details.html',
  styleUrls: [
    '../../../shared/keyboard-focus.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly journalService = inject(JournalService);
  private readonly decisionApi = inject(JournalDecisionApi);
  private readonly navigationAudit = inject(NavigationAuditService);
  private readonly transloco = inject(TranslocoService);
  private readonly viewportScroller = inject(ViewportScroller);
  private readonly journalListQueryState = inject(JournalListQueryStateService);
  readonly journalListReturnState = () => this.journalListQueryState.returnNavigationState();
  readonly decisionNavigationState = () =>
    this.journalListQueryState.journalNavigationState(this.selectedSurveyYear());
  readonly decisionCardAvailable = toSignal(
    inject(OperatingProfileService).currentContext().pipe(
      map(canOpenDecisionCard),
      catchError(() => of(false)),
    ),
    { initialValue: false },
  );
  protected readonly journalNameService = inject(JournalNameService);
  protected readonly showNotIndexedQuestionnaire = signal(false);
  readonly icvHistoryOpen = signal(false);
  private lastReadyJournalId?: number;
  private lastRequestedJournalId?: number | null;

  private readonly surveyYearChanges = new Subject<{ id: number; surveyYear: number }>();
  private readonly journalReloads = new Subject<{ id: number; surveyYear?: number }>();
  private readonly journalId$ = merge(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      startWith(null),
      map(() => ({
        id: this.parseJournalId(this.route.snapshot.paramMap.get('id')),
        surveyYear: this.navigationSurveyYear(),
      })),
    ),
    this.surveyYearChanges,
  ).pipe(
    distinctUntilChanged((previous, current) => previous.id === current.id && previous.surveyYear === current.surveyYear),
    tap(({ id }) => {
      this.showNotIndexedQuestionnaire.set(false);
      this.icvHistoryOpen.set(false);
      if (id !== this.lastRequestedJournalId) {
        this.scrollToTop();
      }
      this.lastRequestedJournalId = id;
    }),
  );

  readonly state = toSignal(
    merge(this.journalId$, this.journalReloads).pipe(
      switchMap(({ id, surveyYear }) => {
        if (id === null) {
          return of({
            ...INITIAL_JOURNAL_DETAILS_STATE,
            status: 'error',
            error: new Error(this.transloco.translate('page.institution.journalDetails.invalidId')),
          } satisfies JournalDetailsState);
        }

        return this.journalService
          .getJournal(id, undefined, false, {
            surveyYear,
            context: withNavigationAuditOperation({
              name: 'JournalService.getJournal',
            }),
          })
          .pipe(
            map(
              (journal): JournalDetailsState => ({
                ...INITIAL_JOURNAL_DETAILS_STATE,
                status: 'success',
                data: journal,
                journalId: id,
                surveyYear: journal.surveyYear ?? surveyYear,
              }),
            ),
            startWith({
              ...INITIAL_JOURNAL_DETAILS_STATE,
              journalId: id,
              surveyYear,
            } satisfies JournalDetailsState),
            catchError((error: HttpErrorResponse) =>
              of({
                ...INITIAL_JOURNAL_DETAILS_STATE,
                status: 'error',
                error,
                journalId: id,
                surveyYear,
              } satisfies JournalDetailsState),
            ),
          );
      }),
      scan((previous: JournalDetailsState, current: JournalDetailsState): JournalDetailsState => {
        const sameJournal = previous.journalId === current.journalId;
        const data = current.data ?? (sameJournal ? previous.data : null);
        const surveyYear = current.surveyYear ?? data?.surveyYear ?? data?.currentIcvYear;
        return {
          ...current,
          data,
          surveyYear,
          selectableSurveyYears: [...new Set([
            ...(sameJournal ? previous.selectableSurveyYears : []),
            ...(current.data?.availableSurveyYears ?? []),
            ...(surveyYear === undefined ? [] : [surveyYear]),
          ])].sort((firstYear, secondYear) => secondYear - firstYear),
        };
      }, INITIAL_JOURNAL_DETAILS_STATE),
    ),
    {
      initialValue: INITIAL_JOURNAL_DETAILS_STATE,
    },
  );

  readonly journal = computed(() => this.state().data);
  private readonly issuesLinks = inject(JournalIssuesLinksService);
  readonly issuesUrl = computed(() => this.issuesLinks.url(this.journal()?.id));
  readonly icvHistory = computed(() => (this.journal()?.icvRatings ?? []).map(rating => ({
    year: rating.year, status: rating.ratingStatus, value: rating.currentMethodPoint,
  })));
  readonly selectedSurveyYear = computed(() => this.state().surveyYear);
  readonly icvBenchmarks = toSignal(toObservable(computed(() => {
    const state = this.state();
    const journal = state.data;
    return this.decisionCardAvailable() && state.status === 'success' && journal?.id &&
      journal.currentStatus === JournalView.CurrentStatusEnum.Indexed &&
      journal.currentIcvYear != null && Number.isInteger(journal.currentIcvYear) &&
      journal.currentIcvYear === state.surveyYear &&
      journal.currentIcvPoints != null && Number.isFinite(journal.currentIcvPoints) && journal.currentIcvPoints >= 0
      ? { id: journal.id, year: journal.currentIcvYear, value: journal.currentIcvPoints }
      : undefined;
  })).pipe(
    distinctUntilChanged((first, second) => first?.id === second?.id && first?.year === second?.year && first?.value === second?.value),
    switchMap(context => context
      ? this.decisionApi.get(context.id, context.year).pipe(startWith(undefined), catchError(() => of(undefined)))
      : of(undefined)),
  ), { initialValue: undefined });
  readonly selectableSurveyYears = computed(() => this.state().selectableSurveyYears);
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');
  readonly errorMessage = computed(() => this.loadErrorMessage(this.state().error));
  readonly title = computed(() => this.journalTitle(this.journal()));
  readonly titleEnglish = computed(() => this.journalTitleEnglish(this.journal()));
  readonly pissn = computed(() => this.journalPissn(this.journal()));
  readonly cover = computed(() => this.journalCover(this.journal()));
  readonly eissn = computed(() => this.journalEissn(this.journal()));
  readonly isPissn = computed(() => this.isJournalPissn(this.journal()));
  readonly isEissn = computed(() => this.isJournalEissn(this.journal()));
  readonly isWww = computed(() => this.isJournalWww(this.journal()));
  readonly languages = computed(() => this.journal()?.languages ?? []);

  constructor() {
    this.trackPageReady();
  }

  selectSurveyYear(year: number): void {
    const journal = this.journal();
    if (!journal?.id || year === this.selectedSurveyYear() || !this.selectableSurveyYears().includes(year)) return;
    this.location.replaceState(this.location.path(true), '', { ...this.historyState(), surveyYear: year });
    this.surveyYearChanges.next({ id: journal.id, surveyYear: year });
  }

  retrySurveyYear(): void {
    const id = this.journal()?.id;
    if (id !== undefined) {
      this.journalReloads.next({ id, surveyYear: this.selectedSurveyYear() });
    }
  }

  private navigationSurveyYear(): number | undefined {
    const state = this.router.currentNavigation()?.extras.state ?? this.historyState();
    const year: unknown = state['surveyYear'];
    return typeof year === 'number' && Number.isInteger(year) && year > 0 ? year : undefined;
  }

  private historyState(): Record<string, unknown> {
    const state: unknown = this.location.getState();
    return typeof state === 'object' && state !== null ? state as Record<string, unknown> : {};
  }

  journalTitle(journal: JournalView | null): string {
    return (
      journal?.titleOriginal ||
      journal?.titleEnglish ||
      journal?.titleShort ||
      this.transloco.translate('page.institution.journalDetails.fallbackTitle')
    );
  }
  private journalCover(journal: null | JournalView) {
    return journal?.cover || '';
  }
  journalTitleEnglish(journal: JournalView | null): string {
    return (
      journal?.titleEnglish ||
      this.transloco.translate('page.institution.journalDetails.fallbackTitle')
    );
  }

  isJournalPissn(journal: JournalView | null): boolean {
    return journal?.pissn !== null;
  }

  isJournalEissn(journal: JournalView | null): boolean {
    return journal?.eissn !== null;
  }

  protected isJournalWww(journal: JournalView | null): boolean {
    return journal?.website !== null;
  }

  journalPissn(journal: JournalView | null): string {
    return journal?.pissn || '';
  }

  journalEissn(journal: JournalView | null): string {
    return journal?.eissn || '';
  }

  formatText(value: string | number | undefined | null): string {
    return value === null || value === undefined || value === '' ? '-' : value.toString();
  }

  languageNames(): string {
    return this.languages()
      .map((language) => this.journalNameService.getLanguageName(language) || '-')
      .join(', ');
  }

  private loadErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiErrorResponse | undefined;
      return body?.message ?? this.transloco.translate('page.institution.journalDetails.loadError');
    }

    if (error instanceof Error) {
      return error.message;
    }

    return this.transloco.translate('page.institution.journalDetails.loadError');
  }

  private parseJournalId(value: string | null): number | null {
    const id = Number(value);
    return Number.isInteger(id) && id > 0 ? id : null;
  }

  private scrollToTop(): void {
    this.viewportScroller.scrollToPosition([0, 0]);
  }

  private trackPageReady(): void {
    effect(() => {
      const journal = this.journal();
      if (!journal?.id || this.state().status !== 'success') {
        return;
      }

      if (this.lastReadyJournalId === journal.id) {
        return;
      }

      this.lastReadyJournalId = journal.id;
      this.navigationAudit.track({
        eventType: 'PAGE_READY',
        action: 'journals.details.ready',
        resource: {
          type: 'journal',
          id: journal.id.toString(),
        },
        properties: {
          journalId: journal.id,
        },
      });
    });
  }

  protected isCurrentNotEvaluated(journal: JournalView): boolean {
    return JournalView.CurrentStatusEnum.NotEvaluated === journal.currentStatus;
  }

  protected isCurrentNotIndexed(journal: JournalView): boolean {
    return JournalView.CurrentStatusEnum.NotIndexed === journal.currentStatus;
  }

  protected isCurrentIndexed(journal: JournalView): boolean {
    return JournalView.CurrentStatusEnum.Indexed === journal.currentStatus;
  }

}
