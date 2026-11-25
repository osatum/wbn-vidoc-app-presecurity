import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgClass } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ApiErrorResponse, PasswordResetRequestForm, PasswordResetService } from '../../../core/backend';

type PasswordResetRequestState =
  | { status: 'idle' | 'loading' | 'success'; error: null }
  | { status: 'error'; error: string };

@Component({
  selector: 'app-password-reset-request',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgClass, TranslocoPipe],
  templateUrl: './password-reset-request.html',
  styleUrl: './password-reset-request.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetRequestPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly passwordResetService = inject(PasswordResetService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly transloco = inject(TranslocoService);

  readonly state = signal<PasswordResetRequestState>({ status: 'idle', error: null });
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly isSuccess = computed(() => this.state().status === 'success');
  readonly errorMessage = computed(() => this.state().error);
  readonly fieldErrors = signal<Record<string, string>>({});

  readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(128)]],
  });

  submitted = false;

  onSubmit(): void {
    if (this.isLoading() || this.isSuccess()) {
      return;
    }

    this.submitted = true;
    this.state.set({ status: 'idle', error: null });
    this.clearBackendFieldErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.state.set({ status: 'loading', error: null });
    this.passwordResetService.requestPasswordReset(this.createPayload())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (this.isLoading()) {
            this.state.set({ status: 'idle', error: null });
          }
        }),
      )
      .subscribe({
        next: () => {
          this.form.disable();
          this.state.set({ status: 'success', error: null });
        },
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  isInvalid(fieldName: 'email'): boolean {
    return this.submitted && this.form.controls[fieldName].invalid;
  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  private createPayload(): PasswordResetRequestForm {
    return {
      email: this.form.controls.email.value.trim(),
    };
  }

  private handleError(error: HttpErrorResponse): void {
    const body = this.apiError(error);
    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.state.set({ status: 'error', error: this.errorText(error, body) });
  }

  private errorText(error: HttpErrorResponse, body: ApiErrorResponse | undefined): string {
    if (error.status >= 500) {
      return this.transloco.translate('common.serverError', { status: error.status });
    }

    if (error.status === 0) {
      return this.transloco.translate('common.connectionError');
    }

    return body?.message ?? this.transloco.translate('page.auth.passwordResetRequest.failed');
  }

  private applyBackendFieldErrors(fieldErrors: Record<string, string>): void {
    this.fieldErrors.set(fieldErrors);

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
