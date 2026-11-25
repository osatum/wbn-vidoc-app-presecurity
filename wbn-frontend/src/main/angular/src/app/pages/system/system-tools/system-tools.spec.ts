import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { of } from 'rxjs';

import { MigrationImportStatusDto, MigrationService } from '../../../core/backend';
import { translocoTestingModule } from '../../../testing/transloco-testing-module';
import { SystemTools } from './system-tools';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';

describe('SystemTools', () => {
  let component: SystemTools;
  let fixture: ComponentFixture<SystemTools>;
  let migrationControllerService: {
    status: ReturnType<typeof vi.fn>;
    getImportStatus: ReturnType<typeof vi.fn>;
    startImport: ReturnType<typeof vi.fn>;
    questionnaireReport2024Status: ReturnType<typeof vi.fn>;
    startQuestionnaireReport2024: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    migrationControllerService = {
      status: vi.fn(() =>
        of({
          state: MigrationImportStatusDto.StateEnum.Idle,
          stage: MigrationImportStatusDto.StageEnum.None,
        }),
      ),
      getImportStatus: vi.fn(() =>
        of({
          state: MigrationImportStatusDto.StateEnum.Idle,
          stage: MigrationImportStatusDto.StageEnum.None,
        }),
      ),
      startImport: vi.fn(() =>
        of({
          state: MigrationImportStatusDto.StateEnum.Completed,
          stage: MigrationImportStatusDto.StageEnum.Journals,
        }),
      ),
      questionnaireReport2024Status: vi.fn(() =>
        of({
          state: MigrationImportStatusDto.StateEnum.Idle,
          stage: MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
        }),
      ),
      startQuestionnaireReport2024: vi.fn(() =>
        of({
          state: MigrationImportStatusDto.StateEnum.Completed,
          stage: MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
          stageTotal: 5,
          stageProcessed: 5,
        }),
      ),
    };

    migrationControllerService.status.mockReturnValue(
      of({
        state: MigrationImportStatusDto.StateEnum.Idle,
        stage: MigrationImportStatusDto.StageEnum.None,
      }),
    );
    migrationControllerService.getImportStatus.mockReturnValue(
      of({
        state: MigrationImportStatusDto.StateEnum.Idle,
        stage: MigrationImportStatusDto.StageEnum.None,
      }),
    );
    migrationControllerService.startImport.mockReturnValue(
      of({
        state: MigrationImportStatusDto.StateEnum.Completed,
        stage: MigrationImportStatusDto.StageEnum.Journals,
      }),
    );
    migrationControllerService.questionnaireReport2024Status.mockReturnValue(
      of({
        state: MigrationImportStatusDto.StateEnum.Idle,
        stage: MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
      }),
    );
    migrationControllerService.startQuestionnaireReport2024.mockReturnValue(
      of({
        state: MigrationImportStatusDto.StateEnum.Completed,
        stage: MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
        stageTotal: 5,
        stageProcessed: 5,
      }),
    );

    await TestBed.configureTestingModule({
      imports: [SystemTools, translocoTestingModule],
      providers: [{ provide: MigrationService, useValue: migrationControllerService }],
    }).compileComponents();

    fixture = TestBed.createComponent(SystemTools);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should include indicator import stages in workflow', () => {
    expect(component.workflowStages.map((stage) => stage.stage)).toEqual([
      MigrationImportStatusDto.StageEnum.DeleteP2Analytics,
      MigrationImportStatusDto.StageEnum.DeleteJournals,
      MigrationImportStatusDto.StageEnum.DeleteDictionaries,
      MigrationImportStatusDto.StageEnum.Dictionaries,
      MigrationImportStatusDto.StageEnum.Journals,
      MigrationImportStatusDto.StageEnum.NotIndexed2024,
      MigrationImportStatusDto.StageEnum.NotIndexed2025,
      MigrationImportStatusDto.StageEnum.DatabasesIndexed2024,
      MigrationImportStatusDto.StageEnum.Mnisw2024,
      MigrationImportStatusDto.StageEnum.WosCategories2024,
      MigrationImportStatusDto.StageEnum.Wos2022,
      MigrationImportStatusDto.StageEnum.Wos2023,
      MigrationImportStatusDto.StageEnum.ScopusCategories2024,
      MigrationImportStatusDto.StageEnum.Scopus2022,
      MigrationImportStatusDto.StageEnum.Scopus2023,
      MigrationImportStatusDto.StageEnum.LinkToReview2024,
      MigrationImportStatusDto.StageEnum.Kind2024,
      MigrationImportStatusDto.StageEnum.InfoReview2024,
      MigrationImportStatusDto.StageEnum.Cooperation2024,
      MigrationImportStatusDto.StageEnum.Stability2024,
      MigrationImportStatusDto.StageEnum.Languages2024,
      MigrationImportStatusDto.StageEnum.Reviewers2024,
      MigrationImportStatusDto.StageEnum.Directors2024,
      MigrationImportStatusDto.StageEnum.EditorialStaff2024,
      MigrationImportStatusDto.StageEnum.Editorial2024,
      MigrationImportStatusDto.StageEnum.Publisher2024,
      MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      MigrationImportStatusDto.StageEnum.BuildP2Analytics,
      MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
    ]);
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Wos2022)).toBe(
      'page.system.system-tools.stages.wos-2022',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.NotIndexed2024)).toBe(
      'page.system.system-tools.stages.not-indexed-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.NotIndexed2025)).toBe(
      'page.system.system-tools.stages.not-indexed-2025',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.DatabasesIndexed2024)).toBe(
      'page.system.system-tools.stages.databases-indexed-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Mnisw2024)).toBe(
      'page.system.system-tools.stages.mnisw-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.WosCategories2024)).toBe(
      'page.system.system-tools.stages.wos-categories-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Wos2023)).toBe(
      'page.system.system-tools.stages.wos-2023',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.ScopusCategories2024)).toBe(
      'page.system.system-tools.stages.scopus-categories-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Scopus2022)).toBe(
      'page.system.system-tools.stages.scopus-2022',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Scopus2023)).toBe(
      'page.system.system-tools.stages.scopus-2023',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.LinkToReview2024)).toBe(
      'page.system.system-tools.stages.link-to-review-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Kind2024)).toBe(
      'page.system.system-tools.stages.kind-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.InfoReview2024)).toBe(
      'page.system.system-tools.stages.info-review-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Cooperation2024)).toBe(
      'page.system.system-tools.stages.cooperation-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Stability2024)).toBe(
      'page.system.system-tools.stages.stability-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Languages2024)).toBe(
      'page.system.system-tools.stages.languages-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Reviewers2024)).toBe(
      'page.system.system-tools.stages.reviewers-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Directors2024)).toBe(
      'page.system.system-tools.stages.directors-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.EditorialStaff2024)).toBe(
      'page.system.system-tools.stages.editorial-staff-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Editorial2024)).toBe(
      'page.system.system-tools.stages.editorial-2024',
    );
    expect(component.stageLabelKey(MigrationImportStatusDto.StageEnum.Publisher2024)).toBe(
      'page.system.system-tools.stages.publisher-2024',
    );
    expect(
      component.stageLabelKey(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe('page.system.system-tools.stages.update-journals-from-current-questionnaire');
    expect(
      component.stageLabelKey(MigrationImportStatusDto.StageEnum.QuestionnaireReport2024),
    ).toBe('page.system.system-tools.stages.questionnaire-report-2024');
  });

  it.each([
    {
      stage: MigrationImportStatusDto.StageEnum.DeleteP2Analytics,
      previous: undefined,
      next: MigrationImportStatusDto.StageEnum.DeleteJournals,
      labelSuffix: 'delete-p2-analytics',
      total: 1,
      processed: 0,
    },
    {
      stage: MigrationImportStatusDto.StageEnum.NotIndexed2024,
      previous: MigrationImportStatusDto.StageEnum.Journals,
      next: MigrationImportStatusDto.StageEnum.NotIndexed2025,
      labelSuffix: 'not-indexed-2024',
      total: 20,
      processed: 16,
    },
    {
      stage: MigrationImportStatusDto.StageEnum.NotIndexed2025,
      previous: MigrationImportStatusDto.StageEnum.NotIndexed2024,
      next: MigrationImportStatusDto.StageEnum.DatabasesIndexed2024,
      labelSuffix: 'not-indexed-2025',
      total: 10,
      processed: 4,
    },
    {
      stage: MigrationImportStatusDto.StageEnum.BuildP2Analytics,
      previous: MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      next: MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
      labelSuffix: 'build-p2-analytics',
      total: 10,
      processed: 4,
    },
  ] as const)(
    'renders $stage progress, translated labels and errors in backend order',
    async ({ stage, previous, next, labelSuffix, total, processed }) => {
      const translations = TestBed.inject(TranslocoService);
      const stageIndex = component.workflowStages.findIndex((item) => item.stage === stage);
      expect(component.workflowStages[stageIndex + 1].stage).toBe(next);
      expect(component.workflowStages[stageIndex - 1]?.stage).toBe(previous);

      for (const [language, messages] of Object.entries({ pl, en })) {
        translations.setTranslation(messages, language);
        translations.setActiveLang(language);
        const label = messages.page.system['system-tools'].stages[labelSuffix];
        for (const state of [
          MigrationImportStatusDto.StateEnum.Running,
          MigrationImportStatusDto.StateEnum.Failed,
          MigrationImportStatusDto.StateEnum.Completed,
        ]) {
          const done = state === MigrationImportStatusDto.StateEnum.Completed;
          component.status.set({
            state,
            stage,
            stageTotal: total,
            stageProcessed: done ? total : processed,
            currentFile:
              stage === MigrationImportStatusDto.StageEnum.BuildP2Analytics ? '2024' : undefined,
          });
          fixture.detectChanges();
          await fixture.whenStable();
          fixture.detectChanges();

          const tile: HTMLElement = fixture.nativeElement.querySelector(`[data-stage="${stage}"]`);
          const expectedProgress = done ? 100 : (processed / total) * 100;
          expect(tile.querySelector<HTMLElement>('.fw-medium')?.title).toBe(label);
          expect(tile.querySelector('.progress-bar')?.getAttribute('aria-label')).toBe(label);
          expect(tile.querySelector('.progress-bar')?.getAttribute('aria-valuenow')).toBe(
            String(expectedProgress),
          );
          expect(tile.textContent).toContain(`${done ? total : processed} / ${total}`);
          expect(component.workflowStageState(stage)).toBe(
            done ? 'completed' : state === 'FAILED' ? 'failed' : 'running',
          );
          expect(component.workflowStageState(next)).toBe('pending');
          if (previous) {
            expect(component.workflowStageProgress(previous)).toBe(100);
          }
          if (state === MigrationImportStatusDto.StateEnum.Failed) {
            expect(tile.querySelector('.badge')?.classList).toContain('bg-danger-subtle');
          }
        }
      }
    },
  );

  it('shows both P2 stages as completed when the import reaches the final report', () => {
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Completed,
      stage: MigrationImportStatusDto.StageEnum.QuestionnaireReport2024,
    });
    for (const stage of [
      MigrationImportStatusDto.StageEnum.DeleteP2Analytics,
      MigrationImportStatusDto.StageEnum.BuildP2Analytics,
    ]) {
      expect(component.workflowStageState(stage)).toBe('completed');
      expect(component.workflowStageProgress(stage)).toBe(100);
    }
  });

  it('should show full workflow labels in hints and update them when language changes', async () => {
    const translations = TestBed.inject(TranslocoService);
    const labelKey = 'page.system.system-tools.stages.update-journals-from-current-questionnaire';
    const labelsByLanguage = {
      pl: 'Aktualizacja czasopism z aktualnych ankiet',
      en: 'Update journals from current questionnaires',
    };
    const stageIndex = component.workflowStages.findIndex(
      (stage) =>
        stage.stage === MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
    );
    const element: HTMLElement = fixture.nativeElement;

    for (const [language, fullLabel] of Object.entries(labelsByLanguage)) {
      translations.setTranslation({ [labelKey]: fullLabel }, language);
      translations.setActiveLang(language);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const labels = element.querySelectorAll<HTMLElement>('.workflow-step .fw-medium');
      expect(labels.length).toBe(component.workflowStages.length);
      labels.forEach((label) => {
        expect(label.title).not.toBe('');
        expect(label.title).toBe(label.textContent?.trim());
      });
      expect(labels[stageIndex].title).toBe(fullLabel);
    }
  });

  it('should show journal synchronization progress between languages and report stages', () => {
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Running,
      stage: MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      stageTotal: 10,
      stageProcessed: 4,
    });
    fixture.detectChanges();

    const stage = MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire;
    expect(component.workflowStageState(stage)).toBe('running');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Cooperation2024),
    ).toBe(100);
    expect(component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Stability2024)).toBe(
      100,
    );
    expect(component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Languages2024)).toBe(
      100,
    );
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.QuestionnaireReport2024),
    ).toBe(0);
    const element: HTMLElement = fixture.nativeElement;
    const stageIndex = component.workflowStages.findIndex((item) => item.stage === stage);
    const tile = element.querySelectorAll<HTMLElement>('.workflow-step')[stageIndex];
    expect(tile.querySelector('.progress-bar')?.getAttribute('aria-valuenow')).toBe('40');
    expect(tile.textContent).toContain('4 / 10');
  });

  it('should show stability progress and translated hint before journal synchronization', async () => {
    const translations = TestBed.inject(TranslocoService);
    const labelKey = 'page.system.system-tools.stages.stability-2024';
    const stage = MigrationImportStatusDto.StageEnum.Stability2024;
    const stageIndex = component.workflowStages.findIndex((item) => item.stage === stage);
    const labelsByLanguage = {
      pl: 'Import stabilności wydawniczej dla ankiet 2024',
      en: 'Publication stability import for 2024 questionnaires',
    };
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Running,
      stage,
      stageTotal: 10,
      stageProcessed: 4,
    });

    expect(component.workflowStageState(stage)).toBe('running');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Cooperation2024),
    ).toBe(100);
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.QuestionnaireReport2024),
    ).toBe(0);
    for (const [language, fullLabel] of Object.entries(labelsByLanguage)) {
      translations.setTranslation({ [labelKey]: fullLabel }, language);
      translations.setActiveLang(language);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const element: HTMLElement = fixture.nativeElement;
      const tile = element.querySelectorAll<HTMLElement>('.workflow-step')[stageIndex];
      expect(tile.querySelector<HTMLElement>('.fw-medium')?.title).toBe(fullLabel);
      expect(tile.querySelector('.progress-bar')?.getAttribute('aria-valuenow')).toBe('40');
      expect(tile.textContent).toContain('4 / 10');
    }
  });

  it('should show failed stability stage without completing synchronization or report tiles', () => {
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Failed,
      stage: MigrationImportStatusDto.StageEnum.Stability2024,
      stageTotal: 10,
      stageProcessed: 4,
    });

    expect(component.workflowStageState(MigrationImportStatusDto.StageEnum.Stability2024)).toBe(
      'failed',
    );
    expect(component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Stability2024)).toBe(
      40,
    );
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Cooperation2024),
    ).toBe(100);
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.QuestionnaireReport2024),
    ).toBe(0);
  });

  it('should show languages progress and translated hint before journal synchronization', async () => {
    const translations = TestBed.inject(TranslocoService);
    const labelKey = 'page.system.system-tools.stages.languages-2024';
    const stage = MigrationImportStatusDto.StageEnum.Languages2024;
    const stageIndex = component.workflowStages.findIndex((item) => item.stage === stage);
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Running,
      stage,
      stageTotal: 10,
      stageProcessed: 4,
    });

    expect(component.workflowStageState(stage)).toBe('running');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Stability2024)).toBe(
      100,
    );
    expect(component.workflowStages[stageIndex + 1].stage).toBe(
      MigrationImportStatusDto.StageEnum.Reviewers2024,
    );
    expect(component.workflowStages[stageIndex + 2].stage).toBe(
      MigrationImportStatusDto.StageEnum.Directors2024,
    );
    expect(component.workflowStages[stageIndex + 3].stage).toBe(
      MigrationImportStatusDto.StageEnum.EditorialStaff2024,
    );
    expect(component.workflowStages[stageIndex + 4].stage).toBe(
      MigrationImportStatusDto.StageEnum.Editorial2024,
    );
    expect(component.workflowStages[stageIndex + 5].stage).toBe(
      MigrationImportStatusDto.StageEnum.Publisher2024,
    );
    expect(component.workflowStages[stageIndex + 6].stage).toBe(
      MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
    );
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.QuestionnaireReport2024),
    ).toBe(0);
    for (const [language, fullLabel] of Object.entries({
      pl: 'Import języków publikacji dla ankiet 2024',
      en: 'Publication languages import for 2024 questionnaires',
    })) {
      translations.setTranslation({ [labelKey]: fullLabel }, language);
      translations.setActiveLang(language);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const element: HTMLElement = fixture.nativeElement;
      const tile = element.querySelectorAll<HTMLElement>('.workflow-step')[stageIndex];
      expect(tile.querySelector<HTMLElement>('.fw-medium')?.title).toBe(fullLabel);
      expect(tile.querySelector('.progress-bar')?.getAttribute('aria-valuenow')).toBe('40');
      expect(tile.textContent).toContain('4 / 10');
    }
  });

  it('should show failed languages stage without completing synchronization or reports', () => {
    const stage = MigrationImportStatusDto.StageEnum.Languages2024;
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Failed,
      stage,
      stageTotal: 10,
      stageProcessed: 4,
    });

    expect(component.workflowStageState(stage)).toBe('failed');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Stability2024)).toBe(
      100,
    );
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.QuestionnaireReport2024),
    ).toBe(0);
  });

  it('should show reviewers progress and translated hints before journal synchronization', async () => {
    const translations = TestBed.inject(TranslocoService);
    const labelKey = 'page.system.system-tools.stages.reviewers-2024';
    const stage = MigrationImportStatusDto.StageEnum.Reviewers2024;
    const stageIndex = component.workflowStages.findIndex((item) => item.stage === stage);
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Running,
      stage,
      stageTotal: 10,
      stageProcessed: 4,
    });

    expect(component.workflowStageState(stage)).toBe('running');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Languages2024)).toBe(
      100,
    );
    expect(component.workflowStages[stageIndex + 1].stage).toBe(
      MigrationImportStatusDto.StageEnum.Directors2024,
    );
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
    for (const [language, label] of Object.entries({
      pl: 'Import recenzentów dla ankiet 2024',
      en: 'Reviewers import for 2024 questionnaires',
    })) {
      translations.setTranslation({ [labelKey]: label }, language);
      translations.setActiveLang(language);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const element: HTMLElement = fixture.nativeElement;
      const tile = element.querySelectorAll<HTMLElement>('.workflow-step')[stageIndex];
      expect(tile.querySelector<HTMLElement>('.fw-medium')?.title).toBe(label);
      expect(tile.querySelector('.progress-bar')?.getAttribute('aria-valuenow')).toBe('40');
      expect(tile.textContent).toContain('4 / 10');
    }

    component.status.update((status) => ({
      ...status,
      state: MigrationImportStatusDto.StateEnum.Failed,
    }));
    expect(component.workflowStageState(stage)).toBe('failed');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
    expect(
      component.workflowStageProgress(MigrationImportStatusDto.StageEnum.QuestionnaireReport2024),
    ).toBe(0);
  });

  it('should show scientific council progress and translated hints before journal synchronization', async () => {
    const translations = TestBed.inject(TranslocoService);
    const labelKey = 'page.system.system-tools.stages.directors-2024';
    const stage = MigrationImportStatusDto.StageEnum.Directors2024;
    const stageIndex = component.workflowStages.findIndex((item) => item.stage === stage);
    component.status.set({
      state: MigrationImportStatusDto.StateEnum.Running,
      stage,
      stageTotal: 10,
      stageProcessed: 4,
    });

    expect(component.workflowStageState(stage)).toBe('running');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(component.workflowStageProgress(MigrationImportStatusDto.StageEnum.Reviewers2024)).toBe(
      100,
    );
    expect(component.workflowStages[stageIndex + 1].stage).toBe(
      MigrationImportStatusDto.StageEnum.EditorialStaff2024,
    );
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
    for (const [language, label] of Object.entries({
      pl: 'Import członków rady naukowej dla ankiet 2024',
      en: 'Scientific council members import for 2024 questionnaires',
    })) {
      translations.setTranslation({ [labelKey]: label }, language);
      translations.setActiveLang(language);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const element: HTMLElement = fixture.nativeElement;
      const tile = element.querySelectorAll<HTMLElement>('.workflow-step')[stageIndex];
      expect(tile.querySelector<HTMLElement>('.fw-medium')?.title).toBe(label);
      expect(tile.querySelector('.progress-bar')?.getAttribute('aria-valuenow')).toBe('40');
      expect(tile.textContent).toContain('4 / 10');
    }

    component.status.update((status) => ({
      ...status,
      state: MigrationImportStatusDto.StateEnum.Failed,
    }));
    expect(component.workflowStageState(stage)).toBe('failed');
    expect(component.workflowStageProgress(stage)).toBe(40);
    expect(
      component.workflowStageProgress(
        MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      ),
    ).toBe(0);
  });

  it.each([
    {
      stage: MigrationImportStatusDto.StageEnum.EditorialStaff2024,
      previous: MigrationImportStatusDto.StageEnum.Directors2024,
      next: MigrationImportStatusDto.StageEnum.Editorial2024,
      labelSuffix: 'editorial-staff-2024',
      pl: 'Import składu redakcji dla ankiet 2024',
      en: 'Editorial staff import for 2024 questionnaires',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Editorial2024,
      previous: MigrationImportStatusDto.StageEnum.EditorialStaff2024,
      next: MigrationImportStatusDto.StageEnum.Publisher2024,
      labelSuffix: 'editorial-2024',
      pl: 'Import danych instytucji redakcji dla ankiet 2024',
      en: 'Editorial office import for 2024 questionnaires',
    },
    {
      stage: MigrationImportStatusDto.StageEnum.Publisher2024,
      previous: MigrationImportStatusDto.StageEnum.Editorial2024,
      next: MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
      labelSuffix: 'publisher-2024',
      pl: 'Import danych wydawców dla ankiet 2024',
      en: 'Publisher import for 2024 questionnaires',
    },
  ])(
    'should show $stage progress and translated hints before journal synchronization',
    async ({ stage, previous, next, labelSuffix, pl, en }) => {
      const translations = TestBed.inject(TranslocoService);
      const labelKey = `page.system.system-tools.stages.${labelSuffix}`;
      const stageIndex = component.workflowStages.findIndex((item) => item.stage === stage);
      component.status.set({
        state: MigrationImportStatusDto.StateEnum.Running,
        stage,
        stageTotal: 10,
        stageProcessed: 4,
      });

      expect(component.workflowStageState(stage)).toBe('running');
      expect(component.workflowStageProgress(stage)).toBe(40);
      expect(component.workflowStageProgress(previous)).toBe(100);
      expect(component.workflowStages[stageIndex + 1].stage).toBe(next);
      expect(
        component.workflowStageProgress(
          MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
        ),
      ).toBe(0);
      for (const [language, label] of Object.entries({ pl, en })) {
        translations.setTranslation({ [labelKey]: label }, language);
        translations.setActiveLang(language);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        const tile = element.querySelectorAll<HTMLElement>('.workflow-step')[stageIndex];
        expect(tile.querySelector<HTMLElement>('.fw-medium')?.title).toBe(label);
        expect(tile.querySelector('.progress-bar')?.getAttribute('aria-valuenow')).toBe('40');
        expect(tile.textContent).toContain('4 / 10');
      }

      component.status.update((status) => ({
        ...status,
        state: MigrationImportStatusDto.StateEnum.Failed,
      }));
      expect(component.workflowStageState(stage)).toBe('failed');
      expect(component.workflowStageProgress(stage)).toBe(40);
      expect(
        component.workflowStageProgress(
          MigrationImportStatusDto.StageEnum.UpdateJournalsFromCurrentQuestionnaire,
        ),
      ).toBe(0);
    },
  );

  it('should start questionnaire report without starting import', () => {
    component.startQuestionnaireReport2024();

    expect(migrationControllerService.startQuestionnaireReport2024).toHaveBeenCalledOnce();
    expect(migrationControllerService.startImport).not.toHaveBeenCalled();
    expect(component.reportStatus()?.state).toBe(MigrationImportStatusDto.StateEnum.Completed);
    expect(component.reportProgress()).toBe(100);
  });
});
