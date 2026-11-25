import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import type {
  NavigationAuditVisitListDto,
  NavigationAuditVisitListQuery,
  NavigationAuditVisitPageResult,
} from '../../../core/backend';
import { NavigationAuditVisitListService } from '../../../core/services/navigation-audit-visits.service';
import { PagedListBase } from '../../../shared/paged-list-base/paged-list-base';
import { ListPaging } from '../../../shared/list-paging/list-paging';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { reportDateRange } from '../navigation-audit-reports/navigation-audit-reports';

const REASONS = ['INACTIVITY', 'LOGOUT', 'EXPIRED', 'REVOKED', 'ANONYMIZED'] as const;
const EMPTY_PAGE: NavigationAuditVisitPageResult = {
  data: [],
  totalElements: 0,
  totalPages: 0,
  size: 10,
};

@Component({
  selector: 'app-navigation-audit-visit-list',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe, ListPaging, LocalSpinner],
  templateUrl: './navigation-audit-visit-list.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavigationAuditVisitList extends PagedListBase<
  NavigationAuditVisitListQuery,
  NavigationAuditVisitListDto,
  NavigationAuditVisitPageResult
> {
  private readonly service = inject(NavigationAuditVisitListService);
  private readonly injector = inject(Injector);
  private readonly builder = inject(FormBuilder);
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly enabled = input(true);
  readonly reasons = REASONS;
  readonly filterForm = this.builder.nonNullable.group(
    {
      dateFrom: ['', Validators.pattern(/^\d{4}-\d{2}-\d{2}$/)],
      dateTo: ['', Validators.pattern(/^\d{4}-\d{2}-\d{2}$/)],
      institution: ['', Validators.maxLength(200)],
      user: ['', Validators.maxLength(200)],
      status: this.builder.nonNullable.control<'' | NavigationAuditVisitListQuery.StatusEnum>(''),
      endReason: this.builder.nonNullable.control<'' | NavigationAuditVisitListQuery.EndReasonEnum>(
        '',
      ),
    },
    { validators: reportDateRange },
  );
  private readonly filters = signal(this.filterForm.getRawValue());
  readonly query = computed<NavigationAuditVisitListQuery>(() => {
    const filters = this.filters();
    return {
      ...this.buildPagingQuery(),
      dateFrom: filters.dateFrom || undefined,
      dateTo: filters.dateTo || undefined,
      institution: filters.institution.trim() || undefined,
      user: filters.user.trim() || undefined,
      status: filters.status || undefined,
      endReason: filters.endReason || undefined,
    };
  });
  readonly timeZone = computed(() => this.result().data.timeZone ?? 'Europe/Warsaw');
  private readonly dateFormatter = computed(
    () =>
      new Intl.DateTimeFormat(this.language() === 'en' ? 'en-GB' : 'pl-PL', {
        timeZone: this.timeZone(),
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23',
      }),
  );

  constructor() {
    super();
    this.initializePagedList({
      query: this.query,
      emptyPage: EMPTY_PAGE,
      injector: this.injector,
      loader: (query) => this.service.findVisits(query),
      enabled: this.enabled,
      retainOnDisable: true,
    });
  }

  applyFilters(): void {
    this.filterForm.markAllAsTouched();
    if (this.filterForm.invalid) {
      return;
    }
    this.resetPaging();
    this.filters.set(this.filterForm.getRawValue());
    this.reloadPage();
  }

  resetFilters(): void {
    this.filterForm.reset();
    this.applyFilters();
  }

  refresh(): void {
    if (this.enabled() && !this.isLoading()) {
      this.reloadPage();
    }
  }

  userLabel(visit: NavigationAuditVisitListDto): string {
    if (!visit.userId) {
      return this.transloco.translate(
        'page.system.navigation-audit-visit-list.' +
          (visit.actorType === 'INSTITUTION' ? 'institution-access' : 'anonymized-user'),
      );
    }
    return (
      [visit.userFirstName, visit.userLastName].filter(Boolean).join(' ') || visit.userEmail || '—'
    );
  }

  formatTimestamp(value: string | undefined): string {
    return value ? this.dateFormatter().format(new Date(value)) : '—';
  }

  formatDuration(seconds: number | undefined): string {
    const duration = Math.max(0, Math.floor(seconds ?? 0));
    return `${Math.floor(duration / 3600)}:${String(Math.floor((duration % 3600) / 60)).padStart(2, '0')}:${String(duration % 60).padStart(2, '0')}`;
  }

  reasonLabel(reason: string | undefined): string {
    return reason && REASONS.some((known) => known === reason)
      ? this.transloco.translate('page.system.navigation-audit-visit-list.reason.' + reason)
      : reason || '—';
  }
}
