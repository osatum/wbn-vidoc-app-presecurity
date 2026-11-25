import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import {
  Event,
  Navigation,
  NavigationCancel,
  NavigationCancellationCode,
  NavigationEnd,
  NavigationSkipped,
  NavigationSkippedCode,
  NavigationStart,
  Router,
} from '@angular/router';
import { BehaviorSubject, Subject, firstValueFrom } from 'rxjs';
import { UserProfileResponse } from '../backend';
import { installSessionStorageMock } from '../../testing/session-storage-testing';
import { feeCriteriaFromQuery } from '../../pages/institution/journal-list/journal-fee-criteria';
import { nonFeeCriteriaFromQuery } from '../../pages/institution/journal-list/journal-non-fee-criteria';
import { AuthService } from './auth.service';
import { JournalListQueryStateService } from './journal-list-query-state.service';
import {
  EMPTY_SURVEY_FILTERS,
  JournalListNavigationState,
  defaultJournalListQuery,
} from './journal-list-state';

const LIST = '/institution/user/journals';

describe('JournalListQueryStateService', () => {
  it('keeps criteria but resets cached pages in both views when the shared year changes', () => {
    const version = service.getContextVersion();
    service.surveyYear.set(2025);
    service.rememberView('BASIC', { ...snapshot, surveyYear: 2025, page: 3 }, version);
    service.rememberView('ADVANCED', { ...snapshot, surveyYear: 2025, page: 4 }, version);

    service.selectSurveyYear(2024);

    expect(service.surveyYear()).toBe(2024);
    for (const view of ['BASIC', 'ADVANCED'] as const) {
      expect(service.restoreView(view)).toMatchObject({ surveyYear: 2024, page: 0, scroll: [0, 0],
        applied: snapshot.applied, draft: snapshot.draft });
    }
  });
  let service: JournalListQueryStateService;
  let events: Subject<Event>;
  let profile: BehaviorSubject<UserProfileResponse | null>;
  let history: Record<string, unknown>;
  let router: {
    url: string;
    events: Subject<Event>;
    currentNavigation: ReturnType<typeof signal<Navigation | null>>;
  };
  let snapshot: JournalListNavigationState;
  let origin: string;

  beforeEach(() => {
    installSessionStorageMock();
    events = new Subject<Event>();
    profile = new BehaviorSubject<UserProfileResponse | null>({
      userId: 1,
      institutionId: 10,
      institutionRole: 'INSTITUTION_USER',
    });
    history = { navigationId: 5 };
    router = { url: LIST, events, currentNavigation: signal<Navigation | null>(null) };
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        {
          provide: Location,
          useValue: {
            getState: () => history,
            path: () => router.url,
            go: vi.fn((_path: string, _query: string, state: Record<string, unknown>) => {
              history = state;
            }),
            replaceState: vi.fn((_path: string, _query: string, state: Record<string, unknown>) => {
              history = state;
            }),
          },
        },
        { provide: AuthService, useValue: { getUserProfile: () => profile } },
      ],
    });
    service = TestBed.inject(JournalListQueryStateService);
    origin = service.createDetailsNavigationState().journalListOrigin;
    const applied = {
      ...EMPTY_SURVEY_FILTERS,
      feeFilters: feeCriteriaFromQuery({ publicationFeeMaxPln: 0 }),
      nonFeeFilters: nonFeeCriteriaFromQuery({ repositoryIds: [10] }),
      journalTitle: 'Applied',
      publisher: '',
      issn: '',
      countryCodes: ['pl'],
      languageCodes: ['en'],
      disciplineCodes: [],
      sortBy: defaultJournalListQuery().sortBy!,
      ratingStatus: '' as const,
      icvFrom: '',
      icvTo: '',
      mniswOnly: false,
      mniswDisciplineCodes: [],
      mniswPointsFrom: '',
      mniswPointsTo: '',
    };
    snapshot = {
      applied,
      draft: { ...structuredClone(applied), journalTitle: 'Draft', reviewDaysTo: '-1' },
      page: 2,
      scroll: [0, 1200],
      expandedDetails: [0],
      showDefaultNotice: false,
    };
  });

  function visitDetails(token = origin, journalId = 28032): void {
    service.rememberForDetails(origin, 28032, snapshot);
    router.url = `${LIST}/${journalId}`;
    history = { navigationId: 6, journalListOrigin: token };
    events.next(new NavigationEnd(6, router.url, router.url));
  }

  it.each(['BASIC', 'ADVANCED'] as const)(
    'supplies only a verified %s source and original list year across Card → details',
    (view) => {
      snapshot.searchView = view;
      snapshot.surveyYear = 2025;
      visitDetails();
      router.url = `${LIST}/28032/decision`;
      history = { navigationId: 7, journalListOrigin: origin, surveyYear: 2024 };
      events.next(new NavigationEnd(7, router.url, router.url));
      expect(service.decisionAuditSource()).toEqual({
        sourceSearchView: view,
        sourceSurveyYear: 2025,
      });
      router.url = `${LIST}/28032`;
      expect(service.decisionAuditSource()?.sourceSurveyYear).toBe(2025);
      history = {
        navigationId: 8,
        surveyYear: 2024,
        sourceSearchView: view,
        sourceSurveyYear: 2025,
      };
      expect(service.decisionAuditSource()).toBeUndefined();
      profile.next({ userId: 2, institutionId: 10 });
      expect(service.decisionAuditSource()).toBeUndefined();
    },
  );

  function returnToList(
    trigger: 'imperative' | 'popstate',
    state: Record<string, unknown> = {},
    restoredId = 5,
    id = 7,
  ): void {
    events.next(
      new NavigationStart(
        id,
        LIST,
        trigger,
        trigger === 'popstate' ? { navigationId: restoredId } : null,
      ),
    );
    router.currentNavigation.set({
      id,
      initialUrl: {} as Navigation['initialUrl'],
      extractedUrl: {} as Navigation['extractedUrl'],
      trigger,
      extras: { state },
      previousNavigation: null,
      abort: vi.fn(),
    });
  }

  it.each([
    '{',
    '[]',
    JSON.stringify({
      countryCodes: ['de'],
      page: 3,
      sortBy: 'SORT_BY_RELEVANCE_DESC',
      publicationFeeMaxPln: 0,
    }),
  ])('ignores any legacy session storage on a fresh entry: %s', async (stored) => {
    sessionStorage.setItem('journalList.query', stored);
    expect(await firstValueFrom(service.getInitialQuery())).toEqual(defaultJournalListQuery());
    expect(service.restoreFromDetails()).toBeUndefined();
  });

  it('starts every fresh request with independent Poland/A-Z/page-one defaults', async () => {
    const query = await firstValueFrom(service.getInitialQuery());
    query.countryCodes?.push('de');
    expect(await firstValueFrom(service.getInitialQuery())).toEqual(defaultJournalListQuery());
    expect(sessionStorage.getItem('journalList.query')).toBeNull();
  });

  it.each(['imperative', 'popstate'] as const)(
    'restores separate applied/draft, zero fees, page and scroll on a recognized %s return',
    (trigger) => {
      visitDetails();
      returnToList(trigger, { journalListReturn: origin });
      expect(service.restoreFromDetails()).toEqual(snapshot);
    },
  );

  it('clones saved and restored nested arrays instead of sharing mutable form state', () => {
    const expected = structuredClone(snapshot);
    visitDetails();
    snapshot.applied.countryCodes.push('de');
    snapshot.draft.nonFeeFilters.repositoryIds.push('20');
    returnToList('popstate');
    const restored = service.restoreFromDetails()!;
    expect(restored).toEqual(expected);
    restored.draft.languageCodes.push('fr');
    router.url = LIST + '/28032';
    history = { navigationId: 8, journalListOrigin: origin };
    events.next(new NavigationEnd(8, router.url, router.url));
    returnToList('popstate', {}, 7, 9);
    expect(service.restoreFromDetails()).toEqual(expected);
  });

  it.each([undefined, 'wrong-origin'])(
    'treats menu or an invalid return token as a fresh entry',
    (token) => {
      visitDetails();
      returnToList('imperative', token ? { journalListReturn: token } : {});
      expect(service.restoreFromDetails()).toBeUndefined();
    },
  );

  it('rejects browser history pointing at a different P1 entry', () => {
    visitDetails();
    returnToList('popstate', {}, 3);
    expect(service.restoreFromDetails()).toBeUndefined();
  });

  it.each(['another-origin', ''])(
    'rejects direct P2 visits without the remembered origin: %s',
    (token) => {
      visitDetails(token);
      returnToList('popstate');
      expect(service.restoreFromDetails()).toBeUndefined();
      expect(service.returnNavigationState()).toEqual({});
    },
  );

  it('rejects a different journal and clears the bounded snapshot when leaving P1/P2', () => {
    visitDetails(origin, 12);
    returnToList('popstate');
    expect(service.restoreFromDetails()).toBeUndefined();
    visitDetails();
    events.next(new NavigationEnd(8, '/home', '/home'));
    returnToList('popstate');
    expect(service.restoreFromDetails()).toBeUndefined();
  });

  it('supplies the explicit P2 back-link state only for its associated journal', () => {
    visitDetails();
    expect(service.returnNavigationState()).toEqual({ journalListReturn: origin });
    router.url = LIST + '/12';
    expect(service.returnNavigationState()).toEqual({});
  });

  it.each([
    null,
    { userId: 2, institutionId: 10 },
    { userId: 1, institutionId: 20 },
    { userId: 1, institutionId: 10, institutionRole: 'INSTITUTION_ADMIN' as const },
  ])('clears state on logout/user/institution/role change: %s', (nextProfile) => {
    visitDetails();
    profile.next(nextProfile);
    returnToList('popstate');
    expect(service.restoreFromDetails()).toBeUndefined();
  });

  it('keeps state when refreshing the same profile, but never accepts a token after reload/new service', () => {
    visitDetails();
    profile.next({ ...profile.value, name: 'Updated display name' });
    returnToList('popstate');
    expect(service.restoreFromDetails()).toEqual(snapshot);
    const freshService = TestBed.runInInjectionContext(() => new JournalListQueryStateService());
    expect(freshService.restoreFromDetails()).toBeUndefined();
  });

  it('resets a live P1 when its menu link is selected again, but not on unrelated skipped/cancelled navigation', () => {
    const fresh = vi.fn();
    service.freshEntry$.subscribe(fresh);
    visitDetails();
    events.next(new NavigationCancel(7, LIST, 'guard', NavigationCancellationCode.GuardRejected));
    events.next(
      new NavigationSkipped(8, '/other', 'same', NavigationSkippedCode.IgnoredSameUrlNavigation),
    );
    expect(fresh).not.toHaveBeenCalled();
    router.url = LIST;
    events.next(
      new NavigationSkipped(9, LIST, 'same', NavigationSkippedCode.IgnoredSameUrlNavigation),
    );
    expect(fresh).toHaveBeenCalledTimes(1);
    returnToList('popstate');
    expect(service.restoreFromDetails()).toBeUndefined();
  });

  it('stores only view identifiers in history, preserves router state and pushes the same URL on switching', () => {
    history['otherState'] = 'preserved';
    const changed = vi.fn();
    service.searchViewChanges$.subscribe(changed);
    service.initializeSearchView('ADVANCED');
    const initial = structuredClone(history);
    service.selectSearchView('BASIC');
    expect(router.url).toBe(LIST);
    expect(history['navigationId']).toBe(5);
    expect(history['otherState']).toBe('preserved');
    const entry = history['journalSearch'] as Record<string, unknown>;
    const initialEntry = initial['journalSearch'] as Record<string, unknown>;
    expect(Object.keys(entry).sort()).toEqual(['contextId', 'entryId', 'view']);
    expect(entry['view']).toBe('BASIC');
    expect(entry['contextId']).toBe(initialEntry['contextId']);
    expect(entry['entryId']).not.toBe(initialEntry['entryId']);
    expect(TestBed.inject(Location).go).toHaveBeenCalledWith(LIST, '', history);
    expect(changed.mock.calls).toEqual([['ADVANCED'], ['BASIC']]);
    service.selectSearchView('BASIC');
    expect(TestBed.inject(Location).go).toHaveBeenCalledTimes(1);
  });

  it('restores a same-URL history view instead of resetting filters on NavigationSkipped', () => {
    service.initializeSearchView('ADVANCED');
    const initial = structuredClone(history);
    service.rememberView('ADVANCED', snapshot, service.getContextVersion());
    service.selectSearchView('BASIC');
    const changed = vi.fn();
    const fresh = vi.fn();
    service.searchViewChanges$.subscribe(changed);
    service.freshEntry$.subscribe(fresh);
    history = initial;
    returnToList('popstate');
    events.next(
      new NavigationSkipped(7, LIST, 'same', NavigationSkippedCode.IgnoredSameUrlNavigation),
    );
    expect(changed).toHaveBeenCalledWith('ADVANCED');
    expect(fresh).not.toHaveBeenCalled();
    expect(service.restoreFromDetails()).toBeUndefined();
    expect(service.restoreView('ADVANCED')).toEqual(snapshot);
  });

  it('does not accept persisted view history after reload or a fresh access context', () => {
    service.initializeSearchView('ADVANCED');
    service.selectSearchView('BASIC');
    const freshService = TestBed.runInInjectionContext(() => new JournalListQueryStateService());
    const fresh = vi.fn();
    const changed = vi.fn();
    freshService.freshEntry$.subscribe(fresh);
    freshService.searchViewChanges$.subscribe(changed);
    returnToList('popstate');
    events.next(
      new NavigationSkipped(7, LIST, 'same', NavigationSkippedCode.IgnoredSameUrlNavigation),
    );
    expect(fresh).toHaveBeenCalledTimes(1);
    expect(changed).not.toHaveBeenCalledWith('BASIC');
  });

  it('distinguishes same-URL view entries with equal Angular navigation IDs when returning from details', () => {
    service.initializeSearchView('ADVANCED');
    const advancedEntry = structuredClone(history['journalSearch']);
    service.selectSearchView('BASIC');
    const basicEntry = structuredClone(history['journalSearch']);
    visitDetails();
    returnToList('popstate');
    events.next(
      new NavigationStart(7, LIST, 'popstate', { navigationId: 5, journalSearch: advancedEntry }),
    );
    expect(service.restoreFromDetails()).toBeUndefined();
    router.url = LIST;
    history = { navigationId: 5, journalSearch: basicEntry };
    visitDetails();
    returnToList('popstate');
    events.next(
      new NavigationStart(7, LIST, 'popstate', { navigationId: 5, journalSearch: basicEntry }),
    );
    expect(service.restoreFromDetails()).toEqual(snapshot);
  });

  it('cannot select a forbidden view or restore one from history after narrowing the profile', () => {
    service.initializeSearchView('ADVANCED');
    service.selectSearchView('BASIC');
    const basic = structuredClone(history);
    service.selectSearchView('ADVANCED');
    service.restrictViews(['ADVANCED']);
    const changed = vi.fn();
    const fresh = vi.fn();
    service.searchViewChanges$.subscribe(changed);
    service.freshEntry$.subscribe(fresh);
    service.selectSearchView('BASIC');
    expect(changed).not.toHaveBeenCalled();
    history = basic;
    returnToList('popstate');
    events.next(
      new NavigationSkipped(7, LIST, 'same', NavigationSkippedCode.IgnoredSameUrlNavigation),
    );
    expect(changed).not.toHaveBeenCalledWith('BASIC');
    expect(fresh).toHaveBeenCalledOnce();
  });

  it('keeps the complete snapshot through decision and full details, never overwriting the list year', () => {
    snapshot.surveyYear = 2025;
    snapshot.size = 50;
    snapshot.applied.reviewDaysFrom = '0';
    snapshot.draft.reviewDaysFrom = '4';
    snapshot.applied.nonFeeFilters.reviewModels = ['DOUBLE_BLIND_REVIEW'];
    snapshot.draft.nonFeeFilters.reviewModels = ['OPEN_PEER_REVIEW'];
    visitDetails();
    router.url = LIST + '/28032/decision';
    history = { navigationId: 7, journalListOrigin: origin, surveyYear: 2025 };
    events.next(new NavigationEnd(7, router.url, router.url));
    expect(service.journalNavigationState(2025)).toEqual({
      journalListOrigin: origin,
      surveyYear: 2025,
    });
    router.url = LIST + '/28032';
    history = { navigationId: 8, ...service.journalNavigationState(2024), journalListOrigin: origin };
    events.next(new NavigationEnd(8, router.url, router.url));
    expect(service.returnNavigationState()).toEqual({ journalListReturn: origin });
    returnToList('imperative', { journalListReturn: origin });
    expect(service.restoreFromDetails()).toEqual(snapshot);
  });

  it('clears both view states when the effective operating context changes even if both allow BASIC', () => {
    service.restrictViews(['BASIC', 'ADVANCED'], 'BOTH');
    service.rememberView('BASIC', snapshot, service.getContextVersion());
    visitDetails();
    service.restrictViews(['BASIC'], 'BASIC');
    expect(service.restoreView('BASIC')).toBeUndefined();
    expect(service.returnNavigationState()).toEqual({});
    expect(service.journalNavigationState(2024)).toEqual({ surveyYear: 2024 });
  });

});
