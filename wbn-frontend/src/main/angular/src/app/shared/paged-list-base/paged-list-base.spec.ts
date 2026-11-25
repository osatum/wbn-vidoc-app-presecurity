import { PageResult, PagedListBase } from './paged-list-base';
import { Injector, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';

type TestQuery = {
  page?: number;
  size?: number;
  first?: number;
};

class TestPagedListBase extends PagedListBase<TestQuery, unknown, PageResult<unknown>> {}

class AsyncPagedList extends PagedListBase<TestQuery, string, PageResult<string>> {
  readonly query = signal<TestQuery>({ page: 0 });
  readonly enabled = signal(true);
  readonly requests: {
    response: Subject<PageResult<string>>;
    cancelled: ReturnType<typeof vi.fn>;
  }[] = [];

  constructor(retainOnDisable = false) {
    super();
    this.initializePagedList({
      query: this.query,
      enabled: this.enabled,
      emptyPage: { data: [] },
      injector: inject(Injector),
      retainOnDisable,
      loader: () =>
        new Observable((subscriber) => {
          const response = new Subject<PageResult<string>>();
          const cancelled = vi.fn();
          this.requests.push({ response, cancelled });
          const subscription = response.subscribe(subscriber);
          return () => {
            subscription.unsubscribe();
            cancelled();
          };
        }),
    });
  }

  retry(): void {
    this.reloadPage();
  }
}

describe('PagedListBase', () => {
  it('should create', () => {
    const component = new TestPagedListBase();

    expect(component).toBeTruthy();
  });

  it('cancels disabled requests and ignores delayed success/error after a newer query or retry', () => {
    const list = TestBed.runInInjectionContext(() => new AsyncPagedList());
    TestBed.tick();
    const first = list.requests[0];
    list.enabled.set(false);
    TestBed.tick();
    expect(first.cancelled).toHaveBeenCalledOnce();
    first.response.next({ data: ['Old'] });
    expect(list.items()).toEqual([]);
    list.enabled.set(true);
    TestBed.tick();
    const second = list.requests[1];
    list.query.set({ page: 2 });
    TestBed.tick();
    second.response.error(new Error('Late failure'));
    list.requests[2].response.next({ data: ['New'], totalPages: 3 });
    expect(list.items()).toEqual(['New']);
    expect(list.hasError()).toBe(false);
    list.retry();
    TestBed.tick();
    expect(list.requests).toHaveLength(4);
    list.requests[2].response.next({ data: ['Too late'] });
    list.requests[3].response.next({ data: [] });
    expect(list.items()).toEqual([]);
    expect(list.hasError()).toBe(false);
  });

  it('preserves existing retain-on-disable tab caching without redundant requests', () => {
    const list = TestBed.runInInjectionContext(() => new AsyncPagedList(true));
    TestBed.tick();
    list.requests[0].response.next({ data: ['Cached'] });
    list.enabled.set(false);
    TestBed.tick();
    expect(list.items()).toEqual(['Cached']);
    list.enabled.set(true);
    TestBed.tick();
    expect(list.requests).toHaveLength(1);
  });
});
