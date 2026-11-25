import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Subject, catchError, combineLatest, finalize, map, of, startWith, switchMap } from 'rxjs';
import {
  ApiErrorResponse,
  Category,
  JournalView,
  QuestionnaireService,
  QuestionnaireView,
  SurveyDto
} from '../../../../core/backend';
import { AlertData } from '../../../../shared/alert-data/alert-data';
import { NgbModule, NgbNavChangeEvent, NgbNavContent, NgbNavItem, NgbNavLink, NgbNavLinkBase } from '@ng-bootstrap/ng-bootstrap';
import { JournalNameService } from '../../../../core/services/journal-name-service';
import { NavigationAuditService } from '../../../../core/services/navigation-audit.service';
import { withNavigationAuditOperation } from '../../../../core/interceptors/navigation-audit.interceptor';
import { Cooperation } from './cooperation/cooperation';
import { Digitalization } from './digitalization/digitalization';
import { Standards } from './standards/standards';
import { Stability } from './stability/stability';
import { ForAuthors } from './for-authors/for-authors';
import { ScientificDatabases } from './scientific-databases/scientific-databases';
import { SupplementaryData } from './supplementary-data/supplementary-data';
import { PublishingVersions } from './publishing-versions/publishing-versions';
import { LocalSpinner } from '../../../../shared/local-spinner/local-spinner';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';
import { downloadFile } from '../../../../shared/file-download/file-download';

type QuestionnaireState = {
  status: 'loading' | 'success' | 'empty' | 'error' | 'hidden';
  data: QuestionnaireView | null;
  error: unknown | null;
};

type QuestionnaireRow = {
  label: string;
  value: string;
};

type SurveyRows = {
  metadata: QuestionnaireRow[];
  journal: QuestionnaireRow[];
  supplementaryData: QuestionnaireRow[];
  publishingVersions: QuestionnaireRow[];
  stability: QuestionnaireRow[];
  standards: QuestionnaireRow[];
  digitalization: QuestionnaireRow[];
  cooperation: QuestionnaireRow[];
  editorial: QuestionnaireRow[];
  publisher: QuestionnaireRow[];
  description: QuestionnaireRow[];
};

type SurveyObject = Record<string, unknown>;

const FIELD_LABEL_KEYS: Record<string, string> = {
  evaluationYear: 'page.institution.questionnaire.labels.evaluationYear',
  timestamp: 'page.institution.questionnaire.labels.timestamp',
  title: 'page.institution.questionnaire.labels.title',
  titleOriginal: 'page.institution.questionnaire.labels.titleOriginal',
  titleEnglish: 'page.institution.questionnaire.labels.titleEnglish',
  titleShort: 'page.institution.questionnaire.labels.titleShort',
  issn: 'page.institution.questionnaire.labels.issn',
  eissn: 'page.institution.questionnaire.labels.eissn',
  pissn: 'page.institution.questionnaire.labels.pissn',
  doi: 'page.institution.questionnaire.labels.doi',
  website: 'page.institution.questionnaire.labels.website',
  publisher: 'page.institution.questionnaire.labels.publisher',
  country: 'page.institution.questionnaire.labels.country',
  languages: 'page.institution.questionnaire.labels.languages',
  email: 'page.institution.questionnaire.labels.email',
  phone: 'page.institution.questionnaire.labels.phone',
  firstName: 'page.institution.questionnaire.labels.firstName',
  lastName: 'page.institution.questionnaire.labels.lastName',
  fullName: 'page.institution.questionnaire.labels.fullName',
  year: 'page.institution.questionnaire.labels.year',
  value: 'page.institution.questionnaire.labels.value',
  content: 'page.institution.questionnaire.labels.content',
};

