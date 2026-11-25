import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {NgClass} from '@angular/common';
import {HttpErrorResponse} from '@angular/common/http';
import {AuthService} from '../../../core/services/auth.service';
import {RedirectService} from '../../../core/services/redirect-service';
import {ToastService} from '../../../core/services/toast-service';
import {ApiErrorResponse, InstitutionAccessDto} from '../../../core/backend';
import {TranslocoDirective, TranslocoService} from '@jsverse/transloco';
import {RouterLink} from '@angular/router';

type LoginState =
  | {
      status: 'idle' | 'loading' | 'success';
      error: null;
    }
  | {
      status: 'error';
      error: string;
    };

@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    NgClass,
    TranslocoDirective,
    RouterLink
  ],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly redirectService = inject(RedirectService);
  private readonly toastService = inject(ToastService);
  private readonly transloco = inject(TranslocoService);

  readonly state = signal<LoginState>({ status: 'idle', error: null });
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');
  readonly errorMessage = computed(() => this.state().error);
  readonly fieldErrors = signal<Record<string, string>>({});

  loginForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]]
  });
  submitted = false;
  fieldTextType = false;

  // convenience getter for easy access to form fields
  get f() {
    return this.loginForm.controls;
  }

  /**
   * Password Hide/Show
   */
  toggleFieldTextType() {
    this.fieldTextType = !this.fieldTextType;
  }

  /**
   * Form submit
   */
  onSubmit() {
    if (this.isLoading()) {
      return;
    }

    this.submitted = true;
    this.state.set({ status: 'idle', error: null });
    this.clearBackendFieldErrors();

    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.state.set({ status: 'loading', error: null });
    this.authService.login(this.f['email'].value, this.f['password'].value, this.getInstitutionId())
      .subscribe({
        next: response => {
          this.state.set({ status: 'success', error: null });
          this.redirectService.redirectAfterLogin(response);
        },
        error: (error: HttpErrorResponse) => this.handleLoginError(error),
      });

  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  getInstitution(): InstitutionAccessDto | null {
    return this.authService.getPendingInstitutionsSnapshot()?.[0] ?? null;
  }
  getInstitutionId(): number | undefined {
    return this.authService.getPendingInstitutionsSnapshot()?.[0]?.id ?? undefined;
  }

  private handleLoginError(error: HttpErrorResponse): void {
    const body = error.error as ApiErrorResponse | undefined;
    const message = this.loginErrorMessage(body);

    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.state.set({ status: 'error', error: message });
    this.toastService.show(message, {classname: 'bg-danger text-white', delay: 15000});
  }

  private loginErrorMessage(body: ApiErrorResponse | undefined): string {
    if (body?.code === 'AUTHENTICATION_FAILED') {
      return this.transloco.translate('page.auth.login.invalidCredentials');
    }

    return body?.message ?? this.transloco.translate('page.auth.login.failed');
  }

  private applyBackendFieldErrors(fieldErrors: Record<string, string>): void {
    this.fieldErrors.set(fieldErrors);

    for (const fieldName of Object.keys(fieldErrors)) {
      const control = this.loginForm.get(fieldName);
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
      const control = this.loginForm.get(fieldName);
      if (!control?.errors?.['backend']) {
        continue;
      }

      const errors = { ...control.errors };
      delete errors['backend'];
      control.setErrors(Object.keys(errors).length ? errors : null);
    }
  }
}
