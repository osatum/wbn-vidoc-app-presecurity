import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  OnDestroy,
  signal,
} from '@angular/core';
import { exhaustMap, finalize, interval, startWith, Subscription } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { MigrationService, MigrationImportStatusDto } from '../../../core/backend';

const STATUS_POLL_INTERVAL_MS = 3000;

interface ImportWorkflowStage {
  stage: MigrationImportStatusDto.StageEnum;
  labelKey: string;
}

/**
 * Prezentuje postęp importu i generowania raportu ankiet na podstawie statusów backendu.
 * Kolejność kafelków odzwierciedla runner, w tym usuwanie starej i przygotowanie nowej analityki P2.
 * Komponent odczytuje liczniki i stany; obliczenia benchmarków wykonuje backend podczas importu.
 */
@Component({
  selector: 'app-system-tools',
  imports: [CommonModule, TranslocoPipe],
  templateUrl: './system-tools.html',
  styleUrl: './system-tools.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SystemTools implements OnDestroy {
  private readonly migrationControllerService = inject(MigrationService);
  private pollSubscription: Subscription | null = null;
  private reportPollSubscription: Subscription | null = null;

  readonly workflowStages: ImportWorkflowStage[] = [
    {
      stage: MigrationImportStatusDto.StageEnum.DeleteP2Analytics,
      labelKey: 'page.system.system-tools.stages.delete-p2-analytics',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.DeleteJournals,
      labelKey: 'page.system.system-tools.stages.delete-journals',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.DeleteDictionaries,
      labelKey: 'page.system.system-tools.stages.delete-dictionaries',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Dictionaries,
      labelKey: 'page.system.system-tools.stages.dictionaries',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Journals,
      labelKey: 'page.system.system-tools.stages.journals',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.NotIndexed2024,
      labelKey: 'page.system.system-tools.stages.not-indexed-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.NotIndexed2025,
      labelKey: 'page.system.system-tools.stages.not-indexed-2025',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.DatabasesIndexed2024,
      labelKey: 'page.system.system-tools.stages.databases-indexed-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Mnisw2024,
      labelKey: 'page.system.system-tools.stages.mnisw-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.WosCategories2024,
      labelKey: 'page.system.system-tools.stages.wos-categories-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Wos2022,
      labelKey: 'page.system.system-tools.stages.wos-2022',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Wos2023,
      labelKey: 'page.system.system-tools.stages.wos-2023',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.ScopusCategories2024,
      labelKey: 'page.system.system-tools.stages.scopus-categories-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Scopus2022,
      labelKey: 'page.system.system-tools.stages.scopus-2022',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Scopus2023,
      labelKey: 'page.system.system-tools.stages.scopus-2023',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.LinkToReview2024,
      labelKey: 'page.system.system-tools.stages.link-to-review-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Kind2024,
      labelKey: 'page.system.system-tools.stages.kind-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.InfoReview2024,
      labelKey: 'page.system.system-tools.stages.info-review-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Cooperation2024,
      labelKey: 'page.system.system-tools.stages.cooperation-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Stability2024,
      labelKey: 'page.system.system-tools.stages.stability-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Languages2024,
      labelKey: 'page.system.system-tools.stages.languages-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Reviewers2024,
      labelKey: 'page.system.system-tools.stages.reviewers-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Directors2024,
      labelKey: 'page.system.system-tools.stages.directors-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.EditorialStaff2024,
      labelKey: 'page.system.system-tools.stages.editorial-staff-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Editorial2024,
      labelKey: 'page.system.system-tools.stages.editorial-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Publisher2024,
      labelKey: 'page.system.system-tools.stages.publisher-2024',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      labelKey: 'page.system.system-tools.stages.update-journals-from-current-questionnaire',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.BuildP2Analytics,
      labelKey: 'page.system.system-tools.stages.build-p2-analytics',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
      labelKey: 'page.system.system-tools.stages.questionnaire-report-2024',
    },
  ];

  readonly status = signal<MigrationImportStatusDto | null>(null);
  readonly reportStatus = signal<MigrationImportStatusDto | null>(null);
  readonly isStarting = signal(false);
  readonly isReportStarting = signal(false);
  readonly hasLoadError = signal(false);
  readonly startError = signal(false);
  readonly reportStartError = signal(false);

  readonly isRunning = computed(
    () => this.status()?.state === MigrationImportStatusDto.StateEnum.Running,
  );
  readonly isReportRunning = computed(
    () => this.reportStatus()?.state === MigrationImportStatusDto.StateEnum.Running,
  );
  readonly canStart = computed(
    () =>
      !this.isRunning() &&
      !this.isReportRunning() &&
      !this.isStarting() &&
      !this.isReportStarting(),
  );
  readonly canStartReport = computed(() => this.canStart());
  readonly errors = computed(() => this.status()?.errors ?? []);
  readonly dictionaryProgress = computed(() =>
    this.progress(this.status()?.dictionaryProcessed, this.status()?.dictionaryTotal),
  );
  readonly journalProgress = computed(() =>
    this.progress(this.status()?.journalProcessed, this.status()?.journalTotal),
  );
  readonly stageProgress = computed(() =>
    this.progress(this.status()?.stageProcessed, this.status()?.stageTotal),
  );
  readonly reportProgress = computed(() =>
    this.progress(this.reportStatus()?.stageProcessed, this.reportStatus()?.stageTotal),
  );

  constructor() {
    this.refreshStatus();
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.stopReportPolling();
  }

  startImport(): void {
    if (!this.canStart()) {
      return;
    }

    this.isStarting.set(true);
    this.startError.set(false);
    this.ensurePolling();

    this.migrationControllerService
      .startImport('body', false, { transferCache: false })
      .pipe(
        finalize(() => {
          this.isStarting.set(false);
          if (!this.isRunning()) {
            this.stopPolling();
          }
        }),
      )
      .subscribe({
        next: (status) => this.applyStatus(status),
        error: () => {
          this.startError.set(true);
          this.refreshStatus();
        },
      });
  }

  startQuestionnaireReport2024(): void {
    if (!this.canStartReport()) {
      return;
    }

    this.isReportStarting.set(true);
    this.reportStartError.set(false);
    this.ensureReportPolling();

    this.migrationControllerService
      .startQuestionnaireReport2024('body', false, { transferCache: false })
      .pipe(
        finalize(() => {
          this.isReportStarting.set(false);
          if (!this.isReportRunning()) {
            this.stopReportPolling();
          }
        }),
      )
      .subscribe({
        next: (status) => this.applyReportStatus(status),
        error: () => {
          this.reportStartError.set(true);
          this.refreshReportStatus();
        },
      });
  }

  refreshStatus(): void {
    this.hasLoadError.set(false);

    this.migrationControllerService.status('body', false, { transferCache: false }).subscribe({
      next: (status) => this.applyStatus(status),
      error: () => this.hasLoadError.set(true),
    });

    this.refreshReportStatus();
  }

  stateLabelKey(state: MigrationImportStatusDto.StateEnum | undefined): string {
    switch (state) {
      case MigrationImportStatusDto.StateEnum.Running:
        return 'page.system.system-tools.states.running';
      case MigrationImportStatusDto.StateEnum.Completed:
        return 'page.system.system-tools.states.completed';
      case MigrationImportStatusDto.StateEnum.Failed:
        return 'page.system.system-tools.states.failed';
      case MigrationImportStatusDto.StateEnum.Idle:
      default:
        return 'page.system.system-tools.states.idle';
    }
  }

  stageLabelKey(stage: MigrationImportStatusDto.StageEnum | undefined): string {
    switch (stage) {
      case MigrationImportStatusDto.StageEnum.Preparing:
        return 'page.system.system-tools.stages.preparing';
      case MigrationImportStatusDto.StageEnum.DeleteP2Analytics:
        return 'page.system.system-tools.stages.delete-p2-analytics';
      case MigrationImportStatusDto.StageEnum.BuildP2Analytics:
        return 'page.system.system-tools.stages.build-p2-analytics';
      case MigrationImportStatusDto.StageEnum.DeleteDictionaries:
        return 'page.system.system-tools.stages.delete-dictionaries';
      case MigrationImportStatusDto.StageEnum.DeleteJournals:
        return 'page.system.system-tools.stages.delete-journals';
      case MigrationImportStatusDto.StageEnum.Dictionaries:
        return 'page.system.system-tools.stages.dictionaries';
      case MigrationImportStatusDto.StageEnum.Index:
        return 'page.system.system-tools.stages.index';
      case MigrationImportStatusDto.StageEnum.Journals:
        return 'page.system.system-tools.stages.journals';
      case MigrationImportStatusDto.StageEnum.NotIndexed2024:
        return 'page.system.system-tools.stages.not-indexed-2024';
      case MigrationImportStatusDto.StageEnum.NotIndexed2025:
        return 'page.system.system-tools.stages.not-indexed-2025';
      case MigrationImportStatusDto.StageEnum.DatabasesIndexed2024:
        return 'page.system.system-tools.stages.databases-indexed-2024';
      case MigrationImportStatusDto.StageEnum.Mnisw2024:
        return 'page.system.system-tools.stages.mnisw-2024';
      case MigrationImportStatusDto.StageEnum.WosCategories2024:
        return 'page.system.system-tools.stages.wos-categories-2024';
      case MigrationImportStatusDto.StageEnum.Wos2022:
        return 'page.system.system-tools.stages.wos-2022';
      case MigrationImportStatusDto.StageEnum.Wos2023:
        return 'page.system.system-tools.stages.wos-2023';
      case MigrationImportStatusDto.StageEnum.ScopusCategories2024:
        return 'page.system.system-tools.stages.scopus-categories-2024';
      case MigrationImportStatusDto.StageEnum.Scopus2022:
        return 'page.system.system-tools.stages.scopus-2022';
      case MigrationImportStatusDto.StageEnum.Scopus2023:
        return 'page.system.system-tools.stages.scopus-2023';
      case MigrationImportStatusDto.StageEnum.LinkToReview2024:
        return 'page.system.system-tools.stages.link-to-review-2024';
      case MigrationImportStatusDto.StageEnum.Kind2024:
        return 'page.system.system-tools.stages.kind-2024';
      case MigrationImportStatusDto.StageEnum.InfoReview2024:
        return 'page.system.system-tools.stages.info-review-2024';
      case MigrationImportStatusDto.StageEnum.Cooperation2024:
        return 'page.system.system-tools.stages.cooperation-2024';
      case MigrationImportStatusDto.StageEnum.Stability2024:
        return 'page.system.system-tools.stages.stability-2024';
      case MigrationImportStatusDto.StageEnum.Languages2024:
        return 'page.system.system-tools.stages.languages-2024';
      case MigrationImportStatusDto.StageEnum.Reviewers2024:
        return 'page.system.system-tools.stages.reviewers-2024';
      case MigrationImportStatusDto.StageEnum.Directors2024:
        return 'page.system.system-tools.stages.directors-2024';
      case MigrationImportStatusDto.StageEnum.EditorialStaff2024:
        return 'page.system.system-tools.stages.editorial-staff-2024';
      case MigrationImportStatusDto.StageEnum.Editorial2024:
        return 'page.system.system-tools.stages.editorial-2024';
      case MigrationImportStatusDto.StageEnum.Publisher2024:
        return 'page.system.system-tools.stages.publisher-2024';
      case MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire:
        return 'page.system.system-tools.stages.update-journals-from-current-questionnaire';
      case MigrationImportStatusDto.StageEnum.QuestionnaireReport2024:
        return 'page.system.system-tools.stages.questionnaire-report-2024';
      case MigrationImportStatusDto.StageEnum.None:
      default:
        return '';
    }
  }

  stateBadgeClass(state: MigrationImportStatusDto.StateEnum | undefined): string {
    switch (state) {
      case MigrationImportStatusDto.StateEnum.Running:
        return 'text-info';
      case MigrationImportStatusDto.StateEnum.Completed:
        return 'text-success';
      case MigrationImportStatusDto.StateEnum.Failed:
        return 'text-danger';
      case MigrationImportStatusDto.StateEnum.Idle:
      default:
        return 'text-secondary';
    }
  }

  value(value: number | undefined): number {
    return value ?? 0;
  }

  workflowStageState(
    stage: MigrationImportStatusDto.StageEnum,
  ): 'pending' | 'running' | 'completed' | 'failed' {
    const status = this.status();
    if (!status || status.state === MigrationImportStatusDto.StateEnum.Idle) {
      return 'pending';
    }

    if (status.stage === stage) {
      if (status.state === MigrationImportStatusDto.StateEnum.Failed) {
        return 'failed';
      }
      if (status.state === MigrationImportStatusDto.StateEnum.Running) {
        return 'running';
      }
      return 'completed';
    }

    const currentIndex = this.workflowStageIndex(status.stage);
    const stageIndex = this.workflowStageIndex(stage);
    if (currentIndex >= 0 && stageIndex >= 0 && stageIndex < currentIndex) {
      return 'completed';
    }

    return 'pending';
  }

  workflowStageBadgeClass(stage: MigrationImportStatusDto.StageEnum): string {
    switch (this.workflowStageState(stage)) {
      case 'completed':
        return 'bg-success-subtle text-success';
      case 'running':
        return 'bg-info-subtle text-info';
      case 'failed':
        return 'bg-danger-subtle text-danger';
      case 'pending':
      default:
        return 'bg-light text-body';
    }
  }

  workflowStageStateLabelKey(stage: MigrationImportStatusDto.StageEnum): string {
    switch (this.workflowStageState(stage)) {
      case 'completed':
        return 'page.system.system-tools.states.completed';
      case 'running':
        return 'page.system.system-tools.states.running';
      case 'failed':
        return 'page.system.system-tools.states.failed';
      case 'pending':
      default:
        return 'page.system.common.pending';
    }
  }

  workflowStageProgress(stage: MigrationImportStatusDto.StageEnum): number {
    switch (this.workflowStageState(stage)) {
      case 'completed':
        return 100;
      case 'running':
      case 'failed':
        return this.stageProgress();
      case 'pending':
      default:
        return 0;
    }
  }

  private applyStatus(status: MigrationImportStatusDto | null | undefined): void {
    if (!status) {
      return;
    }

    this.status.set(status);
    if (status.state === MigrationImportStatusDto.StateEnum.Running) {
      this.ensurePolling();
      return;
    }

    if (!this.isStarting()) {
      this.stopPolling();
    }
  }

  private applyReportStatus(status: MigrationImportStatusDto | null | undefined): void {
    if (!status) {
      return;
    }

    this.reportStatus.set(status);
    if (status.state === MigrationImportStatusDto.StateEnum.Running) {
      this.ensureReportPolling();
      return;
    }

    if (!this.isReportStarting()) {
      this.stopReportPolling();
    }
  }

  private ensurePolling(): void {
    if (this.pollSubscription) {
      return;
    }

    this.pollSubscription = interval(STATUS_POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        exhaustMap(() =>
          this.migrationControllerService.status('body', false, { transferCache: false }),
        ),
      )
      .subscribe({
        next: (status) => {
          this.hasLoadError.set(false);
          this.status.set(status);
          if (status.state !== MigrationImportStatusDto.StateEnum.Running && !this.isStarting()) {
            this.stopPolling();
          }
        },
        error: () => {
          this.hasLoadError.set(true);
          if (!this.isStarting()) {
            this.stopPolling();
          }
        },
      });
  }

  private stopPolling(): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = null;
  }

  private refreshReportStatus(): void {
    this.migrationControllerService
      .questionnaireReport2024Status('body', false, { transferCache: false })
      .subscribe({
        next: (status) => this.applyReportStatus(status),
        error: () => this.hasLoadError.set(true),
      });
  }

  private ensureReportPolling(): void {
    if (this.reportPollSubscription) {
      return;
    }

    this.reportPollSubscription = interval(STATUS_POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        exhaustMap(() =>
          this.migrationControllerService.questionnaireReport2024Status('body', false, {
            transferCache: false,
          }),
        ),
      )
      .subscribe({
        next: (status) => {
          this.hasLoadError.set(false);
          this.reportStatus.set(status);
          if (
            status.state !== MigrationImportStatusDto.StateEnum.Running &&
            !this.isReportStarting()
          ) {
            this.stopReportPolling();
          }
        },
        error: () => {
          this.hasLoadError.set(true);
          if (!this.isReportStarting()) {
            this.stopReportPolling();
          }
        },
      });
  }

  private stopReportPolling(): void {
    this.reportPollSubscription?.unsubscribe();
    this.reportPollSubscription = null;
  }

  private workflowStageIndex(stage: MigrationImportStatusDto.StageEnum | undefined): number {
    if (!stage) {
      return -1;
    }

    return this.workflowStages.findIndex((item) => item.stage === stage);
  }

  private progress(processed: number | undefined, total: number | undefined): number {
    if (!total || total <= 0) {
      return 0;
    }

    return Math.min(100, Math.round(((processed ?? 0) / total) * 100));
  }
}
