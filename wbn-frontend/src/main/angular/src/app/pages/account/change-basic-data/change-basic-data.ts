import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, switchMap } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { ApiErrorResponse, UserBasicDataForm, UserProfileService } from '../../../core/backend';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast-service';

type LoadState = 'loading' | 'success' | 'error';
type ChangeBasicDataControlName = 'firstName' | 'lastName' | 'orcid';
type ValidationErrorName = 'required' | 'maxlength' | 'pattern' | 'backend';

function notBlank(control: AbstractControl): { required: true } | null {
  const value = control.value;
  return typeof value === 'string' && value.trim().length === 0 ? { required: true } : null;
}

@Component({
  selector: 'app-change-basic-data',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './change-basic-data.html',
  styleUrl: './change-basic-data.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangeBasicData {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly authService = inject(AuthService);
  private readonly userProfileService = inject(UserProfileService);
  private readonly transloco = inject(TranslocoService);
  private readonly toastService = inject(ToastService);

  readonly returnUrl = '/institution/user/dashboard';
  readonly loadState = signal<LoadState>('loading');
  readonly isLoading = computed(() => this.loadState() === 'loading');
  readonly hasError = computed(() => this.loadState() === 'error');
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly fieldErrorEntries = computed(() =>
    Object.entries(this.fieldErrors()).filter(([path]) => !this.isKnownFieldError(path)),
  );

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, notBlank, Validators.maxLength(128)]],
    lastName: ['', [Validators.required, notBlank, Validators.maxLength(128)]],
    orcid: ['', [Validators.maxLength(19), Validators.pattern(/^\d{4}-\d{4}-\d{4}-\d{3}[0-9Xx]$/)]],
  });

  private readonly validationMessages: Record<ChangeBasicDataControlName, Partial<Record<ValidationErrorName, string>>> = {
    firstName: {
      required: 'page.account.change-basic-data.validation.first-name.required',
      maxlength: 'page.account.change-basic-data.validation.first-name.maxlength',
    },
    lastName: {
      required: 'page.account.change-basic-data.validation.last-name.required',
      maxlength: 'page.account.change-basic-data.validation.last-name.maxlength',
    },
    orcid: {
      maxlength: 'page.account.change-basic-data.validation.orcid.maxlength',
      pattern: 'page.account.change-basic-data.validation.orcid.pattern',
    },
  };

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.clearBackendFieldErrors();
    });

    this.loadForm();
  }

  save(): void {
    this.errorMessage.set(null);
    this.clearBackendFieldErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSaving.set(true);
    this.userProfileService.updateOwnBasicData(this.createPayload())
      .pipe(
        switchMap(() => this.authService.loadUserProfile()),
        finalize(() => this.isSaving.set(false)),
      )
      .subscribe({
        next: () => this.handleSaveSuccess(),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  validationError(controlName: ChangeBasicDataControlName): string | null {
    const control = this.form.controls[controlName];

    if (!control.touched) {
      return null;
    }

    if (control.errors?.['backend']) {
      return this.fieldError(controlName);
    }

    const errors = control.errors;
    if (errors === null) {
      return null;
    }

    for (const errorName of Object.keys(errors) as ValidationErrorName[]) {
      const messageKey = this.validationMessages[controlName][errorName];
      if (messageKey) {
        return this.transloco.translate(messageKey);
      }
    }

    return null;
  }

  isInvalid(controlName: ChangeBasicDataControlName): boolean {
    const control = this.form.controls[controlName];
    return control.touched && control.invalid;
  }

  private loadForm(): void {
    this.loadState.set('loading');
    this.userProfileService.ownBasicDataForm().subscribe({
      next: (form) => {
        this.form.patchValue({
          firstName: form.firstName ?? '',
          lastName: form.lastName ?? '',
          orcid: form.orcid ?? '',
        });
        this.loadState.set('success');
      },
      error: () => this.loadState.set('error'),
    });
  }

  private createPayload(): UserBasicDataForm {
    const value = this.form.getRawValue();
    const orcid = value.orcid.trim().toUpperCase();
    return {
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      orcid: orcid || undefined,
    };
  }

  private handleSaveSuccess(): void {
    this.toastService.show(this.transloco.translate('page.account.change-basic-data.save-success'), {
      classname: 'bg-success text-white',
      delay: 5000,
    });
    void this.router.navigate([this.returnUrl]);
  }

  private handleError(error: HttpErrorResponse): void {
    const body = this.apiError(error);
    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.errorMessage.set(
      body?.message ?? this.transloco.translate('page.account.change-basic-data.save-error'),
    );
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

  private isKnownFieldError(path: string): boolean {
    return ['firstName', 'lastName', 'orcid'].includes(path);
  }
}
