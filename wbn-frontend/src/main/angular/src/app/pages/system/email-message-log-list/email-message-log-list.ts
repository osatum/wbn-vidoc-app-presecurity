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
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  EmailMessageLogListDto,
  EmailMessageLogListQuery,
  EmailMessageResendResultDto,
  EmailMessagesService,
  PageResultEmailMessageLogListDto,
} from '../../../core/backend';
import { ToastService } from '../../../core/services/toast-service';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { EMAIL_TEMPLATE_TYPE_LABEL_KEYS } from '../email-template-ui-definitions';

type EmailMessageFilters = {
  name: string;
  email: string;
  recipient: string;
  emailType: '' | EmailMessageLogListQuery.EmailTypeEnum;
  status: '' | EmailMessageLogListQuery.StatusEnum;
  createdFrom: string;
  createdTo: string;
};

const EMPTY_EMAIL_MESSAGE_PAGE: PageResultEmailMessageLogListDto = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

const STATUS_LABEL_KEYS = {
  PENDING: 'page.system.email-message-log-list.status.pending',
  PROCESSING: 'page.system.email-message-log-list.status.processing',
  SENT: 'page.system.email-message-log-list.status.sent',
  FAILED: 'page.system.email-message-log-list.status.failed',
  RETRYING: 'page.system.email-message-log-list.status.retrying',
  CANCELLED: 'page.system.email-message-log-list.status.cancelled',
} satisfies Record<EmailMessageLogListDto.StatusEnum, string>;

const BUSY_STATUSES = new Set<EmailMessageLogListDto.StatusEnum>([
  EmailMessageLogListDto.StatusEnum.Pending,
  EmailMessageLogListDto.StatusEnum.Processing,
  EmailMessageLogListDto.StatusEnum.Retrying,
]);

