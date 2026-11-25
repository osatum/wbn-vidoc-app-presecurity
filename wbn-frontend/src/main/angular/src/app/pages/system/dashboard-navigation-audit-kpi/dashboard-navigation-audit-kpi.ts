import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CountUpDirective } from 'ngx-countup';
import { catchError, map, of, startWith } from 'rxjs';
import { DashboardService } from '../../../core/backend';
import type { DashboardNavigationAuditKpiDto } from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { TranslocoPipe } from '@jsverse/transloco';

type DashboardNavigationAuditKpiState = {
  status: 'loading' | 'success' | 'error';
  data: DashboardNavigationAuditKpiDto | null;
  error: unknown | null;
};

@Component({
  selector: 'app-dashboard-navigation-audit-kpi',
  standalone: true,
  imports: [CommonModule, AlertData, CountUpDirective, RouterLink, LocalSpinner, TranslocoPipe],
  templateUrl: './dashboard-navigation-audit-kpi.html',
  styleUrl: './dashboard-navigation-audit-kpi.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardNavigationAuditKpi {
  private readonly dashboardService = inject(DashboardService);

  readonly state = toSignal(
    this.dashboardService.getNavigationAuditKpi('body', false, {
      httpHeaderAccept: 'application/json',
      transferCache: false,
    }).pipe(
      map(
        (data): DashboardNavigationAuditKpiState => ({
          status: 'success',
          data,
          error: null,
        }),
      ),
      startWith({
        status: 'loading',
        data: null,
        error: null,
      } satisfies DashboardNavigationAuditKpiState),
      catchError((error) =>
        of({
          status: 'error',
          data: null,
          error,
        } satisfies DashboardNavigationAuditKpiState),
      ),
    ),
    {
      initialValue: {
        status: 'loading',
        data: null,
        error: null,
      } satisfies DashboardNavigationAuditKpiState,
    },
  );

  readonly kpi = computed(() => this.state().data);
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');

  readonly metrics = computed(() => {
    const data = this.kpi();

    return [
      {
        id: 1,
        labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.users',
        counter: this.formatCount(data?.users),
        captionKey: 'page.system.dashboard-navigation-audit-kpi.captions.events',
        routerLink: '/system/admin/navigation-audit-events',
        icon: 'ri-user-search-line',
        iconClass: 'bg-success-subtle text-success',
      },
      {
        id: 2,
        labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.institutions',
        counter: this.formatCount(data?.institutions),
        captionKey: 'page.system.dashboard-navigation-audit-kpi.captions.events',
        routerLink: '/system/admin/navigation-audit-events',
        icon: 'ri-building-2-line',
        iconClass: 'bg-info-subtle text-info',
      },
      {
        id: 3,
        labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.sessions',
        counter: this.formatCount(data?.sessions),
        captionKey: 'page.system.dashboard-navigation-audit-kpi.captions.events',
        routerLink: '/system/admin/navigation-audit-events',
        icon: 'ri-login-circle-line',
        iconClass: 'bg-warning-subtle text-warning',
      },
      {
        id: 4,
        labelKey: 'page.system.dashboard-navigation-audit-kpi.metrics.events',
        counter: this.formatCount(data?.events),
        captionKey: 'page.system.dashboard-navigation-audit-kpi.captions.events',
        routerLink: '/system/admin/navigation-audit-events',
        icon: 'ri-route-line',
        iconClass: 'bg-primary-subtle text-primary',
      },
    ];
  });

  readonly countUpOptions = {
    startVal: 0,
    useEasing: true,
    duration: 2,
    decimalPlaces: 0,
  };

  formatCount(value: number | undefined): number {
    return value ?? 0;
  }
}
