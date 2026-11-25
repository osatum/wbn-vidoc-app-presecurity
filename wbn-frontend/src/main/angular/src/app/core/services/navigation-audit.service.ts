import { DOCUMENT } from '@angular/common';
import { HttpContext, HttpContextToken } from '@angular/common/http';
import { Injectable, Injector, inject } from '@angular/core';
import { NavigationAuditService as NavigationAuditApiService } from '../backend';
import type { NavigationAuditBatchDto, NavigationAuditEventDto } from '../backend';
import { AuthService } from './auth.service';

export type NavigationAuditEventType = NavigationAuditEventDto.EventTypeEnum;

export type NavigationAuditResource = {
  type: string;
  id?: string;
};

export type NavigationAuditEvent = {
  eventId: string;
  eventType: NavigationAuditEventType;
  occurredAt: string;
  sessionId: string;
  viewId?: string;
  pageKey?: string;
  routeTemplate?: string;
  url?: string;
  params?: Record<string, string>;
  resource?: NavigationAuditResource;
  action?: string;
  target?: string;
  durationMs?: number;
  success?: boolean;
  statusCode?: number;
  properties?: Record<string, unknown>;
};

export type NavigationAuditView = {
  pageKey: string;
  routeTemplate: string;
  url?: string;
  params?: Record<string, string>;
  resource?: NavigationAuditResource;
  publicView?: boolean;
};

export type NavigationAuditViewContext = Pick<
  NavigationAuditEvent,
  'viewId' | 'pageKey' | 'routeTemplate' | 'url' | 'params' | 'resource'
>;

type ActiveNavigationAuditView = NavigationAuditView & {
  viewId: string;
  visibleStartedAt?: number;
};

const HEARTBEAT_INTERVAL_MS = 60_000;
const SERVICE_ENTRY_INACTIVITY_MS = 30 * 60_000;
const SESSION_ID_KEY = 'navigationAudit.sessionId';
const LAST_ACTIVITY_AT_KEY = 'navigationAudit.lastActivityAt';
const LAST_SERVICE_ENTRY_SCOPE_KEY = 'navigationAudit.lastServiceEntryScope';

/**
 * Kolejkuje zdarzenia nawigacji i wysyła je z identyfikatorem zachowanym przy ponowieniu transportu.
 * Reaguje na ukrycie i opuszczenie strony, aby ograniczyć utratę zdarzeń przy zmianie aktywnej karty.
 * Odrzuca oczekujące akcje P1/P2 po zmianie aktora; tożsamość zapisywanego użytkownika ustala backend.
 */
@Injectable({
  providedIn: 'root',
})
export class NavigationAuditService {
  private readonly document = inject(DOCUMENT);
  private readonly injector = inject(Injector);
  private readonly navigationAuditApi = inject(NavigationAuditApiService);
  private readonly queue: NavigationAuditEvent[] = [];
  private readonly journalSearchAccessScopes = new WeakMap<NavigationAuditEvent, string | null>();
  private readonly sessionId = this.getOrCreateSessionId();
  private currentView?: ActiveNavigationAuditView;
  private flushTimer: ReturnType<typeof setTimeout> | undefined;
  private heartbeatTimer: ReturnType<typeof setInterval> | undefined;
  private exitHandled = false;

  private readonly handleVisibilityChange = (): void => {
    if (!this.currentView) {
      return;
    }

    if (this.document.visibilityState === 'hidden') {
      const durationMs = this.consumeActiveDuration(false);
      this.stopHeartbeat();
      this.track({
        eventType: 'PAGE_HIDDEN',
        durationMs,
        properties: {
          reason: 'visibilitychange',
        },
      });
      this.flush();
      return;
    }

    this.currentView.visibleStartedAt = performance.now();
    this.trackServiceEntryIfDue();
    this.track({
      eventType: 'PAGE_VISIBLE',
      properties: {
        reason: 'visibilitychange',
      },
    });
    this.startHeartbeat();
  };

  private readonly handlePageExit = (): void => {
    if (this.exitHandled) {
      return;
    }

    this.exitHandled = true;
    this.finishCurrentView('page-exit', true);
  };

  constructor() {
    this.registerBrowserLifecycleHandlers();
  }

  startView(view: NavigationAuditView): string {
    this.finishCurrentView('route-change', false);

    const viewId = crypto.randomUUID();
    this.currentView = {
      ...view,
      viewId,
      visibleStartedAt: this.isDocumentVisible() ? performance.now() : undefined,
    };

    this.trackServiceEntryIfDue();
    this.track({
      eventType: view.publicView ? 'PUBLIC_PAGE_VIEW' : 'PAGE_VIEW',
      viewId,
      pageKey: view.pageKey,
      routeTemplate: view.routeTemplate,
      url: view.url,
      params: view.params,
      resource: view.resource,
    });

    this.startHeartbeat();
    return viewId;
  }

