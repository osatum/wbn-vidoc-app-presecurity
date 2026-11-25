import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgClass } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ApiErrorResponse, PasswordResetConfirmForm, PasswordResetService, PasswordResetTokenStatusDto } from '../../../core/backend';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type TokenStatus = PasswordResetTokenStatusDto.StatusEnum;

@Component({
  selector: 'app-password-reset-confirm',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, NgClass, LocalSpinner, TranslocoPipe],
  templateUrl: './password-reset-confirm.html',
  styleUrl: './password-reset-confirm.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetConfirmPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly passwordResetService = inject(PasswordResetService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly transloco = inject(TranslocoService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly success = signal(false);
  readonly tokenStatus = signal<TokenStatus | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly canShowForm = computed(() =>
    !this.loading() && !this.success() && this.tokenStatus() === PasswordResetTokenStatusDto.StatusEnum.Valid
  );
  readonly statusMessage = computed(() => this.messageForStatus(this.tokenStatus()));

  readonly form = this.fb.group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    passwordRepeat: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
  });

  private token = '';
  submitted = false;

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';

    if (!this.token) {
      this.loading.set(false);
      this.tokenStatus.set(PasswordResetTokenStatusDto.StatusEnum.Invalid);
      return;
    }

    this.passwordResetService.verifyPasswordResetToken(this.token)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: result => this.tokenStatus.set(result.status ?? PasswordResetTokenStatusDto.StatusEnum.Invalid),
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.extractErrorMessage(error)),
      });
  }

  onSubmit(): void {
    if (this.saving() || this.success() || !this.canShowForm()) {
      return;
    }

    this.submitted = true;
    this.errorMessage.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.form.controls.password.value !== this.form.controls.passwordRepeat.value) {
      this.form.controls.passwordRepeat.setErrors({
        ...(this.form.controls.passwordRepeat.errors ?? {}),
        mismatch: true,
      });
      return;
    }

    this.saving.set(true);
    this.passwordResetService.confirmPasswordReset(this.token, this.createPayload())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: result => this.applyConfirmResult(result.status),
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.extractErrorMessage(error)),
      });
  }

  isInvalid(fieldName: 'password' | 'passwordRepeat'): boolean {
    return this.submitted && this.form.controls[fieldName].invalid;
  }

  private createPayload(): PasswordResetConfirmForm {
    return {
      password: this.form.controls.password.value,
      passwordRepeat: this.form.controls.passwordRepeat.value,
    };
  }

  private applyConfirmResult(status: TokenStatus | undefined): void {
    const resolvedStatus = status ?? PasswordResetTokenStatusDto.StatusEnum.Invalid;
    this.tokenStatus.set(resolvedStatus);

    if (resolvedStatus === PasswordResetTokenStatusDto.StatusEnum.Completed) {
      this.success.set(true);
      this.form.disable();
      return;
    }

    this.errorMessage.set(this.messageForStatus(resolvedStatus));
  }

  private messageForStatus(status: TokenStatus | null): string | null {
    switch (status) {
      case PasswordResetTokenStatusDto.StatusEnum.Invalid:
        return this.transloco.translate('page.auth.passwordResetConfirm.invalid');
      case PasswordResetTokenStatusDto.StatusEnum.Expired:
        return this.transloco.translate('page.auth.passwordResetConfirm.expired');
      case PasswordResetTokenStatusDto.StatusEnum.Used:
        return this.transloco.translate('page.auth.passwordResetConfirm.used');
      default:
        return null;
    }
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = this.apiError(error);

    if (error.status >= 500) {
      return this.transloco.translate('common.serverError', { status: error.status });
    }

    if (error.status === 0) {
      return this.transloco.translate('common.connectionError');
    }

    return body?.message ?? this.transloco.translate('page.auth.passwordResetConfirm.failed');
  }

  private apiError(error: HttpErrorResponse): ApiErrorResponse | undefined {
    if (typeof error.error !== 'object' || error.error === null) {
      return undefined;
    }

    return error.error as ApiErrorResponse;
  }
}
