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
import {
  AuthSessionsService,
  LoginSessionListQuery,
  LoginSessionViewDto,
  PageResultLoginSessionViewDto,
} from '../../../core/backend';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type LoginSessionFilters = {
  name: string;
  email: string;
  ipAddress: string;
  actorType: '' | LoginSessionListQuery.ActorTypeEnum;
  status: '' | LoginSessionListQuery.StatusEnum;
  endReason: '' | LoginSessionListQuery.EndReasonEnum;
};

const EMPTY_LOGIN_SESSION_PAGE: PageResultLoginSessionViewDto = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

const ACTOR_TYPE_LABEL_KEYS = {
  USER: 'page.system.login-session-list.actor-type.user',
  INSTITUTION: 'page.system.login-session-list.actor-type.institution',
} satisfies Record<LoginSessionViewDto.ActorTypeEnum, string>;

const INSTITUTION_ROLE_LABEL_KEYS = {
  INSTITUTION_ADMIN: 'page.system.login-session-list.institution-role.admin',
  INSTITUTION_USER: 'page.system.login-session-list.institution-role.user',
  INSTITUTION_VIEWER: 'page.system.login-session-list.institution-role.viewer',
  INSTITUTION_PENDING: 'page.system.login-session-list.institution-role.pending',
  INSTITUTION_TO_CONFIRM: 'page.system.login-session-list.institution-role.to-confirm',
} satisfies Record<LoginSessionViewDto.InstitutionRoleEnum, string>;

const STATUS_LABEL_KEYS = {
  ACTIVE: 'page.system.login-session-list.status.active',
  LOGGED_OUT: 'page.system.login-session-list.status.logged-out',
  EXPIRED: 'page.system.login-session-list.status.expired',
  REVOKED: 'page.system.login-session-list.status.revoked',
} satisfies Record<LoginSessionViewDto.StatusEnum, string>;

const END_REASON_LABEL_KEYS = {
  LOGOUT: 'page.system.login-session-list.end-reason.logout',
  EXPIRED: 'page.system.login-session-list.end-reason.expired',
  REVOKED: 'page.system.login-session-list.end-reason.revoked',
} satisfies Record<LoginSessionViewDto.EndReasonEnum, string>;

