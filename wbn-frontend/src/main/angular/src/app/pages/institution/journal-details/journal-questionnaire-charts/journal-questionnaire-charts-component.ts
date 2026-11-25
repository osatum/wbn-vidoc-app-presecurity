import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgTemplateOutlet } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import type { ECharts, EChartsCoreOption } from 'echarts/core';
import * as echarts from 'echarts/core';
import { MapChart } from 'echarts/charts';
import { GeoComponent, TooltipComponent, VisualMapComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import {
  ApexAxisChartSeries,
  ApexChart,
  ApexDataLabels,
  ApexGrid,
  ApexLegend,
  ApexMarkers,
  ApexNoData,
  ApexStroke,
  ApexTooltip,
  ApexXAxis,
  ApexYAxis,
  NgApexchartsModule,
} from 'ng-apexcharts';
import { catchError, map, of, startWith } from 'rxjs';

import { AlertData } from '../../../../shared/alert-data/alert-data';
import { LocalSpinner } from '../../../../shared/local-spinner/local-spinner';
import { JournalNameService } from '../../../../core/services/journal-name-service';
import {
  AnalyticsCountryView,
  AnalyticsContainerView,
  AnalyticsJournalService,
  AnalyticsJournalView,
  WorldGeoJson,
} from '../../../../core/services/analytics-journal.service';

type AnalyticsState = {
  status: 'loading' | 'success' | 'error';
  data: AnalyticsJournalView | null;
  error: unknown | null;
};

type JournalQuestionnaireChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  colors: string[];
  dataLabels: ApexDataLabels;
  grid: ApexGrid;
  legend: ApexLegend;
  markers: ApexMarkers;
  noData: ApexNoData;
  stroke: ApexStroke;
  tooltip: ApexTooltip;
  xaxis: ApexXAxis;
  yaxis: ApexYAxis;
};

type ChartSeriesInput = {
  name: string;
  data: (number | null)[];
};

type ChartTableRow = {
  label: string;
  values: (number | null)[];
};

type SingleMetricRow = {
  year: number;
  value: number | null;
};

type ReviewerCountryRow = {
  code: string;
  name: string;
  value: number;
};

type ReviewerCountryMapItem = {
  name: string;
  value: number;
  countryName: string;
  countryCode: string;
};

const REVIEWERS_WORLD_MAP_NAME = 'reviewers-world';
const REVIEWERS_MAP_DEFAULT_ZOOM = 1.4;
let reviewersWorldMapRegistered = false;

echarts.use([MapChart, GeoComponent, TooltipComponent, VisualMapComponent, CanvasRenderer]);

