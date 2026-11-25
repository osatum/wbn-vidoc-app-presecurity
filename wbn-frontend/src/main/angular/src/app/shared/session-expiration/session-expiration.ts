import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
  Output,
  EventEmitter,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { Router } from '@angular/router';
import { toSignal, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, interval, startWith } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../core/services/auth.service';
import {
  JwtTiming,
  SESSION_REFRESH_THRESHOLD_SECONDS,
  SessionActivityService,
} from '../../core/services/session-activity.service';
import { EventService, THEME_EVENTS } from '../../theme/core/services/event.service';

@Component({
  selector: 'app-session-expiration',
  standalone: true,
  imports: [NgClass, TranslocoPipe],
  templateUrl: './session-expiration.html',
  styleUrl: './session-expiration.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionExpiration {

  private static readonly WARNING_REMAINING_RATIO = 0.3;
  private static readonly DANGER_REMAINING_RATIO = 0.1;
  private static readonly WARNING_REMAINING_SECONDS = 180;
  private static readonly DANGER_REMAINING_SECONDS = 60;

  isCondensed = false;


  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sessionActivity = inject(SessionActivityService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly eventService = inject(EventService);
  private loggedOutToken: string | null = null;
  private refreshRequestedForToken: string | null = null;
  private refreshInProgress = false;
  private logoutInProgress = false;

  private readonly token = toSignal(this.auth.tokenChanges(), {
    initialValue: this.auth.token(),
  });
  private readonly now = signal(Date.now());
  private readonly sessionFallbackActivityStartedAt = signal<number | null>(null);
  private readonly sessionValidityLimitSeconds = signal<number | null>(null);

  readonly detailsVisible = signal(false);
  readonly timing = computed(() => this.sessionActivity.readTokenTiming(this.token()));
  readonly hasToken = computed(() => this.token() !== null && this.token() !== undefined);
  readonly tokenValiditySeconds = computed(() => {
    const timing = this.timing();
    if (timing === null) {
      return 0;
    }

    if (timing.issuedAt === null || timing.expiresAt <= timing.issuedAt) {
      return this.sessionTokenRemainingSeconds();
    }

    return Math.max(0, Math.ceil((timing.expiresAt - timing.issuedAt) / 1000));
  });
  readonly sessionValiditySeconds = computed(() => {
    return this.sessionValidityLimitSeconds() ?? this.tokenValiditySeconds();
  });
  readonly sessionTokenRemainingSeconds = computed(() => {
    const timing = this.timing();
    if (timing === null) {
      return 0;
    }

    return Math.max(0, Math.ceil((timing.expiresAt - this.now()) / 1000));
  });

  readonly sessionRemainingSeconds = computed(() => {
    return Math.max(
      0,
      this.sessionValiditySeconds() -
        SESSION_REFRESH_THRESHOLD_SECONDS -
        5 -
        this.activityElapsedSeconds(),
    );
  });
  readonly sessionDisplayTime = computed(() => this.formatTime(this.sessionRemainingSeconds()));
  readonly tokenValidityDisplayTime = computed(() => this.formatTime(this.tokenValiditySeconds()));
  readonly refreshRemainingSeconds = computed(() => {
    return Math.max(0, this.sessionTokenRemainingSeconds() - SESSION_REFRESH_THRESHOLD_SECONDS);
  });
  readonly refreshDisplayTime = computed(() => this.formatTime(this.refreshRemainingSeconds()));

  readonly activityElapsedSeconds = computed(() => {
    const lastBackendActivityAt =
      this.sessionActivity.lastBackendActivityAt() ?? this.sessionFallbackActivityStartedAt();

    if (lastBackendActivityAt === null) {
      return 0;
    }

    return Math.max(0, Math.floor((this.now() - lastBackendActivityAt) / 1000));
  });
  readonly activityDisplayTime = computed(() => this.formatTime(this.activityElapsedSeconds()));

  readonly progressPercent = computed(() => {
    const validity = this.sessionValiditySeconds() - SESSION_REFRESH_THRESHOLD_SECONDS - 5;
    if (validity <= 0) {
      return this.sessionRemainingSeconds() > 0 ? 100 : 0;
    }

    return Math.round((this.sessionRemainingSeconds() / validity) * 100);
  });
  readonly statusClass = computed(() => {
    const validity = this.sessionValiditySeconds();
    const remaining = this.sessionRemainingSeconds();
    const remainingRatio = validity <= 0 ? 0 : remaining / validity;

    if (
      remaining <= SessionExpiration.DANGER_REMAINING_SECONDS ||
      remainingRatio <= SessionExpiration.DANGER_REMAINING_RATIO
    ) {
      return 'session-countdown-danger';
    }

    if (
      remaining <= SessionExpiration.WARNING_REMAINING_SECONDS ||
      remainingRatio <= SessionExpiration.WARNING_REMAINING_RATIO
    ) {
      return 'session-countdown-warning';
    }

    return 'session-countdown-ok';
  });

  constructor() {
    const mobileMenuSubscription = this.eventService.subscribe(
      THEME_EVENTS.mobileMenuButtonClicked,
      () => this.onToggleMobileMenu(),
    );

    this.destroyRef.onDestroy(() => mobileMenuSubscription.unsubscribe());

    interval(1000)
      .pipe(startWith(0), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.now.set(Date.now()));
    console.log('Session expiration service initialized');
    effect(() => {
      const token = this.token();
      const timing = this.timing();

      if (!token || timing === null) {
        this.sessionFallbackActivityStartedAt.set(null);
        this.sessionValidityLimitSeconds.set(null);
        this.loggedOutToken = null;
        this.logoutInProgress = false;
        return;
      }

      if (this.sessionValidityLimitSeconds() !== null) {
        return;
      }

      this.sessionValidityLimitSeconds.set(this.calculateTokenValiditySeconds(timing));
      this.sessionFallbackActivityStartedAt.set(
        this.sessionActivity.lastBackendActivityAt() ?? timing.issuedAt ?? Date.now(),
      );
    });
    effect(() => {
      const token = this.token();
      const timing = this.timing();

      if (!token || timing === null) {
        this.loggedOutToken = null;
        return;
      }

      if (
        this.sessionRemainingSeconds() > 0 ||
        this.loggedOutToken === token ||
        this.logoutInProgress
      ) {
        return;
      }

      this.loggedOutToken = token;
      this.logoutInProgress = true;
      this.auth
        .logoutObservabler()
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          finalize(() => {
            this.logoutInProgress = false;
            void this.router.navigate(['/']);
          }),
        )
        .subscribe();
    });
    effect(() => {
      const token = this.token();
      const timing = this.timing();

      if (!token || timing === null) {
        this.refreshRequestedForToken = null;
        this.refreshInProgress = false;
        return;
      }

      if (this.refreshRemainingSeconds() > 0) {
        this.refreshRequestedForToken = null;
        return;
      }

      if (
        this.sessionTokenRemainingSeconds() <= 0 ||
        this.sessionRemainingSeconds() <= 0 ||
        this.refreshInProgress ||
        this.refreshRequestedForToken === token
      ) {
        return;
      }

      this.refreshInProgress = true;
      this.refreshRequestedForToken = token;
      this.auth
        .refreshOnce()
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          catchError(() => {
            if (this.token() === token) {
              this.auth.clearSession();
              void this.router.navigate(['/']);
            }

            return EMPTY;
          }),
          finalize(() => {
            this.refreshInProgress = false;
          }),
        )
        .subscribe();
    });
  }

  toggleDetails(): void {
    this.detailsVisible.update((visible) => !visible);
  }

  private calculateTokenValiditySeconds(timing: JwtTiming): number {
    if (timing.issuedAt === null || timing.expiresAt <= timing.issuedAt) {
      return Math.max(0, Math.ceil((timing.expiresAt - this.now()) / 1000));
    }

    return Math.max(0, Math.ceil((timing.expiresAt - timing.issuedAt) / 1000));
  }

  private formatTime(totalSeconds: number): string {
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (hours > 0) {
      return `${hours}:${this.pad(minutes)}:${this.pad(seconds)}`;
    }

    return `${minutes}:${this.pad(seconds)}`;
  }

  private pad(value: number): string {
    return value.toString().padStart(2, '0');
  }

  onToggleMobileMenu() {
    console.log("onTogel")
    this.isCondensed = !this.isCondensed;
    this.cdr.markForCheck();
  }
}