  captureView(): NavigationAuditViewContext {
    return {
      viewId: this.currentView?.viewId,
      pageKey: this.currentView?.pageKey,
      routeTemplate: this.currentView?.routeTemplate,
      url: this.currentView?.url,
      params: this.currentView?.params ? { ...this.currentView.params } : undefined,
      resource: this.currentView?.resource ? { ...this.currentView.resource } : undefined,
    };
  }

  track(
    partial: Omit<Partial<NavigationAuditEvent>, 'eventId' | 'occurredAt' | 'sessionId'> & {
      eventType: NavigationAuditEventType;
    },
  ): void {
    const event = this.buildEvent(partial);
    if (!event) {
      return;
    }

    this.queueEvent(event);
    this.recordActivity();
  }

  flush(): void {
    if (this.queue.length === 0) {
      return;
    }

    if (this.flushTimer) {
      clearTimeout(this.flushTimer);
      this.flushTimer = undefined;
    }

    const events = this.eventsForCurrentActor(this.queue.splice(0, this.queue.length));
    if (events.length === 0) return;

    this.navigationAuditApi
      .collect(this.batch(events), 'body', false, {
        context: new HttpContext().set(DISABLE_NAVIGATION_AUDIT, true),
        transferCache: false,
      })
      .subscribe({
        error: () => {
          this.queue.unshift(...events);
          this.trimQueue();
        },
      });
  }

  private finishCurrentView(reason: string, immediate: boolean): void {
    if (!this.currentView) {
      return;
    }

    const durationMs = this.consumeActiveDuration(false);
    const event = this.buildEvent({
      eventType: 'PAGE_LEAVE',
      durationMs,
      properties: {
        reason,
      },
    });

    this.currentView = undefined;
    this.stopHeartbeat();

    if (!event) {
      return;
    }

    this.recordActivity();

    if (immediate) {
      const events = this.queue.splice(0, this.queue.length);
      events.push(event);
      this.sendImmediately(events);
      return;
    }

    this.queueEvent(event);
  }

  private trackHeartbeat(): void {
    if (!this.currentView) {
      this.stopHeartbeat();
      return;
    }

    if (!this.isDocumentVisible()) {
      return;
    }

    const durationMs = this.consumeActiveDuration(true);
    if (durationMs <= 0) {
      return;
    }

    this.track({
      eventType: 'PAGE_HEARTBEAT',
      durationMs,
    });
  }

  private consumeActiveDuration(continueVisible: boolean): number {
    if (this.currentView?.visibleStartedAt === undefined) {
      return 0;
    }

    const now = performance.now();
    const durationMs = Math.max(0, Math.round(now - this.currentView.visibleStartedAt));
    this.currentView.visibleStartedAt = continueVisible ? now : undefined;
    return durationMs;
  }

  private buildEvent(
    partial: Omit<Partial<NavigationAuditEvent>, 'eventId' | 'occurredAt' | 'sessionId'> & {
      eventType: NavigationAuditEventType;
    },
  ): NavigationAuditEvent | undefined {
    const event: NavigationAuditEvent = {
      eventId: crypto.randomUUID(),
      occurredAt: new Date().toISOString(),
      sessionId: this.sessionId,
      viewId: partial.viewId ?? this.currentView?.viewId,
      pageKey: partial.pageKey ?? this.currentView?.pageKey,
      routeTemplate: partial.routeTemplate ?? this.currentView?.routeTemplate,
      url: partial.url ?? this.currentView?.url,
      params: partial.params ?? this.currentView?.params,
      resource: partial.resource ?? this.currentView?.resource,
      ...partial,
    };

    if (!event.pageKey || !event.routeTemplate) {
      return undefined;
    }

    return event;
  }

  private queueEvent(event: NavigationAuditEvent): void {
    if (event.eventType.startsWith('JOURNAL_')) {
      this.journalSearchAccessScopes.set(event, this.journalSearchAccessScope());
    }
    this.queue.push(event);
    this.trimQueue();

    if (this.queue.length >= 10) {
      this.flush();
      return;
    }

    this.scheduleFlush();
  }

  private scheduleFlush(): void {
    if (this.flushTimer) {
      return;
    }

    this.flushTimer = setTimeout(() => this.flush(), 1000);
  }

  private trimQueue(): void {
    const maxQueueSize = 200;
    if (this.queue.length <= maxQueueSize) {
      return;
    }

    this.queue.splice(0, this.queue.length - maxQueueSize);
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();

    if (!this.currentView || !this.isDocumentVisible()) {
      return;
    }

    this.heartbeatTimer = setInterval(() => this.trackHeartbeat(), HEARTBEAT_INTERVAL_MS);
  }

  private stopHeartbeat(): void {
    if (!this.heartbeatTimer) {
      return;
    }

    clearInterval(this.heartbeatTimer);
    this.heartbeatTimer = undefined;
  }

