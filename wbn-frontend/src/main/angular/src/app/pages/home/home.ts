import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {HomeService, InstitutionAccessDto, InstitutionsDetectionDto, UserProfileResponse} from '../../core/backend';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {LocalSpinner} from '../../shared/local-spinner/local-spinner';
import {AuthService} from '../../core/services/auth.service';
import {ToastService} from '../../core/services/toast-service';
import {Router, RouterLink } from '@angular/router';
import {RedirectService} from '../../core/services/redirect-service';
import {catchError, map, of, switchMap} from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-home',
  imports: [LocalSpinner, RouterLink, TranslocoPipe],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home implements OnInit {
  institutionsDetection: InstitutionsDetectionDto | null = null;
  userProfile: UserProfileResponse | null = null;
  loading = signal(true);
  #destroyRef = inject(DestroyRef);
  homeService = inject(HomeService);
  authService = inject(AuthService);
  toastService = inject(ToastService);
  redirectService = inject(RedirectService);
  router = inject(Router);
  error: any = null;

  ngOnInit(): void {
    this.authService
      .tokenChanges()
      .pipe(
        switchMap((token) => {
          this.loading.set(true);
          this.error = null;
          this.userProfile = null;
          this.institutionsDetection = null;

          if (token) {
            return this.authService.loadUserProfile().pipe(
              map((profile) => ({
                userProfile: profile,
                institutionsDetection: null,
                error: null,
              })),
              catchError((error) => of({ userProfile: null, institutionsDetection: null, error })),
            );
          }

          return this.homeService.home().pipe(
            map((ctx) => ({ userProfile: null, institutionsDetection: ctx, error: null })),
            catchError((error) => of({ userProfile: null, institutionsDetection: null, error })),
          );
        }),
        takeUntilDestroyed(this.#destroyRef),
      )
      .subscribe((state) => {
        this.userProfile = state.userProfile;
        this.institutionsDetection = state.institutionsDetection;
        this.error = state.error;
        this.loading.set(false);
      });
  }

  goToLogin(value: InstitutionAccessDto): void {
    this.authService.addPendingInstitution(value);
    this.router.navigate(['/auth/login']);
  }

  goToRegistration(value: InstitutionAccessDto): void {
    if (value.id === undefined) {
      return;
    }

    this.router.navigate(['/registration', value.id]);
  }

  getToDashboard() {
    if (this.userProfile === null) {
      return;
    }
    this.redirectService.redirectToDashboard(this.userProfile);
  }

  authorizeIp(value: InstitutionAccessDto) {
    this.authService
      .authorizeIp(value.id, value.name)
      .pipe(takeUntilDestroyed(this.#destroyRef))
      .subscribe({
        next: (response) => {
          this.loading.set(false);
          this.redirectService.redirectAfterLogin(response);
        },
        error: (error) => {
          this.toastService.show(error.message, {
            classname: 'bg-danger text-white',
            delay: 15000,
          });
          this.error = error;
          this.loading.set(false);
        },
      });
  }
}
