import { Location } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  NavigationEnd,
  NavigationSkipped,
  NavigationSkippedCode,
  NavigationStart,
  Router,
} from '@angular/router';
import { Observable, Subject, distinctUntilChanged, map, of } from 'rxjs';

import { JournalListQuery, SearchView } from '../backend';
import { AuthService } from './auth.service';
import { JournalListNavigationState, defaultJournalListQuery } from './journal-list-state';

const LIST_URL = '/institution/user/journals';
interface SearchViewHistoryEntry {
  contextId: string;
  entryId: string;
  view: SearchView;
}

interface SavedNavigation {
  origin: string;
  journalId: number;
  sourceNavigationId: number;
  sourceEntryId?: string;
  state: JournalListNavigationState;
}

/**
 * Zachowuje osobne konteksty widoków podczas przełączania oraz rozpoznanego powrotu z Karty lub Pełnych danych JML w tej samej sesji SPA.
 * Nie zapisuje wyników ani stanu w sessionStorage; nowe wejście, F5 i zmiana aktora nie odtwarzają starego formularza.
 */
@Injectable({ providedIn: 'root' })
export class JournalListQueryStateService {
  readonly surveyYear = signal<number | undefined>(undefined);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly freshEntry = new Subject<void>();
  readonly freshEntry$ = this.freshEntry.asObservable();
  private readonly searchViewChanges = new Subject<SearchView | undefined>();
  readonly searchViewChanges$ = this.searchViewChanges.asObservable();
  private historyContextId = crypto.randomUUID();
  private currentSearchView?: SearchView;
  private navigationSourcePath?: string;
  private saved?: SavedNavigation;
  private detailsOrigin?: string;
  private navigationStart?: NavigationStart;
  private hasProfile = false;
  private readonly viewStates = new Map<SearchView, JournalListNavigationState>();
  private contextVersion = 0;
  private pendingReturn?: JournalListNavigationState;
  private switchingViews = false;
  private allowedViews: readonly SearchView[] = ['BASIC', 'ADVANCED'];
  private operatingContextKey?: string;

  constructor() {
    inject(AuthService)
      .getUserProfile()
      .pipe(
        map((profile) =>
          profile
            ? JSON.stringify([
                profile.userId,
                profile.institutionId,
                profile.institutionRole,
                profile.key,
              ])
            : null,
        ),
        distinctUntilChanged(),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        // Zmiana użytkownika lub instytucji nie może odziedziczyć kryteriów poprzedniego kontekstu dostępu.
        this.resetContext();
        if (this.hasProfile && this.path(this.router.url) === LIST_URL) this.freshEntry.next();
        this.hasProfile = true;
      });

