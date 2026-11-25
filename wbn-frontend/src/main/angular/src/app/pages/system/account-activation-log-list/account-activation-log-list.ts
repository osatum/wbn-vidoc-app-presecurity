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
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  AccountActivationLogListDto,
  AccountActivationLogListQuery,
  AccountActivationsService,
  PageResultAccountActivationLogListDto,
} from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type AccountActivationLogFilters = {
  name: string;
  email: string;
  ipAddress: string;
  userAgent: string;
  status: '' | AccountActivationLogListQuery.StatusEnum;
  attemptedFrom: string;
  attemptedTo: string;
};

const EMPTY_ACCOUNT_ACTIVATION_LOG_PAGE: PageResultAccountActivationLogListDto = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

const STATUS_LABEL_KEYS = {
  REGISTRATION_SUBMITTED: 'page.system.account-activation-log-list.status.registration-submitted',
  SUCCESS_AUTO_ACTIVATED: 'page.system.account-activation-log-list.status.success-auto-activated',
  SUCCESS_WAITING_FOR_CONFIRMATION: 'page.system.account-activation-log-list.status.success-waiting-for-confirmation',
  FAILED_TOKEN_NOT_FOUND: 'page.system.account-activation-log-list.status.failed-token-not-found',
  FAILED_TOKEN_EXPIRED: 'page.system.account-activation-log-list.status.failed-token-expired',
  FAILED_TOKEN_ALREADY_USED: 'page.system.account-activation-log-list.status.failed-token-already-used',
  FAILED_MEMBERSHIP_NOT_FOUND: 'page.system.account-activation-log-list.status.failed-membership-not-found',
  FAILED_USER_NOT_FOUND: 'page.system.account-activation-log-list.status.failed-user-not-found',
  NEW_TOKEN_GENERATED: 'page.system.account-activation-log-list.status.new-token-generated',
} satisfies Record<AccountActivationLogListDto.StatusEnum, string>;

@Component({
  selector: 'app-account-activation-log-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertData, ListPaging, TranslocoPipe, LocalSpinner],
  templateUrl: './account-activation-log-list.html',
  styleUrl: './account-activation-log-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountActivationLogList extends PagedListBase<
  AccountActivationLogListQuery,
  AccountActivationLogListDto,
  PageResultAccountActivationLogListDto
> {
  private readonly transloco = inject(TranslocoService);
  private readonly accountActivationsService = inject(AccountActivationsService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);

  readonly emptyMessage = input(
    this.transloco.translate('page.system.account-activation-log-list.empty'),
  );

  readonly statuses = [
    {
      value: AccountActivationLogListQuery.StatusEnum.RegistrationSubmitted,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.RegistrationSubmitted],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.SuccessAutoActivated,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.SuccessAutoActivated],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.SuccessWaitingForConfirmation,
      labelKey:
        STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.SuccessWaitingForConfirmation],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.FailedTokenNotFound,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.FailedTokenNotFound],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.FailedTokenExpired,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.FailedTokenExpired],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.FailedTokenAlreadyUsed,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.FailedTokenAlreadyUsed],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.FailedMembershipNotFound,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.FailedMembershipNotFound],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.FailedUserNotFound,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.FailedUserNotFound],
    },
    {
      value: AccountActivationLogListQuery.StatusEnum.NewTokenGenerated,
      labelKey: STATUS_LABEL_KEYS[AccountActivationLogListDto.StatusEnum.NewTokenGenerated],
    },
  ] satisfies ReadonlyArray<{ value: AccountActivationLogListQuery.StatusEnum; labelKey: string }>;

  readonly filterForm = this.fb.nonNullable.group({
    name: [''],
    email: [''],
    ipAddress: [''],
    userAgent: [''],
    status: ['' as AccountActivationLogFilters['status']],
    attemptedFrom: [''],
    attemptedTo: [''],
  });

  private readonly filters = signal<AccountActivationLogFilters>(this.filterForm.getRawValue());

  readonly query = computed<AccountActivationLogListQuery>(() => {
    const filters = this.filters();

    return {
      ...this.buildPagingQuery(),
      name: filters.name.trim() || undefined,
      email: filters.email.trim() || undefined,
      ipAddress: filters.ipAddress.trim() || undefined,
      userAgent: filters.userAgent.trim() || undefined,
      status: filters.status || undefined,
      attemptedFrom: this.toIsoDateBoundary(filters.attemptedFrom, 'start'),
      attemptedTo: this.toIsoDateBoundary(filters.attemptedTo, 'end'),
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_ACCOUNT_ACTIVATION_LOG_PAGE,
      injector: this.injector,
      loader: (query) => this.accountActivationsService.findActivationLogs(query),
    });
  }

  applyFilters(): void {
    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  resetFilters(): void {
    this.filterForm.reset({
      name: '',
      email: '',
      ipAddress: '',
      userAgent: '',
      status: '',
      attemptedFrom: '',
      attemptedTo: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  statusLabelKey(value: AccountActivationLogListDto.StatusEnum | undefined): string {
    return value ? (STATUS_LABEL_KEYS[value] ?? '-') : '-';
  }

  userName(log: AccountActivationLogListDto): string {
    return [log.userFirstName, log.userLastName].filter(Boolean).join(' ') || '-';
  }

  trackByActivationLog(_: number, log: AccountActivationLogListDto): number | string {
    return log.id ?? `${log.userEmail ?? ''}-${log.attemptedAt ?? ''}-${log.status ?? ''}`;
  }

  private toIsoDateBoundary(value: string, boundary: 'start' | 'end'): string | undefined {
    if (!value) {
      return undefined;
    }

    const date =
      boundary === 'start' ? new Date(`${value}T00:00:00.000`) : new Date(`${value}T23:59:59.999`);

    return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
  }
}
