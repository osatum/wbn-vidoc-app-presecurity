import { NgComponentOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, Injector, computed, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  Subject,
  catchError,
  combineLatest,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { OperatingContextDto, SearchView } from '../../../core/backend';
import { AuthService } from '../../../core/services/auth.service';
import { JournalListQueryStateService } from '../../../core/services/journal-list-query-state.service';
import {
  JOURNAL_SEARCH_VIEW,
  JOURNAL_DECISION_CARD_AVAILABLE,
  OperatingProfileService,
  availableSearchViews,
  canOpenDecisionCard,
} from '../../../core/services/operating-profile.service';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { JournalList } from './journal-list';

type ContextState =
  | { status: 'loading' | 'error'; context?: never }
  | { status: 'success'; context: OperatingContextDto };

@Component({
  selector: 'app-journal-search-page',
  imports: [NgComponentOutlet, TranslocoPipe, LocalSpinner],
  template: `
    @if (state().status === 'loading') {
      <app-local-spinner [height]="200" [overlay]="false" />
    } @else if (state().status === 'error') {
      <div class="alert alert-danger" role="alert">
        {{ 'operatingProfile.contextError' | transloco }}
        <button type="button" class="btn btn-primary ms-2" (click)="retry.next()">
          {{ 'page.institution.journalList.retrySearch' | transloco }}
        </button>
      </div>
    } @else {
      @if (views().length > 1) {
        <nav
          class="nav nav-pills gap-2 mb-3"
          [attr.aria-label]="'operatingProfile.searchViews' | transloco"
        >
          @for (view of views(); track view) {
            <button
              type="button"
              class="nav-link"
              [class.active]="selectedView() === view"
              [class.btn]="selectedView() !== view"
              [class.btn-light]="selectedView() !== view"
              [class.bg-white]="selectedView() !== view"
              [class.text-dark]="selectedView() !== view"
              [attr.aria-current]="selectedView() === view ? 'page' : null"
              [attr.data-testid]="'search-view-' + view"
              (click)="selectView(view)"
            >
              {{ 'operatingProfile.search.' + view | transloco }}
            </button>
          }
        </nav>
      }
      <ng-container
        [ngComponentOutlet]="listComponent"
        [ngComponentOutletInjector]="viewInjector()"
      />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalSearchPage {
  private readonly injector = inject(Injector);
  private readonly profiles = inject(OperatingProfileService);
  private readonly listState = inject(JournalListQueryStateService);
  readonly retry = new Subject<void>();
  readonly listComponent = JournalList;
  private readonly requestedView = toSignal(this.listState.searchViewChanges$, {
    initialValue: this.listState.prepareReturn(),
  });
  readonly state = toSignal(
    combineLatest([
      inject(AuthService)
        .getUserProfile()
        .pipe(
          map((profile) =>
            profile
              ? {
                  key: JSON.stringify([
                    profile.userId,
                    profile.institutionId,
                    profile.institutionRole,
                    profile.key,
                  ]),
                  institutionId: profile.institutionId,
                }
              : null,
          ),
          distinctUntilChanged((previous, current) => previous?.key === current?.key),
        ),
      this.retry.pipe(startWith(undefined)),
    ]).pipe(
      switchMap(([actor]) => {
        if (!actor) return of<ContextState>({ status: 'error' });
        return this.profiles.currentContext().pipe(
          map((context): ContextState => {
            const views = availableSearchViews(context);
            if (
              context.institutionId !== actor.institutionId ||
              !views.length ||
              !views.includes(context.defaultSearchView)
            ) {
              throw new Error('Invalid operating context');
            }
            this.listState.restrictViews(
              views,
              JSON.stringify([
                context.institutionId,
                context.configuredProfile,
                context.defaultProfile,
                context.effectiveProfile,
                [...context.availableFeatures].sort(),
              ]),
            );
            this.listState.initializeSearchView(
              views.find((view) => view === this.requestedView()) ?? context.defaultSearchView,
            );
            return { status: 'success', context };
          }),
          catchError(() => of<ContextState>({ status: 'error' })),
          startWith<ContextState>({ status: 'loading' }),
        );
      }),
    ),
    { initialValue: { status: 'loading' } as ContextState },
  );
  readonly views = computed(() => {
    const state = this.state();
    return state.status === 'success' ? availableSearchViews(state.context) : [];
  });
  readonly selectedView = computed<SearchView>(() => {
    const state = this.state();
    const requested = this.requestedView();
    return (
      this.views().find((view) => view === requested) ??
      (state.status === 'success' ? state.context.defaultSearchView : 'ADVANCED')
    );
  });
  readonly viewInjector = computed(() => {
    const state = this.state();
    return Injector.create({
      providers: [
        { provide: JOURNAL_SEARCH_VIEW, useValue: this.selectedView() },
        {
          provide: JOURNAL_DECISION_CARD_AVAILABLE,
          useValue: state.status === 'success' && canOpenDecisionCard(state.context),
        },
      ],
      parent: this.injector,
      name: state.status === 'success' ? `search-${state.context.institutionId}` : 'search-loading',
    });
  });

  constructor() {
    this.listState.freshEntry$.pipe(takeUntilDestroyed()).subscribe(() => {
      this.retry.next();
    });
  }

  selectView(view: SearchView): void {
    if (!this.views().includes(view) || view === this.selectedView()) return;
    this.listState.selectSearchView(view);
  }
}
