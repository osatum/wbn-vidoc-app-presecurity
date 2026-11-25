import { HttpErrorResponse, HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { withLocalUnauthorizedHandling } from './auth-error-context';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let authService: {
    token: ReturnType<typeof vi.fn>;
    refreshOnce: ReturnType<typeof vi.fn>;
    handleIpAccessDenied: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    authService = {
      token: vi.fn(() => 'access-token'),
      refreshOnce: vi.fn(),
      handleIpAccessDenied: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
      ],
    });
  });

  it('does not refresh token for locally handled unauthorized errors', () => {
    const request = new HttpRequest('PUT', '/api/profile/password', {}, {
      context: withLocalUnauthorizedHandling(),
    });
    const next: HttpHandlerFn = vi.fn(() => throwError(() => new HttpErrorResponse({
      status: 401,
      error: {
        code: 'AUTHENTICATION_FAILED',
      },
    })));
    let emittedError: unknown;

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe({
        error: (error) => {
          emittedError = error;
        },
      });
    });

    expect(emittedError).toBeInstanceOf(HttpErrorResponse);
    expect(authService.refreshOnce).not.toHaveBeenCalled();
    expect(next).toHaveBeenCalledTimes(1);
  });

  it('attaches the access token when selecting another context', () => {
    const request = new HttpRequest('POST', '/api/auth/select-institution', {
      institutionId: 20,
      membershipId: 30,
    });
    const next: HttpHandlerFn = vi.fn(() => of(new HttpResponse({ status: 200 })));

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe();
    });

    const forwardedRequest = vi.mocked(next).mock.calls[0][0];
    expect(forwardedRequest.headers.get('Authorization')).toBe('Bearer access-token');
  });

  it('clears the session after an IP access denial', () => {
    const request = new HttpRequest('GET', '/api/institution/7');
    const next: HttpHandlerFn = vi.fn(() => throwError(() => new HttpErrorResponse({
      status: 403,
      error: { code: 'IP_ACCESS_DENIED' },
    })));
    let emittedError: unknown;

    TestBed.runInInjectionContext(() => {
      authInterceptor(request, next).subscribe({
        error: (error) => {
          emittedError = error;
        },
      });
    });

    expect(emittedError).toBeInstanceOf(HttpErrorResponse);
    expect(authService.handleIpAccessDenied).toHaveBeenCalledOnce();
    expect(authService.refreshOnce).not.toHaveBeenCalled();
  });
});
