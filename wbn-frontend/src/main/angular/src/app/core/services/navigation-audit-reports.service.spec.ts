import { HttpHeaders, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NavigationAuditReportService } from './navigation-audit-reports.service';

describe('NavigationAuditReportService', () => {
  it('posts typed dates and downloads binary XLSX with the server filename', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(NavigationAuditReportService);
    const http = TestBed.inject(HttpTestingController);
    const request = { dateFrom: '2026-10-03', dateTo: '2026-10-03', reportType: 'VISITS' as const };
    const received = vi.fn();
    service.export(request).subscribe(received);
    const call = http.expectOne((item) =>
      item.url.endsWith('/api/navigation-audit/reports/export'),
    );
    expect(call.request.method).toBe('POST');
    expect(call.request.body).toEqual(request);
    expect(call.request.responseType).toBe('blob');
    expect(call.request.headers.get('Accept')).toBe(
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    );
    const file = new Blob(['xlsx']);
    call.flush(file, {
      headers: new HttpHeaders({ 'Content-Disposition': 'attachment; filename="report.xlsx"' }),
    });
    expect(received).toHaveBeenCalledWith({ file, filename: 'report.xlsx' });
    http.verify();
  });

  it('uses a deterministic filename when Content-Disposition is unavailable', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(NavigationAuditReportService);
    const http = TestBed.inject(HttpTestingController);
    const received = vi.fn();
    service
      .export({ dateFrom: '2026-10-03', dateTo: '2026-10-03', reportType: 'FILE_DOWNLOADS' })
      .subscribe(received);
    http
      .expectOne((item) => item.url.endsWith('/api/navigation-audit/reports/export'))
      .flush(new Blob(['xlsx']));
    expect(received.mock.calls[0][0].filename).toBe(
      'navigation-file_downloads-2026-10-03-2026-10-03.xlsx',
    );
    http.verify();
  });

  it.each([
    { disposition: 'attachment; filename="entries.xls"', filename: 'entries.xls' },
    { disposition: undefined, filename: 'navigation-service_entries-2026-10-05-2026-10-05.xls' },
    {
      disposition: 'attachment; filename="../entries.xls"',
      filename: 'navigation-service_entries-2026-10-05-2026-10-05.xls',
    },
  ])('downloads service entries as XLS with filename $filename', ({ disposition, filename }) => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(NavigationAuditReportService);
    const http = TestBed.inject(HttpTestingController);
    const received = vi.fn();
    const request = {
      dateFrom: '2026-10-05',
      dateTo: '2026-10-05',
      reportType: 'SERVICE_ENTRIES' as const,
    };
    service.export(request).subscribe(received);
    const call = http.expectOne((item) =>
      item.url.endsWith('/api/navigation-audit/reports/export'),
    );
    expect(call.request.body).toEqual(request);
    expect(call.request.method).toBe('POST');
    expect(call.request.responseType).toBe('blob');
    expect(call.request.headers.get('Accept')).toBe('application/vnd.ms-excel');
    const file = new Blob(['xls'], { type: 'application/vnd.ms-excel' });
    call.flush(file, {
      headers: disposition
        ? new HttpHeaders({ 'Content-Disposition': disposition })
        : new HttpHeaders(),
    });
    expect(received).toHaveBeenCalledWith({ file, filename });
    http.verify();
  });

  it('propagates service entry export errors', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(NavigationAuditReportService);
    const http = TestBed.inject(HttpTestingController);
    const error = vi.fn();
    service
      .export({ dateFrom: '2026-10-05', dateTo: '2026-10-05', reportType: 'SERVICE_ENTRIES' })
      .subscribe({ error });
    http
      .expectOne((item) => item.url.endsWith('/api/navigation-audit/reports/export'))
      .flush(new Blob(), { status: 400, statusText: 'Bad Request' });
    expect(error).toHaveBeenCalledWith(expect.objectContaining({ status: 400 }));
    http.verify();
  });
});
