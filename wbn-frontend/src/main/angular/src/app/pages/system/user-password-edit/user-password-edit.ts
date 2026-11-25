import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { UserPasswordForm, UserService, UserViewDto } from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ToastService } from '../../../core/services/toast-service';
import {
  type ApiErrorResponse,
  type SystemValidationErrorName,
  type SystemValidationMessages,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type LoadState = 'loading' | 'success' | 'error';
type UserPasswordControlName = 'password' | 'passwordRepeat';

@Component({
  selector: 'app-user-password-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AlertData, TranslocoPipe, LocalSpinner],
  templateUrl: './user-password-edit.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserPasswordEdit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly transloco = inject(TranslocoService);
  private readonly toastService = inject(ToastService);

  readonly userId = signal<number | null>(this.parseUserId(this.route.snapshot.paramMap.get('id')));
  readonly user = signal<UserViewDto | null>(null);
  readonly loadState = signal<LoadState>('loading');
  readonly isLoading = computed(() => this.loadState() === 'loading');
  readonly hasError = computed(() => this.loadState() === 'error');
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly fieldErrorEntries = computed(() =>
    Object.entries(this.fieldErrors()).filter(([path]) => !this.isKnownFieldError(path)),
  );
  readonly returnUrl = computed(() => {
    const id = this.userId();
    return id === null ? '/system/admin/users' : `/system/admin/users/${id}`;
  });

  readonly form = this.fb.nonNullable.group(
    {
      password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
      passwordRepeat: [
        '',
        [Validators.required, Validators.minLength(8), Validators.maxLength(128)],
      ],
    },
    { validators: [this.passwordsMatchValidator] },
  );
  private readonly validationMessages: SystemValidationMessages<UserPasswordControlName> = {
    password: {
      required: 'page.system.user-password-edit.validation.password.required',
      minlength: 'page.system.user-password-edit.validation.password.minlength',
      maxlength: 'page.system.user-password-edit.validation.password.maxlength',
    },
    passwordRepeat: {
      required: 'page.system.user-password-edit.validation.password-repeat.required',
      minlength: 'page.system.user-password-edit.validation.password-repeat.minlength',
      maxlength: 'page.system.user-password-edit.validation.password-repeat.maxlength',
      passwordMismatch: 'page.system.user-password-edit.validation.password-repeat.match',
    },
  };

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.clearBackendFieldErrors();
    });

    this.loadUser();
  }

  save(): void {
    this.errorMessage.set(null);
    this.clearBackendFieldErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const userId = this.userId();
    if (userId === null) {
      this.errorMessage.set(
        this.transloco.translate('page.system.user-password-edit.invalid-user-id'),
      );
      return;
    }

    const value = this.form.getRawValue();
    const form: UserPasswordForm = {
      password: value.password,
      passwordRepeat: value.passwordRepeat,
    };

    this.isSaving.set(true);
    this.userService
      .updateUserPassword(userId, form)
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (user) => this.handleSaveSuccess(user),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  validationError(controlName: UserPasswordControlName): string | null {
    const control = this.form.controls[controlName];
    return this.validationErrorForControl(
      control,
      this.validationMessages[controlName],
      controlName,
    );
  }

  isInvalid(controlName: UserPasswordControlName): boolean {
    const control = this.form.controls[controlName];
    return control.touched && (control.invalid || this.hasPasswordMismatch(controlName));
  }

  private loadUser(): void {
    const userId = this.userId();
    if (userId === null) {
      this.loadState.set('error');
      return;
    }

    this.loadState.set('loading');
    this.userService.findUser(userId).subscribe({
      next: (user) => {
        this.user.set(user);
        this.loadState.set('success');
      },
      error: () => this.loadState.set('error'),
    });
  }

  private handleSaveSuccess(user: UserViewDto): void {
    const userId = user.id ?? this.userId();
    this.toastService.show(
      this.transloco.translate('page.system.user-password-edit.save-success'),
      {
        classname: 'bg-success text-white',
        delay: 5000,
      },
    );

    if (userId !== null && userId !== undefined) {
      void this.router.navigate(['/system/admin/users', userId]);
      return;
    }

    void this.router.navigate(['/system/admin/users']);
  }

  private handleError(error: HttpErrorResponse): void {
    const body = error.error as ApiErrorResponse | undefined;
    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.errorMessage.set(
      body?.message ?? this.transloco.translate('page.system.user-password-edit.save-error'),
    );
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

  private validationErrorForControl(
    control: AbstractControl,
    messages: Partial<Record<SystemValidationErrorName, string>>,
    backendPath: UserPasswordControlName,
  ): string | null {
    const errors = control.errors;

    if (!control.touched) {
      return null;
    }

    if (errors?.['backend']) {
      return this.fieldError(backendPath);
    }

    if (this.hasPasswordMismatch(backendPath)) {
      const message = messages.passwordMismatch;
      return message ? this.transloco.translate(message) : null;
    }

    if (errors === null) {
      return null;
    }

    for (const errorName of Object.keys(errors) as SystemValidationErrorName[]) {
      const message = messages[errorName];
      if (message) {
        return this.transloco.translate(message);
      }
    }

    return null;
  }

  private passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
    const password = control.get('password')?.value;
    const passwordRepeat = control.get('passwordRepeat')?.value;

    if (!password || !passwordRepeat || password === passwordRepeat) {
      return null;
    }

    return { passwordMismatch: true };
  }

  private hasPasswordMismatch(controlName: UserPasswordControlName): boolean {
    return controlName === 'passwordRepeat' && this.form.hasError('passwordMismatch');
  }

  private isKnownFieldError(path: string): boolean {
    return ['password', 'passwordRepeat'].includes(path);
  }

  private parseUserId(value: string | null): number | null {
    const id = Number(value);
    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
