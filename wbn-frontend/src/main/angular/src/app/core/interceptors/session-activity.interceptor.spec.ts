import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { sessionActivityInterceptor } from './session-activity.interceptor';
import { AuthService } from '../services/auth.service';
import { SessionActivityService } from '../services/session-activity.service';
import { DISABLE_NAVIGATION_AUDIT } from '../services/navigation-audit.service';

describe('sessionActivityInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let sessionActivity: {
    markBackendActivity: ReturnType<typeof vi.fn>;
    secondsUntilTokenExpiration: ReturnType<typeof vi.fn>;
  };
  let auth: {
    token: ReturnType<typeof vi.fn>;
    refreshOnce: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    sessionActivity = {
      markBackendActivity: vi.fn(),
      secondsUntilTokenExpiration: vi.fn(() => 300),
    };
    auth = {
      token: vi.fn(() => 'token'),
      refreshOnce: vi.fn(() => of({})),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sessionActivityInterceptor])),
        provideHttpClientTesting(),
        { provide: SessionActivityService, useValue: sessionActivity },
        { provide: AuthService, useValue: auth },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should mark regular backend response as session activity', () => {
    http.get('/api/institutions').subscribe();

    httpMock.expectOne('/api/institutions').flush({});

    expect(sessionActivity.markBackendActivity).toHaveBeenCalledOnce();
    expect(sessionActivity.secondsUntilTokenExpiration).toHaveBeenCalledWith('token');
  });

  it('should not mark navigation audit batch response as session activity', () => {
    http.post('/api/navigation-audit/events/batch', { events: [] }).subscribe();

    httpMock.expectOne('/api/navigation-audit/events/batch').flush({});

    expect(sessionActivity.markBackendActivity).not.toHaveBeenCalled();
    expect(sessionActivity.secondsUntilTokenExpiration).not.toHaveBeenCalled();
  });

  it('should not mark disabled navigation audit request as session activity', () => {
    http
      .post('/api/other', {}, {
        context: new HttpContext().set(DISABLE_NAVIGATION_AUDIT, true),
      })
      .subscribe();

    httpMock.expectOne('/api/other').flush({});

    expect(sessionActivity.markBackendActivity).not.toHaveBeenCalled();
    expect(sessionActivity.secondsUntilTokenExpiration).not.toHaveBeenCalled();
  });
});
