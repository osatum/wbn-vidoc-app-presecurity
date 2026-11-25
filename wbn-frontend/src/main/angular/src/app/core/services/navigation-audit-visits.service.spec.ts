import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NavigationAuditVisitListService } from './navigation-audit-visits.service';

describe('NavigationAuditVisitListService', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('posts typed filters and expects a JSON page including the server time zone', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(NavigationAuditVisitListService);
    const http = TestBed.inject(HttpTestingController);
    const response = {
      data: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      timeZone: 'America/New_York',
    };
    const received = vi.fn();
    service
      .findVisits({ page: 1, size: 10, dateFrom: '2026-10-03', status: 'OPEN' })
      .subscribe(received);
    const request = http.expectOne((candidate) =>
      candidate.url.endsWith('/api/navigation-audit/visits/search'),
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.responseType).toBe('json');
    expect(request.request.headers.get('Accept')).toBe('application/json');
    expect(request.request.body).toEqual({
      page: 1,
      size: 10,
      dateFrom: '2026-10-03',
      status: 'OPEN',
    });
    request.flush(response);
    expect(received).toHaveBeenCalledWith(response);
  });
});
