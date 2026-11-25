import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, map, of, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { DashboardCardinalityDto, DashboardService } from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { CountUpDirective } from 'ngx-countup';
import { RouterLink } from '@angular/router';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { TranslocoPipe } from '@jsverse/transloco';

type DashboardCardinalityState = {
  status: 'loading' | 'success' | 'error';
  data: DashboardCardinalityDto | null;
  error: unknown | null;
};

export interface TitleBox1Model {
  id: number;
  labelKey: string;
  labelClass: string;
  percentage?: string | null;
  percentageClass?: string | null;
  percentageIcon?: string | null;
  counter: number;
  captionKey: string;
  routerLink: string;
  icon: string;
  iconClass: string;
  bgColor?: string;
  counterClass?: string;
  captionClass?: string;
  decimals: number;
  prefix?: string;
  suffix?: string;
}

@Component({
  selector: 'app-dashboard-cardinality',
  imports: [CommonModule, AlertData, CountUpDirective, RouterLink, LocalSpinner, TranslocoPipe],
  templateUrl: './dashboard-cardinality.html',
  styleUrl: './dashboard-cardinality.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardCardinality {
  private readonly dashboardService = inject(DashboardService);

  readonly state = toSignal(
    this.dashboardService.getCardinality().pipe(
      map(
        (data): DashboardCardinalityState => ({
          status: 'success',
          data,
          error: null,
        }),
      ),
      startWith({
        status: 'loading',
        data: null,
        error: null,
      } satisfies DashboardCardinalityState),
      catchError((error) =>
        of({
          status: 'error',
          data: null,
          error,
        } satisfies DashboardCardinalityState),
      ),
    ),
    {
      initialValue: {
        status: 'loading',
        data: null,
        error: null,
      } satisfies DashboardCardinalityState,
    },
  );

  readonly cardinality = computed(() => this.state().data);
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');

  readonly metrics = computed<TitleBox1Model[]>(() => {
    const data = this.cardinality();

    return [
      {
        id: 1,
        labelKey: 'page.system.dashboard-cardinality.metrics.institutions-total',
        labelClass: 'muted',
        counter: this.formatCount(data?.institutionsTotal),
        captionKey: 'page.system.dashboard-cardinality.captions.details',
        routerLink: '/system/admin/institutions',
        icon: 'ri-building-4-line',
        iconClass: 'bg-success-subtle text-success',
        decimals: 2,
      },
      {
        id: 2,
        labelKey: 'page.system.dashboard-cardinality.metrics.research-units',
        labelClass: 'white-50',
        percentageClass: 'warning',
        counter: this.formatCount(data?.researchUnits),
        captionKey: 'page.system.dashboard-cardinality.captions.details',
        routerLink: '/system/admin/institutions',
        icon: 'ri-flask-line',
        iconClass: 'bg-white bg-opacity-25',
        bgColor: 'bg-info',
        counterClass: 'text-white',
        captionClass: 'text-white-50',
        decimals: 0,
      },
      {
        id: 3,
        labelKey: 'page.system.dashboard-cardinality.metrics.users-total',
        labelClass: 'muted',
        percentageClass: 'success',
        counter: this.formatCount(data?.usersTotal),
        captionKey: 'page.system.dashboard-cardinality.captions.details',
        routerLink: '/system/admin/users',
        icon: 'bx bx-user-circle',
        iconClass: 'bg-warning-subtle text-warning',
        decimals: 0,
      },
      {
        id: 4,
        labelKey: 'page.system.dashboard-cardinality.metrics.memberships',
        labelClass: 'muted',
        percentage: null,
        percentageClass: 'muted',
        percentageIcon: null,
        counter: this.formatCount(data?.institutionUserMemberships),
        captionKey: '',
        routerLink: '',
        icon: 'ri-links-line',
        iconClass: 'bg-primary-subtle text-primary',
        decimals: 0,
      },
      {
        id: 5,
        labelKey: 'page.system.dashboard-cardinality.metrics.active-sessions',
        labelClass: 'muted',
        percentage: '',
        percentageClass: 'muted',
        percentageIcon: '',
        counter: this.formatCount(data?.activeSessions),
        captionKey: 'page.system.dashboard-cardinality.captions.last-15-minutes',
        routerLink: '/system/admin/navigation-audit-events',
        icon: 'ri-pulse-line',
        iconClass: 'bg-primary-subtle text-primary',
        decimals: 0,
      },
      {
        id: 6,
        labelKey: 'page.system.dashboard-cardinality.metrics.sessions-today',
        labelClass: 'muted',
        percentage: '',
        percentageClass: 'muted',
        percentageIcon: '',
        counter: this.formatCount(data?.sessionsToday),
        captionKey: 'page.system.dashboard-cardinality.captions.events',
        routerLink: '/system/admin/navigation-audit-events',
        icon: 'ri-calendar-check-line',
        iconClass: 'bg-primary-subtle text-primary',
        decimals: 0,
      },
      {
        id: 7,
        labelKey: 'page.system.dashboard-cardinality.metrics.sessions-last-7-days',
        labelClass: 'muted',
        percentage: '',
        percentageClass: 'muted',
        percentageIcon: '',
        counter: this.formatCount(data?.sessionsLast7Days),
        captionKey: 'page.system.dashboard-cardinality.captions.events',
        routerLink: '/system/admin/navigation-audit-events',
        icon: 'ri-calendar-2-line',
        iconClass: 'bg-primary-subtle text-primary',
        decimals: 0,
      },
      {
        id: 8,
        labelKey: 'page.system.dashboard-cardinality.metrics.users-multiple-institutions',
        labelClass: 'muted',
        percentage: '',
        percentageClass: 'muted',
        percentageIcon: '',
        counter: this.formatCount(data?.usersAssignedToMultipleInstitutions),
        captionKey: '',
        routerLink: '',
        icon: 'ri-group-line',
        iconClass: 'bg-primary-subtle text-primary',
        decimals: 0,
      },
    ];
  });

  option = {
    startVal: 0,
    useEasing: true,
    duration: 2,
    decimalPlaces: 0,
  };

  formatCount(value: number | undefined): number {
    return value ?? 0;
  }
}
