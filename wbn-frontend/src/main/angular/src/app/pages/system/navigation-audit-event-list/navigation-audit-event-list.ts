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
  NavigationAuditEventListDto,
  NavigationAuditEventListQuery,
  NavigationAuditEventPageResult,
  NavigationAuditEventType,
  NavigationAuditEventsService,
} from '../../../core/services/navigation-audit-events.service';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { DECISION_AUDIT_LABELS, DecisionAuditAction } from '../../../core/services/journal-decision-audit';

type NavigationAuditEventFilters = {
  occurredFrom: string;
  occurredTo: string;
  institution: string;
  user: string;
  pageKey: string;
  eventType: '' | NavigationAuditEventType;
  journalId: string;
};

const EMPTY_NAVIGATION_AUDIT_EVENT_PAGE: NavigationAuditEventPageResult = {
  data: [],
  totalElements: 0,
  totalPages: 0,
};

const EVENT_TYPE_LABEL_KEYS = {
  SERVICE_ENTRY: 'page.system.navigation-audit-event-list.event-type.service-entry',
  PUBLIC_PAGE_VIEW: 'page.system.navigation-audit-event-list.event-type.public-page-view',
  PAGE_VIEW: 'page.system.navigation-audit-event-list.event-type.page-view',
  PAGE_READY: 'page.system.navigation-audit-event-list.event-type.page-ready',
  API_RESULT: 'page.system.navigation-audit-event-list.event-type.api-result',
  FILE_DOWNLOAD: 'page.system.navigation-audit-event-list.event-type.file-download',
  QUESTIONNAIRE_SHOWN: 'page.system.navigation-audit-event-list.event-type.questionnaire-shown',
  TAB_CLICK: 'page.system.navigation-audit-event-list.event-type.tab-click',
  PAGE_HEARTBEAT: 'page.system.navigation-audit-event-list.event-type.page-heartbeat',
  PAGE_LEAVE: 'page.system.navigation-audit-event-list.event-type.page-leave',
  PAGE_VISIBLE: 'page.system.navigation-audit-event-list.event-type.page-visible',
  PAGE_HIDDEN: 'page.system.navigation-audit-event-list.event-type.page-hidden',
  JOURNAL_SEARCH: 'page.system.navigation-audit-event-list.event-type.journal-search',
  JOURNAL_FILTERS_CLEAR: 'page.system.navigation-audit-event-list.event-type.journal-filters-clear',
  JOURNAL_SEARCH_RESULT: 'page.system.navigation-audit-event-list.event-type.journal-search-result',
  JOURNAL_SEARCH_EMPTY: 'page.system.navigation-audit-event-list.event-type.journal-search-empty',
  JOURNAL_ISSUES_CLICK: 'page.system.navigation-audit-event-list.event-type.journal-issues-click',
  JOURNAL_CARD_OPEN: 'page.system.navigation-audit-event-list.event-type.journal-card-open',
  JOURNAL_DECISION_ACTION: 'decisionCard.audit.eventType',
} satisfies Record<NavigationAuditEventType, string>;

const PAGE_LABEL_KEYS: Record<string, string> = {
  'public.home': 'page.system.navigation-audit-event-list.page.public-home',
  'public.registration': 'page.system.navigation-audit-event-list.page.public-registration',
  'public.registration.institution': 'page.system.navigation-audit-event-list.page.public-registration-institution',
  'public.denied': 'page.system.navigation-audit-event-list.page.public-denied',
  'auth.login': 'page.system.navigation-audit-event-list.page.auth-login',
  'auth.password-reset': 'page.system.navigation-audit-event-list.page.auth-password-reset',
  'auth.select-institutions': 'page.system.navigation-audit-event-list.page.auth-select-institutions',
  'auth.select-membership': 'page.system.navigation-audit-event-list.page.auth-select-membership',
  'system.admin.dashboard': 'page.system.navigation-audit-event-list.page.system-admin-dashboard',
  'institution.admin.dashboard': 'page.system.navigation-audit-event-list.page.institution-admin-dashboard',
  'institution.user.dashboard': 'page.system.navigation-audit-event-list.page.institution-user-dashboard',
  'institution.journals.list': 'page.system.navigation-audit-event-list.page.journals-list',
  'institution.journals.details': 'page.system.navigation-audit-event-list.page.journal-details',
  'institution.journals.decision': 'decisionCard.title',
};