    this.router.events.pipe(takeUntilDestroyed()).subscribe((event) => {
      if (event instanceof NavigationStart) {
        this.navigationStart = event;
        this.navigationSourcePath = this.path(this.router.url);
      }
      if (
        event instanceof NavigationSkipped &&
        event.code === NavigationSkippedCode.IgnoredSameUrlNavigation &&
        this.path(event.url) === LIST_URL
      ) {
        this.handleListNavigation();
      }
      if (event instanceof NavigationEnd) {
        const path = this.path(event.urlAfterRedirects);
        if (this.saved && this.isJournalPage(path, this.saved.journalId)) {
          this.detailsOrigin =
            this.historyState()['journalListOrigin'] === this.saved.origin
              ? this.saved.origin
              : undefined;
          if (!this.detailsOrigin) this.resetContext();
        } else if (path !== LIST_URL) {
          this.resetContext();
        } else if (this.navigationSourcePath === LIST_URL) {
          this.handleListNavigation();
        }
      }
    });
  }

  /** Zwraca stan nowego wejścia: Polska, sortowanie A–Z i pierwsza strona. */
  getInitialQuery(searchView: SearchView = 'ADVANCED'): Observable<JournalListQuery> {
    return of(defaultJournalListQuery(searchView));
  }

  getContextVersion(): number {
    return this.contextVersion;
  }

  selectSurveyYear(year: number): void {
    if (this.surveyYear() === year) return;
    this.surveyYear.set(year);
    for (const state of this.viewStates.values()) {
      state.surveyYear = year;
      state.page = 0;
      state.scroll = [0, 0];
      state.analytics = undefined;
    }
  }

  rememberView(searchView: SearchView, state: JournalListNavigationState, version: number): void {
    if (version === this.contextVersion && this.allowedViews.includes(searchView)) {
      this.viewStates.set(searchView, structuredClone(state));
    }
  }

  beginViewSwitch(): void {
    this.switchingViews = true;
  }

  initializeSearchView(view: SearchView): void {
    if (!this.allowedViews.includes(view)) return;
    this.currentSearchView = view;
    const entry = this.readHistoryEntry(this.historyState());
    this.writeSearchViewHistory(view, false, entry?.view === view ? entry : undefined);
    this.searchViewChanges.next(view);
  }

  selectSearchView(view: SearchView): void {
    if (!this.allowedViews.includes(view) || view === this.currentSearchView) return;
    this.beginViewSwitch();
    this.currentSearchView = view;
    this.writeSearchViewHistory(view, true);
    this.searchViewChanges.next(view);
  }

  startFreshEntry(): void {
    this.resetContext();
    this.freshEntry.next();
  }

  restoreView(searchView: SearchView): JournalListNavigationState | undefined {
    const state = this.viewStates.get(searchView);
    return state ? structuredClone(state) : undefined;
  }

  prepareReturn(): SearchView | undefined {
    this.pendingReturn = this.restoreFromDetails();
    return this.pendingReturn ? (this.pendingReturn.searchView ?? 'ADVANCED') : undefined;
  }

  /** Zmiana efektywnego kontekstu usuwa stan poprzedniej instytucji, również gdy lista dostępnych widoków jest taka sama. */
  restrictViews(allowed: readonly SearchView[], contextKey?: string): void {
    if (contextKey !== undefined) {
      if (this.operatingContextKey !== undefined && this.operatingContextKey !== contextKey) {
        this.resetContext();
      }
      this.operatingContextKey = contextKey;
    }
    this.allowedViews = [...allowed];
    for (const view of this.viewStates.keys()) {
      if (!allowed.includes(view)) this.viewStates.delete(view);
    }
    if (this.pendingReturn && !allowed.includes(this.pendingReturn.searchView ?? 'ADVANCED')) {
      this.clear();
    }
  }

  resetContext(): void {
    this.surveyYear.set(undefined);
    this.clear();
    this.viewStates.clear();
    this.contextVersion++;
    this.switchingViews = false;
    this.historyContextId = crypto.randomUUID();
    this.currentSearchView = undefined;
    this.searchViewChanges.next(undefined);
  }

  /** Oznacza źródłową listę, aby odróżnić powrót z jej karty od niezależnej nawigacji. */
  createDetailsNavigationState(): { journalListOrigin: string } {
    return { journalListOrigin: crypto.randomUUID() };
  }

  /**
   * Kopiuje zastosowane i robocze kryteria, sortowanie, stronę, otwarte grupy i przewinięcia przed przejściem do P2.
   * Powiązanie z wpisem historii zapobiega odtworzeniu stanu po zwykłym wejściu z menu lub innej karty.
   */
  rememberForDetails(origin: string, journalId: number, state: JournalListNavigationState): void {
    const navigationId = this.historyState()['navigationId'];
    if (typeof navigationId !== 'number') return;
    this.saved = {
      origin,
      journalId,
      sourceNavigationId: navigationId,
      sourceEntryId: this.readHistoryEntry(this.historyState())?.entryId,
      state: structuredClone(state),
    };
    this.detailsOrigin = undefined;
  }

  /**
   * Przywraca kopię kontekstu tylko po zgodnym powrocie Wstecz lub oznaczonym linkiem P2.
   * Zwraca sam stan formularza i nawigacji; wyniki muszą zostać pobrane ponownie z aktualnych danych.
   */
  restoreFromDetails(): JournalListNavigationState | undefined {
    if (this.pendingReturn) {
      const restored = this.pendingReturn;
      this.pendingReturn = undefined;
      this.switchingViews = false;
      return structuredClone(restored);
    }
    const saved = this.saved;
    const navigation = this.router.currentNavigation();
    const previousUrl = navigation?.previousNavigation?.finalUrl;
    const fromDetails =
      saved &&
      this.detailsOrigin === saved.origin &&
      this.isJournalPage(
        this.path(previousUrl ? this.router.serializeUrl(previousUrl) : this.router.url),
        saved.journalId,
      );
    const returning =
      fromDetails &&
      navigation &&
      (navigation.trigger === 'popstate'
        ? this.navigationStart?.restoredState?.navigationId === saved.sourceNavigationId &&
          (!saved.sourceEntryId ||
            this.readHistoryEntry(this.navigationStart?.restoredState ?? {})?.entryId ===
              saved.sourceEntryId)
        : navigation.extras.state?.['journalListReturn'] === saved.origin);
    if (!returning || !saved) {
      this.clear();
      if (!this.switchingViews) this.viewStates.clear();
      this.switchingViews = false;
      return undefined;
    }
    saved.sourceNavigationId = navigation.id;
    saved.sourceEntryId = this.readHistoryEntry(this.historyState())?.entryId;
    this.detailsOrigin = undefined;
    return structuredClone(saved.state);
  }

  /** Udostępnia znacznik powrotu wyłącznie karcie odpowiadającej zapamiętanej liście i czasopismu. */
  returnNavigationState(): Record<string, string> {
    const saved = this.saved;
    return saved &&
      this.historyState()['journalListOrigin'] === saved.origin &&
      this.isJournalPage(this.path(this.router.url), saved.journalId)
      ? { journalListReturn: saved.origin }
      : {};
  }

  /** Źródło P1 pochodzi z rozpoznanej listy, niezależnie od roku aktualnej Karty i profilu instytucji. */
  decisionAuditSource(): { sourceSearchView: SearchView; sourceSurveyYear: number } | undefined {
    const state = this.saved?.state;
    return this.returnNavigationState()['journalListReturn'] && state?.surveyYear !== undefined
      ? { sourceSearchView: state.searchView ?? 'ADVANCED', sourceSurveyYear: state.surveyYear }
      : undefined;
  }

  /** Przenosi tylko zweryfikowane źródło listy i rok celu między Kartą a Pełnymi danymi JML. */
  journalNavigationState(surveyYear?: number): { journalListOrigin?: string; surveyYear?: number } {
    return {
      ...(this.returnNavigationState()['journalListReturn']
        ? { journalListOrigin: this.saved!.origin }
        : {}),
      ...(surveyYear === undefined ? {} : { surveyYear }),
    };
  }

  private clear(): void {
    this.saved = undefined;
    this.detailsOrigin = undefined;
    this.pendingReturn = undefined;
  }

  private handleListNavigation(): void {
    if (this.router.currentNavigation()?.trigger === 'popstate') {
      const entry = this.readHistoryEntry(this.historyState());
      if (entry && this.allowedViews.includes(entry.view)) {
        if (entry.view !== this.currentSearchView) {
          this.beginViewSwitch();
          this.currentSearchView = entry.view;
          this.searchViewChanges.next(entry.view);
        }
        return;
      }
    }
    this.startFreshEntry();
  }

  private writeSearchViewHistory(
    view: SearchView,
    push: boolean,
    existing?: SearchViewHistoryEntry,
  ): void {
    const journalSearch: SearchViewHistoryEntry = existing ?? {
      contextId: this.historyContextId,
      entryId: crypto.randomUUID(),
      view,
    };
    const state = { ...this.historyState(), journalSearch };
    const url = this.location.path(true);
    if (push) {
      this.location.go(url, '', state);
    } else {
      this.location.replaceState(url, '', state);
    }
  }

  private readHistoryEntry(state: Record<string, unknown>): SearchViewHistoryEntry | undefined {
    const value = state['journalSearch'];
    if (typeof value !== 'object' || value === null) return undefined;
    const entry = value as Record<string, unknown>;
    return entry['contextId'] === this.historyContextId &&
      typeof entry['entryId'] === 'string' &&
      (entry['view'] === 'BASIC' || entry['view'] === 'ADVANCED')
      ? { contextId: this.historyContextId, entryId: entry['entryId'], view: entry['view'] }
      : undefined;
  }

  private path(url: string): string {
    return url.split(/[?#]/, 1)[0];
  }

  private isJournalPage(path: string, journalId: number): boolean {
    return path === `${LIST_URL}/${journalId}` || path === `${LIST_URL}/${journalId}/decision`;
  }

  private historyState(): Record<string, unknown> {
    const state: unknown = this.location.getState();
    return typeof state === 'object' && state !== null ? (state as Record<string, unknown>) : {};
  }
}
