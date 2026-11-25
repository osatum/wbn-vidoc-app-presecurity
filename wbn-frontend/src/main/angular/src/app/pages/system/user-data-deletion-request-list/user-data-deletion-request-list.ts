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
  PageResultUserDataDeletionRequestListDto,
  UserDataDeletionRequestListDto,
  UserDataDeletionRequestListQuery,
  UserDataDeletionRequestsService,
} from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type UserDataDeletionRequestFilters = {
  name: string;
  email: string;
  institutionName: string;
  ipAddress: string;
  userAgent: string;
  status: '' | UserDataDeletionRequestListQuery.StatusEnum;
  requestedFrom: string;
  requestedTo: string;
};

const EMPTY_USER_DATA_DELETION_REQUEST_PAGE: PageResultUserDataDeletionRequestListDto = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

const STATUS_LABEL_KEYS = {
  EMAIL_QUEUED: 'page.system.user-data-deletion-request-list.status.email-queued',
  LINK_CLICKED: 'page.system.user-data-deletion-request-list.status.link-clicked',
  COMPLETED: 'page.system.user-data-deletion-request-list.status.completed',
  EXPIRED: 'page.system.user-data-deletion-request-list.status.expired',
  INVALIDATED: 'page.system.user-data-deletion-request-list.status.invalidated',
  REJECTED: 'page.system.user-data-deletion-request-list.status.rejected',
  FAILED: 'page.system.user-data-deletion-request-list.status.failed',
} satisfies Record<UserDataDeletionRequestListDto.StatusEnum, string>;

@Component({
  selector: 'app-user-data-deletion-request-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertData, ListPaging, TranslocoPipe, LocalSpinner],
  templateUrl: './user-data-deletion-request-list.html',
  styleUrl: './user-data-deletion-request-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDataDeletionRequestList extends PagedListBase<
  UserDataDeletionRequestListQuery,
  UserDataDeletionRequestListDto,
  PageResultUserDataDeletionRequestListDto
> {
  private readonly transloco = inject(TranslocoService);
  private readonly userDataDeletionRequestsService = inject(UserDataDeletionRequestsService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);

  readonly emptyMessage = input(
    this.transloco.translate('page.system.user-data-deletion-request-list.empty'),
  );

  readonly statuses = [
    {
      value: UserDataDeletionRequestListQuery.StatusEnum.EmailQueued,
      labelKey: STATUS_LABEL_KEYS[UserDataDeletionRequestListDto.StatusEnum.EmailQueued],
    },
    {
      value: UserDataDeletionRequestListQuery.StatusEnum.LinkClicked,
      labelKey: STATUS_LABEL_KEYS[UserDataDeletionRequestListDto.StatusEnum.LinkClicked],
    },
    {
      value: UserDataDeletionRequestListQuery.StatusEnum.Completed,
      labelKey: STATUS_LABEL_KEYS[UserDataDeletionRequestListDto.StatusEnum.Completed],
    },
    {
      value: UserDataDeletionRequestListQuery.StatusEnum.Expired,
      labelKey: STATUS_LABEL_KEYS[UserDataDeletionRequestListDto.StatusEnum.Expired],
    },
    {
      value: UserDataDeletionRequestListQuery.StatusEnum.Invalidated,
      labelKey: STATUS_LABEL_KEYS[UserDataDeletionRequestListDto.StatusEnum.Invalidated],
    },
    {
      value: UserDataDeletionRequestListQuery.StatusEnum.Rejected,
      labelKey: STATUS_LABEL_KEYS[UserDataDeletionRequestListDto.StatusEnum.Rejected],
    },
    {
      value: UserDataDeletionRequestListQuery.StatusEnum.Failed,
      labelKey: STATUS_LABEL_KEYS[UserDataDeletionRequestListDto.StatusEnum.Failed],
    },
  ] satisfies ReadonlyArray<{
    value: UserDataDeletionRequestListQuery.StatusEnum;
    labelKey: string;
  }>;

  readonly filterForm = this.fb.nonNullable.group({
    name: [''],
    email: [''],
    institutionName: [''],
    ipAddress: [''],
    userAgent: [''],
    status: ['' as UserDataDeletionRequestFilters['status']],
    requestedFrom: [''],
    requestedTo: [''],
  });

  private readonly filters = signal<UserDataDeletionRequestFilters>(this.filterForm.getRawValue());

  readonly query = computed<UserDataDeletionRequestListQuery>(() => {
    const filters = this.filters();

    return {
      ...this.buildPagingQuery(),
      name: filters.name.trim() || undefined,
      email: filters.email.trim() || undefined,
      institutionName: filters.institutionName.trim() || undefined,
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
      emptyPage: EMPTY_USER_DATA_DELETION_REQUEST_PAGE,
      injector: this.injector,
      loader: (query) => this.userDataDeletionRequestsService.findUserDataDeletionRequests(query),
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
      institutionName: '',
      ipAddress: '',
      userAgent: '',
      status: '',
      requestedFrom: '',
      requestedTo: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  statusLabelKey(value: UserDataDeletionRequestListDto.StatusEnum | undefined): string {
    return value ? (STATUS_LABEL_KEYS[value] ?? '-') : '-';
  }

  userName(request: UserDataDeletionRequestListDto): string {
    return [request.userFirstName, request.userLastName].filter(Boolean).join(' ') || '-';
  }

  trackByUserDataDeletionRequest(
    _: number,
    request: UserDataDeletionRequestListDto,
  ): number | string {
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
