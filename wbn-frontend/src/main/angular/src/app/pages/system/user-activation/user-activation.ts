import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  EntityStateChangeForm,
  UserMembershipDto,
  UserService,
  UserStateChangeResultDto,
  UserViewDto,
} from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ToastService } from '../../../core/services/toast-service';
import {
  SYSTEM_INSTITUTION_ROLE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

@Component({
  selector: 'app-user-activation',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslocoPipe, AlertData, LocalSpinner],
  templateUrl: './user-activation.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserActivation {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly userService = inject(UserService);
  private readonly transloco = inject(TranslocoService);
  private readonly toastService = inject(ToastService);

  readonly userId = this.parseUserId(this.route.snapshot.paramMap.get('id'));
  readonly returnUrl =
    this.userId === null ? '/system/admin/users' : `/system/admin/users/${this.userId}`;
  readonly user = signal<UserViewDto | null>(null);
  readonly memberships = signal<ReadonlyArray<UserMembershipDto>>([]);
  readonly status = signal<UserStateChangeResultDto.StatusEnum | null>(null);
  readonly isLoading = signal(false);
  readonly hasError = signal(false);
  readonly isSaving = signal(false);
  readonly selectedIds = signal<ReadonlySet<number>>(new Set<number>());
  readonly canShowForm = computed(
    () =>
      this.userId !== null &&
      !this.isLoading() &&
      !this.hasError() &&
      this.status() === UserStateChangeResultDto.StatusEnum.MembershipSelectionRequired &&
      this.memberships().length > 0,
  );
  readonly canSubmit = computed(() => this.selectedIds().size > 0 && !this.isSaving());

  constructor() {
    effect(() => {
      const ids = this.memberships()
        .map((membership) => membership.id)
        .filter((id): id is number => id !== undefined);

      this.selectedIds.set(new Set(ids));
    });

    this.loadActivationForm();
  }

  toggleMembership(id: number | undefined, event: Event): void {
    if (id === undefined) {
      return;
    }

    const checked = event.target instanceof HTMLInputElement && event.target.checked;
    this.selectedIds.update((ids) => {
      const next = new Set(ids);

      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }

      return next;
    });
  }

  isSelected(id: number | undefined): boolean {
    return id !== undefined && this.selectedIds().has(id);
  }

  confirmMembershipActivation(): void {
    if (this.userId === null || this.isSaving()) {
      return;
    }

    this.isSaving.set(true);
    this.userService
      .changeUserState(this.userId, {
        state: EntityStateChangeForm.StateEnum.Active,
        membershipIds: [...this.selectedIds()],
      })
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (result) => this.handleStateChangeResult(result),
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

  cancelMembershipActivation(): void {
    this.router.navigateByUrl(this.returnUrl);
  }

  roleLabelKey(role: UserMembershipDto.RoleEnum | undefined): string {
    return role ? (SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? '-') : '-';
  }

  institutionTypeLabelKey(type: UserMembershipDto.InstitutionTypeEnum | undefined): string {
    return type ? (SYSTEM_INSTITUTION_TYPE_LABELS[type] ?? '-') : '-';
  }

  private loadActivationForm(): void {
    if (this.userId === null) {
      this.hasError.set(true);
      return;
    }

    this.isLoading.set(true);
    this.hasError.set(false);

    this.userService
      .prepareUserActivationForm(this.userId)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (result) => this.applyActivationForm(result),
        error: () => {
          this.hasError.set(true);
        },
      });
  }

  private handleStateChangeResult(result: UserStateChangeResultDto | undefined): void {
    if (result?.status === UserStateChangeResultDto.StatusEnum.MembershipSelectionRequired) {
      this.applyActivationForm(result);
      return;
    }

    if (result?.status === UserStateChangeResultDto.StatusEnum.InactiveInstitution) {
      this.applyActivationForm(result);
      this.toastService.show(
        this.transloco.translate('page.system.user-activation.inactive-institution'),
        {
          classname: 'bg-warning text-dark',
          delay: 9000,
        },
      );
      return;
    }

    this.toastService.show(
      this.transloco.translate('page.system.user-state-change.activated-with-membership'),
      {
        classname: 'bg-success text-white',
        delay: 5000,
      },
    );
    this.router.navigateByUrl(this.returnUrl);
  }

  private applyActivationForm(result: UserStateChangeResultDto | undefined): void {
    this.status.set(result?.status ?? null);
    this.user.set(result?.user ?? null);
    this.memberships.set(result?.memberships ?? []);
  }

  private parseUserId(value: string | null): number | null {
    const id = Number(value);

    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
