import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  effect,
  inject,
  input,
  model,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, catchError, finalize, of } from 'rxjs';
import {
  EntityStateChangeForm,
  PageResult,
  UserListQuery,
  UserListViewDto,
  UserService,
  UserStateChangeResultDto,
} from '../../../core/backend';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ToastService } from '../../../core/services/toast-service';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_ENTITY_STATE_LABELS,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { UserListQueryStateService } from '../../../core/services/user-list-query-state.service';

type UserFilters = {
  email: string;
  name: string;
};

const EMPTY_USER_PAGE: PageResult = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

@Component({
  selector: 'app-user-lookup-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    AlertData,
    ListPaging,
    TranslocoPipe,
    LocalSpinner,
  ],
  templateUrl: './user-lookup-list.html',
  styleUrl: './user-lookup-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserLookupList extends PagedListBase<
  UserListQuery,
  UserListViewDto,
  PageResult
> {
  private readonly transloco = inject(TranslocoService);

  readonly institutionId = input<number | null | undefined>(undefined);
  readonly emptyMessage = input(this.transloco.translate('page.system.user-lookup-list.empty'));
  readonly errorMessage = input(
    this.transloco.translate('page.system.user-lookup-list.load-error'),
  );
  readonly showHeader = input(true);
  readonly sortBy = model<UserListQuery.SortByEnum>(
    UserListQuery.SortByEnum.SortByNameAsc,
  );

  private readonly userService = inject(UserService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);
  private readonly toastService = inject(ToastService);
  private readonly router = inject(Router);
  private readonly userListQueryState = inject(UserListQueryStateService);

  readonly stateChangePendingIds = signal<ReadonlySet<number>>(new Set<number>());
  private readonly isQueryInitialized = signal(false);
  private readonly appliedStorageScope = signal<string | undefined>(undefined);

  readonly filterForm = this.fb.nonNullable.group({
    email: [''],
    name: [''],
  });

  private readonly filters = signal<UserFilters>(this.filterForm.getRawValue());
  private readonly normalizedInstitutionId = computed(() => {
    const id = this.institutionId();

    return typeof id === 'number' && Number.isInteger(id) && id > 0 ? id : undefined;
  });
  private readonly storageScope = computed(() => {
    const institutionId = this.normalizedInstitutionId();

    return institutionId === undefined ? 'global' : `institution-${institutionId}`;
  });

  readonly query = computed<UserListQuery>(() => {
    const filters = this.filters();
    const institutionId = this.normalizedInstitutionId();

    return {
      ...this.buildPagingQuery(),
      email: filters.email.trim() || undefined,
      name: filters.name.trim() || undefined,
      institutionId,
      sortBy: this.sortBy(),
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_USER_PAGE,
      injector: this.injector,
      enabled: this.isQueryInitialized,
      loader: (query) => this.loadUsers(query),
    });
    this.restoreQueryState();
    this.persistQueryState();
  }

  private restoreQueryState(): void {
    effect((onCleanup) => {
      const scope = this.storageScope();
      this.isQueryInitialized.set(false);

      const subscription = this.userListQueryState
        .getInitialQuery(scope)
        .pipe(catchError(() => of(this.defaultUserListQuery())))
        .subscribe((query) => {
          if (this.storageScope() !== scope) {
            return;
          }

          this.applyInitialQuery(query);
          this.appliedStorageScope.set(scope);
          this.isQueryInitialized.set(true);
        });

      onCleanup(() => subscription.unsubscribe());
    });
  }

  private persistQueryState(): void {
    effect(() => {
      const scope = this.storageScope();

      if (!this.isQueryInitialized() || this.appliedStorageScope() !== scope) {
        return;
      }

      this.userListQueryState.saveQuery(this.query(), scope);
    });
  }

  private applyInitialQuery(query: UserListQuery): void {
    const size = this.positiveInteger(query.size);

    if (size !== undefined) {
      this.changePageSizeValue(size);
    }

    this.page.set(this.nonNegativeInteger(query.page) ?? 0);
    this.sortBy.set(this.sortByFromQuery(query.sortBy));
    this.filterForm.reset(this.filtersFromQuery(query));
    this.filters.set(this.filterForm.getRawValue());
  }

  private sortByFromQuery(value: UserListQuery.SortByEnum | undefined): UserListQuery.SortByEnum {
    switch (value) {
      case UserListQuery.SortByEnum.SortByEmailAsc:
      case UserListQuery.SortByEnum.SortByEmailDesc:
      case UserListQuery.SortByEnum.SortByNameAsc:
      case UserListQuery.SortByEnum.SortByNameDesc:
        return value;
      default:
        return UserListQuery.SortByEnum.SortByNameAsc;
    }
  }

  private filtersFromQuery(query: UserListQuery): UserFilters {
    return {
      email: this.textFilterValue(query.email),
      name: this.textFilterValue(query.name),
    };
  }

  private defaultUserListQuery(): UserListQuery {
    return {
      page: 0,
      size: 10,
      first: 0,
      sortBy: UserListQuery.SortByEnum.SortByNameAsc,
    };
  }

  private textFilterValue(value: unknown): string {
    return typeof value === 'string' ? value : '';
  }

  private positiveInteger(value: unknown): number | undefined {
    const parsed = this.integerValue(value);

    if (parsed === undefined || parsed <= 0) {
      return undefined;
    }

    return parsed;
  }

  private nonNegativeInteger(value: unknown): number | undefined {
    const parsed = this.integerValue(value);

    if (parsed === undefined || parsed < 0) {
      return undefined;
    }

    return parsed;
  }

  private integerValue(value: unknown): number | undefined {
    if (typeof value === 'number') {
      return Number.isInteger(value) ? value : undefined;
    }

    if (typeof value !== 'string') {
      return undefined;
    }

    const trimmed = value.trim();

    if (!trimmed) {
      return undefined;
    }

    const parsed = Number(trimmed);
    return Number.isInteger(parsed) ? parsed : undefined;
  }

  applyFilters(): void {
    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  resetFilters(): void {
    this.filterForm.reset({
      email: '',
      name: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  trackByUser(_: number, user: UserListViewDto): number | string {
    return user.id ?? user.email ?? '';
  }

  stateLabelKey(state: UserListViewDto.StateEnum | undefined): string {
    return state ? (SYSTEM_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: UserListViewDto.StateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  stateActionLabelKey(state: UserListViewDto.StateEnum | undefined): string {
    return state === UserListViewDto.StateEnum.Active
      ? 'page.system.common.deactivate'
      : 'page.system.common.activate';
  }

  stateActionIcon(state: UserListViewDto.StateEnum | undefined): string {
    return state === UserListViewDto.StateEnum.Active
      ? 'ri-close-circle-line'
      : 'ri-checkbox-circle-line';
  }

  stateActionButtonClass(state: UserListViewDto.StateEnum | undefined): string {
    return state === UserListViewDto.StateEnum.Active
      ? 'btn btn-soft-warning btn-label mb-3'
      : 'btn btn-soft-success btn-label mb-3';
  }

  isStateChangePending(id: number | undefined): boolean {
    return id !== undefined && this.stateChangePendingIds().has(id);
  }

  changeState(user: UserListViewDto): void {
    const id = user.id;
    const state = this.nextState(user.state);

    if (id === undefined || state === undefined || this.isStateChangePending(id)) {
      return;
    }

    this.markStateChangePending(id, true);

    this.userService
      .changeUserState(id, { state })
      .pipe(finalize(() => this.markStateChangePending(id, false)))
      .subscribe({
        next: (result) => this.handleStateChangeResult(id, state, result),
        error: () => {
          this.toastService.show(
            this.transloco.translate('page.system.common.state-change-error'),
            {
              classname: 'bg-danger text-white',
              delay: 7000,
            },
          );
        },
      });
  }

  private loadUsers(query: UserListQuery): Observable<PageResult> {
    const { institutionId, ...userListQuery } = query;

    if (institutionId !== undefined) {
      return this.userService.findUsersByInstitution(institutionId, userListQuery);
    }

    return this.userService.findUsers(userListQuery);
  }

  private handleStateChangeResult(
    userId: number,
    state: EntityStateChangeForm.StateEnum,
    result: UserStateChangeResultDto | undefined,
  ): void {
    if (result?.status === UserStateChangeResultDto.StatusEnum.MembershipSelectionRequired) {
      this.router.navigate(['/system/admin/users', userId, 'activation']);
      return;
    }

    if (result?.status === UserStateChangeResultDto.StatusEnum.InactiveInstitution) {
      this.toastService.show(
        this.transloco.translate('page.system.user-activation.inactive-institution'),
        {
          classname: 'bg-warning text-dark',
          delay: 9000,
        },
      );
      return;
    }

    this.toastService.show(this.transloco.translate(this.stateChangeSuccessMessageKey(state)), {
      classname: 'bg-success text-white',
      delay: 5000,
    });
    this.reloadPage();
  }

  private stateChangeSuccessMessageKey(state: EntityStateChangeForm.StateEnum): string {
    if (state === EntityStateChangeForm.StateEnum.Inactive) {
      return 'page.system.user-state-change.deactivated-with-memberships';
    }

    if (state === EntityStateChangeForm.StateEnum.Active) {
      return 'page.system.user-state-change.activated-with-membership';
    }

    return 'page.system.common.state-change-success';
  }

  private nextState(
    state: UserListViewDto.StateEnum | undefined,
  ): EntityStateChangeForm.StateEnum | undefined {
    if (state === UserListViewDto.StateEnum.Active) {
      return EntityStateChangeForm.StateEnum.Inactive;
    }

    if (state === UserListViewDto.StateEnum.Inactive) {
      return EntityStateChangeForm.StateEnum.Active;
    }

    return undefined;
  }

  private markStateChangePending(id: number, pending: boolean): void {
    this.stateChangePendingIds.update((ids) => {
      const next = new Set(ids);

      if (pending) {
        next.add(id);
      } else {
        next.delete(id);
      }

      return next;
    });
  }
}
