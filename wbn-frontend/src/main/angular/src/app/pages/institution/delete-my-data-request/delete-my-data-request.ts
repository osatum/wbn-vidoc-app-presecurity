import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ApiErrorResponse, UserDataDeletionRequestForm, UserDataDeletionService } from '../../../core/backend';

@Component({
  selector: 'app-delete-my-data-request',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './delete-my-data-request.html',
  styleUrl: './delete-my-data-request.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeleteMyDataRequest {
  private readonly fb = inject(FormBuilder);
  private readonly userDataDeletionService = inject(UserDataDeletionService);
  private readonly transloco = inject(TranslocoService);

  readonly isSaving = signal(false);
  readonly success = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly fieldErrorEntries = computed(() =>
    Object.entries(this.fieldErrors()).filter(([path]) => path !== 'email'),
  );

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(128)]],
  });

  submitted = false;

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.clearBackendFieldErrors());
  }

  submit(): void {
    if (this.isSaving() || this.success()) {
      return;
    }

    this.submitted = true;
    this.errorMessage.set(null);
    this.clearBackendFieldErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving.set(true);
    this.userDataDeletionService.requestUserDataDeletion(this.createPayload())
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: () => {
          this.success.set(true);
          this.form.disable();
        },
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  isInvalid(fieldName: 'email'): boolean {
    const control = this.form.controls[fieldName];
    return (this.submitted || control.touched) && control.invalid;
  }

  emailError(): string | null {
    const control = this.form.controls.email;

    if (!this.isInvalid('email')) {
      return null;
    }

    if (control.errors?.['required']) {
      return this.transloco.translate('page.institution.deleteMyData.validation.emailRequired');
    }

    if (control.errors?.['email']) {
      return this.transloco.translate('page.institution.deleteMyData.validation.emailInvalid');
    }

    if (control.errors?.['maxlength']) {
      return this.transloco.translate('page.institution.deleteMyData.validation.emailMaxlength');
    }

    if (control.errors?.['backend']) {
      return this.fieldErrors()['email'] ?? this.transloco.translate('page.institution.deleteMyData.invalidAccount');
    }

    return null;
  }

  private createPayload(): UserDataDeletionRequestForm {
    return {
      email: this.form.controls.email.value.trim(),
    };
  }

  private handleError(error: HttpErrorResponse): void {
    const body = this.apiError(error);
    const message = this.errorText(error, body);

    if (error.status === 400 || error.status === 403) {
      this.applyBackendFieldErrors({
        email: message,
      });
    }

    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.errorMessage.set(message);
  }

  private errorText(error: HttpErrorResponse, body: ApiErrorResponse | undefined): string {
    if (error.status >= 500) {
      return this.transloco.translate('common.serverError', { status: error.status });
    }

    if (error.status === 0) {
      return this.transloco.translate('common.connectionError');
    }

    if (error.status === 400 || error.status === 403) {
      return body?.message ?? this.transloco.translate('page.institution.deleteMyData.invalidAccount');
    }

    return body?.message ?? this.transloco.translate('page.institution.deleteMyData.failed');
  }

  private applyBackendFieldErrors(fieldErrors: Record<string, string>): void {
    this.fieldErrors.set({ ...this.fieldErrors(), ...fieldErrors });

    for (const fieldName of Object.keys(fieldErrors)) {
      const control = this.form.get(fieldName);
      if (control) {
        control.setErrors({ ...(control.errors ?? {}), backend: true });
        control.markAsTouched();
      }
    }
  }

  private clearBackendFieldErrors(): void {
    const fieldErrors = this.fieldErrors();
    this.fieldErrors.set({});

    for (const fieldName of Object.keys(fieldErrors)) {
      const control = this.form.get(fieldName);
      if (!control?.errors?.['backend']) {
        continue;
      }

      const errors = { ...control.errors };
      delete errors['backend'];
      control.setErrors(Object.keys(errors).length ? errors : null);
    }
  }

  private apiError(error: HttpErrorResponse): ApiErrorResponse | undefined {
    if (typeof error.error !== 'object' || error.error === null) {
      return undefined;
    }

    return error.error as ApiErrorResponse;
  }
}
