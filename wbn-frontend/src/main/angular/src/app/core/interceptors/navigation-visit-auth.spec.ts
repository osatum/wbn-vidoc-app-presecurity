import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';
import { sessionExpiredInterceptor } from './session-expired.interceptor';
import { SKIP_SESSION_REFRESH } from './auth-error-context';

describe('visit telemetry authentication isolation', () => {
  it('does not refresh or clear authentication after a telemetry 401', () => {
    const auth = { token: () => 'token', refreshOnce: vi.fn(() => of({})), clearSession: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionExpiredInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });
    const http = TestBed.inject(HttpClient);
    const mock = TestBed.inject(HttpTestingController);
    const failed = vi.fn();
    http
      .post(
        '/api/navigation-audit/visits/activity',
        {},
        { context: new HttpContext().set(SKIP_SESSION_REFRESH, true) },
      )
      .subscribe({ error: failed });
    const request = mock.expectOne('/api/navigation-audit/visits/activity');
    expect(request.request.headers.get('Authorization')).toBe('Bearer token');
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.refreshOnce).not.toHaveBeenCalled();
    expect(auth.clearSession).not.toHaveBeenCalled();
    expect(failed).toHaveBeenCalledOnce();
    mock.verify();
  });
});
