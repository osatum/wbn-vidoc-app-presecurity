import { Signal, computed, signal, Injector } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  Observable,
  catchError,
  distinctUntilChanged,
  filter,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';

export interface PageResult<TItem> {
  data?: TItem[];
  totalElements?: number;
  totalPages?: number;
  size?: number;
}

export type PagedListState<TPage> =
  | {
      status: 'loading';
      data: TPage;
      error: null;
    }
  | {
      status: 'success';
      data: TPage;
      error: null;
    }
  | {
      status: 'error';
      data: TPage;
      error: unknown;
    };

export abstract class PagedListBase<
  TQuery extends {
    page?: number;
    size?: number;
    first?: number;
  },
  TItem,
  TPage extends PageResult<TItem>,
> {
  readonly page = signal(0);
  private readonly requestedSize = signal(10);
  private readonly refreshCounter = signal(0);

  protected result!: Signal<PagedListState<TPage>>;

  readonly items = computed<TItem[]>(() => this.result().data.data ?? []);
  readonly totalElements = computed(() => this.result().data.totalElements ?? 0);
  readonly totalPages = computed(() => this.result().data.totalPages ?? 0);
  readonly size = computed(() => this.result().data.size ?? this.requestedSize());

  readonly isLoading = computed(() => this.result().status === 'loading');
  readonly hasError = computed(() => this.result().status === 'error');

  readonly canGoPrevious = computed(() => this.page() > 0);
  readonly canGoNext = computed(() => this.page() + 1 < this.totalPages());

  protected initializePagedList(params: {
    query: Signal<TQuery>;
    emptyPage: TPage;
    injector: Injector;
    loader: (query: TQuery) => Observable<TPage>;
    enabled?: Signal<boolean>;
    retainOnDisable?: boolean;
  }): void {
    const queryWithRefresh = computed<{ query: TQuery; refresh: number } | undefined>(() => {
      if (params.enabled && !params.enabled()) {
        return undefined;
      }

      return { query: { ...params.query() }, refresh: this.refreshCounter() };
    });

    this.result = toSignal(
      toObservable(queryWithRefresh, { injector: params.injector }).pipe(
        filter((request) => request !== undefined || params.retainOnDisable !== true),
        distinctUntilChanged(
          (previous, current) =>
            params.retainOnDisable === true &&
            previous !== undefined &&
            current !== undefined &&
            previous.refresh === current.refresh &&
            JSON.stringify(previous.query) === JSON.stringify(current.query),
        ),
        switchMap((request) =>
          request === undefined
            ? of({
                status: 'loading',
                data: params.emptyPage,
                error: null,
              } satisfies PagedListState<TPage>)
            : params.loader(request.query).pipe(
                map(
                  (data): PagedListState<TPage> => ({
                    status: 'success',
                    data,
                    error: null,
                  }),
                ),
                startWith({
                  status: 'loading',
                  data: params.emptyPage,
                  error: null,
                } satisfies PagedListState<TPage>),
                catchError((error) =>
                  of({
                    status: 'error',
                    data: params.emptyPage,
                    error,
                  } satisfies PagedListState<TPage>),
                ),
              ),
        ),
      ),
      {
        initialValue: {
          status: 'loading',
          data: params.emptyPage,
          error: null,
        } satisfies PagedListState<TPage>,
        injector: params.injector,
      },
    );
  }

  protected buildPagingQuery(): Required<Pick<TQuery, 'page' | 'size' | 'first'>> {
    const page = this.page();
    const size = this.requestedSize();

    return {
      page,
      size,
      first: page * size,
    };
  }

  protected resetPaging(): void {
    this.page.set(0);
  }

  protected reloadPage(): void {
    this.refreshCounter.update((value) => value + 1);
  }

  previousPage(): void {
    if (!this.canGoPrevious()) {
      return;
    }

    this.page.update((page) => page - 1);
  }

  nextPage(): void {
    if (!this.canGoNext()) {
      return;
    }

    this.page.update((page) => page + 1);
  }

  changePageSizeValue(size: number): void {
    this.requestedSize.set(size);
    this.resetPaging();
  }
}
