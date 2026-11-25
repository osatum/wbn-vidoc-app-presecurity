import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import {
  ApexAxisChartSeries,
  ApexChart,
  ApexDataLabels,
  ApexGrid,
  ApexLegend,
  ApexMarkers,
  ApexStroke,
  ApexTitleSubtitle,
  ApexXAxis,
  ApexYAxis,
  NgApexchartsModule,
} from 'ng-apexcharts';
import { RatingView } from '../../../../core/backend';

type JournalRatingChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  xaxis: ApexXAxis;
  stroke: ApexStroke;
  colors: string[];
  dataLabels: ApexDataLabels;
  legend: ApexLegend;
  markers: ApexMarkers;
  grid: ApexGrid;
  yaxis: ApexYAxis;
  title: ApexTitleSubtitle;
};

@Component({
  selector: 'app-journal-rating-chart',
  standalone: true,
  imports: [NgApexchartsModule],
  templateUrl: './journal-rating-chart.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalRatingChart {
  chartOptions: JournalRatingChartOptions = this.buildChartOptions([]);

  @Input()
  set ratings(value: RatingView[] | null | undefined) {
    this.chartOptions = this.buildChartOptions(value ?? []);
  }

  private buildChartOptions(ratings: RatingView[]): JournalRatingChartOptions {
    const sortedRatings = [...ratings].sort((left, right) => (left.year ?? 0) - (right.year ?? 0));

    return {
      chart: {
        height: 380,
        type: 'line',
        zoom: {
          enabled: false,
        },
        toolbar: {
          show: false,
        },
      },
      colors: ['#405189', '#0ab39c'],
      dataLabels: {
        enabled: false,
      },
      stroke: {
        width: [3],
        curve: 'straight',
      },
      series: [
        {
          name: 'ICV',
          data: sortedRatings.map((rating) => rating.currentMethodPoint ?? 0),
        },
      ],
      title: {
        align: 'left',
        style: {
          fontWeight: 500,
        },
      },
      grid: {
        row: {
          colors: ['#ffffff', '#ffffff'],
          opacity: 0.2,
        },
        borderColor: '#f1f1f1',
      },
      markers: {
        size: 6,
      },
      xaxis: {
        categories: sortedRatings.map((rating) => rating.year?.toString() ?? '-'),
        title: {
          text: 'Year',
        },
      },
      yaxis: {
        min: 0,
        max: 300,
      },
      legend: {
        position: 'top',
        horizontalAlign: 'right',
        floating: true,
        offsetY: -25,
        offsetX: -5,
      },
    };
  }
}
