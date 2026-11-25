import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { of, Subject, throwError } from 'rxjs';
import { NavigationAuditEventLog } from './navigation-audit-event';
import { NavigationAuditEventList } from '../navigation-audit-event-list/navigation-audit-event-list';
import { NavigationAuditVisitList } from '../navigation-audit-visit-list/navigation-audit-visit-list';
import { NavigationAuditReports } from '../navigation-audit-reports/navigation-audit-reports';
import { NavigationAuditEventsService } from '../../../core/services/navigation-audit-events.service';
import { NavigationAuditVisitListService } from '../../../core/services/navigation-audit-visits.service';
import { NavigationAuditReportService } from '../../../core/services/navigation-audit-reports.service';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';

describe('NavigationAuditEventLog tabs', () => {
  let fixture: ComponentFixture<NavigationAuditEventLog>;
  let findEvents: ReturnType<typeof vi.fn>;
  let findVisits: ReturnType<typeof vi.fn>;
  const reportExport = vi.fn();

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }
  async function tab(index: number) {
    (fixture.nativeElement.querySelectorAll('[role="tab"]')[index] as HTMLButtonElement).click();
    await settle();
  }

  beforeEach(async () => {
    findEvents = vi.fn(() => of({ data: [], totalElements: 20, totalPages: 2, size: 10 }));
    findVisits = vi.fn(() =>
      of({ data: [], totalElements: 20, totalPages: 2, size: 10, timeZone: 'Europe/Warsaw' }),
    );
    reportExport.mockClear();
    await TestBed.configureTestingModule({
      imports: [
        NavigationAuditEventLog,
        TranslocoTestingModule.forRoot({
          langs: { pl, en },
          translocoConfig: {
            availableLangs: ['pl', 'en'],
            defaultLang: 'pl',
            prodMode: true,
          },
          preloadLangs: true,
        }),
      ],
      providers: [
        { provide: NavigationAuditEventsService, useValue: { findEvents } },
        { provide: NavigationAuditVisitListService, useValue: { findVisits } },
        { provide: NavigationAuditReportService, useValue: { export: reportExport } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(NavigationAuditEventLog);
    await settle();
  });

  it('loads visits lazily and preserves independent filters, pages and report values on tab changes', async () => {
    expect(fixture.componentInstance.activeTab()).toBe(1);
    expect(findEvents).toHaveBeenCalledOnce();
    expect(findVisits).not.toHaveBeenCalled();
    const events = fixture.debugElement.query(By.directive(NavigationAuditEventList))
      .componentInstance as NavigationAuditEventList;
    const reports = fixture.debugElement.query(By.directive(NavigationAuditReports))
      .componentInstance as NavigationAuditReports;
    reports.form.patchValue({
      dateFrom: '2026-10-01',
      dateTo: '2026-10-31',
      reportType: 'PAGE_VIEWS',
    });
    events.filterForm.patchValue({ user: 'Event user' });
    events.applyFilters();
    await settle();
    events.nextPage();
    await settle();
    const eventCalls = findEvents.mock.calls.length;
    await tab(1);
    const visits = fixture.debugElement.query(By.directive(NavigationAuditVisitList))
      .componentInstance as NavigationAuditVisitList;
    expect(findVisits).toHaveBeenCalledOnce();
    visits.filterForm.patchValue({ user: 'Visit user' });
    visits.applyFilters();
    await settle();
    visits.nextPage();
    await settle();
    const visitCalls = findVisits.mock.calls.length;
    await tab(0);
    await tab(1);
    expect(findEvents).toHaveBeenCalledTimes(eventCalls);
    expect(findVisits).toHaveBeenCalledTimes(visitCalls);
    expect(events.page()).toBe(1);
    expect(visits.page()).toBe(1);
    expect(events.filterForm.controls.user.value).toBe('Event user');
    expect(visits.filterForm.controls.user.value).toBe('Visit user');
    expect(reports.form.getRawValue()).toEqual({
      dateFrom: '2026-10-01',
      dateTo: '2026-10-31',
      reportType: 'PAGE_VIEWS',
    });
    const cards = fixture.nativeElement.querySelectorAll('.card');
    expect(cards[0].querySelector('form')).not.toBeNull();
    expect(cards[1].querySelector('[role="tablist"]')).not.toBeNull();
    expect(reportExport).not.toHaveBeenCalled();
  });

  it('visit loading errors do not hide reports or prevent returning to events', async () => {
    findVisits.mockReturnValue(throwError(() => new Error('Visits unavailable')));
    await tab(1);
    expect(fixture.nativeElement.querySelector('app-navigation-audit-reports')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Nie udało się pobrać wizyt.');
    await tab(0);
    expect(fixture.componentInstance.activeTab()).toBe(1);
    expect(findEvents).toHaveBeenCalledOnce();
  });

  it('keeps the report form enabled while visits are loading', async () => {
    const pending = new Subject();
    findVisits.mockReturnValue(pending);
    await tab(1);
    const reports = fixture.debugElement.query(By.directive(NavigationAuditReports))
      .componentInstance as NavigationAuditReports;
    expect(reports.loading()).toBe(false);
    expect(
      fixture.nativeElement.querySelector('app-navigation-audit-reports button').disabled,
    ).toBe(false);
    pending.complete();
  });
});