@Component({
  selector: 'app-journal-questionnaire-charts',
  imports: [AlertData, LocalSpinner, NgApexchartsModule, NgbNavModule, NgTemplateOutlet, TranslocoPipe],
  templateUrl: './journal-questionnaire-charts-component.html',
  styleUrl: './journal-questionnaire-charts-component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalQuestionnaireChartsComponent implements OnInit, OnDestroy {
  readonly journalId = input.required<number>();
  readonly surveyYear = input<number>();

  @ViewChild('reviewersMap')
  set reviewersMapContainer(element: ElementRef<HTMLDivElement> | undefined) {
    const nextElement = element?.nativeElement ?? null;
    if (this.reviewersMapElement === nextElement) {
      return;
    }

    this.disposeReviewersMap();
    this.reviewersMapElement = nextElement;
    this.renderReviewersMap();
  }

  private readonly analyticsJournalService = inject(AnalyticsJournalService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly journalNameService = inject(JournalNameService);
  private readonly transloco = inject(TranslocoService);
  private readonly numberFormatter = new Intl.NumberFormat('pl-PL', {
    maximumFractionDigits: 2,
  });
  private reviewersMapElement: HTMLDivElement | null = null;
  private reviewersMap: ECharts | null = null;
  private reviewersMapResizeObserver: ResizeObserver | null = null;
  private worldGeoJsonLoading = false;
  private worldMapRegistered = false;
  private readonly activeLang = signal(this.transloco.getActiveLang());

  private readonly state = signal<AnalyticsState>({
    status: 'loading',
    data: null,
    error: null,
  });

  protected readonly analytics = computed(() => this.state().data);
  protected readonly loading = computed(() => this.state().status === 'loading');
  protected readonly error = computed(() => this.state().error);
  protected readonly hasError = computed(() => this.state().status === 'error');
  protected readonly errorMessage = computed(() => this.extractErrorMessage(this.error()));

  protected readonly publicationYears = computed(() =>
    this.yearsFor(this.analytics()?.articleScientificAmount, this.analytics()?.articleManuscriptReceived),
  );

  protected readonly publicationRows = computed<ChartTableRow[]>(() => {
    const years = this.publicationYears();

    return [
      {
        label: this.translate('page.institution.questionnaireCharts.publication.articleScientificAmount'),
        values: this.valuesForYears(this.analytics()?.articleScientificAmount, years),
      },
      {
        label: this.translate('page.institution.questionnaireCharts.publication.articleManuscriptReceived'),
        values: this.valuesForYears(this.analytics()?.articleManuscriptReceived, years),
      },
    ];
  });

  protected readonly authorRows = computed(() => this.rowsFor(this.analytics()?.authorUnique));
  protected readonly reviewerRows = computed(() => this.rowsFor(this.analytics()?.reviewerForeign));
  protected readonly reviewerCountryRows = computed(() =>
    this.rowsForReviewerCountries(this.analytics()?.reviewersByCountry),
  );
  protected readonly hasReviewerCountryData = computed(() => this.reviewerCountryRows().length > 0);

  protected readonly publicationChart = computed(() =>
    this.buildLineChartOptions(
      this.publicationYears(),
      this.publicationRows().map((row) => ({
        name: row.label,
        data: row.values,
      })),
      ['#0ab39c', '#405189'],
    ),
  );

  protected readonly authorChart = computed(() => {
    const container = this.analytics()?.authorUnique;
    const years = this.yearsFor(container);

    return this.buildLineChartOptions(
      years,
      [
        {
          name: this.translate('page.institution.questionnaireCharts.author.series'),
          data: this.valuesForYears(container, years),
        },
      ],
      ['#299cdb'],
    );
  });

  protected readonly reviewerChart = computed(() => {
    const container = this.analytics()?.reviewerForeign;
    const years = this.yearsFor(container);

    return this.buildLineChartOptions(
      years,
      [
        {
          name: this.translate('page.institution.questionnaireCharts.reviewer.series'),
          data: this.valuesForYears(container, years),
        },
      ],
      ['#f7b84b'],
    );
  });

  private readonly reviewersMapRenderEffect = effect(() => {
    this.reviewerCountryRows();
    this.renderReviewersMap();
  });

  private readonly analyticsReloadEffect = effect((onCleanup) => {
    const subscription = this.loadAnalytics(this.journalId(), this.surveyYear());
    onCleanup(() => subscription.unsubscribe());
  });

  ngOnInit(): void {
    this.transloco.langChanges$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((lang) => {
        this.activeLang.set(lang);
        this.renderReviewersMap();
      });

  }

  private loadAnalytics(journalId: number, surveyYear?: number) {
    return this.analyticsJournalService
      .getAnalyticsJournal(journalId, surveyYear)
      .pipe(
        map(
          (data): AnalyticsState => ({
            status: 'success',
            data,
            error: null,
          }),
        ),
        startWith({
          status: 'loading',
          data: null,
          error: null,
        } satisfies AnalyticsState),
        catchError((error: unknown) =>
          of({
            status: 'error',
            data: null,
            error,
          } satisfies AnalyticsState),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => {
        this.state.set(state);

        if (this.hasReviewerCountryItems(state.data?.reviewersByCountry)) {
          this.loadWorldGeoJson();
        } else {
          this.disposeReviewersMap();
        }
      });
  }

  ngOnDestroy(): void {
    this.disposeReviewersMap();
  }

  protected formatValue(value: number | null | undefined): string {
    if (value === null || value === undefined || !Number.isFinite(value)) {
      return '-';
    }

    return this.numberFormatter.format(value);
  }

  private buildLineChartOptions(
    years: number[],
    series: ChartSeriesInput[],
    colors: string[],
  ): JournalQuestionnaireChartOptions {
    return {
      series,
      chart: {
        height: 300,
        type: 'line',

        toolbar: {
          show: false,
        },
        zoom: {
          enabled: false,
        },
      },
      colors,
      dataLabels: {
        enabled: false,
      },
      grid: {
        borderColor: '#e9ebec',
        strokeDashArray: 4,
      },
      legend: {
        horizontalAlign: 'right',
        position: 'top',
        fontSize: '14px',
      },
      markers: {
        size: 5,
      },
      noData: {
        text: this.translate('page.institution.questionnaireCharts.common.noData'),
        style: {
          fontSize: '14px !important',
        },
      },
      stroke: {
        curve: 'straight',
        width: 3,
      },
      tooltip: {
        style: {
          fontSize: '14px !important',
        },
        y: {
          formatter: (value) => this.formatValue(value),
        },
      },
      xaxis: {
        categories: years.map((year) => year.toString()),
        title: {
          text: this.translate('page.institution.questionnaireCharts.table.year'),
          style: {
            fontSize: '14px !important',
          },
        },
        labels: {
          style: {
            fontSize: '14px !important',
          },
        },
      },
      yaxis: {
        forceNiceScale: true,
        min: 0,
        labels: {
          formatter: (value) => this.formatValue(value),
          style: {
            fontSize: '14px !important',
          },
        },
      },
    };
  }

  private loadWorldGeoJson(): void {
    if (this.worldMapRegistered || reviewersWorldMapRegistered) {
      this.worldMapRegistered = true;
      this.renderReviewersMap();
      return;
    }

    if (this.worldGeoJsonLoading) {
      return;
    }

    this.worldGeoJsonLoading = true;
    this.analyticsJournalService
      .getWorldGeoJson()
      .pipe(
        catchError(() => of(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((geoJson) => {
        this.worldGeoJsonLoading = false;

        if (geoJson === null) {
          return;
        }

        echarts.registerMap(
          REVIEWERS_WORLD_MAP_NAME,
          this.withCountryCodesAsMapNames(geoJson) as unknown as Parameters<typeof echarts.registerMap>[1],
        );
        reviewersWorldMapRegistered = true;
        this.worldMapRegistered = true;
        this.renderReviewersMap();
      });
  }

  private renderReviewersMap(): void {
    const rows = this.reviewerCountryRows();
    if (rows.length === 0) {
      this.disposeReviewersMap();
      return;
    }

    if (this.reviewersMapElement === null || !this.worldMapRegistered) {
      return;
    }

    if (this.reviewersMap === null) {
      this.reviewersMap = echarts.init(this.reviewersMapElement);
      this.reviewersMapResizeObserver = new ResizeObserver(() => this.reviewersMap?.resize());
      this.reviewersMapResizeObserver.observe(this.reviewersMapElement);
    }

    this.reviewersMap.setOption(this.buildReviewersMapOptions(rows), true);
    this.reviewersMap.resize();
  }

  private disposeReviewersMap(): void {
    this.reviewersMapResizeObserver?.disconnect();
    this.reviewersMapResizeObserver = null;
    this.reviewersMap?.dispose();
    this.reviewersMap = null;
  }

  private buildReviewersMapOptions(rows: ReviewerCountryRow[]): EChartsCoreOption {
    const maxValue = Math.max(...rows.map((row) => Math.ceil(row.value)), 1);
    const data: ReviewerCountryMapItem[] = rows.map((row) => ({
      name: row.code,
      value: row.value,
      countryName: row.name,
      countryCode: row.code,
    }));

    return {
      textStyle: {
        fontSize: 14,
      },
      tooltip: {
        trigger: 'item',
        formatter: (params: unknown) => this.formatReviewersMapTooltip(params),
        textStyle: {
          fontSize: 14,
        },
      },
      visualMap: {
        min: 0,
        max: maxValue,
        left: 0,
        bottom: 0,
        calculable: true,
        precision: 0,
        textStyle: {
          color: '#405189',
          fontSize: 14,
        },
        inRange: {
          color: ['#b0beff', '#6272ad', '#405189'],
        },
        outOfRange: {
          color: ['#eef1f4'],
        },
      },
      series: [
        {
          type: 'map',
          map: REVIEWERS_WORLD_MAP_NAME,
          roam: true,
          zoom: REVIEWERS_MAP_DEFAULT_ZOOM,
          selectedMode: false,
          data,
          label: {
            show: false,
          },
          itemStyle: {
            areaColor: '#edf1f5',
            borderColor: '#c8d0d9',
            borderWidth: 0.5,
          },
          emphasis: {
            label: {
              show: false,
            },
            itemStyle: {
              areaColor: '#6fb7aa',
            },
          },
        },
      ],
    };
  }

  private formatReviewersMapTooltip(params: unknown): string {
    const data = (params as { data?: Partial<ReviewerCountryMapItem> }).data;
    if (
      data?.countryName === undefined
      || data.countryCode === undefined
      || data.value === undefined
      || !Number.isFinite(data.value)
    ) {
      return '';
    }

    return `${this.escapeHtml(data.countryName)} (${this.escapeHtml(data.countryCode)})<br>${this.escapeHtml(this.translate('page.institution.questionnaireCharts.reviewersMap.tooltipLabel'))}: ${this.formatValue(data.value)}`;
  }

  private withCountryCodesAsMapNames(geoJson: WorldGeoJson): WorldGeoJson {
    for (const feature of geoJson.features ?? []) {
      const properties = feature.properties;
      const code = this.normalizeCountryCode(properties?.iso_a2 ?? properties?.postal);
      if (properties !== undefined && code !== null) {
        properties.name = code;
      }
    }

    return geoJson;
  }

  private rowsForReviewerCountries(items: AnalyticsCountryView[] | null | undefined): ReviewerCountryRow[] {
    const rowsByCountry = new Map<string, ReviewerCountryRow>();

    for (const item of items ?? []) {
      const code = this.normalizeCountryCode(item.country?.code);
      const value = this.normalizeValue(item.value);

      if (code === null || value === null || value <= 0) {
        continue;
      }

      const current = rowsByCountry.get(code);
      if (current !== undefined) {
        current.value += value;
        continue;
      }

      rowsByCountry.set(code, {
        code,
        name: this.journalNameService.getCountryName(item.country ?? undefined) || code,
        value,
      });
    }

    return [...rowsByCountry.values()].sort(
      (left, right) => right.value - left.value || left.name.localeCompare(right.name),
    );
  }

  private hasReviewerCountryItems(items: AnalyticsCountryView[] | null | undefined): boolean {
    return (items ?? []).some((item) => {
      const code = this.normalizeCountryCode(item.country?.code);
      const value = this.normalizeValue(item.value);
      return code !== null && value !== null && value > 0;
    });
  }

  private rowsFor(container: AnalyticsContainerView | null | undefined): SingleMetricRow[] {
    return this.sortedItems(container).map((item) => ({
      year: item.year,
      value: item.value,
    }));
  }

  private yearsFor(...containers: (AnalyticsContainerView | null | undefined)[]): number[] {
    const years = new Set<number>();

    for (const container of containers) {
      for (const item of container?.items ?? []) {
        const year = item.year;
        if (typeof year === 'number' && Number.isInteger(year)) {
          years.add(year);
        }
      }
    }

    return [...years].sort((left, right) => left - right);
  }

  private valuesForYears(container: AnalyticsContainerView | null | undefined, years: number[]): (number | null)[] {
    const valuesByYear = new Map<number, number | null>();

    for (const item of container?.items ?? []) {
      const year = item.year;
      if (typeof year === 'number' && Number.isInteger(year)) {
        valuesByYear.set(year, this.normalizeValue(item.value));
      }
    }

    return years.map((year) => valuesByYear.get(year) ?? null);
  }

  private sortedItems(container: AnalyticsContainerView | null | undefined): SingleMetricRow[] {
    const rows: SingleMetricRow[] = [];

    for (const item of container?.items ?? []) {
      const year = item.year;
      if (typeof year !== 'number' || !Number.isInteger(year)) {
        continue;
      }

      rows.push({
        year,
        value: this.normalizeValue(item.value),
      });
    }

    return rows.sort((left, right) => left.year - right.year);
  }

  private normalizeValue(value: number | null | undefined): number | null {
    return value === null || value === undefined || !Number.isFinite(value) ? null : value;
  }

  private normalizeCountryCode(value: unknown): string | null {
    if (typeof value !== 'string') {
      return null;
    }

    const code = value.trim().toUpperCase();
    return code.length === 2 ? code : null;
  }

  private escapeHtml(value: string): string {
    return value
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#039;');
  }

  private extractErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && this.hasMessage(error.error)) {
      return error.error.message;
    }

    if (error instanceof Error) {
      return error.message;
    }

    return this.translate('page.institution.questionnaireCharts.loadError');
  }

  private translate(key: string): string {
    this.activeLang();
    return this.transloco.translate(key);
  }

  private hasMessage(value: unknown): value is { message: string } {
    return (
      typeof value === 'object'
      && value !== null
      && 'message' in value
      && typeof (value as { message?: unknown }).message === 'string'
    );
  }
}
