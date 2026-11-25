import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import {
  NavigationAuditBatchDto,
  NavigationAuditService as NavigationAuditApiService,
} from '../backend';
import { AuthService } from './auth.service';
import { NavigationAuditService } from './navigation-audit.service';

describe('NavigationAuditService view context', () => {
  const collect = vi.fn((batch: NavigationAuditBatchDto) => of(undefined));
  let service: NavigationAuditService;

  beforeEach(() => {
    vi.useFakeTimers();
    collect.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: NavigationAuditApiService, useValue: { collect } },
        { provide: AuthService, useValue: { token: () => null } },
      ],
    });
    service = TestBed.inject(NavigationAuditService);
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('keeps the original view and copies resource and route parameters', () => {
    const params = { id: '123' };
    const resource = { type: 'journal', id: '123' };
    const viewId = service.startView({
      pageKey: 'institution.journals.details',
      routeTemplate: '/institution/user/journals/:id',
      url: '/institution/user/journals/123',
      params,
      resource,
    });
    const viewContext = service.captureView();
    params.id = '456';
    resource.id = '456';
    service.startView({
      pageKey: 'institution.journals.list',
      routeTemplate: '/institution/user/journals',
      url: '/institution/user/journals',
    });
    service.track({ ...viewContext, eventType: 'FILE_DOWNLOAD', success: true });
    service.flush();

    const events = collect.mock.calls[0][0].events;
    expect(events.find((event) => event.eventType === 'FILE_DOWNLOAD')).toEqual(
      expect.objectContaining({
        viewId,
        pageKey: 'institution.journals.details',
        url: '/institution/user/journals/123',
        params: { id: '123' },
        resource: { type: 'journal', id: '123' },
      }),
    );
  });

  it('retries P2 with the same event ID, action and card year and discards it on actor change', () => {
    const token = vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue('p2-actor');
    service.startView({
      pageKey: 'institution.journals.decision',
      routeTemplate: '/institution/user/journals/:id/decision',
    });
    service.flush();
    collect.mockClear();
    service.track({
      eventType: 'JOURNAL_DECISION_ACTION',
      action: 'E01',
      properties: { schemaVersion: 1, journalId: 123, surveyYear: 2024 },
    });
    collect.mockReturnValueOnce(throwError(() => new Error('offline')));
    service.flush();
    service.flush();
    expect(collect.mock.calls[1][0].events).toEqual(collect.mock.calls[0][0].events);
    expect(collect.mock.calls[0][0].events).toHaveLength(1);
    collect.mockClear();
    service.track({
      eventType: 'JOURNAL_DECISION_ACTION',
      action: 'E03',
      properties: { schemaVersion: 1, journalId: 123, surveyYear: 2024 },
    });
    token.mockReturnValue('different-p2-actor');
    service.flush();
    expect(collect).not.toHaveBeenCalled();
  });

  it('resends the same P1 event ids and full criteria after a failed batch delivery', () => {
    service.startView({
      pageKey: 'institution.journals.list',
      routeTemplate: '/institution/user/journals',
    });
    service.flush();
    collect.mockClear();
    const properties = {
      operationId: crypto.randomUUID(),
      criteria: { journalTitle: 'Long title '.repeat(600) },
    };
    service.track({ eventType: 'JOURNAL_SEARCH', properties });
    collect.mockReturnValueOnce(throwError(() => new Error('offline')));
    service.flush();
    service.flush();
    const first = collect.mock.calls[0][0].events;
    const retry = collect.mock.calls[1][0].events;
    expect(first).toEqual(retry);
    expect(first).toHaveLength(1);
    expect(first[0].properties).toEqual(properties);
    expect(JSON.stringify(first[0].properties).length).toBeGreaterThan(4000);
  });

  it('does not deliver queued P1 criteria under a different authentication context', () => {
    const auth = TestBed.inject(AuthService);
    const token = vi.spyOn(auth, 'token').mockReturnValue('original-test-context');
    service.startView({
      pageKey: 'institution.journals.list',
      routeTemplate: '/institution/user/journals',
    });
    service.flush();
    collect.mockClear();
    service.track({
      eventType: 'JOURNAL_SEARCH',
      properties: { criteria: { journalTitle: 'Old context' } },
    });
    token.mockReturnValue('new-test-context');
    service.flush();
    expect(collect).not.toHaveBeenCalled();
    service.track({
      eventType: 'JOURNAL_SEARCH',
      properties: { criteria: { journalTitle: 'New context' } },
    });
    service.flush();
    expect(collect.mock.calls[0][0].events).toHaveLength(1);
    expect(collect.mock.calls[0][0].events[0].properties).toEqual({
      criteria: { journalTitle: 'New context' },
    });
    token.mockRestore();
  });

  it('discards a failed P1 batch on context change without suppressing ordinary file audit', () => {
    const token = vi
      .spyOn(TestBed.inject(AuthService), 'token')
      .mockReturnValue('original-test-context');
    service.startView({
      pageKey: 'institution.journals.list',
      routeTemplate: '/institution/user/journals',
    });
    service.flush();
    collect.mockClear();
    service.track({ eventType: 'JOURNAL_SEARCH' });
    collect.mockReturnValueOnce(throwError(() => new Error('offline')));
    service.flush();
    token.mockReturnValue(null);
    service.track({ eventType: 'FILE_DOWNLOAD' });
    service.flush();
    expect(collect.mock.calls[1][0].events.map((event) => event.eventType)).toEqual([
      'FILE_DOWNLOAD',
    ]);
    token.mockRestore();
  });

  it('keeps queued criteria through token refresh of the same session without sending token claims', () => {
    const claims = {
      type: 'USER',
      sub: '1',
      institutionId: 10,
      institutionRole: 'INSTITUTION_USER',
      loginSessionId: 42,
    };
    const firstToken = `header.${btoa(JSON.stringify({ ...claims, exp: 100 }))}.signature`;
    const nextToken = `header.${btoa(JSON.stringify({ ...claims, exp: 200 }))}.signature`;
    const token = vi.spyOn(TestBed.inject(AuthService), 'token').mockReturnValue(firstToken);
    service.startView({
      pageKey: 'institution.journals.list',
      routeTemplate: '/institution/user/journals',
    });
    service.flush();
    collect.mockClear();
    service.track({
      eventType: 'JOURNAL_SEARCH',
      properties: { criteria: { countryCodes: ['pl'] } },
    });
    token.mockReturnValue(nextToken);
    service.flush();
    expect(collect.mock.calls[0][0].events).toHaveLength(1);
    expect(JSON.stringify(collect.mock.calls[0][0])).not.toContain('loginSessionId');
    expect(JSON.stringify(collect.mock.calls[0][0])).not.toContain('signature');
    token.mockRestore();
  });

  it('does not attribute a download without an original view to a later view', () => {
    const viewContext = service.captureView();
    service.startView({
      pageKey: 'institution.journals.list',
      routeTemplate: '/institution/user/journals',
    });
    service.track({ ...viewContext, eventType: 'FILE_DOWNLOAD' });
    service.flush();

    expect(
      collect.mock.calls[0][0].events.some((event) => event.eventType === 'FILE_DOWNLOAD'),
    ).toBe(false);
  });
});
