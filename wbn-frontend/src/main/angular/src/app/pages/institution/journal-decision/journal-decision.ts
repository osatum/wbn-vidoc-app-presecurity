import { CommonModule, Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  afterRenderEffect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NgbAccordionModule } from '@ng-bootstrap/ng-bootstrap';
import {
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  filter,
  map,
  merge,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';
import { ConfigurationService } from '../../../core/backend';
import {
  JournalDecisionApi,
  JournalDecisionView,
} from '../../../core/services/journal-decision-api';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';
import { DecisionAuditAction } from '../../../core/services/journal-decision-audit';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { SafeImageComponent } from '../../../shared/safe-image/safe-image.component';
import { formatDecisionNumber } from './journal-decision-presentation';
import { JournalDecisionReference } from './journal-decision-reference';
import { JournalDecisionTimes } from './journal-decision-times';
import { JournalDecisionFees } from './journal-decision-fees';
import { JournalDecisionProcess } from './journal-decision-process';
import { JournalDecisionInternational } from './journal-decision-international';
import { JournalDecisionStandards } from './journal-decision-standards';
import { JournalDecisionSummary } from './journal-decision-summary';
import { JournalDecisionSectionPreview } from './journal-decision-section-preview';

const SECTIONS = ['time', 'costs', 'process', 'international', 'standards'] as const;
type Section = (typeof SECTIONS)[number];
type DecisionState = { status: 'loading' | 'error' | 'success'; data?: JournalDecisionView };

@Component({
  selector: 'app-journal-decision',
  host: { '[attr.lang]': 'language()' },
  imports: [
    CommonModule,
    RouterLink,
    TranslocoPipe,
    NgbAccordionModule,
    LocalSpinner,
    SafeImageComponent,
    JournalDecisionReference,
    JournalDecisionTimes,
    JournalDecisionFees,
    JournalDecisionProcess,
    JournalDecisionInternational,
    JournalDecisionStandards,
    JournalDecisionSummary,
    JournalDecisionSectionPreview,
  ],
  templateUrl: './journal-decision.html',
  styleUrls: [
    './journal-decision.css',
    '../../../shared/keyboard-focus.css',
    '../../../shared/journal-disclosure-accordion.css',
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecision {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);
  private readonly api = inject(JournalDecisionApi);
  private readonly configuration = inject(ConfigurationService);
  private readonly listState = inject(JournalListQueryStateService);
  private readonly audit = inject(NavigationAuditService);
  private readonly transloco = inject(TranslocoService);
  protected readonly names = inject(JournalNameService);
  private readonly reload = new Subject<void>();
  private readonly surveyYearChanges = new Subject<{ id: number; year: number }>();
  private readonly links = toSignal(
    this.reload.pipe(
      startWith(undefined),
      switchMap(() => this.configuration.getJournalLinks().pipe(catchError(() => of(null)))),
    ),
  );
  private requestedKey?: string;
  private readyKey?: string;
  private entryContext?: {
    navigationId: number | undefined;
    id: number | null;
    year: number | undefined;
  };
  readonly openSections = signal<ReadonlySet<Section>>(new Set());
  readonly sections = SECTIONS;
  readonly descriptionOpen = signal(false);
  protected readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  private readonly navigationContext = this.router.events.pipe(
    filter((event) => event instanceof NavigationEnd),
    startWith(null),
    map(() => {
      const value = this.route.snapshot.paramMap.get('id') ?? '';
      const id =
        /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : null;
      const navigationId = (
        this.router.currentNavigation() ?? this.router.lastSuccessfulNavigation()
      )?.id;
      // Dopisanie rozstrzygniętego roku do historii przez API nie rozpoczyna drugiego odczytu tej samej nawigacji.
      if (
        this.entryContext &&
        this.entryContext.navigationId === navigationId &&
        this.entryContext.id === id
      ) {
        return this.entryContext;
      }
      const state = this.router.currentNavigation()?.extras.state ?? this.historyState();
      const year: unknown = state['surveyYear'];
      return (this.entryContext = {
        navigationId,
        id,
        year: typeof year === 'number' && Number.isSafeInteger(year) && year > 0 ? year : undefined,
      });
    }),
  );
  private readonly context = merge(this.navigationContext, this.surveyYearChanges).pipe(
    distinctUntilChanged((a, b) => a.id === b.id && a.year === b.year),
  );
  readonly state = toSignal(
    combineLatest([
      this.context,
      this.reload.pipe(startWith(undefined)),
      inject(AuthService)
        .getUserProfile()
        .pipe(
          map((actor) =>
            JSON.stringify([
              actor?.userId,
              actor?.institutionId,
              actor?.institutionRole,
              actor?.key,
            ]),
          ),
          distinctUntilChanged(),
        ),
    ]).pipe(
      tap(([context, , actor]) => {
        const key = JSON.stringify([context.id, context.year, actor]);
        if (key !== this.requestedKey) {
          this.openSections.set(new Set());
          this.descriptionOpen.set(false);
          this.requestedKey = key;
        }
      }),
      switchMap(([{ id, year }]) =>
        id === null
          ? of<DecisionState>({ status: 'error' })
          : this.api.get(id, year ?? this.historyYear()).pipe(
              tap((data) =>
                this.location.replaceState(this.location.path(true), '', {
                  ...this.historyState(),
                  surveyYear: data.surveyYear,
                }),
              ),
              map((data): DecisionState => ({ status: 'success', data })),
              catchError(() => of<DecisionState>({ status: 'error' })),
              startWith<DecisionState>({ status: 'loading' }),
            ),
      ),
    ),
    { initialValue: { status: 'loading' } as DecisionState },
  );
  readonly data = computed(() => this.state().data);
  readonly identity = computed(() => this.data()?.identity);
  readonly issuesUrl = computed(() => {
    const template = this.links()?.issuesUrlTemplate;
    const id = this.identity()?.id;
    return template && id ? template.replace('{journalId}', String(id)) : null;
  });
  readonly annualAvailable = computed(() => this.data()?.annualStatus === 'AVAILABLE');
  readonly selectableSurveyYears = computed(() => {
    const card = this.data();
    return card
      ? [...new Set([...(card.availableSurveyYears ?? []), card.surveyYear])]
          .filter((year) => Number.isSafeInteger(year) && year > 0)
          .sort((a, b) => b - a)
      : [];
  });
  readonly benchmarkDiscipline = computed(() => {
    this.language();
    const card = this.data();
    const group =
      card?.annual?.surveyYear === card?.surveyYear ? card?.annual?.profile.mainGroup : undefined;
    return group?.status === 'AVAILABLE' && group.value
      ? this.names
          .getCategoryName({
            namePl: group.value.namePl ?? undefined,
            nameEn: group.value.nameEn ?? undefined,
            nameRu: group.value.nameRu ?? undefined,
          })
          ?.trim() || undefined
      : undefined;
  });
  readonly description = computed(() => {
    this.language();
    const descriptions = this.identity()?.descriptions;
    return descriptions?.length
      ? this.names.getJournalDescription({ descriptions })?.trim()
      : undefined;
  });
  readonly formatNumber = (value: number | null | undefined) =>
    formatDecisionNumber(value, this.language());
  readonly title = computed(
    () =>
      this.identity()?.titleOriginal ||
      this.identity()?.titleEnglish ||
      this.transloco.translate('decisionCard.title'),
  );
  readonly languageNames = computed(() => {
    this.language();
    return this.identity()
      ?.languages?.map((value) => this.names.getLanguageName(value) || value.code)
      .join(', ');
  });
  readonly countryName = computed(() => {
    this.language();
    const country = this.identity()?.country;
    return country ? this.names.getCountryName(country) || country.code : undefined;
  });
  readonly depositFields = [
    { field: 'percentFullText', label: 'fullText' },
    { field: 'percentAbstracts', label: 'abstracts' },
    { field: 'percentKeywords', label: 'keywords' },
    { field: 'percentReferences', label: 'references' },
  ] as const;
  readonly returnState = () => this.listState.returnNavigationState();
  readonly detailsState = () => this.listState.journalNavigationState(this.data()?.surveyYear);

  constructor() {
    afterRenderEffect(() => {
      const data = this.data();
      if (!data) return;
      const navigation = this.router.lastSuccessfulNavigation();
      if (!navigation || this.router.currentNavigation()) return;
      const key = `${navigation.id}:${data.identity.id}:${data.surveyYear}:${this.requestedKey}`;
      if (this.readyKey === key) return;
      this.readyKey = key;
      try {
        this.audit.track({
          eventType: 'PAGE_READY',
          resource: { type: 'journal', id: String(data.identity.id) },
          properties: { journalId: data.identity.id, surveyYear: data.surveyYear },
        });
      } catch {
        // Zdarzenie techniczne nie warunkuje wyświetlenia Karty ani rejestracji E01.
      }
      this.recordAction('E01');
    });
  }

  selectSurveyYear(year: number): void {
    const card = this.data();
    if (!card || year === card.surveyYear || !this.selectableSurveyYears().includes(year)) return;
    this.location.replaceState(this.location.path(true), '', {
      ...this.historyState(),
      surveyYear: year,
    });
    if (this.entryContext?.id === card.identity.id) {
      this.entryContext = { ...this.entryContext, year };
    }
    this.surveyYearChanges.next({ id: card.identity.id, year });
  }

  setSectionOpen(section: Section, opening: boolean): void {
    if (this.openSections().has(section) === opening) return;
    this.openSections.update((current) => {
      const next = new Set(current);
      if (opening) next.add(section);
      else next.delete(section);
      return next;
    });
    if (opening) {
      const actions = {
        time: 'E08',
        costs: 'E09',
        standards: 'E10',
        process: 'E11',
        international: 'E12',
      } as const;
      this.recordAction(actions[section]);
    }
  }

  setDescriptionOpen(open: boolean): void {
    if (this.descriptionOpen() === open) return;
    this.descriptionOpen.set(open);
    if (open) this.recordAction('E02');
  }

  recordAction(action: DecisionAuditAction): void {
    const card = this.data();
    if (!card) return;
    try {
      this.audit.track({
        eventType: 'JOURNAL_DECISION_ACTION',
        pageKey: 'institution.journals.decision',
        routeTemplate: '/institution/user/journals/:id/decision',
        url: `/institution/user/journals/${card.identity.id}/decision`,
        action,
        resource: { type: 'journal', id: String(card.identity.id) },
        properties: {
          schemaVersion: 1,
          journalId: card.identity.id,
          surveyYear: card.surveyYear,
          ...this.listState.decisionAuditSource(),
        },
      });
      if (action === 'E14') this.audit.flush();
    } catch {
      // Telemetria jest pomocnicza: jej awaria nie może zablokować rozwinięcia ani linku.
    }
  }

  recordAuxiliaryLink(event: MouseEvent, action: 'E07' | 'E13' | 'E14'): void {
    if (event.button === 1) this.recordAction(action);
  }

  retry(): void {
    this.reload.next();
  }

  private historyState(): Record<string, unknown> {
    const state: unknown = this.location.getState();
    return typeof state === 'object' && state !== null ? (state as Record<string, unknown>) : {};
  }

  private historyYear(): number | undefined {
    const year = this.historyState()['surveyYear'];
    return typeof year === 'number' && Number.isSafeInteger(year) && year > 0 ? year : undefined;
  }
}