@Component({
  selector: 'app-login-session-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertData, ListPaging, TranslocoPipe, LocalSpinner],
  templateUrl: './login-session-list.html',
  styleUrl: './login-session-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginSessionList extends PagedListBase<
  LoginSessionListQuery,
  LoginSessionViewDto,
  PageResultLoginSessionViewDto
> {
  private readonly transloco = inject(TranslocoService);

  readonly activeOnly = input(false);
  readonly userId = input<number | null | undefined>(undefined);
  readonly userEmail = input<string | null | undefined>(undefined);
  readonly institutionId = input<number | null | undefined>(undefined);
  readonly emptyMessage = input(this.transloco.translate('page.system.login-session-list.empty'));
  readonly showHeader = input(true);
  readonly showFilters = input(true);
  readonly showStatusFilters = input(true);

  private readonly authSessionsService = inject(AuthSessionsService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);
  private readonly normalizedUserId = computed(() => this.normalizeId(this.userId()));
  private readonly normalizedInstitutionId = computed(() => this.normalizeId(this.institutionId()));

  readonly actorTypes = [
    {
      value: LoginSessionListQuery.ActorTypeEnum.User,
      labelKey: ACTOR_TYPE_LABEL_KEYS[LoginSessionViewDto.ActorTypeEnum.User],
    },
    {
      value: LoginSessionListQuery.ActorTypeEnum.Institution,
      labelKey: ACTOR_TYPE_LABEL_KEYS[LoginSessionViewDto.ActorTypeEnum.Institution],
    },
  ] satisfies ReadonlyArray<{ value: LoginSessionListQuery.ActorTypeEnum; labelKey: string }>;

  readonly statuses = [
    {
      value: LoginSessionListQuery.StatusEnum.Active,
      labelKey: STATUS_LABEL_KEYS[LoginSessionViewDto.StatusEnum.Active],
    },
    {
      value: LoginSessionListQuery.StatusEnum.LoggedOut,
      labelKey: STATUS_LABEL_KEYS[LoginSessionViewDto.StatusEnum.LoggedOut],
    },
    {
      value: LoginSessionListQuery.StatusEnum.Expired,
      labelKey: STATUS_LABEL_KEYS[LoginSessionViewDto.StatusEnum.Expired],
    },
    {
      value: LoginSessionListQuery.StatusEnum.Revoked,
      labelKey: STATUS_LABEL_KEYS[LoginSessionViewDto.StatusEnum.Revoked],
    },
  ] satisfies ReadonlyArray<{ value: LoginSessionListQuery.StatusEnum; labelKey: string }>;

  readonly endReasons = [
    {
      value: LoginSessionListQuery.EndReasonEnum.Logout,
      labelKey: END_REASON_LABEL_KEYS[LoginSessionViewDto.EndReasonEnum.Logout],
    },
    {
      value: LoginSessionListQuery.EndReasonEnum.Expired,
      labelKey: END_REASON_LABEL_KEYS[LoginSessionViewDto.EndReasonEnum.Expired],
    },
    {
      value: LoginSessionListQuery.EndReasonEnum.Revoked,
      labelKey: END_REASON_LABEL_KEYS[LoginSessionViewDto.EndReasonEnum.Revoked],
    },
  ] satisfies ReadonlyArray<{ value: LoginSessionListQuery.EndReasonEnum; labelKey: string }>;

  readonly filterForm = this.fb.nonNullable.group({
    name: [''],
    email: [''],
    ipAddress: [''],
    actorType: ['' as LoginSessionFilters['actorType']],
    status: ['' as LoginSessionFilters['status']],
    endReason: ['' as LoginSessionFilters['endReason']],
  });

  private readonly filters = signal<LoginSessionFilters>(this.filterForm.getRawValue());

  readonly query = computed<LoginSessionListQuery>(() => {
    const filters = this.filters();
    const userId = this.normalizedUserId();
    const fixedEmail = this.userEmail()?.trim();
    const institutionId = this.normalizedInstitutionId();

    return {
      ...this.buildPagingQuery(),
      name: institutionId === undefined ? filters.name.trim() || undefined : undefined,
      email: userId === undefined ? fixedEmail || filters.email.trim() || undefined : undefined,
      ipAddress: filters.ipAddress.trim() || undefined,
      actorType: filters.actorType || undefined,
      status: this.activeOnly() ? undefined : filters.status || undefined,
      endReason: this.activeOnly() ? undefined : filters.endReason || undefined,
      activeOnly: this.activeOnly() || undefined,
      userId,
      institutionId,
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_LOGIN_SESSION_PAGE,
      injector: this.injector,
      loader: (query) => this.loadSessions(query),
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
      actorType: '',
      status: '',
      endReason: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  actorTypeLabelKey(value: LoginSessionViewDto.ActorTypeEnum | undefined): string {
    return value ? (ACTOR_TYPE_LABEL_KEYS[value] ?? '-') : '-';
  }

  institutionRoleLabelKey(value: LoginSessionViewDto.InstitutionRoleEnum | undefined): string {
    return value ? (INSTITUTION_ROLE_LABEL_KEYS[value] ?? '-') : '-';
  }

  statusLabelKey(value: LoginSessionViewDto.StatusEnum | undefined): string {
    return value ? (STATUS_LABEL_KEYS[value] ?? '-') : '-';
  }

  endReasonLabelKey(value: LoginSessionViewDto.EndReasonEnum | undefined): string {
    return value ? (END_REASON_LABEL_KEYS[value] ?? '-') : '-';
  }

  trackBySession(_: number, session: LoginSessionViewDto): number | string {
    return session.id ?? `${session.userEmail ?? ''}-${session.startedAt ?? ''}`;
  }

  private loadSessions(query: LoginSessionListQuery) {
    const userId = this.normalizedUserId();
    const institutionId = this.normalizedInstitutionId();

    console.log('userId', userId);
    console.log('institutionId', institutionId);
    console.log(userId !== undefined);

    if (userId !== undefined) {
      console.log(userId !== undefined);
      console.log('userId', userId);
      return this.authSessionsService.findLoginSessionsForUser(userId, query);
    }

    if (institutionId !== undefined) {
      return this.authSessionsService.findLoginSessionsForInstitution(institutionId, query);
    }

    if (this.activeOnly()) {
      return this.authSessionsService.findActiveSessions(query);
    }

    return this.authSessionsService.findLoginSessions(query);
  }

  private normalizeId(value: number | null | undefined): number | undefined {
    return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : undefined;
  }
}
