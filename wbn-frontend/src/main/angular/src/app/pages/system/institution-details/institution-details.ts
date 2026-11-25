import { ChangeDetectionStrategy, Component, TemplateRef, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, combineLatest, finalize, map, of, shareReplay, startWith, switchMap } from 'rxjs';
import {
  EntityStateChangeForm,
  InstitutionIpRuleDto,
  InstitutionService,
  InstitutionStateChangeResultDto,
  InstitutionViewDto,
} from '../../../core/backend';
import { PagedListState } from '../../../shared/paged-list-base/paged-list-base';
import { InstitutionMembershipList } from '../institution-membership-list/institution-membership-list';
import { LoginSessionList } from '../login-session-list/login-session-list';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ToastService } from '../../../core/services/toast-service';
import {
  InstitutionDeletionConfirmation,
  InstitutionDeletionService,
} from '../../../core/services/institution-deletion.service';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_INSTITUTION_ENTITY_STATE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { OperatingProfileSetting } from '../institution-form/operating-profile-setting';

type InstitutionDetailsState = PagedListState<InstitutionViewDto | null>;
type ModalContext = {
  close: (result?: unknown) => void;
  dismiss: (reason?: unknown) => void;
};

const AUTH_MODE_LABELS = {
  AUTH_NOT_REQUIRED: 'page.system.institution-details.auth-mode.not-required',
  AUTH_REQUIRED: 'page.system.institution-details.auth-mode.required',
} satisfies Record<InstitutionViewDto.AuthModeEnum, string>;

const IP_ADDRESS_FAMILY_LABELS = {
  IPV4: 'IPv4',
  IPV6: 'IPv6',
} satisfies Record<InstitutionIpRuleDto.AddressFamilyEnum, string>;

const IP_RULE_TYPE_LABEL_KEYS = {
  SINGLE_IP: 'page.system.institution-form.options.single-ip',
  RANGE: 'page.system.institution-form.options.range',
  CIDR: 'page.system.institution-form.options.cidr',
  WILDCARD: 'page.system.institution-form.options.wildcard',
} satisfies Record<InstitutionIpRuleDto.RuleTypeEnum, string>;

