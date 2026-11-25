import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, finalize, map, Observable, of, shareReplay, switchMap, tap } from 'rxjs';
import {
  AuthApplicationService,
  AuthResponse,
  InstitutionAccessDto,
  LoginRequest,
  SelectInstitutionAndRoleRequest,
  UserProfileResponse,
  UserProfileService
} from '../backend';
import { catchError, mapTo } from 'rxjs/operators';
import { ToastService } from './toast-service';
import { Router } from '@angular/router';
import { SessionActivityService } from './session-activity.service';


@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly pendingInstitutions$ = new BehaviorSubject<InstitutionAccessDto[] | null>(null);
  private accessToken$ = new BehaviorSubject<string | undefined | null>(null);
  private refreshing = false;
  private refreshInFlight$?: Observable<AuthResponse>;
  private userProfileSubject = new BehaviorSubject<UserProfileResponse | null>(null);
  public readonly userProfile$ = this.userProfileSubject.asObservable();
  router: Router = inject(Router)


  constructor(
    private http: HttpClient,
    private authApi: AuthApplicationService,
    private profileService: UserProfileService,
    private toastService: ToastService,
    private sessionActivityService: SessionActivityService,
  ) {}

  token(): string | undefined | null {
    return this.accessToken$.value;
  }

  tokenChanges(): Observable<string | undefined | null> {
    return this.accessToken$.asObservable();
  }

  isLoggedIn(): boolean {
    return !!this.token();
  }

  silentRefresh(): Observable<boolean> {
    console.log('silentRefresh');
    return this.refreshOnce().pipe(
      mapTo(true),
      catchError(() => of(false)), // brak cookie / 401 / 403 -> traktuj jako "niezalogowany"
    );
  }

  login(
    email: string,
    password: string,
    institutionId: number | undefined = undefined,
  ): Observable<AuthResponse> {
    const body: LoginRequest = { email, password, institutionId };
    console.log('login', email, institutionId);
    return this.authApi.login(body).pipe(
      tap((r) => {
        this.applyAuthResponse(r);

        if (AuthResponse.StatusEnum.InstitutionRequired === r.status) {
          console.log('Instytucja wymagana');
        } else if (AuthResponse.StatusEnum.MembershipRequired === r.status) {
          console.log('Rola wymagana');
        } else if (r.status === 'OK') {
          console.log('OK');
        }
      }),

      switchMap((r) => this.loadUserProfileForOkResponse(r)),
    );
  }

  selectInstitution(institutionId: number, membershipId: number): Observable<AuthResponse> {
    const body: SelectInstitutionAndRoleRequest = { institutionId, membershipId };
    console.log('selectInstitution', institutionId, membershipId);
    return this.authApi.selectInstitution(body).pipe(
      tap((r) => {
        this.applyAuthResponse(r);

        if (AuthResponse.StatusEnum.InstitutionRequired === r.status) {
          console.log('Instytucja wymagana');
        } else if (AuthResponse.StatusEnum.MembershipRequired === r.status) {
          console.log('Rola wymagana');
        } else if (r.status === 'OK') {
          console.log('OK');
        }
      }),

      switchMap((r) => this.loadUserProfileForOkResponse(r)),
    );
  }

  refresh(): Observable<AuthResponse> {
    return this.authApi.refresh().pipe(tap((r) => this.accessToken$.next(r.accessToken)));
  }

  /**
   * Single-flight refresh: wiele 401 naraz -> 1 refresh request, reszta podpina się pod wynik.
   */
  refreshOnce(): Observable<AuthResponse> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.authApi.refresh().pipe(
        tap((r) => this.accessToken$.next(r.accessToken)),
        finalize(() => {
          this.refreshInFlight$ = undefined;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight$;
  }

  logout() {
    const logoutObservable = this.logoutObservabler();
    logoutObservable.subscribe(() => {
      this.router.navigate(['/']); // albo na stronę główną
    });
  }

  clearSession(): void {
    this.accessToken$.next(null);
    this.pendingInstitutions$.next(null);
    this.sessionActivityService.clearBackendActivity();
    this.setUserProfile(null);
  }

  handleIpAccessDenied(): void {
    this.clearSession();
    this.toastService.show('Dostęp z bieżącego adresu IP nie jest dozwolony.', {
      classname: 'bg-warning text-dark',
      delay: 15000,
    });
    void this.router.navigate(['/']);
  }

  logoutObservabler(): Observable<void> {
    return this.authApi.logout().pipe(
      tap(() => {
        this.clearSession();
      }),
      // jeśli backend zwraca body, a ty chcesz void:
      mapTo(void 0),
      catchError((err) => {
        this.toastService.show(err?.message ?? 'Logout failed', {
          classname: 'bg-danger text-white',
          delay: 15000,
        });
        // i tak czyścimy lokalnie stan (opcjonalnie, ale zwykle warto)
        this.clearSession();
        return of(void 0);
      }),
    );

  }

  setRefreshing(v: boolean) {
    this.refreshing = v;
  }

  isRefreshing(): boolean {
    return this.refreshing;
  }

  addPendingInstitution(institution: InstitutionAccessDto): void {
    const current = this.pendingInstitutions$.value ?? [];
    this.pendingInstitutions$.next([...current, institution]);
  }

  resetPendingInstitution(): void {
    const current = this.pendingInstitutions$.value ?? [];
    this.pendingInstitutions$.next(null);
  }

  getPendingInstitutionsSnapshot(): InstitutionAccessDto[] | null {
    return this.pendingInstitutions$.value;
  }

  isPendingInstitutionsSnapshot(): boolean {
    return this.pendingInstitutions$.value === null ? false : true;
  }

  loadUserProfile(): Observable<UserProfileResponse> {
    console.log('loadUserProfile');
    return this.profileService.profile().pipe(
      tap((ctx) => {
        this.setUserProfile(ctx);
      }),
    );
  }

  private setUserProfile(ctx: UserProfileResponse | null): void {
    this.userProfileSubject.next(ctx);
  }

  private applyAuthResponse(r: AuthResponse): void {
    if (AuthResponse.StatusEnum.Ok === r.status) {
      this.accessToken$.next(r.accessToken);
      this.pendingInstitutions$.next(null);
      return;
    }

    this.accessToken$.next(null);
    this.sessionActivityService.clearBackendActivity();
    this.setUserProfile(null);

    if (
      AuthResponse.StatusEnum.InstitutionRequired === r.status ||
      AuthResponse.StatusEnum.MembershipRequired === r.status
    ) {
      this.pendingInstitutions$.next(r.institutions ?? []);
      return;
    }

    this.pendingInstitutions$.next(null);
  }

  getUserProfile(): Observable<UserProfileResponse | null> {
    return this.userProfile$;
  }

  authorizeIp(id: number | undefined, name: string | undefined): Observable<AuthResponse> {
    const request = { id, name };

    return this.authApi.autorizeIp(request).pipe(
      tap((r) => {
        this.accessToken$.next(r.accessToken);
        this.pendingInstitutions$.next(null);
      }),

      switchMap((r) => this.loadUserProfileForOkResponse(r)),
    );
  }

  private loadUserProfileForOkResponse(r: AuthResponse): Observable<AuthResponse> {
    if (r.status !== 'OK') {
      return of(r);
    }

    return this.profileService.profile().pipe(
      tap((profile) => this.setUserProfile(profile)),
      map(() => r),
    );
  }

}