  private trackServiceEntryIfDue(): void {
    const now = Date.now();
    const lastActivityAt = this.readLastActivityAt();
    const scope = this.currentAccessToken() ? 'authenticated' : 'anonymous';
    const lastScope = this.readLastServiceEntryScope();
    if (
      lastActivityAt !== undefined &&
      now - lastActivityAt <= SERVICE_ENTRY_INACTIVITY_MS &&
      lastScope === scope
    ) {
      return;
    }

    const event = this.buildEvent({
      eventType: 'SERVICE_ENTRY',
      properties: {
        scope,
        inactivityMs: lastActivityAt === undefined ? undefined : now - lastActivityAt,
      },
    });

    if (!event) {
      return;
    }

    this.queueEvent(event);
    this.recordActivity(now);
    this.recordServiceEntryScope(scope);
  }

  private readLastActivityAt(): number | undefined {
    try {
      const value = sessionStorage.getItem(LAST_ACTIVITY_AT_KEY);
      if (!value) {
        return undefined;
      }

      const parsed = Number(value);
      return Number.isFinite(parsed) ? parsed : undefined;
    } catch {
      return undefined;
    }
  }

  private recordActivity(value = Date.now()): void {
    try {
      sessionStorage.setItem(LAST_ACTIVITY_AT_KEY, String(value));
    } catch {
      return;
    }
  }

  private readLastServiceEntryScope(): string | undefined {
    try {
      return sessionStorage.getItem(LAST_SERVICE_ENTRY_SCOPE_KEY) ?? undefined;
    } catch {
      return undefined;
    }
  }

  private recordServiceEntryScope(value: string): void {
    try {
      sessionStorage.setItem(LAST_SERVICE_ENTRY_SCOPE_KEY, value);
    } catch {
      return;
    }
  }

  private sendImmediately(events: NavigationAuditEvent[]): void {
    events = this.eventsForCurrentActor(events);
    if (events.length === 0) {
      return;
    }

    const body = JSON.stringify(this.batch(events));
    const token = this.currentAccessToken();
    const endpoint = this.batchEndpoint();

    if (token && typeof fetch === 'function') {
      void fetch(endpoint, {
        method: 'POST',
        body,
        keepalive: true,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
      });
      return;
    }

    if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
      const blob = new Blob([body], { type: 'application/json' });
      if (navigator.sendBeacon(endpoint, blob)) {
        return;
      }
    }

    if (typeof fetch === 'function') {
      void fetch(endpoint, {
        method: 'POST',
        body,
        keepalive: true,
        headers: {
          'Content-Type': 'application/json',
        },
      });
    }
  }

  private batch(events: NavigationAuditEvent[]): NavigationAuditBatchDto {
    return {
      events: events as NavigationAuditEventDto[],
    };
  }

  private eventsForCurrentActor(events: NavigationAuditEvent[]): NavigationAuditEvent[] {
    if (!events.some((event) => event.eventType.startsWith('JOURNAL_'))) return events;
    const scope = this.journalSearchAccessScope();
    return events.filter(
      (event) =>
        !event.eventType.startsWith('JOURNAL_') ||
        this.journalSearchAccessScopes.get(event) === scope,
    );
  }

  private journalSearchAccessScope(): string | null {
    const token = this.currentAccessToken() ?? null;
    if (!token) return null;
    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const claims: Record<string, unknown> = JSON.parse(
        atob(payload.padEnd(payload.length + ((4 - (payload.length % 4)) % 4), '=')),
      );
      if (
        claims['loginSessionId'] == null ||
        typeof claims['type'] !== 'string' ||
        typeof claims['sub'] !== 'string'
      )
        return token;
      return JSON.stringify([
        claims['type'],
        claims['sub'],
        claims['institutionId'],
        claims['institutionRole'],
        claims['loginSessionId'],
      ]);
    } catch {
      return token;
    }
  }

  private batchEndpoint(): string {
    const basePath = this.navigationAuditApi.configuration.basePath ?? '';
    return `${basePath}/api/navigation-audit/events/batch`;
  }

  private currentAccessToken(): string | null | undefined {
    try {
      return this.injector.get(AuthService, null)?.token();
    } catch {
      return null;
    }
  }

  private registerBrowserLifecycleHandlers(): void {
    if (typeof window === 'undefined') {
      return;
    }

    this.document.addEventListener('visibilitychange', this.handleVisibilityChange);
    window.addEventListener('pagehide', this.handlePageExit);
    window.addEventListener('beforeunload', this.handlePageExit);
  }

  private isDocumentVisible(): boolean {
    return this.document.visibilityState !== 'hidden';
  }

  private getOrCreateSessionId(): string {
    try {
      const existing = sessionStorage.getItem(SESSION_ID_KEY);

      if (existing) {
        return existing;
      }

      const created = crypto.randomUUID();
      sessionStorage.setItem(SESSION_ID_KEY, created);
      return created;
    } catch {
      return crypto.randomUUID();
    }
  }
}

export const DISABLE_NAVIGATION_AUDIT = new HttpContextToken<boolean>(() => false);
