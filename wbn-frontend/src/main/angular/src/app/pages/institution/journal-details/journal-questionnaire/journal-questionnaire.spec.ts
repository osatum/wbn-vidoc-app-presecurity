import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, Subject, throwError } from 'rxjs';
import { TranslocoService } from '@jsverse/transloco';

import {
  QuestionnaireResponse,
  QuestionnaireService,
} from '../../../../core/backend';
import { JournalNameService } from '../../../../core/services/journal-name-service';
import { NavigationAuditService, NavigationAuditViewContext } from '../../../../core/services/navigation-audit.service';
import { NAVIGATION_AUDIT_OPERATION } from '../../../../core/interceptors/navigation-audit.interceptor';
import { translocoTestingModule } from '../../../../testing/transloco-testing-module';
import { JournalQuestionnaire } from './journal-questionnaire';

describe('JournalQuestionnaire', () => {
  let fixture: ComponentFixture<JournalQuestionnaire>;
  let questionnaireResponse: BehaviorSubject<QuestionnaireResponse>;
  let generateReport: ReturnType<typeof vi.fn>;
  let audit: { track: ReturnType<typeof vi.fn>; captureView: ReturnType<typeof vi.fn> };
  const viewContext: NavigationAuditViewContext = {
    pageKey: 'institution.journals.details',
    routeTemplate: '/institution/user/journals/:id',
    url: '/institution/user/journals/1',
    viewId: 'questionnaire-view',
  };

  beforeEach(async () => {
    questionnaireResponse = new BehaviorSubject<QuestionnaireResponse>({
      questionnaire: { detailedSectionEnabled: false },
    });
    generateReport = vi.fn(() => of(new Blob(['report'])));
    audit = { track: vi.fn(), captureView: vi.fn(() => viewContext) };
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:report-test'),
      revokeObjectURL: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    await TestBed.configureTestingModule({
      imports: [JournalQuestionnaire, translocoTestingModule],
      providers: [
        {
          provide: QuestionnaireService,
          useValue: {
            findCurrentQuestionnaire: vi.fn(() => questionnaireResponse.asObservable()),
            generateCurrentQuestionnaireReport: generateReport,
          },
        },
        {
          provide: NavigationAuditService,
          useValue: audit,
        },
        {
          provide: JournalNameService,
          useValue: {},
        },
      ],
    })
      .overrideComponent(JournalQuestionnaire, {
        set: {
          template: `
            @if (isDetailedSectionEnabled()) {
              <div data-testid="detailed-section"></div>
            }
          `,
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(JournalQuestionnaire);
    fixture.componentRef.setInput('journalId', 1);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it.each(['pl', 'en'])('audits a completed questionnaire download in %s', (language) => {
    questionnaireResponse.next({ questionnaire: { year: 2024 } });
    TestBed.inject(TranslocoService).setActiveLang(language);
    audit.track.mockClear();
    fixture.componentInstance.downloadQuestionnaireReport();

    expect(audit.track).toHaveBeenCalledExactlyOnceWith({
      ...viewContext,
      eventType: 'FILE_DOWNLOAD', action: 'questionnaire.report.download',
      target: `survey-2024-1-${language}.xls`, resource: { type: 'journal', id: '1' },
      success: true, properties: { year: 2024, language, format: 'xls', sizeBytes: 6 },
    });
    const options = generateReport.mock.calls[0][4];
    expect(options.context.get(NAVIGATION_AUDIT_OPERATION)).toEqual({
      name: 'QuestionnaireService.generateCurrentQuestionnaireReport', viewContext,
    });
    expect(fixture.componentInstance.isReportDownloading()).toBe(false);
  });

  it('keeps the original journal, language and view while the response is pending', () => {
    const response = new Subject<Blob>();
    generateReport.mockReturnValue(response);
    questionnaireResponse.next({ questionnaire: { year: 2024 } });
    audit.track.mockClear();
    fixture.componentInstance.downloadQuestionnaireReport();
    fixture.componentInstance.downloadQuestionnaireReport();
    expect(generateReport).toHaveBeenCalledOnce();
    expect(audit.track).not.toHaveBeenCalled();
    fixture.componentRef.setInput('journalId', 2);
    TestBed.inject(TranslocoService).setActiveLang('en');
    audit.captureView.mockReturnValue({ pageKey: 'institution.journals.list' });
    response.next(new Blob(['report']));
    response.complete();

    expect(audit.track).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
      ...viewContext, target: 'survey-2024-1-pl.xls', resource: { type: 'journal', id: '1' },
    }));
  });

  it('shows HTTP errors without recording a file download', () => {
    questionnaireResponse.next({ questionnaire: { year: 2024 } });
    generateReport.mockReturnValue(throwError(() => new Error('Report unavailable')));
    audit.track.mockClear();
    fixture.componentInstance.downloadQuestionnaireReport();

    expect(audit.track).not.toHaveBeenCalled();
    expect(fixture.componentInstance.reportDownloadError()).toBeTruthy();
    expect(fixture.componentInstance.isReportDownloading()).toBe(false);
  });

  it('does not record success when browser download preparation fails', () => {
    questionnaireResponse.next({ questionnaire: { year: 2024 } });
    vi.stubGlobal('URL', {});
    audit.track.mockClear();
    fixture.componentInstance.downloadQuestionnaireReport();

    expect(audit.track).not.toHaveBeenCalled();
    expect(fixture.componentInstance.reportDownloadError()).toBeTruthy();
  });

  it('does not request a report when the questionnaire year is unknown', () => {
    fixture.componentInstance.downloadQuestionnaireReport();
    expect(generateReport).not.toHaveBeenCalled();
    expect(fixture.componentInstance.reportDownloadError()).toBeTruthy();
  });

  it('reloads the questionnaire for the selected year and uses that year for its XLS', async () => {
    fixture.componentRef.setInput('surveyYear', 2025);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(TestBed.inject(QuestionnaireService).findCurrentQuestionnaire).toHaveBeenLastCalledWith(
      1, undefined, false, expect.objectContaining({ surveyYear: 2025 }),
    );
    questionnaireResponse.next({ questionnaire: { year: 2025 } });
    fixture.componentInstance.downloadQuestionnaireReport();
    expect(generateReport).toHaveBeenLastCalledWith(
      1, 'pl', 'body', false, expect.objectContaining({ surveyYear: 2025 }),
    );
  });

  it('renders detailed section only when enabled in questionnaire response', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="detailed-section"]')).toBeNull();

    questionnaireResponse.next({ questionnaire: { detailedSectionEnabled: true } });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="detailed-section"]')).not.toBeNull();
  });

  it('does not download a stale questionnaire after the selected year changes', async () => {
    questionnaireResponse.next({ questionnaire: { year: 2024 } });
    fixture.componentRef.setInput('surveyYear', 2025);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.downloadQuestionnaireReport();
    expect(generateReport).not.toHaveBeenCalled();
  });

  it('does not display an old report error in the newly selected year', async () => {
    const response = new Subject<Blob>();
    generateReport.mockReturnValue(response);
    questionnaireResponse.next({ questionnaire: { year: 2024 } });
    fixture.componentInstance.downloadQuestionnaireReport();
    fixture.componentRef.setInput('surveyYear', 2025);
    fixture.detectChanges();
    await fixture.whenStable();
    questionnaireResponse.next({ questionnaire: { year: 2025 } });
    response.error(new Error('Previous report failed'));
    expect(fixture.componentInstance.reportDownloadError()).toBeNull();
    expect(fixture.componentInstance.isReportDownloading()).toBe(false);
  });
});