const DOWNLOAD_ACTION_LABEL_KEYS: Record<string, string> = {
  'questionnaire.report.download': 'page.system.navigation-audit-event-list.action.questionnaire-report-download',
  'questionnaire.report.bulk.download': 'page.system.navigation-audit-event-list.action.questionnaire-report-bulk-download',
};

@Component({
  selector: 'app-navigation-audit-event-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AlertData, ListPaging, TranslocoPipe, LocalSpinner],
  templateUrl: './navigation-audit-event-list.html',
  styles: ['.journal-audit-properties { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 24rem; overflow: auto; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavigationAuditEventList extends PagedListBase<
  NavigationAuditEventListQuery,
  NavigationAuditEventListDto,
  NavigationAuditEventPageResult
> {
  private readonly transloco = inject(TranslocoService);
  private readonly navigationAuditEventsService = inject(NavigationAuditEventsService);
  private readonly injector = inject(Injector);
  private readonly fb = inject(FormBuilder);

  readonly emptyMessage = input(
    this.transloco.translate('page.system.navigation-audit-event-list.empty'),
  );
  readonly enabled = input(true);

  readonly pages = [
    {
      value: 'public.home',
      labelKey: PAGE_LABEL_KEYS['public.home'],
    },
    {
      value: 'public.registration',
      labelKey: PAGE_LABEL_KEYS['public.registration'],
    },
    {
      value: 'public.registration.institution',
      labelKey: PAGE_LABEL_KEYS['public.registration.institution'],
    },
    {
      value: 'public.denied',
      labelKey: PAGE_LABEL_KEYS['public.denied'],
    },
    {
      value: 'auth.login',
      labelKey: PAGE_LABEL_KEYS['auth.login'],
    },
    {
      value: 'auth.password-reset',
      labelKey: PAGE_LABEL_KEYS['auth.password-reset'],
    },
    {
      value: 'auth.select-institutions',
      labelKey: PAGE_LABEL_KEYS['auth.select-institutions'],
    },
    {
      value: 'auth.select-membership',
      labelKey: PAGE_LABEL_KEYS['auth.select-membership'],
    },
    {
      value: 'system.admin.dashboard',
      labelKey: PAGE_LABEL_KEYS['system.admin.dashboard'],
    },
    {
      value: 'institution.admin.dashboard',
      labelKey: PAGE_LABEL_KEYS['institution.admin.dashboard'],
    },
    {
      value: 'institution.user.dashboard',
      labelKey: PAGE_LABEL_KEYS['institution.user.dashboard'],
    },
    {
      value: 'institution.journals.list',
      labelKey: PAGE_LABEL_KEYS['institution.journals.list'],
    },
    {
      value: 'institution.journals.details',
      labelKey: PAGE_LABEL_KEYS['institution.journals.details'],
    },
    {
      value: 'institution.journals.decision',
      labelKey: PAGE_LABEL_KEYS['institution.journals.decision'],
    },
  ] satisfies ReadonlyArray<{ value: string; labelKey: string }>;

  readonly eventTypes = [
    ...(['JOURNAL_SEARCH', 'JOURNAL_FILTERS_CLEAR', 'JOURNAL_SEARCH_RESULT', 'JOURNAL_SEARCH_EMPTY', 'JOURNAL_CARD_OPEN', 'JOURNAL_ISSUES_CLICK', 'JOURNAL_DECISION_ACTION'] as const)
      .map((value) => ({ value, labelKey: EVENT_TYPE_LABEL_KEYS[value] })),
    {
      value: 'SERVICE_ENTRY',
      labelKey: EVENT_TYPE_LABEL_KEYS.SERVICE_ENTRY,
    },
    {
      value: 'PUBLIC_PAGE_VIEW',
      labelKey: EVENT_TYPE_LABEL_KEYS.PUBLIC_PAGE_VIEW,
    },
    {
      value: 'PAGE_VIEW',
      labelKey: EVENT_TYPE_LABEL_KEYS.PAGE_VIEW,
    },
    {
      value: 'PAGE_READY',
      labelKey: EVENT_TYPE_LABEL_KEYS.PAGE_READY,
    },
    {
      value: 'API_RESULT',
      labelKey: EVENT_TYPE_LABEL_KEYS.API_RESULT,
    },
    {
      value: 'FILE_DOWNLOAD',
      labelKey: EVENT_TYPE_LABEL_KEYS.FILE_DOWNLOAD,
    },
    {
      value: 'QUESTIONNAIRE_SHOWN',
      labelKey: EVENT_TYPE_LABEL_KEYS.QUESTIONNAIRE_SHOWN,
    },
    {
      value: 'TAB_CLICK',
      labelKey: EVENT_TYPE_LABEL_KEYS.TAB_CLICK,
    },
    {
      value: 'PAGE_HEARTBEAT',
      labelKey: EVENT_TYPE_LABEL_KEYS.PAGE_HEARTBEAT,
    },
    {
      value: 'PAGE_LEAVE',
      labelKey: EVENT_TYPE_LABEL_KEYS.PAGE_LEAVE,
    },
    {
      value: 'PAGE_VISIBLE',
      labelKey: EVENT_TYPE_LABEL_KEYS.PAGE_VISIBLE,
    },
    {
      value: 'PAGE_HIDDEN',
      labelKey: EVENT_TYPE_LABEL_KEYS.PAGE_HIDDEN,
    },
  ] satisfies ReadonlyArray<{ value: NavigationAuditEventType; labelKey: string }>;

  readonly filterForm = this.fb.nonNullable.group({
    occurredFrom: [''],
    occurredTo: [''],
    institution: [''],
    user: [''],
    pageKey: [''],
    eventType: ['' as NavigationAuditEventFilters['eventType']],
    journalId: [''],
  });

  private readonly filters = signal<NavigationAuditEventFilters>(this.filterForm.getRawValue());

  readonly query = computed<NavigationAuditEventListQuery>(() => {
    const filters = this.filters();

    return {
      ...this.buildPagingQuery(),
      occurredFrom: this.toIsoDateBoundary(filters.occurredFrom, 'start'),
      occurredTo: this.toIsoDateBoundary(filters.occurredTo, 'end'),
      institution: filters.institution.trim() || undefined,
      user: filters.user.trim() || undefined,
      pageKey: filters.pageKey || undefined,
      eventType: filters.eventType || undefined,
      journalId: filters.journalId.trim() || undefined,
    };
  });

  constructor() {
    super();

    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_NAVIGATION_AUDIT_EVENT_PAGE,
      injector: this.injector,
      loader: (query) => this.navigationAuditEventsService.findEvents(query),
      enabled: this.enabled,
      retainOnDisable: true,
    });
  }

  applyFilters(): void {
    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  resetFilters(): void {
    this.filterForm.reset({
      occurredFrom: '',
      occurredTo: '',
      institution: '',
      user: '',
      pageKey: '',
      eventType: '',
      journalId: '',
    });

    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
  }

  eventTypeLabelKey(value: NavigationAuditEventType | undefined): string {
    return value ? (EVENT_TYPE_LABEL_KEYS[value] ?? value) : '-';
  }

  retry(): void {
    if (this.enabled() && !this.isLoading()) {
      this.reloadPage();
    }
  }

  downloadActionLabelKey(value: string | undefined): string {
    return value ? (DOWNLOAD_ACTION_LABEL_KEYS[value] ?? value) : '-';
  }

  decisionActionLabelKey(value: string | undefined): string {
    return value && Object.hasOwn(DECISION_AUDIT_LABELS, value)
      ? DECISION_AUDIT_LABELS[value as DecisionAuditAction]
      : '-';
  }

  pageLabelKey(value: string | undefined): string {
    return value ? (PAGE_LABEL_KEYS[value] ?? value) : '-';
  }

  resultBadgeClass(value: boolean | undefined): string {
    if (value === true) {
      return 'bg-success-subtle text-success';
    }
    if (value === false) {
      return 'bg-danger-subtle text-danger';
    }
    return 'bg-light text-body';
  }

  resultLabelKey(value: boolean | undefined): string {
    if (value === true) {
      return 'page.system.navigation-audit-event-list.success';
    }
    if (value === false) {
      return 'page.system.navigation-audit-event-list.failure';
    }
    return 'page.system.navigation-audit-event-list.unknown-result';
  }

  userName(event: NavigationAuditEventListDto): string {
    return [event.userFirstName, event.userLastName].filter(Boolean).join(' ') || '-';
  }

  trackByNavigationAuditEvent(_: number, event: NavigationAuditEventListDto): number | string {
    return (
      event.id ??
      event.eventId ??
      `${event.pageKey ?? ''}-${event.occurredAt ?? ''}-${event.eventType ?? ''}`
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
