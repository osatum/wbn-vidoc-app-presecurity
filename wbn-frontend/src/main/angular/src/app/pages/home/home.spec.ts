import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

import { Home } from './home';
import { HomeService } from '../../core/backend';
import { AuthService } from '../../core/services/auth.service';
import { RedirectService } from '../../core/services/redirect-service';
import { ToastService } from '../../core/services/toast-service';
import { translocoTestingModule } from '../../testing/transloco-testing-module';

describe('Home', () => {
  let component: Home;
  let fixture: ComponentFixture<Home>;
  let tokenChanges$: BehaviorSubject<string | null>;
  let homeService: { home: ReturnType<typeof vi.fn> };
  let authService: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    tokenChanges: ReturnType<typeof vi.fn>;
    loadUserProfile: ReturnType<typeof vi.fn>;
    addPendingInstitution: ReturnType<typeof vi.fn>;
    authorizeIp: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    tokenChanges$ = new BehaviorSubject<string | null>(null);
    homeService = {
      home: vi.fn(() => of({institutionsDetected: false, institutions: []})),
    };
    authService = {
      isLoggedIn: vi.fn(() => false),
      tokenChanges: vi.fn(() => tokenChanges$.asObservable()),
      loadUserProfile: vi.fn(() => of({
        name: 'Jan Kowalski',
        institutionId: 1,
        institutionType: 'RESEARCH_UNIT',
      })),
      addPendingInstitution: vi.fn(),
      authorizeIp: vi.fn(() => of({})),
    };

    await TestBed.configureTestingModule({
      imports: [Home, translocoTestingModule],
      providers: [
        provideRouter([]),
        { provide: HomeService, useValue: homeService },
        { provide: AuthService, useValue: authService },
        { provide: RedirectService, useValue: { redirectToDashboard: vi.fn(), redirectAfterLogin: vi.fn() } },
        { provide: ToastService, useValue: { show: vi.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show institution access and licence links when no institution is detected', () => {
    const element: HTMLElement = fixture.nativeElement;

    expect(element.querySelector('a[href="https://wbn.icm.edu.pl/lista-instytucji-zgloszonych"]')).not.toBeNull();
    expect(element.querySelector('a[href="https://wbn.icm.edu.pl/index-copernicus"]')).not.toBeNull();
  });

  it('should hide institution access and licence links when an institution is detected', async () => {
    homeService.home.mockReturnValue(of({institutionsDetected: true, institutions: []}));
    tokenChanges$.next(null);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('a[href^="https://wbn.icm.edu.pl/"]')).toBeNull();
  });

  it('should hide institution access and licence links for an authenticated user', async () => {
    tokenChanges$.next('token');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('a[href^="https://wbn.icm.edu.pl/"]')).toBeNull();
  });

  it('should load public home data after logout on the home route', async () => {
    homeService.home.mockClear();
    tokenChanges$.next('token');
    await fixture.whenStable();

    expect(authService.loadUserProfile).toHaveBeenCalled();
    expect(homeService.home).not.toHaveBeenCalled();

    tokenChanges$.next(null);
    await fixture.whenStable();

    expect(homeService.home).toHaveBeenCalledTimes(1);
    expect(component.institutionsDetection).toEqual({institutionsDetected: false, institutions: []});
    expect(component.userProfile).toBeNull();
  });

  it('should fetch user profile when token exists on direct home route entry', async () => {
    tokenChanges$.next('token');
    await fixture.whenStable();

    expect(authService.loadUserProfile).toHaveBeenCalled();
    expect(component.userProfile).toEqual({
      name: 'Jan Kowalski',
      institutionId: 1,
      institutionType: 'RESEARCH_UNIT',
    });
    expect(component.institutionsDetection).toBeNull();
  });
});
