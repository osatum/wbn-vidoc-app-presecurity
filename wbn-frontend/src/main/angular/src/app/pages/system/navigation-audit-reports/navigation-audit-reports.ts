import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { finalize } from 'rxjs';
import { NavigationAuditReportRequest } from '../../../core/backend';
import { NavigationAuditReportService } from '../../../core/services/navigation-audit-reports.service';
import { downloadFile } from '../../../shared/file-download/file-download';

function currentMonthDates(): { dateFrom: string; dateTo: string } {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const lastDay = new Date(year, today.getMonth() + 1, 0).getDate();
  return { dateFrom: `${year}-${month}-01`, dateTo: `${year}-${month}-${lastDay}` };
}

export function reportDateRange(control: AbstractControl): ValidationErrors | null {
  const from = control.get('dateFrom')?.value as string;
  const to = control.get('dateTo')?.value as string;
  return from && to && from > to ? { dateRange: true } : null;
}

@Component({
  selector: 'app-navigation-audit-reports',
  standalone: true,
  imports: [ReactiveFormsModule, TranslocoPipe],
  templateUrl: './navigation-audit-reports.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavigationAuditReports {
  private readonly document = inject(DOCUMENT);
  private readonly reports = inject(NavigationAuditReportService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly builder = inject(FormBuilder);
  private readonly defaultDates = currentMonthDates();
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly completed = signal(false);
  readonly form = this.builder.nonNullable.group(
    {
      dateFrom: [
        this.defaultDates.dateFrom,
        [Validators.required, Validators.pattern(/^\d{4}-\d{2}-\d{2}$/)],
      ],
      dateTo: [
        this.defaultDates.dateTo,
        [Validators.required, Validators.pattern(/^\d{4}-\d{2}-\d{2}$/)],
      ],
      reportType: this.builder.nonNullable.control<NavigationAuditReportRequest.ReportTypeEnum>(
        'VISITS',
        Validators.required,
      ),
    },
    { validators: reportDateRange },
  );

  generate(): void {
    if (this.loading()) {
      return;
    }
    this.form.markAllAsTouched();
    this.error.set(null);
    this.completed.set(false);
    if (this.form.invalid) {
      return;
    }
    const request = this.form.getRawValue();
    this.loading.set(true);
    this.reports
      .export(request)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (report) => {
          if (downloadFile(this.document, report.file, report.filename)) {
            this.completed.set(true);
          } else {
            this.error.set('download-error');
          }
        },
        error: (error: unknown) => {
          this.error.set(
            error instanceof HttpErrorResponse && error.status === 400
              ? 'invalid-request'
              : error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)
                ? 'access-error'
                : 'generate-error',
          );
        },
      });
  }
}
