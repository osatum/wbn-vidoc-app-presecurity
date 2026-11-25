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
  PageResultPasswordResetRequestListDto,
  PasswordResetRequestListDto,
  PasswordResetRequestListQuery,
  PasswordResetRequestsService,
} from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type PasswordResetRequestFilters = {
  name: string;
  email: string;
  ipAddress: string;
  userAgent: string;
  status: '' | PasswordResetRequestListQuery.StatusEnum;
  requestedFrom: string;
  requestedTo: string;
};

const EMPTY_PASSWORD_RESET_REQUEST_PAGE: PageResultPasswordResetRequestListDto = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

const STATUS_LABEL_KEYS = {
  EMAIL_QUEUED: 'page.system.password-reset-request-list.status.email-queued',
  LINK_CLICKED: 'page.system.password-reset-request-list.status.link-clicked',
  COMPLETED: 'page.system.password-reset-request-list.status.completed',
  EXPIRED: 'page.system.password-reset-request-list.status.expired',
  INVALIDATED: 'page.system.password-reset-request-list.status.invalidated',
  USER_NOT_FOUND: 'page.system.password-reset-request-list.status.user-not-found',
  USER_NOT_ACTIVE: 'page.system.password-reset-request-list.status.user-not-active',
  RATE_LIMITED: 'page.system.password-reset-request-list.status.rate-limited',
  FAILED: 'page.system.password-reset-request-list.status.failed',
} satisfies Record<PasswordResetRequestListDto.StatusEnum, string>;

@Component({
  selector: 'app-password-reset-request-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertData, ListPaging, TranslocoPipe, LocalSpinner],
  templateUrl: './password-reset-request-list.html',
  styleUrl: './password-reset-request-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetRequestList extends PagedListBase<
  PasswordResetRequestListQuery,
  PasswordResetRequestListDto,
  PageResultPasswordResetRequestListDto
> {
  private readonly transloco = inject(TranslocoService);
  private readonly passwordResetRequestsService = inject(PasswordResetRequestsService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);

  readonly emptyMessage = input(
    this.transloco.translate('page.system.password-reset-request-list.empty'),
  );

  readonly statuses = [
    {
      value: PasswordResetRequestListQuery.StatusEnum.EmailQueued,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.EmailQueued],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.LinkClicked,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.LinkClicked],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.Completed,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.Completed],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.Expired,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.Expired],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.Invalidated,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.Invalidated],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.UserNotFound,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.UserNotFound],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.UserNotActive,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.UserNotActive],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.RateLimited,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.RateLimited],
    },
    {
      value: PasswordResetRequestListQuery.StatusEnum.Failed,
      labelKey: STATUS_LABEL_KEYS[PasswordResetRequestListDto.StatusEnum.Failed],
    },
  ] satisfies ReadonlyArray<{ value: PasswordResetRequestListQuery.StatusEnum; labelKey: string }>;

  readonly filterForm = this.fb.nonNullable.group({
    name: [''],
    email: [''],
    ipAddress: [''],
    userAgent: [''],
    status: ['' as PasswordResetRequestFilters['status']],
    requestedFrom: [''],
    requestedTo: [''],
  });

  private readonly filters = signal<PasswordResetRequestFilters>(this.filterForm.getRawValue());

  readonly query = computed<PasswordResetRequestListQuery>(() => {
    const filters = this.filters();

    return {
      ...this.buildPagingQuery(),
      name: filters.name.trim() || undefined,
      email: filters.email.trim() || undefined,
      ipAddress: filters.ipAddress.trim() || undefined,
      userAgent: filters.userAgent.trim() || undefined,
      status: filters.status || undefined,
      requestedFrom: this.toIsoDateBoundary(filters.requestedFrom, 'start'),
      requestedTo: this.toIsoDateBoundary(filters.requestedTo, 'end'),
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_PASSWORD_RESET_REQUEST_PAGE,
      injector: this.injector,
      loader: (query) => this.passwordResetRequestsService.findPasswordResetRequests(query),
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
      requestedFrom: '',
      requestedTo: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  statusLabelKey(value: PasswordResetRequestListDto.StatusEnum | undefined): string {
    return value ? (STATUS_LABEL_KEYS[value] ?? '-') : '-';
  }

  userName(request: PasswordResetRequestListDto): string {
    return [request.userFirstName, request.userLastName].filter(Boolean).join(' ') || '-';
  }

  trackByPasswordResetRequest(_: number, request: PasswordResetRequestListDto): number | string {
    return (
      request.id ?? `${request.email ?? ''}-${request.requestedAt ?? ''}-${request.status ?? ''}`
    );
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
