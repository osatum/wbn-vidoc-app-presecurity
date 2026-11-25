import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTransloco, TranslocoLoader, TranslocoService, Translation } from '@jsverse/transloco';
import { of, throwError } from 'rxjs';

import { NavigationAuditEventListDto, NavigationAuditEventsService } from '../../../core/services/navigation-audit-events.service';
import { NavigationAuditEventList } from './navigation-audit-event-list';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation(language: string) {
    return of((language === 'en' ? en : pl) as Translation);
  }
}

describe('NavigationAuditEventList', () => {
  let component: NavigationAuditEventList;
  let fixture: ComponentFixture<NavigationAuditEventList>;
  let navigationAuditEventsService: {
    findEvents: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    navigationAuditEventsService = {
      findEvents: vi.fn(() => of({ data: [], totalElements: 0, totalPages: 0 })),
    };

    await TestBed.configureTestingModule({
      imports: [NavigationAuditEventList],
      providers: [
        provideTransloco({
          config: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            prodMode: true,
            reRenderOnLangChange: true,
          },
          loader: TestingTranslocoLoader,
        }),
        { provide: NavigationAuditEventsService, useValue: navigationAuditEventsService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NavigationAuditEventList);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('filters and labels P2 separately and exposes the action/year context in PL and EN', async () => {
    const propertiesJson = JSON.stringify({
      schemaVersion: 1,
      journalId: 123,
      surveyYear: 2024,
      sourceSearchView: 'BASIC',
      sourceSurveyYear: 2025,
      effectiveProfile: 'BOTH',
    });
    navigationAuditEventsService.findEvents.mockReturnValue(
      of({
        data: [
          {
            id: 1,
            eventType: 'JOURNAL_DECISION_ACTION',
            pageKey: 'institution.journals.decision',
            action: 'E13',
            propertiesJson,
          },
          {
            id: 2,
            eventType: 'JOURNAL_DECISION_ACTION',
            pageKey: 'institution.journals.decision',
            action: 'E14',
            target: 'https://journals.indexcopernicus.com/search/journal/issue?issueId=all&journalId=123',
            propertiesJson,
          },
        ],
        totalElements: 2,
        totalPages: 1,
      }),
    );
    component.filterForm.patchValue({
      eventType: 'JOURNAL_DECISION_ACTION',
      pageKey: 'institution.journals.decision',
      journalId: '123',
    });
    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(navigationAuditEventsService.findEvents).toHaveBeenLastCalledWith(
      expect.objectContaining({
        eventType: 'JOURNAL_DECISION_ACTION',
        pageKey: 'institution.journals.decision',
        journalId: '123',
      }),
    );
    expect(fixture.nativeElement.textContent).toContain('E13 — Przejście do WWW czasopisma');
    expect(fixture.nativeElement.textContent).toContain('E14 — Przejście do wydań i zawartości czasopisma');
    expect(fixture.nativeElement.querySelector('pre').textContent).toBe(propertiesJson);
    TestBed.inject(TranslocoService).setActiveLang('en');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('E13 — Journal website opened');
    expect(fixture.nativeElement.textContent).toContain('E14 — Journal issues and contents opened');
    expect(component.pages.some((page) => page.value === 'institution.journals.decision')).toBe(
      true,
    );
  });

  it.each(['JOURNAL_SEARCH', 'JOURNAL_FILTERS_CLEAR', 'JOURNAL_SEARCH_RESULT', 'JOURNAL_SEARCH_EMPTY', 'JOURNAL_CARD_OPEN'] as const)(
    'can filter the semantic P1 event %s independently of technical page/API events', async (eventType) => {
      component.filterForm.controls.eventType.setValue(eventType);
      component.applyFilters();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(navigationAuditEventsService.findEvents).toHaveBeenLastCalledWith(expect.objectContaining({ eventType }));
      expect(component.eventTypes.some((option) => option.value === eventType)).toBe(true);
      expect(component.eventTypeLabelKey(eventType)).toContain('event-type.journal-');
    },
  );

  it('renders complete P1 criteria as text in an accessible disclosure without interpreting HTML', async () => {
    const properties = JSON.stringify({ criteria: { journalTitle: '<script>private()</script>'.repeat(300) }, operationId: 'operation' });
    navigationAuditEventsService.findEvents.mockReturnValue(of({ data: [{ id: 1, eventType: 'JOURNAL_SEARCH', propertiesJson: properties }], totalElements: 1, totalPages: 1 }));
    component.filterForm.controls.eventType.setValue('JOURNAL_SEARCH');
    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const content = fixture.nativeElement.querySelector('details pre') as HTMLElement;
    expect(content.textContent).toBe(properties);
    expect(content.getAttribute('tabindex')).toBe('0');
    expect(content.querySelector('script')).toBeNull();
    expect(fixture.nativeElement.querySelector('details summary')).toBeTruthy();
  });

  it('should search navigation audit events with applied filters', async () => {
    component.filterForm.setValue({
      occurredFrom: '2026-06-01',
      occurredTo: '2026-06-12',
      institution: 'Research',
      user: 'jan@example.edu',
      pageKey: 'institution.journals.details',
      eventType: 'TAB_CLICK',
      journalId: '309',
    });

    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(navigationAuditEventsService.findEvents).toHaveBeenCalledTimes(2);
    expect(navigationAuditEventsService.findEvents).toHaveBeenLastCalledWith(
      expect.objectContaining({
        occurredFrom: expect.any(String),
        occurredTo: expect.any(String),
        institution: 'Research',
        user: 'jan@example.edu',
        pageKey: 'institution.journals.details',
        eventType: 'TAB_CLICK',
        journalId: '309',
      }),
    );
  });

  it('allows an explicit retry after a failed read without resetting filters', async () => {
    navigationAuditEventsService.findEvents.mockReturnValue(throwError(() => new Error('Network failed')));
    component.filterForm.patchValue({ user: 'ada@example.test' });
    component.applyFilters();
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
    expect(component.hasError()).toBe(true);
    navigationAuditEventsService.findEvents.mockReturnValue(of({ data: [], totalElements: 0, totalPages: 0 }));
    component.retry(); fixture.detectChanges(); await fixture.whenStable();
    expect(component.hasError()).toBe(false);
    expect(navigationAuditEventsService.findEvents).toHaveBeenLastCalledWith(expect.objectContaining({ user: 'ada@example.test' }));
  });

  it('filters file downloads', async () => {
    component.filterForm.controls.eventType.setValue('FILE_DOWNLOAD');
    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(navigationAuditEventsService.findEvents).toHaveBeenLastCalledWith(
      expect.objectContaining({ eventType: 'FILE_DOWNLOAD' }),
    );
    expect(component.eventTypes.some((event) => event.value === 'FILE_DOWNLOAD')).toBe(true);
  });

  it.each([
    ['pl', 'Pobranie pliku', 'Pobranie raportu ankiety', 'Pobranie zbiorczego raportu ankiet'],
    ['en', 'File download', 'Questionnaire report download', 'Bulk questionnaire report download'],
  ])('renders both download actions and filenames in %s', async (language, eventLabel, singleLabel, bulkLabel) => {
    const events: NavigationAuditEventListDto[] = [
      { id: 1, eventType: 'FILE_DOWNLOAD', action: 'questionnaire.report.download', target: 'survey-2024-123-pl.xls', success: true },
      { id: 2, eventType: 'FILE_DOWNLOAD', action: 'questionnaire.report.bulk.download', target: 'questionnaire-report-2024-pl.xlsx', success: true },
    ];
    navigationAuditEventsService.findEvents.mockReturnValue(of({ data: events, totalElements: 2, totalPages: 1 }));
    TestBed.inject(TranslocoService).setActiveLang(language);
    component.filterForm.controls.eventType.setValue('FILE_DOWNLOAD');
    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain(eventLabel);
    expect(element.textContent).toContain(singleLabel);
    expect(element.textContent).toContain(bulkLabel);
    for (const event of events) {
      expect(element.textContent).toContain(event.target);
      expect(element.querySelector(`[title="${event.target}"]`)?.classList.contains('text-truncate')).toBe(true);
    }
    expect(element.textContent).not.toContain('questionnaire.report.download');
  });

  it('renders a placeholder for a missing filename and preserves other event actions', async () => {
    const events: NavigationAuditEventListDto[] = [
      { id: 1, eventType: 'FILE_DOWNLOAD', action: 'questionnaire.report.download' },
      { id: 2, eventType: 'API_RESULT', action: 'QuestionnaireService.generateCurrentQuestionnaireReport', target: 'other-target' },
    ];
    navigationAuditEventsService.findEvents.mockReturnValue(of({ data: events, totalElements: 2, totalPages: 1 }));
    component.filterForm.controls.pageKey.setValue('institution.journals.details');
    component.applyFilters();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Plik: -');
    expect(fixture.nativeElement.textContent).toContain('QuestionnaireService.generateCurrentQuestionnaireReport');
    expect(fixture.nativeElement.textContent).not.toContain('other-target');
  });
});
