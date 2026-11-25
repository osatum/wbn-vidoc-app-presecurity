import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ApiErrorResponse, OwnPasswordChangeForm, UserProfileService } from '../../../core/backend';
import { withLocalUnauthorizedHandling } from '../../../core/interceptors/auth-error-context';

type ChangePasswordControlName = 'currentPassword' | 'password' | 'passwordRepeat';
type ChangePasswordViewMode = 'system' | 'institution';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './change-password.html',
  styleUrl: './change-password.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePassword {
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly userProfileService = inject(UserProfileService);
  private readonly transloco = inject(TranslocoService);

  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly fieldErrorEntries = computed(() =>
    Object.entries(this.fieldErrors()).filter(([path]) => !this.isKnownFieldError(path)),
  );
  readonly returnUrl = signal(this.route.snapshot.data['returnUrl'] ?? '/');
  readonly viewMode = signal<ChangePasswordViewMode>(this.resolveViewMode(this.route.snapshot.data['viewMode']));
  readonly isSystemView = computed(() => this.viewMode() === 'system');
  readonly titleKey = computed(() => `page.account.change-password.${this.viewMode()}.title`);
  readonly descriptionKey = computed(() => `page.account.change-password.${this.viewMode()}.description`);
  readonly submitKey = computed(() => `page.account.change-password.${this.viewMode()}.submit`);

  readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
      passwordRepeat: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    },
    { validators: [this.passwordsMatchValidator] },
  );

  private readonly validationMessages: Record<ChangePasswordControlName, Partial<Record<string, string>>> = {
    currentPassword: {
      required: 'page.account.change-password.validation.current-password.required',
      minlength: 'page.account.change-password.validation.current-password.minlength',
      maxlength: 'page.account.change-password.validation.current-password.maxlength',
      backend: 'page.account.change-password.validation.current-password.invalid',
    },
    password: {
      required: 'page.account.change-password.validation.password.required',
      minlength: 'page.account.change-password.validation.password.minlength',
      maxlength: 'page.account.change-password.validation.password.maxlength',
    },
    passwordRepeat: {
      required: 'page.account.change-password.validation.password-repeat.required',
      minlength: 'page.account.change-password.validation.password-repeat.minlength',
      maxlength: 'page.account.change-password.validation.password-repeat.maxlength',
      passwordMismatch: 'page.account.change-password.validation.password-repeat.match',
    },
  };

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.successMessage.set(null);
      this.clearBackendFieldErrors();
    });
  }

  save(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.clearBackendFieldErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving.set(true);
    this.userProfileService.changeOwnPassword(this.createPayload(), undefined, undefined, {
      context: withLocalUnauthorizedHandling(),
    })
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: () => this.handleSaveSuccess(),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  validationError(controlName: ChangePasswordControlName): string | null {
    const control = this.form.controls[controlName];

    if (!control.touched) {
      return null;
    }

    if (control.errors?.['backend']) {
      return this.fieldError(controlName)
        ?? this.transloco.translate(this.validationMessages[controlName]['backend'] ?? '');
    }

    if (this.hasPasswordMismatch(controlName)) {
      return this.transloco.translate('page.account.change-password.validation.password-repeat.match');
    }

    const errors = control.errors;
    if (errors === null) {
      return null;
    }

    for (const errorName of Object.keys(errors)) {
      const messageKey = this.validationMessages[controlName][errorName];
      if (messageKey) {
        return this.transloco.translate(messageKey);
      }
    }

    return null;
  }

  isInvalid(controlName: ChangePasswordControlName): boolean {
    const control = this.form.controls[controlName];
    return control.touched && (control.invalid || this.hasPasswordMismatch(controlName));
  }

  private createPayload(): OwnPasswordChangeForm {
    const value = this.form.getRawValue();
    return {
      currentPassword: value.currentPassword,
      password: value.password,
      passwordRepeat: value.passwordRepeat,
    };
  }

  private handleSaveSuccess(): void {
    this.form.reset();
    this.successMessage.set(this.transloco.translate('page.account.change-password.save-success'));
  }

  private handleError(error: HttpErrorResponse): void {
    const body = this.apiError(error);

    if (this.isCurrentPasswordError(error, body)) {
      const message = this.transloco.translate('page.account.change-password.current-password-error');
      this.applyBackendFieldErrors({
        currentPassword: message,
      });
      this.errorMessage.set(message);
      return;
    }

    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.errorMessage.set(
      body?.message ?? this.transloco.translate('page.account.change-password.save-error'),
    );
  }

  private isCurrentPasswordError(error: HttpErrorResponse, body: ApiErrorResponse | undefined): boolean {
    return error.status === 401
      || body?.code === 'AUTHENTICATION_FAILED'
      || body?.fieldErrors?.['currentPassword'] !== undefined;
  }

  private apiError(error: HttpErrorResponse): ApiErrorResponse | undefined {
    if (typeof error.error !== 'object' || error.error === null) {
      return undefined;
    }

    return error.error as ApiErrorResponse;
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

  private passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
    const password = control.get('password')?.value;
    const passwordRepeat = control.get('passwordRepeat')?.value;

    if (!password || !passwordRepeat || password === passwordRepeat) {
      return null;
    }

    return { passwordMismatch: true };
  }

  private hasPasswordMismatch(controlName: ChangePasswordControlName): boolean {
    return controlName === 'passwordRepeat' && this.form.hasError('passwordMismatch');
  }

  private isKnownFieldError(path: string): boolean {
    return ['currentPassword', 'password', 'passwordRepeat'].includes(path);
  }

  private resolveViewMode(value: unknown): ChangePasswordViewMode {
    return value === 'system' ? 'system' : 'institution';
  }
}
