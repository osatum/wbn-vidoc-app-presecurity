import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { NavigationAuditReportRequest, NavigationAuditReportsService } from '../backend';

export interface NavigationAuditReportFile {
  file: Blob;
  filename: string;
}

@Injectable({ providedIn: 'root' })
export class NavigationAuditReportService {
  private readonly api = inject(NavigationAuditReportsService);

  export(request: NavigationAuditReportRequest): Observable<NavigationAuditReportFile> {
    const xls = request.reportType === 'SERVICE_ENTRIES';
    return this.api
      .exportNavigationAuditReport(request, 'response', false, {
        httpHeaderAccept: xls
          ? 'application/vnd.ms-excel'
          : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        transferCache: false,
      })
      .pipe(
        map((response) => {
          if (!response.body) {
            throw new Error('Empty report response.');
          }
          const fallback = `navigation-${request.reportType.toLowerCase()}-${request.dateFrom}-${request.dateTo}.${xls ? 'xls' : 'xlsx'}`;
          const disposition = response.headers.get('Content-Disposition');
          const filename = disposition?.match(/filename="?([^";]+)"?/i)?.[1];
          return {
            file: response.body,
            filename: filename && !/[\\/]/.test(filename) ? filename : fallback,
          };
        }),
      );
  }
}
