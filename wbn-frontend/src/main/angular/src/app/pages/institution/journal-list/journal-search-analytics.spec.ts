import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { JournalListQuery, PageResultJournalListView } from '../../../core/backend';
import { NavigationAuditService } from '../../../core/services/navigation-audit.service';
import { JournalSearchAnalytics, journalAnalyticsCriteria } from './journal-search-analytics';

describe('JournalSearchAnalytics', () => {
  it('records both processing time bounds including zero', () => {
    const ranges: JournalListQuery = {
      reviewDaysFrom: 0, reviewDaysTo: 0,
      initialAssessmentDaysFrom: 2, initialAssessmentDaysTo: 5,
      publicationDaysFrom: 30, publicationDaysTo: 60,
    };
    expect(journalAnalyticsCriteria(ranges)).toEqual(ranges);
  });

  it('includes independent applied MNiSW and JML codes without retaining mutable arrays', () => {
    const applied: JournalListQuery = {
      mniswOnly: true,
      mniswDisciplineCodes: ['2021', '2061'],
      disciplineCodes: ['42'],
      reviewModels: ['DOUBLE_BLIND_REVIEW', 'OPEN_PEER_REVIEW'],
    };
    const criteria = journalAnalyticsCriteria(applied);
    applied.mniswDisciplineCodes!.push('101');
    applied.reviewModels!.push('SINGLE_BLIND_REVIEW');
    expect(criteria).toEqual({
      mniswOnly: true,
      mniswDisciplineCodes: ['2021', '2061'],
      disciplineCodes: ['42'],
      reviewModels: ['DOUBLE_BLIND_REVIEW', 'OPEN_PEER_REVIEW'],
    });
  });
  let analytics: JournalSearchAnalytics;
  let audit: { track: ReturnType<typeof vi.fn>; captureView: ReturnType<typeof vi.fn>; flush: ReturnType<typeof vi.fn> };
  const query: JournalListQuery = {
    journalTitle: 'Applied title',
    countryCodes: ['pl'],
    page: 0,
    size: 10,
    sortBy: JournalListQuery.SortByEnum.SortByTitleAsc,
    publicationFeeMaxPln: 0,
    reviewDaysTo: 0,
  };
  const events = (type: string) =>
    audit.track.mock.calls.map(([event]) => event).filter((event) => event.eventType === type);

  beforeEach(() => {
    audit = { track: vi.fn(), flush: vi.fn(), captureView: vi.fn(() => ({ viewId: 'p1-view' })) };
    TestBed.configureTestingModule({
      providers: [JournalSearchAnalytics, { provide: NavigationAuditService, useValue: audit }],
    });
    analytics = TestBed.inject(JournalSearchAnalytics);
  });

  it('registers Search once, correlates failure/retry/zero and never logs an error as zero', () => {
    analytics.begin(query, 'SEARCH');
    const operationId = analytics.snapshot()!.operationId;
    analytics
      .load(query, () =>
        throwError(() => new HttpErrorResponse({ status: 503, error: 'private error' })),
      )
      .subscribe({ error: () => undefined });
    expect(events('JOURNAL_SEARCH_EMPTY')).toHaveLength(0);
    analytics.load(query, () => of({ totalElements: 0, data: [] })).subscribe();
    analytics.load(query, () => of({ totalElements: 0, data: [] })).subscribe();
    expect(events('JOURNAL_SEARCH')).toHaveLength(1);
    expect(events('JOURNAL_SEARCH_EMPTY')).toHaveLength(1);
    expect(events('JOURNAL_SEARCH_RESULT').map((event) => event.success)).toEqual([
      false,
      true,
      true,
    ]);
    expect(events('JOURNAL_SEARCH_RESULT').map((event) => event.properties.attempt)).toEqual([
      1, 2, 3,
    ]);
    expect(
      events('JOURNAL_SEARCH_RESULT').every(
        (event) => event.properties.operationId === operationId,
      ),
    ).toBe(true);
    expect(events('JOURNAL_SEARCH_RESULT')[0].statusCode).toBe(503);
    expect(JSON.stringify(audit.track.mock.calls)).not.toContain('private error');
  });

  it.each(['NOT_INDEXED', 'NOT_EVALUATED'] as const)(
    'includes applied %s in search analytics without point bounds',
    (ratingStatus) => {
      const criteria = { ...query, ratingStatus };
      analytics.begin(criteria, 'SEARCH');
      analytics.load(criteria, () => of({ totalElements: 0 })).subscribe();
      analytics.cardOpen(criteria, 28032);
      for (const [event] of audit.track.mock.calls) {
        expect(event.properties.criteria.ratingStatus).toBe(ratingStatus);
        expect(event.properties.criteria.icvFrom).toBeUndefined();
        expect(event.properties.criteria.icvTo).toBeUndefined();
      }
    },
  );

  it('counts identical explicit Searches independently but not initialization or return', () => {
    analytics.begin(query, 'INITIAL');
    analytics.load(query, () => of({ totalElements: 2 })).subscribe();
    expect(events('JOURNAL_SEARCH')).toHaveLength(0);
    analytics.begin(query, 'SEARCH');
    analytics.begin(query, 'SEARCH');
    const intents = events('JOURNAL_SEARCH');
    expect(intents).toHaveLength(2);
    expect(intents[0].properties.operationId).not.toBe(intents[1].properties.operationId);
    analytics.begin(query, 'RETURN', analytics.snapshot());
    expect(events('JOURNAL_SEARCH')).toHaveLength(2);
  });

  it('distinguishes Clear from Search and records successful initial emptiness without a Search', () => {
    analytics.begin(query, 'INITIAL');
    analytics.load(query, () => of({ totalElements: 0 })).subscribe();
    expect(events('JOURNAL_SEARCH')).toHaveLength(0);
    expect(events('JOURNAL_SEARCH_EMPTY')[0].properties.origin).toBe('INITIAL');
    analytics.begin({}, 'CLEAR');
    expect(events('JOURNAL_FILTERS_CLEAR')).toHaveLength(1);
    expect(events('JOURNAL_FILTERS_CLEAR')[0].properties.criteria).toEqual({});
    expect(events('JOURNAL_SEARCH')).toHaveLength(0);
  });

  it('does not report a second zero after P2 restoration and keeps the attempt sequence', () => {
    analytics.begin(query, 'SEARCH');
    analytics.load(query, () => of({ totalElements: 0 })).subscribe();
    const state = analytics.snapshot()!;
    analytics.begin(query, 'RETURN', state);
    analytics.load(query, () => of({ totalElements: 0 })).subscribe();
    expect(events('JOURNAL_SEARCH')).toHaveLength(1);
    expect(events('JOURNAL_SEARCH_EMPTY')).toHaveLength(1);
    expect(events('JOURNAL_SEARCH_RESULT')[1].properties.attempt).toBe(2);
    expect(state.attempt).toBe(1);
  });

  it('does not interpret an empty page outside the result range as zero matches', () => {
    analytics.begin(query, 'SEARCH');
    analytics.load({ ...query, page: 2 }, () => of({ data: [], totalElements: 1 })).subscribe();
    expect(events('JOURNAL_SEARCH_EMPTY')).toHaveLength(0);
    expect(events('JOURNAL_SEARCH_RESULT')[0].properties).toMatchObject({
      page: 2,
      resultCount: 1,
    });
  });

  it('ignores late results and errors of an operation superseded by a newer Search', () => {
    const old = new Subject<PageResultJournalListView>();
    analytics.begin(query, 'SEARCH');
    analytics.load(query, () => old).subscribe({ error: () => undefined });
    analytics.begin({ ...query, journalTitle: 'New applied title' }, 'SEARCH');
    old.next({ totalElements: 0 });
    old.error(new Error('old failure'));
    expect(events('JOURNAL_SEARCH_RESULT')).toHaveLength(0);
    expect(events('JOURNAL_SEARCH_EMPTY')).toHaveLength(0);
  });

  it('records card links with applied query values and a public journal id without mutating the query', () => {
    const original = structuredClone(query);
    analytics.begin(query, 'SEARCH');
    analytics.cardOpen(query, 28032);
    expect(events('JOURNAL_CARD_OPEN')[0]).toMatchObject({
      resource: { type: 'journal', id: '28032' },
      properties: {
        criteria: {
          journalTitle: 'Applied title',
          countryCodes: ['pl'],
          publicationFeeMaxPln: 0,
          reviewDaysTo: 0,
        },
      },
    });
    expect(query).toEqual(original);
  });

  it('records issues separately from the decision card and flushes without changing search state', () => {
    analytics.begin(query, 'SEARCH');
    const state = analytics.snapshot();
    analytics.issuesClick(query, 28032);
    expect(events('JOURNAL_ISSUES_CLICK')).toHaveLength(1);
    expect(events('JOURNAL_ISSUES_CLICK')[0]).toMatchObject({
      pageKey: 'institution.journals.list',
      resource: { type: 'journal', id: '28032' },
      properties: { criteria: { journalTitle: 'Applied title', publicationFeeMaxPln: 0 } },
    });
    expect(events('JOURNAL_DECISION_ACTION')).toHaveLength(0);
    expect(events('JOURNAL_CARD_OPEN')).toHaveLength(0);
    expect(audit.flush).toHaveBeenCalledTimes(1);
    expect(analytics.snapshot()).toEqual(state);
  });

  it('keeps a full independent criteria snapshot and omits legacy/noncriteria/private fields', () => {
    const input = {
      ...query,
      journalTitle: 'Long title '.repeat(700),
      countryCodes: ['pl'],
      editor: 'excluded',
      state: JournalListQuery.StateEnum.Active,
      doiOnly: false,
      languageCodes: [],
      accessToken: 'private',
    };
    const values = journalAnalyticsCriteria(input);
    input.countryCodes.push('de');
    expect(values['countryCodes']).toEqual(['pl']);
    expect(values['journalTitle']).toBe(input.journalTitle);
    expect(JSON.stringify(values).length).toBeGreaterThan(4000);
    expect(values).not.toHaveProperty('sortBy');
    expect(values).not.toHaveProperty('page');
    expect(values).not.toHaveProperty('editor');
    expect(values).not.toHaveProperty('accessToken');
    expect(values).not.toHaveProperty('doiOnly');
    expect(values).not.toHaveProperty('languageCodes');
  });

  it('does not attribute P1 events to an unrelated previous view', () => {
    audit.captureView.mockReturnValue({
      viewId: 'old-p2-view',
      pageKey: 'institution.journals.details',
    });
    analytics.begin(query, 'SEARCH');
    expect(events('JOURNAL_SEARCH')[0].pageKey).toBe('institution.journals.list');
    expect(events('JOURNAL_SEARCH')[0].viewId).toBeUndefined();
  });

  it('includes page size with result events without treating it as a search criterion', () => {
    const resized = { ...query, size: 50 };
    analytics.begin(resized, 'SEARCH');
    analytics.load(resized, () => of({ totalElements: 1199 })).subscribe();
    expect(events('JOURNAL_SEARCH_RESULT')[0].properties.size).toBe(50);
    expect(events('JOURNAL_SEARCH_RESULT')[0].properties.criteria).not.toHaveProperty('size');
  });
});
