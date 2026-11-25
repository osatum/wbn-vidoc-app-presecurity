import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { NavigationAuditService, NavigationAuditViewContext } from '../services/navigation-audit.service';
import { navigationAuditInterceptor, withNavigationAuditOperation } from './navigation-audit.interceptor';

describe('navigationAuditInterceptor', () => {
  const originalView: NavigationAuditViewContext = {
    pageKey: 'institution.journals.list',
    routeTemplate: '/institution/user/journals',
    viewId: 'original-view',
  };
  let http: HttpClient;
  let controller: HttpTestingController;
  let audit: { track: ReturnType<typeof vi.fn>; captureView: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    audit = { track: vi.fn(), captureView: vi.fn(() => originalView) };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([navigationAuditInterceptor])),
        provideHttpClientTesting(),
        { provide: NavigationAuditService, useValue: audit },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('records a successful result with the context from the start of the request', () => {
    http.get('/report', { context: withNavigationAuditOperation({ name: 'Report.download' }) }).subscribe();
    audit.captureView.mockReturnValue({ pageKey: 'system.admin.dashboard' });
    controller.expectOne('/report').flush({});

    expect(audit.track).toHaveBeenCalledOnce();
    expect(audit.track).toHaveBeenCalledWith(expect.objectContaining({
      ...originalView, eventType: 'API_RESULT', action: 'Report.download', success: true, statusCode: 200,
    }));
  });

  it('uses the explicitly captured download context and records HTTP errors', () => {
    http.get('/report', {
      context: withNavigationAuditOperation({ name: 'Report.download', viewContext: originalView }),
    }).subscribe({ error: () => undefined });
    controller.expectOne('/report').flush({}, { status: 404, statusText: 'Not Found' });

    expect(audit.captureView).not.toHaveBeenCalled();
    expect(audit.track).toHaveBeenCalledWith(expect.objectContaining({
      ...originalView, eventType: 'API_RESULT', success: false, statusCode: 404,
    }));
  });

  it('omits the non-HTTP status zero so a network failure can pass batch validation', () => {
    http.get('/report', { context: withNavigationAuditOperation({ name: 'Report.download' }) })
      .subscribe({ error: () => undefined });
    controller.expectOne('/report').error(new ProgressEvent('error'));

    expect(audit.track).toHaveBeenCalledWith(expect.objectContaining({
      eventType: 'API_RESULT', success: false, statusCode: undefined,
    }));
  });
});
