import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Observable, finalize, of } from 'rxjs';
import {
  EntityStateChangeForm,
  InstitutionMembershipListDto,
  InstitutionMembershipListQuery,
  InstitutionMembershipStateChangeResultDto,
  InstitutionService,
  PageResult,
} from '../../../core/backend';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ToastService } from '../../../core/services/toast-service';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_ENTITY_STATE_LABELS,
  SYSTEM_INSTITUTION_ROLE_LABELS,
  type SystemEntityStateValue,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type InstitutionMembershipFilters = {
  email: string;
  name: string;
};

const EMPTY_MEMBERSHIP_PAGE: PageResult = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

const STATE_CHANGE_MESSAGES = {
  MEMBERSHIP_DEACTIVATED: 'page.system.institution-membership-list.state-change.membership-deactivated',
  MEMBERSHIP_DEACTIVATED_USER_DEACTIVATED:
    'page.system.institution-membership-list.state-change.membership-deactivated-user-deactivated',
  MEMBERSHIP_ACTIVATED: 'page.system.institution-membership-list.state-change.membership-activated',
  MEMBERSHIP_ACTIVATED_USER_ACTIVATED:
    'page.system.institution-membership-list.state-change.membership-activated-user-activated',
  MEMBERSHIP_CONFIRMED: 'page.system.institution-membership-list.state-change.membership-confirmed',
} satisfies Record<InstitutionMembershipStateChangeResultDto.StatusEnum, string>;

@Component({
  selector: 'app-institution-membership-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertData, ListPaging, TranslocoPipe, LocalSpinner],
  templateUrl: './institution-membership-list.html',
  styleUrl: './institution-membership-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionMembershipList extends PagedListBase<
  InstitutionMembershipListQuery,
  InstitutionMembershipListDto,
  PageResult
> {
  private readonly institutionService = inject(InstitutionService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);
  private readonly transloco = inject(TranslocoService);
  private readonly toastService = inject(ToastService);

  readonly institutionId = input<number | null | undefined>(undefined);
  readonly emptyMessage = input(
    this.transloco.translate('page.system.institution-membership-list.empty'),
  );
  readonly errorMessage = input(
    this.transloco.translate('page.system.institution-membership-list.load-error'),
  );
  readonly stateChangePendingIds = signal<ReadonlySet<number>>(new Set<number>());

  readonly filterForm = this.fb.nonNullable.group({
    email: [''],
    name: [''],
  });

  private readonly filters = signal<InstitutionMembershipFilters>(this.filterForm.getRawValue());
  private readonly normalizedInstitutionId = computed(() => {
    const id = this.institutionId();

    return typeof id === 'number' && Number.isInteger(id) && id > 0 ? id : undefined;
  });

  readonly query = computed<InstitutionMembershipListQuery>(() => {
    const filters = this.filters();
    const institutionId = this.normalizedInstitutionId();

    return {
      ...this.buildPagingQuery(),
      institutionId,
      email: filters.email.trim() || undefined,
      name: filters.name.trim() || undefined,
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_MEMBERSHIP_PAGE,
      injector: this.injector,
      loader: (query) => this.loadMemberships(query),
    });
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

  trackByMembership(_: number, membership: InstitutionMembershipListDto): number | string {
    return membership.id ?? membership.email ?? '';
  }

  stateLabelKey(state: SystemEntityStateValue | undefined): string {
    return state ? (SYSTEM_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: SystemEntityStateValue | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  roleLabelKey(role: InstitutionMembershipListDto.RoleEnum | undefined): string {
    return role ? (SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? '-') : '-';
  }

  stateActionLabelKey(state: InstitutionMembershipListDto.StateEnum | undefined): string {
    return state === InstitutionMembershipListDto.StateEnum.Active
      ? 'page.system.common.deactivate'
      : 'page.system.common.activate';
  }

  stateActionIcon(state: InstitutionMembershipListDto.StateEnum | undefined): string {
    return state === InstitutionMembershipListDto.StateEnum.Active
      ? 'ri-close-circle-line'
      : 'ri-checkbox-circle-line';
  }

  stateActionButtonClass(state: InstitutionMembershipListDto.StateEnum | undefined): string {
    return state === InstitutionMembershipListDto.StateEnum.Active
      ? 'btn btn-soft-warning btn-label mb-3'
      : 'btn btn-soft-success btn-label mb-3';
  }

  isStateChangePending(id: number | undefined): boolean {
    return id !== undefined && this.stateChangePendingIds().has(id);
  }

  canConfirmMembership(membership: InstitutionMembershipListDto): boolean {
    return (
      membership.role === InstitutionMembershipListDto.RoleEnum.InstitutionToConfirm &&
      membership.state === InstitutionMembershipListDto.StateEnum.Active
    );
  }

  confirmMembership(membership: InstitutionMembershipListDto): void {
    const institutionId = this.normalizedInstitutionId();
    const membershipId = membership.id;

    if (
      institutionId === undefined ||
      membershipId === undefined ||
      !this.canConfirmMembership(membership) ||
      this.isStateChangePending(membershipId)
    ) {
      return;
    }

    this.markStateChangePending(membershipId, true);

    this.institutionService
      .confirmInstitutionMembership(institutionId, membershipId)
      .pipe(finalize(() => this.markStateChangePending(membershipId, false)))
      .subscribe({
        next: (result) => {
          this.toastService.show(
            this.transloco.translate(this.stateChangeMessageKey(result?.status)),
            {
              classname: 'bg-success text-white',
              delay: 7000,
            },
          );
          this.reloadPage();
        },
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

  changeState(membership: InstitutionMembershipListDto): void {
    const institutionId = this.normalizedInstitutionId();
    const membershipId = membership.id;
    const state = this.nextState(membership.state);

    if (
      institutionId === undefined ||
      membershipId === undefined ||
      state === undefined ||
      this.isStateChangePending(membershipId)
    ) {
      return;
    }

    this.markStateChangePending(membershipId, true);

    this.institutionService
      .changeInstitutionMembershipState(institutionId, membershipId, { state })
      .pipe(finalize(() => this.markStateChangePending(membershipId, false)))
      .subscribe({
        next: (result) => {
          this.toastService.show(
            this.transloco.translate(this.stateChangeMessageKey(result?.status)),
            {
              classname: 'bg-success text-white',
              delay: 7000,
            },
          );
          this.reloadPage();
        },
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

  private loadMemberships(
    query: InstitutionMembershipListQuery,
  ): Observable<PageResult> {
    const institutionId = this.normalizedInstitutionId();

    if (institutionId === undefined) {
      return of(EMPTY_MEMBERSHIP_PAGE);
    }

    return this.institutionService.searchInstitutionMemberships(institutionId, query);
  }

  private stateChangeMessageKey(
    status: InstitutionMembershipStateChangeResultDto.StatusEnum | undefined,
  ): string {
    return status
      ? (STATE_CHANGE_MESSAGES[status] ?? 'page.system.common.state-change-success')
      : 'page.system.common.state-change-success';
  }

  private nextState(
    state: InstitutionMembershipListDto.StateEnum | undefined,
  ): EntityStateChangeForm.StateEnum | undefined {
    if (state === InstitutionMembershipListDto.StateEnum.Active) {
      return EntityStateChangeForm.StateEnum.Inactive;
    }

    if (state === InstitutionMembershipListDto.StateEnum.Inactive) {
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