@Component({
  selector: 'app-email-message-log-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertData, ListPaging, TranslocoPipe, LocalSpinner],
  templateUrl: './email-message-log-list.html',
  styleUrl: './email-message-log-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailMessageLogList extends PagedListBase<
  EmailMessageLogListQuery,
  EmailMessageLogListDto,
  PageResultEmailMessageLogListDto
> {
  private readonly transloco = inject(TranslocoService);
  private readonly emailMessagesService = inject(EmailMessagesService);
  private readonly toastService = inject(ToastService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);

  readonly emptyMessage = input(
    this.transloco.translate('page.system.email-message-log-list.empty'),
  );
  readonly resendPendingIds = signal<ReadonlySet<number>>(new Set<number>());

  readonly emailTypes = [
    {
      value: EmailMessageLogListQuery.EmailTypeEnum.AccountActivationLink,
      labelKey: EMAIL_TEMPLATE_TYPE_LABEL_KEYS[EmailMessageLogListDto.EmailTypeEnum.AccountActivationLink],
    },
    {
      value: EmailMessageLogListQuery.EmailTypeEnum.ActivationLinkRegenerated,
      labelKey:
        EMAIL_TEMPLATE_TYPE_LABEL_KEYS[EmailMessageLogListDto.EmailTypeEnum.ActivationLinkRegenerated],
    },
    {
      value: EmailMessageLogListQuery.EmailTypeEnum.PasswordResetLink,
      labelKey: EMAIL_TEMPLATE_TYPE_LABEL_KEYS[EmailMessageLogListDto.EmailTypeEnum.PasswordResetLink],
    },
    {
      value: EmailMessageLogListQuery.EmailTypeEnum.UserDataDeletionConfirmation,
      labelKey:
        EMAIL_TEMPLATE_TYPE_LABEL_KEYS[EmailMessageLogListDto.EmailTypeEnum.UserDataDeletionConfirmation],
    },
    {
      value: EmailMessageLogListQuery.EmailTypeEnum.AccountActivated,
      labelKey: EMAIL_TEMPLATE_TYPE_LABEL_KEYS[EmailMessageLogListDto.EmailTypeEnum.AccountActivated],
    },
    {
      value: EmailMessageLogListQuery.EmailTypeEnum.AccountWaitingForConfirmation,
      labelKey:
        EMAIL_TEMPLATE_TYPE_LABEL_KEYS[EmailMessageLogListDto.EmailTypeEnum.AccountWaitingForConfirmation],
    },
  ] satisfies ReadonlyArray<{ value: EmailMessageLogListQuery.EmailTypeEnum; labelKey: string }>;

  readonly statuses = [
    {
      value: EmailMessageLogListQuery.StatusEnum.Pending,
      labelKey: STATUS_LABEL_KEYS[EmailMessageLogListDto.StatusEnum.Pending],
    },
    {
      value: EmailMessageLogListQuery.StatusEnum.Processing,
      labelKey: STATUS_LABEL_KEYS[EmailMessageLogListDto.StatusEnum.Processing],
    },
    {
      value: EmailMessageLogListQuery.StatusEnum.Sent,
      labelKey: STATUS_LABEL_KEYS[EmailMessageLogListDto.StatusEnum.Sent],
    },
    {
      value: EmailMessageLogListQuery.StatusEnum.Failed,
      labelKey: STATUS_LABEL_KEYS[EmailMessageLogListDto.StatusEnum.Failed],
    },
    {
      value: EmailMessageLogListQuery.StatusEnum.Retrying,
      labelKey: STATUS_LABEL_KEYS[EmailMessageLogListDto.StatusEnum.Retrying],
    },
    {
      value: EmailMessageLogListQuery.StatusEnum.Cancelled,
      labelKey: STATUS_LABEL_KEYS[EmailMessageLogListDto.StatusEnum.Cancelled],
    },
  ] satisfies ReadonlyArray<{ value: EmailMessageLogListQuery.StatusEnum; labelKey: string }>;

  readonly filterForm = this.fb.nonNullable.group({
    name: [''],
    email: [''],
    recipient: [''],
    emailType: ['' as EmailMessageFilters['emailType']],
    status: ['' as EmailMessageFilters['status']],
    createdFrom: [''],
    createdTo: [''],
  });

  private readonly filters = signal<EmailMessageFilters>(this.filterForm.getRawValue());

  readonly query = computed<EmailMessageLogListQuery>(() => {
    const filters = this.filters();

    return {
      ...this.buildPagingQuery(),
      name: filters.name.trim() || undefined,
      email: filters.email.trim() || undefined,
      recipient: filters.recipient.trim() || undefined,
      emailType: filters.emailType || undefined,
      status: filters.status || undefined,
      createdFrom: this.toIsoDateBoundary(filters.createdFrom, 'start'),
      createdTo: this.toIsoDateBoundary(filters.createdTo, 'end'),
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_EMAIL_MESSAGE_PAGE,
      injector: this.injector,
      loader: (query) => this.emailMessagesService.findEmailMessages(query),
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
      recipient: '',
      emailType: '',
      status: '',
      createdFrom: '',
      createdTo: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  canResend(message: EmailMessageLogListDto): boolean {
    return (
      message.id !== undefined &&
      message.status !== undefined &&
      !BUSY_STATUSES.has(message.status) &&
      !this.isResendPending(message.id)
    );
  }

  isResendPending(id: number | undefined): boolean {
    return id !== undefined && this.resendPendingIds().has(id);
  }

  resendMessage(message: EmailMessageLogListDto): void {
    const id = message.id;
    if (id === undefined || !this.canResend(message)) {
      return;
    }

    this.markResendPending(id, true);
    this.emailMessagesService
      .resendEmailMessage(id)
      .pipe(finalize(() => this.markResendPending(id, false)))
      .subscribe({
        next: (result) => {
          this.toastService.show(this.transloco.translate(this.resendMessageKey(result?.status)), {
            classname: 'bg-success text-white',
            delay: 7000,
          });
          this.reloadPage();
        },
        error: () => {
          this.toastService.show(
            this.transloco.translate('page.system.email-message-log-list.resend-error'),
            {
              classname: 'bg-danger text-white',
              delay: 7000,
            },
          );
        },
      });
  }

  emailTypeLabelKey(value: EmailMessageLogListDto.EmailTypeEnum | undefined): string {
    return value ? (EMAIL_TEMPLATE_TYPE_LABEL_KEYS[value] ?? '-') : '-';
  }

  statusLabelKey(value: EmailMessageLogListDto.StatusEnum | undefined): string {
    return value ? (STATUS_LABEL_KEYS[value] ?? '-') : '-';
  }

  statusBadgeClass(value: EmailMessageLogListDto.StatusEnum | undefined): string {
    switch (value) {
      case EmailMessageLogListDto.StatusEnum.Sent:
        return 'bg-success-subtle text-success';
      case EmailMessageLogListDto.StatusEnum.Failed:
      case EmailMessageLogListDto.StatusEnum.Cancelled:
        return 'bg-danger-subtle text-danger';
      case EmailMessageLogListDto.StatusEnum.Processing:
      case EmailMessageLogListDto.StatusEnum.Retrying:
        return 'bg-warning-subtle text-warning';
      default:
        return 'bg-light text-body';
    }
  }

  userName(message: EmailMessageLogListDto): string {
    return [message.userFirstName, message.userLastName].filter(Boolean).join(' ') || '-';
  }

  trackByMessage(_: number, message: EmailMessageLogListDto): number | string {
    return (
      message.id ??
      `${message.recipient ?? ''}-${message.createdAt ?? ''}-${message.emailType ?? ''}`
    );
  }

  private resendMessageKey(status: EmailMessageResendResultDto.StatusEnum | undefined): string {
    return status === EmailMessageResendResultDto.StatusEnum.EmailResendQueued
      ? 'page.system.email-message-log-list.resend-queued'
      : 'page.system.email-message-log-list.resend-success';
  }

  private markResendPending(id: number, pending: boolean): void {
    this.resendPendingIds.update((ids) => {
      const next = new Set(ids);
      if (pending) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
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
