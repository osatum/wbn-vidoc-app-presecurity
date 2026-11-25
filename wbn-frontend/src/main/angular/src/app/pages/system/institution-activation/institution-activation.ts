import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  EntityStateChangeForm,
  InstitutionMembershipListDto,
  InstitutionService,
  InstitutionStateChangeResultDto,
  InstitutionViewDto,
} from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ToastService } from '../../../core/services/toast-service';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_ENTITY_STATE_LABELS,
  SYSTEM_INSTITUTION_ROLE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

@Component({
  selector: 'app-institution-activation',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslocoPipe, AlertData, LocalSpinner],
  templateUrl: './institution-activation.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionActivation {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly institutionService = inject(InstitutionService);
  private readonly transloco = inject(TranslocoService);
  private readonly toastService = inject(ToastService);

  readonly institutionId = this.parseInstitutionId(this.route.snapshot.paramMap.get('id'));
  readonly returnUrl =
    this.institutionId === null
      ? '/system/admin/institutions'
      : `/system/admin/institutions/${this.institutionId}`;
  readonly institution = signal<InstitutionViewDto | null>(null);
  readonly memberships = signal<ReadonlyArray<InstitutionMembershipListDto>>([]);
  readonly status = signal<InstitutionStateChangeResultDto.StatusEnum | null>(null);
  readonly selectedIds = signal<ReadonlySet<number>>(new Set<number>());
  readonly isLoading = signal(false);
  readonly hasError = signal(false);
  readonly isSaving = signal(false);
  readonly canShowForm = computed(
    () =>
      this.institutionId !== null &&
      !this.isLoading() &&
      !this.hasError() &&
      this.status() === InstitutionStateChangeResultDto.StatusEnum.MembershipSelectionRequired &&
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

  selectAll(): void {
    const ids = this.memberships()
      .map((membership) => membership.id)
      .filter((id): id is number => id !== undefined);

    this.selectedIds.set(new Set(ids));
  }

  clearSelection(): void {
    this.selectedIds.set(new Set<number>());
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

  confirmActivation(): void {
    if (this.institutionId === null || this.isSaving() || !this.canSubmit()) {
      return;
    }

    this.isSaving.set(true);
    this.institutionService
      .changeInstitutionState(this.institutionId, {
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

  cancelActivation(): void {
    this.router.navigateByUrl(this.returnUrl);
  }

  institutionTypeLabelKey(type: InstitutionViewDto.InstitutionTypeEnum | undefined): string {
    return type ? (SYSTEM_INSTITUTION_TYPE_LABELS[type] ?? '-') : '-';
  }

  roleLabelKey(role: InstitutionMembershipListDto.RoleEnum | undefined): string {
    return role ? (SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? '-') : '-';
  }

  stateLabelKey(state: InstitutionMembershipListDto.UserStateEnum | undefined): string {
    return state ? (SYSTEM_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: InstitutionMembershipListDto.UserStateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  private loadActivationForm(): void {
    if (this.institutionId === null) {
      this.hasError.set(true);
      return;
    }

    this.isLoading.set(true);
    this.hasError.set(false);

    this.institutionService
      .prepareInstitutionActivationForm(this.institutionId)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (result) => this.applyActivationForm(result),
        error: () => {
          this.hasError.set(true);
        },
      });
  }

  private handleStateChangeResult(result: InstitutionStateChangeResultDto | undefined): void {
    if (result?.status === InstitutionStateChangeResultDto.StatusEnum.MembershipSelectionRequired) {
      this.applyActivationForm(result);
      return;
    }

    this.toastService.show(this.transloco.translate('page.system.common.state-change-success'), {
      classname: 'bg-success text-white',
      delay: 5000,
    });
    this.router.navigateByUrl(this.returnUrl);
  }

  private applyActivationForm(result: InstitutionStateChangeResultDto | undefined): void {
    this.status.set(result?.status ?? null);
    this.institution.set(result?.institution ?? null);
    this.memberships.set(result?.memberships ?? []);
  }

  private parseInstitutionId(value: string | null): number | null {
    const id = Number(value);

    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
