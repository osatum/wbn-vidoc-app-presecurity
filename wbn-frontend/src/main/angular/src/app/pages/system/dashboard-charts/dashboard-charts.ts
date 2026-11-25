import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { catchError, map, of, startWith } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  ApexAxisChartSeries,
  ApexChart,
  ApexDataLabels,
  ApexGrid,
  ApexNoData,
  ApexPlotOptions,
  ApexTooltip,
  ApexXAxis,
  ApexYAxis,
  NgApexchartsModule,
} from 'ng-apexcharts';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { DashboardChartsDto, DashboardService } from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type DashboardChartsState = {
  status: 'loading' | 'success' | 'error';
  data: DashboardChartsDto | null;
  error: unknown | null;
};

type DashboardChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  colors: string[];
  dataLabels: ApexDataLabels;
  grid: ApexGrid;
  noData: ApexNoData;
  plotOptions: ApexPlotOptions;
  tooltip: ApexTooltip;
  xaxis: ApexXAxis;
  yaxis: ApexYAxis;
};

@Component({
  selector: 'app-dashboard-charts',
  imports: [CommonModule, AlertData, NgApexchartsModule, LocalSpinner, TranslocoPipe],
  templateUrl: './dashboard-charts.html',
  styleUrl: './dashboard-charts.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardCharts {
  private readonly dashboardService = inject(DashboardService);
  private readonly transloco = inject(TranslocoService);

  readonly state = toSignal(
    this.dashboardService.getCharts().pipe(
      map(
        (data): DashboardChartsState => ({
          status: 'success',
          data,
          error: null,
        }),
      ),
      startWith({
        status: 'loading',
        data: null,
        error: null,
      } satisfies DashboardChartsState),
      catchError((error) =>
        of({
          status: 'error',
          data: null,
          error,
        } satisfies DashboardChartsState),
      ),
    ),
    {
      initialValue: {
        status: 'loading',
        data: null,
        error: null,
      } satisfies DashboardChartsState,
    },
  );

  readonly charts = computed(() => this.state().data);
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');

  readonly dailyLoginsChart = computed<DashboardChartOptions>(() => {
    const items = this.charts()?.loginsLast7Days ?? [];

    return {
      series: [
        {
          name: this.transloco.translate('page.system.dashboard-charts.logins-series'),
          data: items.map((item) => item.loginCount ?? 0),
        },
      ],
      chart: {
        type: 'bar',
        height: 320,
        toolbar: {
          show: false,
        },
      },
      colors: ['#405189'],
      dataLabels: {
        enabled: false,
      },
      grid: {
        borderColor: '#e9ebec',
        strokeDashArray: 4,
      },
      noData: {
        text: this.transloco.translate('page.system.dashboard-charts.no-data'),
      },
      plotOptions: {
        bar: {
          borderRadius: 4,
          columnWidth: '42%',
        },
      },
      tooltip: {
        y: {
          formatter: (value) => this.loginCountLabel(value),
        },
      },
      xaxis: {
        categories: items.map((item) => item.label ?? item.date ?? '-'),
        labels: {
          rotate: 0,
        },
      },
      yaxis: {
        min: 0,
        forceNiceScale: true,
        labels: {
          formatter: (value) => `${Math.trunc(value)}`,
        },
      },
    };
  });

  readonly topInstitutionsChart = computed<DashboardChartOptions>(() => {
    const items = this.charts()?.topInstitutions ?? [];

    return {
      series: [
        {
          name: this.transloco.translate('page.system.dashboard-charts.logins-series'),
          data: items.map((item) => item.loginCount ?? 0),
        },
      ],
      chart: {
        type: 'bar',
        height: 360,
        toolbar: {
          show: false,
        },
      },
      colors: ['#0ab39c'],
      dataLabels: {
        enabled: false,
      },
      grid: {
        borderColor: '#e9ebec',
        strokeDashArray: 4,
      },
      noData: {
        text: this.transloco.translate('page.system.dashboard-charts.no-data'),
      },
      plotOptions: {
        bar: {
          borderRadius: 4,
          horizontal: true,
          barHeight: '58%',
        },
      },
      tooltip: {
        y: {
          formatter: (value) => this.loginCountLabel(value),
        },
      },
      xaxis: {
        min: 0,
        forceNiceScale: true,
        categories: items.map((item) =>
          item.institutionName ?? this.transloco.translate('page.system.dashboard-charts.institution-fallback'),
        ),
        labels: {
          formatter: (value) => `${Math.trunc(Number(value))}`,
        },
      },
      yaxis: {
        labels: {
          maxWidth: 220,
        },
      },
    };
  });

  private loginCountLabel(value: number): string {
    return this.transloco.translate('page.system.dashboard-charts.login-count', { count: value });
  }
}
