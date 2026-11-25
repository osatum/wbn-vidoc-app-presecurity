import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpContext } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import {
  asyncScheduler,
  catchError,
  distinctUntilChanged,
  EMPTY,
  filter,
  fromEvent,
  map,
  merge,
  retry,
  switchMap,
  throttleTime,
} from 'rxjs';
import { NavigationAuditVisitsService } from '../backend';
import { AuthService } from './auth.service';
import { DISABLE_NAVIGATION_AUDIT } from './navigation-audit.service';
import { SKIP_SESSION_REFRESH } from '../interceptors/auth-error-context';

@Injectable({ providedIn: 'root' })
export class NavigationVisitActivityService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly api = inject(NavigationAuditVisitsService);
  private readonly destroyRef = inject(DestroyRef);
  private initialized = false;
  readonly failedRequests = signal(0);

  initialize(): void {
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    const interactions = merge(
      fromEvent(this.document, 'pointerdown', { capture: true, passive: true }),
      fromEvent(this.document, 'keydown', { capture: true, passive: true }),
      fromEvent(this.document, 'scroll', { capture: true, passive: true }),
    ).pipe(filter((event) => event.isTrusted));
    const navigation = this.router.events.pipe(filter((event) => event instanceof NavigationEnd));
    this.auth
      .tokenChanges()
      .pipe(
        map((token) => this.sessionId(token)),
        distinctUntilChanged(),
        switchMap((sessionId) =>
          sessionId === null
            ? EMPTY
            : merge(interactions, navigation).pipe(
                filter(
                  () =>
                    this.document.visibilityState === 'visible' &&
                    /^\/(system|institution)(\/|$)/.test(this.router.url),
                ),
                map(() => ({ eventId: crypto.randomUUID(), occurredAt: new Date().toISOString() })),
                throttleTime(5000, asyncScheduler, { leading: true, trailing: true }),
                switchMap((activity) =>
                  this.api
                    .recordNavigationVisitActivity(activity, 'body', false, {
                      context: new HttpContext()
                        .set(DISABLE_NAVIGATION_AUDIT, true)
                        .set(SKIP_SESSION_REFRESH, true),
                      transferCache: false,
                    })
                    .pipe(
                      retry({ count: 1, delay: 1000 }),
                      catchError(() => {
                        this.failedRequests.update((count) => count + 1);
                        console.warn('Visit activity could not be recorded.');
                        return EMPTY;
                      }),
                    ),
                ),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private sessionId(token: string | undefined | null): string | null {
    if (!token) {
      return null;
    }
    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const claims: { loginSessionId?: number } = JSON.parse(
        atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=')),
      );
      return claims.loginSessionId == null ? null : String(claims.loginSessionId);
    } catch {
      return null;
    }
  }
}