@Component({
  selector: 'app-journal-questionnaire',
  standalone: true,
  imports: [
    CommonModule,
    AlertData,
    NgbModule,
    NgbNavContent,
    NgbNavLink,
    NgbNavLinkBase,
    NgbNavItem,
    Cooperation,
    Digitalization,
    Standards,
    Stability,
    ForAuthors,
    ScientificDatabases,
    SupplementaryData,
    PublishingVersions,
    LocalSpinner,
    TranslocoPipe,
    RouterLink,
  ],
  templateUrl: './journal-questionnaire.html',
  styleUrl: './journal-questionnaire.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalQuestionnaire {
  private readonly questionnaireService = inject(QuestionnaireService);
  private readonly document = inject(DOCUMENT);
  private readonly navigationAudit = inject(NavigationAuditService);
  private readonly transloco = inject(TranslocoService);

  readonly journalId = input.required<number>();
  readonly surveyYear = input<number>();
  readonly availableYears = input<readonly number[]>([]);
  readonly yearsWithData = input<readonly number[]>([]);
  readonly questionnaireAvailable = input<boolean>();
  readonly questionnaireVisible = input(true);
  readonly revealRequired = input(false);
  readonly notIndexingReason = input<JournalView['currentReasonNotIndexing']>();
  readonly contextLoading = input(false);
  readonly contextError = input<string | null>(null);
  readonly yearChange = output<number>();
  readonly reveal = output<void>();
  readonly retryContext = output<void>();
  protected readonly journalNameService = inject(JournalNameService);
  activeQuestionnaireTabId = 1;
  private readonly questionnaireTabLabels: Record<number, string> = {
    1: 'page.institution.questionnaire.tabs.publishingVersions',
    2: 'page.institution.questionnaire.tabs.stability',
    3: 'page.institution.questionnaire.tabs.reviewStandards',
    4: 'page.institution.questionnaire.tabs.forAuthors',
    5: 'page.institution.questionnaire.tabs.digitalization',
    6: 'page.institution.questionnaire.tabs.scientificDatabases',
    7: 'page.institution.questionnaire.tabs.cooperation',
    8: 'page.institution.questionnaire.tabs.supplementaryData',
  };
  private lastShownQuestionnaireKey?: string;
  private readonly retries = new Subject<void>();
  private readonly requestContext = computed(() => ({
    id: this.journalId(),
    surveyYear: this.surveyYear(),
    loading: this.contextLoading(),
    error: this.contextError(),
    available: this.questionnaireAvailable(),
    visible: this.questionnaireVisible(),
  }));
  private readonly stateSource = combineLatest([
    toObservable(this.requestContext),
    this.retries.pipe(startWith(undefined)),
  ]).pipe(
    switchMap(([{ id, surveyYear, loading, error, available, visible }]) => {
      if (loading || error || available === false || !visible) {
        const status = loading ? 'loading' : error ? 'error' : available === false ? 'empty' : 'hidden';
        return of({ status, data: null, error } satisfies QuestionnaireState);
      }
      return this.questionnaireService
        .findCurrentQuestionnaire(id, undefined, false, {
          surveyYear,
          context: withNavigationAuditOperation({
            name: 'QuestionnaireService.findCurrentQuestionnaire',
          }),
        })
        .pipe(
          map((response): QuestionnaireState => {
            const questionnaire = response.questionnaire ?? null;

            return questionnaire === null
              ? {
                  status: 'empty',
                  data: null,
                  error: null,
                }
              : {
                  status: 'success',
                  data: questionnaire,
                  error: null,
                };
          }),
          startWith({
            status: 'loading',
            data: null,
            error: null,
          } satisfies QuestionnaireState),
          catchError((error: HttpErrorResponse) =>
            of({
              status: error.status === 404 ? 'empty' : 'error',
              data: null,
              error,
            } satisfies QuestionnaireState),
          ),
        );
    }),
  );

  readonly state = toSignal(this.stateSource, {
    initialValue: {
      status: 'loading',
      data: null,
      error: null,
    } satisfies QuestionnaireState,
  });

  readonly questionnaire = computed(() => this.state().data);
  readonly year = computed(() => this.state().data?.year);
  readonly isLoading = computed(() => this.contextLoading() || this.state().status === 'loading');
  readonly isEmpty = computed(() => !this.isLoading() && !this.contextError() && this.state().status === 'empty');
  readonly hasError = computed(() => !this.isLoading() && (!!this.contextError() || this.state().status === 'error'));
  readonly isHidden = computed(() => !this.isLoading() && !this.hasError() && this.state().status === 'hidden');
  readonly emptyMessage = computed(() => this.loadEmptyMessage(this.state().error));
  readonly errorMessage = computed(() => this.contextError() ?? this.loadErrorMessage(this.state().error));
  readonly latestAvailableYear = computed(() =>
    this.yearsWithData()
      .filter((year) => year !== this.surveyYear() && this.availableYears().includes(year))
      .reduce<number | undefined>((latest, year) => latest === undefined || year > latest ? year : latest, undefined),
  );
  readonly canDownloadReport = computed(() =>
    !this.isLoading() && !this.hasError() && this.questionnaireVisible()
    && this.questionnaireAvailable() !== false && this.state().status === 'success'
    && this.year() !== undefined && (this.surveyYear() === undefined || this.year() === this.surveyYear()),
  );
  readonly surveyRows = computed(() => this.buildSurveyRows(this.questionnaire()?.survey));
  readonly hasSurveyRows = computed(() =>
    Object.values(this.surveyRows()).some((rows) => rows.length > 0),
  );
  readonly isDetailedSectionEnabled = computed(
    () => this.questionnaire()?.detailedSectionEnabled === true,
  );
  readonly isReportDownloading = signal(false);
  readonly reportDownloadError = signal<string | null>(null);

  constructor() {
    this.trackQuestionnaireShown();
    effect(() => {
      this.journalId();
      this.surveyYear();
      this.reportDownloadError.set(null);
    });
  }

  retryQuestionnaire(): void {
    if (this.contextError()) {
      this.retryContext.emit();
    } else {
      this.retries.next();
    }
  }

  onQuestionnaireTabChange(event: NgbNavChangeEvent): void {
    const questionnaire = this.questionnaire();

    if (!questionnaire) {
      return;
    }

    const nextId = Number(event.nextId);
    const activeId = Number(event.activeId);

    this.navigationAudit.track({
      eventType: 'TAB_CLICK',
      action: 'questionnaire.tab.click',
      target: nextId.toString(),
      resource: {
        type: 'journal',
        id: this.journalId().toString(),
      },
      properties: {
        journalId: this.journalId(),
        year: questionnaire.year,
        from: activeId.toString(),
        to: nextId.toString(),
        tabLabel: this.questionnaireTabLabel(nextId),
      },
    });
  }

  downloadQuestionnaireReport(): void {
    if (this.isReportDownloading()) {
      return;
    }

    const id = this.journalId();
    const year = this.year();
    const language = this.reportLanguage();
    this.reportDownloadError.set(null);

    if (year === undefined) {
      this.reportDownloadError.set('Nie udało się ustalić roku ankiety.');
      return;
    }
    if (!this.canDownloadReport()) {
      return;
    }

    const filename = `survey-${year}-${id}-${language}.xls`;
    const viewContext = this.navigationAudit.captureView();

    this.isReportDownloading.set(true);

    this.questionnaireService
      .generateCurrentQuestionnaireReport(id, language, 'body', false, {
        surveyYear: year,
        httpHeaderAccept: 'application/vnd.ms-excel',
        context: withNavigationAuditOperation({
          name: 'QuestionnaireService.generateCurrentQuestionnaireReport',
          viewContext,
        }),
        transferCache: false,
      })
      .pipe(finalize(() => this.isReportDownloading.set(false)))
      .subscribe({
        next: (report) => {
          const file = this.toReportBlob(report);
          if (!downloadFile(this.document, file, filename)) {
            if (id === this.journalId() && year === (this.surveyYear() ?? this.year())) {
              this.reportDownloadError.set(this.transloco.translate('page.institution.questionnaire.reportError'));
            }
            return;
          }

          this.navigationAudit.track({
            ...viewContext,
            eventType: 'FILE_DOWNLOAD',
            action: 'questionnaire.report.download',
            target: filename,
            resource: { type: 'journal', id: id.toString() },
            success: true,
            properties: { year, language, format: 'xls', sizeBytes: file.size },
          });
        },
        error: () => {
          if (id === this.journalId() && year === (this.surveyYear() ?? this.year())) {
            this.reportDownloadError.set(this.transloco.translate('page.institution.questionnaire.reportError'));
          }
        },
      });
  }

  trackByRow(_: number, row: QuestionnaireRow): string {
    return `${row.label}-${row.value}`;
  }

  formatText(value: string | number | undefined | null): string {
    return value === null || value === undefined || value === '' ? '-' : value.toString();
  }

  formatPercent(value: number | undefined): string {
    return value === undefined ? '-' : `${value}%`;
  }

  formatBoolean(value: boolean | undefined): string {
    if (value === undefined) {
      return '-';
    }
    return value ? this.transloco.translate('common.yes') : this.transloco.translate('common.no');
  }

  hasCollectionItems(value: unknown): boolean {
    if (value === null || value === undefined) {
      return false;
    }

    if (Array.isArray(value)) {
      return value.length > 0;
    }

    if (value instanceof Set || value instanceof Map) {
      return value.size > 0;
    }

    return false;
  }

  private buildSurveyRows(survey: SurveyDto | undefined): SurveyRows {
    if (!survey) {
      return this.emptySurveyRows();
    }

    return {
      metadata: [
        ...this.valueToRows(survey.evaluationYear, this.fieldLabel('evaluationYear')),
        ...this.valueToRows(survey.timestamp, this.fieldLabel('timestamp')),
      ],
      journal: this.valueToRows(survey.journal, ''),
      supplementaryData: this.valueToRows(survey.supplementaryData, ''),
      publishingVersions: this.valueToRows(survey.publishingVersions, ''),
      stability: this.valueToRows(survey.stability, ''),
      standards: this.valueToRows(survey.standards, ''),
      digitalization: this.valueToRows(survey.digitalization, ''),
      cooperation: this.valueToRows(survey.cooperation, ''),
      editorial: this.valueToRows(survey.editorial, ''),
      publisher: this.valueToRows(survey.publisher, ''),
      description: this.valueToRows(survey.description, ''),
    };
  }

  private valueToRows(value: unknown, label: string): QuestionnaireRow[] {
    if (this.isEmptyValue(value)) {
      return [];
    }

    if (this.isPrimitive(value)) {
      return [
        {
          label: label || this.transloco.translate('page.institution.questionnaire.value'),
          value: this.formatPrimitive(value),
        },
      ];
    }

    if (Array.isArray(value)) {
      if (value.every((item) => this.isPrimitive(item) || this.isEmptyValue(item))) {
        const joined = value
          .filter((item) => !this.isEmptyValue(item))
          .map((item) => this.formatPrimitive(item as string | number | boolean))
          .join(', ');

        return joined
          ? [
              {
                label: label || this.transloco.translate('page.institution.questionnaire.value'),
                value: joined,
              },
            ]
          : [];
      }

      return value.flatMap((item, index) =>
        this.valueToRows(
          item,
          label
            ? `${label} ${index + 1}`
            : this.transloco.translate('page.institution.questionnaire.element', {
                index: index + 1,
              }),
        ),
      );
    }

    if (this.isObject(value)) {
      return Object.entries(value).flatMap(([key, nestedValue]) => {
        const nestedLabel = label ? `${label} - ${this.fieldLabel(key)}` : this.fieldLabel(key);
        return this.valueToRows(nestedValue, nestedLabel);
      });
    }

    return [];
  }

  private isPrimitive(value: unknown): value is string | number | boolean {
    return ['string', 'number', 'boolean'].includes(typeof value);
  }

  private isObject(value: unknown): value is SurveyObject {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }

  private emptySurveyRows(): SurveyRows {
    return {
      metadata: [],
      journal: [],
      supplementaryData: [],
      publishingVersions: [],
      stability: [],
      standards: [],
      digitalization: [],
      cooperation: [],
      editorial: [],
      publisher: [],
      description: [],
    };
  }

  private isEmptyValue(value: unknown): boolean {
    if (value === null || value === undefined || value === '') {
      return true;
    }

    if (Array.isArray(value)) {
      return value.every((item) => this.isEmptyValue(item));
    }

    if (this.isObject(value)) {
      return Object.values(value).every((item) => this.isEmptyValue(item));
    }

    return false;
  }

  private formatPrimitive(value: string | number | boolean): string {
    if (typeof value === 'boolean') {
      return value ? this.transloco.translate('common.yes') : this.transloco.translate('common.no');
    }

    return value.toString();
  }

  private fieldLabel(key: string): string {
    const labelKey = FIELD_LABEL_KEYS[key];
    return labelKey ? this.transloco.translate(labelKey) : this.humanizeKey(key);
  }

  private humanizeKey(key: string): string {
    return key
      .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
      .replace(/[_-]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/^./, (value) => value.toUpperCase());
  }

  private loadErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiErrorResponse | undefined;
      return body?.message ?? this.transloco.translate('page.institution.questionnaire.loadError');
    }

    return this.transloco.translate('page.institution.questionnaire.loadError');
  }

  private loadEmptyMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiErrorResponse | undefined;
      return body?.message ?? this.transloco.translate('page.institution.questionnaire.empty');
    }

    return this.transloco.translate('page.institution.questionnaire.empty');
  }

  private questionnaireTabLabel(tabId: number): string {
    const key = this.questionnaireTabLabels[tabId];
    return key ? this.transloco.translate(key) : tabId.toString();
  }

  private toReportBlob(report: object): Blob {
    return report as Blob;
  }

  private reportLanguage(): string {
    const activeLanguage = this.transloco.getActiveLang();
    const normalized = activeLanguage?.trim().replace('_', '-').toLowerCase().split('-')[0];
    return normalized || 'pl';
  }

  private trackQuestionnaireShown(): void {
    effect(() => {
      const questionnaire = this.questionnaire();

      if (!questionnaire) {
        return;
      }

      const key = `${this.journalId()}-${questionnaire.year ?? 'current'}`;
      if (this.lastShownQuestionnaireKey === key) {
        return;
      }

      this.lastShownQuestionnaireKey = key;
      this.navigationAudit.track({
        eventType: 'QUESTIONNAIRE_SHOWN',
        action: 'questionnaire.shown',
        resource: {
          type: 'journal',
          id: this.journalId().toString(),
        },
        properties: {
          journalId: this.journalId(),
          year: questionnaire.year,
        },
      });
    });
  }
  filterMainCategory(categories: Array<Category> | undefined): string | undefined {
    if (categories === undefined || categories.length === 0) {
      return undefined;
    }
    let category = categories.find((c) => c.main);
    if (category === undefined) {
      return undefined;
    }
    let name = this.journalNameService.getCountryName(category);
    console.log(name);
    return name;
  }

  filterOtherCategories(categories: Array<Category> | undefined) {
    if (categories === undefined || categories.length === 0) {
      return [];
    }
    console.log(categories);
    return categories
      .filter((c) => !c.main)
      ?.map((c) => this.journalNameService.getCategoryName(c));
  }

  protected readonly catchError = catchError;
}
