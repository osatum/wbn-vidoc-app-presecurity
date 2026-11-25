import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { NavigationAuditVisitsService } from '../backend';
import { AuthService } from './auth.service';
import { NavigationVisitActivityService } from './navigation-visit-activity.service';
import { DISABLE_NAVIGATION_AUDIT } from './navigation-audit.service';
import { SKIP_SESSION_REFRESH } from '../interceptors/auth-error-context';

describe('NavigationVisitActivityService', () => {
  let tokens: BehaviorSubject<string | null>;
  let events: Subject<NavigationEnd>;
  let record: ReturnType<typeof vi.fn>;
  let service: NavigationVisitActivityService;
  let router: { url: string; events: Subject<NavigationEnd> };
  const token = (session: number, rotation = 0) =>
    `header.${btoa(JSON.stringify({ loginSessionId: session, iat: rotation }))}.signature`;

  beforeEach(() => {
    vi.useFakeTimers();
    tokens = new BehaviorSubject<string | null>(null);
    events = new Subject<NavigationEnd>();
    router = { url: '/institution/user/untracked', events };
    record = vi.fn(() => of(undefined));
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { tokenChanges: () => tokens.asObservable() } },
        { provide: Router, useValue: router },
        {
          provide: NavigationAuditVisitsService,
          useValue: { recordNavigationVisitActivity: record },
        },
      ],
    });
    service = TestBed.inject(NavigationVisitActivityService);
    service.initialize();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  const navigate = () => events.next(new NavigationEnd(1, router.url, router.url));

  it('records navigation on untracked authenticated routes without recording idle time', () => {
    tokens.next(token(7));
    navigate();
    expect(record).toHaveBeenCalledOnce();
    const context = record.mock.calls[0][3].context;
    expect(context.get(DISABLE_NAVIGATION_AUDIT)).toBe(true);
    expect(context.get(SKIP_SESSION_REFRESH)).toBe(true);
    vi.advanceTimersByTime(60000);
    expect(record).toHaveBeenCalledOnce();
  });

  it('retains the last interaction in a throttled series', () => {
    tokens.next(token(7));
    navigate();
    vi.advanceTimersByTime(1000);
    navigate();
    vi.advanceTimersByTime(1000);
    navigate();
    const lastTime = new Date().toISOString();
    vi.advanceTimersByTime(3000);
    expect(record).toHaveBeenCalledTimes(2);
    expect(record.mock.calls[1][0].occurredAt).toBe(lastTime);
  });

  it('does not treat token rotation as activity or reset the throttle', () => {
    tokens.next(token(7));
    navigate();
    tokens.next(token(7, 99));
    navigate();
    expect(record).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(5000);
    expect(record).toHaveBeenCalledTimes(2);
  });

  it('cancels pending activity on logout and allows a new session', () => {
    tokens.next(token(7));
    navigate();
    navigate();
    tokens.next(null);
    vi.advanceTimersByTime(6000);
    expect(record).toHaveBeenCalledOnce();
    tokens.next(token(8));
    navigate();
    expect(record).toHaveBeenCalledTimes(2);
  });

  it('ignores public pages, hidden tabs, synthetic interactions and repeated initialization', () => {
    service.initialize();
    tokens.next(token(7));
    document.dispatchEvent(new Event('pointerdown'));
    expect(record).not.toHaveBeenCalled();
    router.url = '/auth/login';
    navigate();
    expect(record).not.toHaveBeenCalled();
    router.url = '/system/admin/dashboard';
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    navigate();
    expect(record).not.toHaveBeenCalled();
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    navigate();
    expect(record).toHaveBeenCalledOnce();
  });
});