@Component({
  selector: 'app-institution-details',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    InstitutionMembershipList,
    LoginSessionList,
    AlertData,
    NgbModule,
    TranslocoPipe,
    LocalSpinner,
    OperatingProfileSetting,
  ],
  templateUrl: './institution-details.html',
  styleUrl: './institution-details.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly institutionService = inject(InstitutionService);
  private readonly toastService = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly modalService = inject(NgbModal);
  private readonly institutionDeletionService = inject(InstitutionDeletionService);
  private readonly refreshCounter = signal(0);
  private readonly refreshCounter$ = toObservable(this.refreshCounter);
  private readonly institutionId$ = this.route.paramMap.pipe(
    map((params) => this.parseInstitutionId(params.get('id'))),
    shareReplay({ bufferSize: 1, refCount: true }),
  );

  readonly institutionId = toSignal(this.institutionId$, { initialValue: null });
  readonly isStateChangePending = signal(false);
  readonly isDeletionConfirmationPending = signal(false);
  readonly isDeletePending = signal(false);
  readonly deletionConfirmation = signal<InstitutionDeletionConfirmation | null>(null);
  readonly deletionConfirmationCode = signal('');

  readonly state = toSignal(
    combineLatest([this.institutionId$, this.refreshCounter$]).pipe(
      switchMap(([id]) => {
        if (id === null) {
          return of({
            status: 'error',
            data: null,
            error: new Error(this.transloco.translate('page.system.institution-details.invalid-id')),
          } satisfies InstitutionDetailsState);
        }

        return this.institutionService.findInstitution(id).pipe(
          map(
            (institution): InstitutionDetailsState => ({
              status: 'success',
              data: institution,
              error: null,
            }),
          ),
          startWith({
            status: 'loading',
            data: null,
            error: null,
          } satisfies InstitutionDetailsState),
          catchError((error) =>
            of({
              status: 'error',
              data: null,
              error,
            } satisfies InstitutionDetailsState),
          ),
        );
      }),
    ),
    {
      initialValue: {
        status: 'loading',
        data: null,
        error: null,
      } satisfies InstitutionDetailsState,
    },
  );

  readonly institution = computed(() => this.state().data);
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');
  readonly ipRules = computed(() => this.institution()?.institutionIpRules ?? []);
  readonly emailDomains = computed(() => this.institution()?.emailDomains ?? []);
  readonly deletionConfirmationCodeMatches = computed(() => {
    const confirmation = this.deletionConfirmation();

    return confirmation !== null && this.deletionConfirmationCode() === confirmation.confirmationCode;
  });
  readonly isDeletionActionPending = computed(
    () => this.isDeletionConfirmationPending() || this.isDeletePending(),
  );

  readonly institutionTypeLabelKey = computed(() => {
    const institutionType = this.institution()?.institutionType;

    return institutionType ? (SYSTEM_INSTITUTION_TYPE_LABELS[institutionType] ?? '-') : '-';
  });

  readonly authModeLabelKey = computed(() => {
    const authMode = this.institution()?.authMode;

    return authMode ? (AUTH_MODE_LABELS[authMode] ?? '-') : '-';
  });

  ipAddressFamilyLabel(addressFamily: InstitutionIpRuleDto.AddressFamilyEnum | undefined): string {
    return addressFamily ? (IP_ADDRESS_FAMILY_LABELS[addressFamily] ?? '-') : '-';
  }

  ipRuleTypeLabelKey(ruleType: InstitutionIpRuleDto.RuleTypeEnum | undefined): string {
    return ruleType ? (IP_RULE_TYPE_LABEL_KEYS[ruleType] ?? '-') : '-';
  }

  booleanLabelKey(value: boolean | undefined): string {
    if (value === undefined) {
      return '-';
    }

    return value ? 'page.system.common.yes' : 'page.system.common.no';
  }

  stateLabelKey(state: InstitutionViewDto.StateEnum | undefined): string {
    return state ? (SYSTEM_INSTITUTION_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: InstitutionViewDto.StateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  stateActionLabelKey(state: InstitutionViewDto.StateEnum | undefined): string {
    return state === InstitutionViewDto.StateEnum.Active
      ? 'page.system.common.deactivate'
      : 'page.system.common.activate';
  }

  stateActionIcon(state: InstitutionViewDto.StateEnum | undefined): string {
    return state === InstitutionViewDto.StateEnum.Active
      ? 'ri-close-circle-line'
      : 'ri-checkbox-circle-line';
  }

  stateActionButtonClass(state: InstitutionViewDto.StateEnum | undefined): string {
    return state === InstitutionViewDto.StateEnum.Active
      ? 'btn btn-soft-warning btn-label ms-1'
      : 'btn btn-soft-success btn-label ms-1';
  }

  changeState(institution: InstitutionViewDto): void {
    const id = institution.id ?? this.institutionId();
    const state = this.nextState(institution.state);

    if (id === null || id === undefined || state === undefined || this.isStateChangePending()) {
      return;
    }

    this.isStateChangePending.set(true);

    this.institutionService
      .changeInstitutionState(id, { state })
      .pipe(finalize(() => this.isStateChangePending.set(false)))
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

  openDeleteModal(content: TemplateRef<unknown>): void {
    const id = this.institutionId();

    if (id === null || this.isDeletionActionPending()) {
      return;
    }

    this.isDeletionConfirmationPending.set(true);

    this.institutionDeletionService
      .createConfirmation(id)
      .pipe(finalize(() => this.isDeletionConfirmationPending.set(false)))
      .subscribe({
        next: (confirmation) => {
          this.deletionConfirmation.set(confirmation);
          this.deletionConfirmationCode.set('');
          this.modalService.open(content, {
            centered: true,
            backdrop: 'static',
            keyboard: false,
          });
        },
        error: () => {
          this.toastService.show(
            this.transloco.translate(
              'page.system.institution-details.delete-institution.preparation-error',
            ),
            {
              classname: 'bg-danger text-white',
              delay: 7000,
            },
          );
        },
      });
  }

  onDeletionConfirmationCodeInput(event: Event): void {
    const input = event.target;

    if (!(input instanceof HTMLInputElement)) {
      return;
    }

    this.deletionConfirmationCode.set(
      input.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8),
    );
  }

  deleteInstitution(modal: ModalContext): void {
    const id = this.institutionId();
    const confirmation = this.deletionConfirmation();

    if (
      id === null ||
      confirmation === null ||
      !this.deletionConfirmationCodeMatches() ||
      this.isDeletePending()
    ) {
      return;
    }

    this.isDeletePending.set(true);

    this.institutionDeletionService
      .deleteInstitution(id, {
        confirmationId: confirmation.confirmationId,
        confirmationCode: this.deletionConfirmationCode(),
      })
      .pipe(finalize(() => this.isDeletePending.set(false)))
      .subscribe({
        next: () => {
          modal.close('deleted');
          this.clearDeletionConfirmation();
          this.toastService.show(
            this.transloco.translate('page.system.institution-details.delete-institution.success'),
            {
              classname: 'bg-success text-white',
              delay: 5000,
            },
          );
          this.router.navigate(['/system/admin/institutions']);
        },
        error: (error: unknown) => {
          const errorCode = this.apiErrorCode(error);

          if (
            errorCode === 'INSTITUTION_DELETION_CONFIRMATION_EXPIRED' ||
            errorCode === 'INSTITUTION_DELETION_CONFIRMATION_USED'
          ) {
            modal.dismiss('confirmation-unavailable');
            this.clearDeletionConfirmation();
          }

          this.toastService.show(this.transloco.translate(this.deletionErrorMessageKey(errorCode)), {
            classname: 'bg-danger text-white',
            delay: 7000,
          });
        },
      });
  }

  private handleStateChangeResult(result: InstitutionStateChangeResultDto | undefined): void {
    if (result?.status === InstitutionStateChangeResultDto.StatusEnum.MembershipSelectionRequired) {
      const id = this.institutionId();

      if (id !== null) {
        this.router.navigate(['/system/admin/institutions', id, 'activation']);
      }
      return;
    }

    this.toastService.show(this.transloco.translate('page.system.common.state-change-success'), {
      classname: 'bg-success text-white',
      delay: 5000,
    });
    this.refreshCounter.update((value) => value + 1);
  }

  trackByIpRule(
    _: number,
    rule: NonNullable<InstitutionViewDto['institutionIpRules']>[number],
  ): string {
    return `${rule.id ?? ''}-${rule.displayValue ?? rule.ruleRaw ?? ''}`;
  }

  trackByEmailDomain(
    _: number,
    domain: NonNullable<InstitutionViewDto['emailDomains']>[number],
  ): string {
    return domain.domain ?? '';
  }

  private parseInstitutionId(value: string | null): number | null {
    const id = Number(value);

    return Number.isInteger(id) && id > 0 ? id : null;
  }

  private clearDeletionConfirmation(): void {
    this.deletionConfirmation.set(null);
    this.deletionConfirmationCode.set('');
  }

  private apiErrorCode(error: unknown): string | null {
    if (!(error instanceof HttpErrorResponse) || typeof error.error !== 'object' || error.error === null) {
      return null;
    }

    const code = (error.error as { code?: unknown }).code;
    return typeof code === 'string' ? code : null;
  }

  private deletionErrorMessageKey(errorCode: string | null): string {
    switch (errorCode) {
      case 'INSTITUTION_DELETION_CONFIRMATION_INVALID':
        return 'page.system.institution-details.delete-institution.confirmation-invalid';
      case 'INSTITUTION_DELETION_CONFIRMATION_EXPIRED':
      case 'INSTITUTION_DELETION_CONFIRMATION_USED':
        return 'page.system.institution-details.delete-institution.confirmation-expired';
      case 'INSTITUTION_DELETION_NOT_ALLOWED':
        return 'page.system.institution-details.delete-institution.not-allowed';
      default:
        return 'page.system.institution-details.delete-institution.error';
    }
  }

  private nextState(
    state: InstitutionViewDto.StateEnum | undefined,
  ): EntityStateChangeForm.StateEnum | undefined {
    if (state === InstitutionViewDto.StateEnum.Active) {
      return EntityStateChangeForm.StateEnum.Inactive;
    }

    if (state === InstitutionViewDto.StateEnum.Inactive) {
      return EntityStateChangeForm.StateEnum.Active;
    }

    return undefined;
  }
}
