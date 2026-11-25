import { ChangeDetectionStrategy, Component, TemplateRef, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, finalize, map, of, shareReplay, startWith, switchMap } from 'rxjs';
import {
  EntityStateChangeForm,
  UserMembershipDto,
  UserService,
  UserStateChangeResultDto,
  UserViewDto,
} from '../../../core/backend';
import { PagedListState } from '../../../shared/paged-list-base/paged-list-base';
import { LoginSessionList } from '../login-session-list/login-session-list';
import { AlertData } from '../../../shared/alert-data/alert-data';
import {
  NgbModule,
  NgbModal,
  NgbNav,
  NgbNavContent,
  NgbNavLink,
  NgbNavLinkBase,
} from '@ng-bootstrap/ng-bootstrap';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ToastService } from '../../../core/services/toast-service';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_ENTITY_STATE_LABELS,
  SYSTEM_INSTITUTION_ROLE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type UserDetailsState = PagedListState<UserViewDto | null>;
type ModalContext = {
  close: (result?: unknown) => void;
  dismiss: (reason?: unknown) => void;
};

@Component({
  selector: 'app-user-details',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    LoginSessionList,
    AlertData,
    NgbNav,
    NgbNavContent,
    NgbNavLink,
    NgbNavLinkBase,
    NgbModule,
    TranslocoPipe,
    LocalSpinner,
  ],
  templateUrl: './user-details.html',
  styleUrl: './user-details.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly userService = inject(UserService);
  private readonly toastService = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly modalService = inject(NgbModal);
  private readonly refreshCounter = signal(0);
  private readonly refreshCounter$ = toObservable(this.refreshCounter);
  private readonly userId$ = this.route.paramMap.pipe(
    map((params) => this.parseUserId(params.get('id'))),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly userId = toSignal(this.userId$, { initialValue: null });
  readonly isStateChangePending = signal(false);
  readonly isDeletePending = signal(false);

  readonly state = toSignal(
    combineLatest([this.userId$, this.refreshCounter$]).pipe(
      switchMap(([id]) => {
        if (id === null) {
          return of({
            status: 'error',
            data: null,
            error: new Error(this.transloco.translate('page.system.user-details.invalid-id')),
          } satisfies UserDetailsState);
        }

        return this.userService.findUser(id).pipe(
          map(
            (user): UserDetailsState => ({
              status: 'success',
              data: user,
              error: null,
            }),
          ),
          startWith({
            status: 'loading',
            data: null,
            error: null,
          } satisfies UserDetailsState),
          catchError((error) =>
            of({
              status: 'error',
              data: null,
              error,
            } satisfies UserDetailsState),
          ),
        );
      }),
    ),
    {
      initialValue: {
        status: 'loading',
        data: null,
        error: null,
      } satisfies UserDetailsState,
    },
  );

  readonly user = computed(() => this.state().data);
  readonly loginSessionUserId = computed(() => this.normalizeId(this.user()?.id) ?? this.userId());
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');
  readonly memberships = computed(() => this.user()?.memberships ?? []);

  readonly fullName = computed(() => {
    const firstName = this.user()?.firstName?.trim() ?? '';
    const lastName = this.user()?.lastName?.trim() ?? '';
    const name = `${firstName} ${lastName}`.trim();

    return name || '-';
  });

  roleLabelKey(role: UserMembershipDto.RoleEnum | undefined): string {
    return role ? (SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? '-') : '-';
  }

  institutionTypeLabelKey(type: UserMembershipDto.InstitutionTypeEnum | undefined): string {
    return type ? (SYSTEM_INSTITUTION_TYPE_LABELS[type] ?? '-') : '-';
  }

  stateLabelKey(state: UserViewDto.StateEnum | undefined): string {
    return state ? (SYSTEM_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: UserViewDto.StateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  stateActionLabelKey(state: UserViewDto.StateEnum | undefined): string {
    return state === UserViewDto.StateEnum.Active
      ? 'page.system.common.deactivate'
      : 'page.system.common.activate';
  }

  stateActionIcon(state: UserViewDto.StateEnum | undefined): string {
    return state === UserViewDto.StateEnum.Active
      ? 'ri-close-circle-line'
      : 'ri-checkbox-circle-line';
  }

  stateActionButtonClass(state: UserViewDto.StateEnum | undefined): string {
    return state === UserViewDto.StateEnum.Active
      ? 'btn btn-soft-warning btn-label ms-1'
      : 'btn btn-soft-success btn-label ms-1';
  }

  changeState(user: UserViewDto): void {
    const id = user.id ?? this.userId();
    const state = this.nextState(user.state);

    if (id === null || id === undefined || state === undefined || this.isStateChangePending()) {
      return;
    }

    this.isStateChangePending.set(true);

    this.userService
      .changeUserState(id, { state })
      .pipe(finalize(() => this.isStateChangePending.set(false)))
      .subscribe({
        next: (result) => this.handleStateChangeResult(state, result),
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

  openDeleteModal(content: TemplateRef<unknown>): void {
    if (this.isDeletePending()) {
      return;
    }

    this.modalService.open(content, {
      centered: true,
      backdrop: 'static',
      keyboard: false,
    });
  }

  deleteUser(modal: ModalContext): void {
    const id = this.userId();

    if (id === null || this.isDeletePending()) {
      return;
    }

    this.isDeletePending.set(true);

    this.userService
      .deleteUser(id)
      .pipe(finalize(() => this.isDeletePending.set(false)))
      .subscribe({
        next: () => {
          modal.close('deleted');
          this.toastService.show(
            this.transloco.translate('page.system.user-details.delete-success'),
            {
              classname: 'bg-success text-white',
              delay: 5000,
            },
          );
          this.router.navigate(['/system/admin/users']);
        },
        error: () => {
          this.toastService.show(
            this.transloco.translate('page.system.user-details.delete-error'),
            {
              classname: 'bg-danger text-white',
              delay: 7000,
            },
          );
        },
      });
  }

  trackByMembership(_: number, membership: UserMembershipDto): string {
    return `${membership.id ?? ''}-${membership.institutionName ?? ''}-${membership.role ?? ''}`;
  }

  private parseUserId(value: string | null): number | null {
    return this.normalizeId(Number(value)) ?? null;
  }

  private normalizeId(value: number | undefined): number | undefined {
    if (value === undefined) {
      return undefined;
    }

    return Number.isInteger(value) && value > 0 ? value : undefined;
  }

  private nextState(
    state: UserViewDto.StateEnum | undefined,
  ): EntityStateChangeForm.StateEnum | undefined {
    if (state === UserViewDto.StateEnum.Active) {
      return EntityStateChangeForm.StateEnum.Inactive;
    }

    if (state === UserViewDto.StateEnum.Inactive) {
      return EntityStateChangeForm.StateEnum.Active;
    }

    return undefined;
  }

  private handleStateChangeResult(
    state: EntityStateChangeForm.StateEnum,
    result: UserStateChangeResultDto | undefined,
  ): void {
    if (result?.status === UserStateChangeResultDto.StatusEnum.MembershipSelectionRequired) {
      const id = this.userId();

      if (id !== null) {
        this.router.navigate(['/system/admin/users', id, 'activation']);
      }
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
    this.refreshCounter.update((value) => value + 1);
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
}
