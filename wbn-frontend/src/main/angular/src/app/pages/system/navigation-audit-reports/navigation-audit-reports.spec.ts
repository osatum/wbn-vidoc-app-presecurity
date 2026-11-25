import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslocoService, TranslocoTestingModule } from '@jsverse/transloco';
import pl from '../../../../assets/i18n/pl.json';
import en from '../../../../assets/i18n/en.json';
import { of, Subject, throwError } from 'rxjs';
import { NavigationAuditReports } from './navigation-audit-reports';
import {
  NavigationAuditReportFile,
  NavigationAuditReportService,
} from '../../../core/services/navigation-audit-reports.service';

describe('NavigationAuditReports', () => {
  let component: NavigationAuditReports;
  let fixture: ComponentFixture<NavigationAuditReports>;
  let exportReport: ReturnType<typeof vi.fn>;
  let createUrl: ReturnType<typeof vi.fn>;
  beforeEach(async () => {
    exportReport = vi.fn(() => of({ file: new Blob(['xlsx']), filename: 'report.xlsx' }));
    await TestBed.configureTestingModule({
      imports: [
        NavigationAuditReports,
        TranslocoTestingModule.forRoot({
          langs: { pl, en },
          translocoConfig: { availableLangs: ['pl', 'en'], defaultLang: 'pl', prodMode: true },
          preloadLangs: true,
        }),
      ],
      providers: [{ provide: NavigationAuditReportService, useValue: { export: exportReport } }],
    }).compileComponents();
    fixture = TestBed.createComponent(NavigationAuditReports);
    component = fixture.componentInstance;
    createUrl = vi.fn(() => 'blob:report');
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });
  const fill = () =>
    component.form.setValue({ dateFrom: '2026-10-03', dateTo: '2026-10-03', reportType: 'VISITS' });

  it('renders two labelled date fields and exactly four report choices', () => {
    fixture.detectChanges();
    const view = fixture.nativeElement as HTMLElement;
    expect(view.querySelectorAll('input[type="date"]')).toHaveLength(2);
    expect(Array.from(view.querySelectorAll('option')).map((option) => option.value)).toEqual([
      'VISITS',
      'PAGE_VIEWS',
      'FILE_DOWNLOADS',
      'SERVICE_ENTRIES',
    ]);
    for (const field of ['report-date-from', 'report-date-to', 'report-type']) {
      expect(view.querySelector('label[for="' + field + '"]')).not.toBeNull();
    }
  });

  it('requires dates and rejects reversed ranges without HTTP requests', () => {
    component.form.patchValue({ dateFrom: '', dateTo: '' });
    component.generate();
    expect(exportReport).not.toHaveBeenCalled();
    component.form.setValue({ dateFrom: '2026-10-04', dateTo: '2026-10-03', reportType: 'VISITS' });
    component.generate();
    expect(component.form.hasError('dateRange')).toBe(true);
    expect(exportReport).not.toHaveBeenCalled();
  });

  it.each([
    { today: new Date(2026, 9, 1, 0, 5), dateFrom: '2026-10-01', dateTo: '2026-10-31' },
    { today: new Date(2026, 3, 30, 23, 55), dateFrom: '2026-04-01', dateTo: '2026-04-30' },
    { today: new Date(2026, 1, 15), dateFrom: '2026-02-01', dateTo: '2026-02-28' },
    { today: new Date(2028, 1, 15), dateFrom: '2028-02-01', dateTo: '2028-02-29' },
    { today: new Date(2026, 11, 31, 23, 55), dateFrom: '2026-12-01', dateTo: '2026-12-31' },
  ])('defaults to the current calendar month: $dateFrom through $dateTo', (range) => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(range.today);
    fixture.destroy();
    fixture = TestBed.createComponent(NavigationAuditReports);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.form.getRawValue()).toEqual({
      dateFrom: range.dateFrom,
      dateTo: range.dateTo,
      reportType: 'VISITS',
    });
    const view = fixture.nativeElement as HTMLElement;
    expect(view.querySelector<HTMLInputElement>('#report-date-from')?.value).toBe(range.dateFrom);
    expect(view.querySelector<HTMLInputElement>('#report-date-to')?.value).toBe(range.dateTo);
    expect(component.form.valid).toBe(true);
    expect(exportReport).not.toHaveBeenCalled();
  });

  it.each(['VISITS', 'PAGE_VIEWS', 'FILE_DOWNLOADS', 'SERVICE_ENTRIES'] as const)(
    'posts %s and starts a download',
    (reportType) => {
      fill();
      component.form.controls.reportType.setValue(reportType);
      component.generate();
      expect(exportReport).toHaveBeenCalledWith({
        dateFrom: '2026-10-03',
        dateTo: '2026-10-03',
        reportType,
      });
      expect(createUrl).toHaveBeenCalledWith(expect.any(Blob));
      expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledOnce();
      expect(component.completed()).toBe(true);
      expect(component.loading()).toBe(false);
    },
  );

  it('prevents duplicate requests while generating', () => {
    const response = new Subject<NavigationAuditReportFile>();
    exportReport.mockReturnValue(response);
    fill();
    component.form.controls.reportType.setValue('SERVICE_ENTRIES');
    component.generate();
    component.generate();
    expect(exportReport).toHaveBeenCalledOnce();
    expect(component.loading()).toBe(true);
    response.next({ file: new Blob(), filename: 'report.xlsx' });
    response.complete();
    expect(component.loading()).toBe(false);
  });

  it('keeps form values and resets loading after errors so generation can be retried', () => {
    exportReport.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
    fill();
    component.form.controls.reportType.setValue('SERVICE_ENTRIES');
    component.generate();
    expect(component.error()).toBe('invalid-request');
    expect(component.loading()).toBe(false);
    expect(component.form.controls.dateFrom.value).toBe('2026-10-03');
    component.generate();
    expect(exportReport).toHaveBeenCalledTimes(2);
  });

  it('reports a failed browser download', () => {
    createUrl.mockImplementation(() => {
      throw new Error('Download blocked');
    });
    fill();
    component.form.controls.reportType.setValue('SERVICE_ENTRIES');
    component.generate();
    expect(component.error()).toBe('download-error');
    expect(component.completed()).toBe(false);
  });

  it.each([
    { language: 'pl', label: 'Wejścia do serwisu (XLS)' },
    { language: 'en', label: 'Service entries (XLS)' },
  ])('translates the service entries option in $language', ({ language, label }) => {
    TestBed.inject(TranslocoService).setActiveLang(language);
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement)
        .querySelector('option[value="SERVICE_ENTRIES"]')
        ?.textContent?.trim(),
    ).toBe(label);
  });
});
