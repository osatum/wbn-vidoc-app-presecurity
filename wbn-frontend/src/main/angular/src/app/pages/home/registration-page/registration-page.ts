import {ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {NgClass} from '@angular/common';
import {NonNullableFormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';

import {
  ApiErrorResponse,
  RegistrationAvailabilityDto,
  RegistrationInstitutionDto,
  RegistrationService,
  RegistrationSubmitForm,
} from '../../../core/backend';
import {LocalSpinner} from '../../../shared/local-spinner/local-spinner';

type RegistrationField =
  | 'email'
  | 'firstName'
  | 'lastName'
  | 'orcid'
  | 'password'
  | 'termsAndPrivacyPolicyAccepted'
  | 'personalDataProcessingAcknowledged';

@Component({
  selector: 'app-registration-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    NgClass,
    LocalSpinner,
    TranslocoPipe,
  ],
  templateUrl: './registration-page.html',
  styleUrl: './registration-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistrationPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly registrationService = inject(RegistrationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly transloco = inject(TranslocoService);

  readonly institutions = signal<RegistrationInstitutionDto[]>([]);
  readonly selectedInstitution = signal<RegistrationInstitutionDto | null>(null);
  readonly selectedInstitutionId = signal<number | null>(null);
  readonly routeHasInstitution = signal(false);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly success = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly existingAccountError = signal(false);

  readonly showInstitutionChoice = computed(() => !this.routeHasInstitution());
  readonly showRegistrationForm = computed(() =>
    this.selectedInstitutionId() !== null && !this.loading() && !this.success()
  );
  readonly canSubmit = computed(() =>
    this.selectedInstitutionId() !== null && !this.loading() && !this.saving() && !this.success()
  );

  readonly registrationForm = this.fb.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    orcid: ['', [Validators.maxLength(19), Validators.pattern(/^\d{4}-\d{4}-\d{4}-\d{3}[0-9Xx]$/)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    termsAndPrivacyPolicyAccepted: [false, [Validators.requiredTrue]],
    personalDataProcessingAcknowledged: [false, [Validators.requiredTrue]],
  });

  submitted = false;

  ngOnInit(): void {
    const rawInstitutionId = this.route.snapshot.paramMap.get('id');
    this.routeHasInstitution.set(rawInstitutionId !== null);

    if (rawInstitutionId === null) {
      this.loadInstitutions();
      return;
    }

    const institutionId = this.parseInstitutionId(rawInstitutionId);
    if (institutionId === null) {
      this.errorMessage.set(this.transloco.translate('page.home.registration.invalid-institution-id'));
      return;
    }

    this.validateInstitution(institutionId);
  }

  onInstitutionSelection(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;

    if (!value) {
      this.selectedInstitution.set(null);
      this.selectedInstitutionId.set(null);
      this.errorMessage.set(null);
      return;
    }

    const institutionId = this.parseInstitutionId(value);
    if (institutionId === null) {
      this.errorMessage.set(this.transloco.translate('page.home.registration.invalid-institution-id'));
      return;
    }

    this.validateInstitution(institutionId);
  }

  onSubmit(): void {
    if (this.saving() || this.success()) {
      return;
    }

    this.submitted = true;
    this.errorMessage.set(null);
    this.existingAccountError.set(false);
    this.clearBackendFieldErrors();

    if (this.registrationForm.invalid) {
      this.registrationForm.markAllAsTouched();
      return;
    }

    const institutionId = this.selectedInstitutionId();
    if (institutionId === null) {
      this.errorMessage.set(this.transloco.translate('page.home.registration.select-institution-before-submit'));
      return;
    }

    this.saving.set(true);
    this.registrationService.submitRegistration(institutionId, this.createPayload())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: () => {
          this.success.set(true);
          this.registrationForm.disable();
        },
        error: (error: HttpErrorResponse) => this.handleSubmitError(error),
      });
  }

  isInvalid(fieldName: RegistrationField): boolean {
    const control = this.registrationForm.controls[fieldName];
    return this.submitted && control.invalid;
  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  private loadInstitutions(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.registrationService.findGeneralRegistrationInstitutions()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: institutions => {
          this.institutions.set(institutions);

          if (institutions.length === 0) {
            this.errorMessage.set(this.transloco.translate('page.home.registration.no-institutions'));
          }
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage.set(this.extractErrorMessage(
            error,
            this.transloco.translate('page.home.registration.load-institutions-error'),
          ));
        },
      });
  }

  private validateInstitution(institutionId: number): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.selectedInstitution.set(null);
    this.selectedInstitutionId.set(null);

    this.registrationService.validateRegistrationStart(institutionId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: availability => this.applyAvailability(institutionId, availability),
        error: (error: HttpErrorResponse) => {
          this.errorMessage.set(this.extractErrorMessage(
            error,
            this.transloco.translate('page.home.registration.check-availability-error'),
          ));
        },
      });
  }

  private applyAvailability(institutionId: number, availability: RegistrationAvailabilityDto): void {
    if (!availability.allowed) {
      this.errorMessage.set(this.availabilityMessage(availability.reason));
      return;
    }

    const institution = availability.institution
      ?? this.institutions().find(candidate => candidate.id === institutionId)
      ?? {id: institutionId};

    this.selectedInstitution.set(institution);
    this.selectedInstitutionId.set(institutionId);
  }

  private createPayload(): RegistrationSubmitForm {
    const value = this.registrationForm.getRawValue();
    const orcid = value.orcid.trim();

    return {
      email: value.email.trim(),
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      password: value.password,
      termsAndPrivacyPolicyAccepted: value.termsAndPrivacyPolicyAccepted,
      personalDataProcessingAcknowledged: value.personalDataProcessingAcknowledged,
      ...(orcid ? {orcid: orcid.toUpperCase()} : {}),
    };
  }

  private handleSubmitError(error: HttpErrorResponse): void {
    const body = this.apiError(error);

    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.existingAccountError.set(this.isEmailAlreadyUsedError(error, body));
    this.errorMessage.set(this.registrationErrorMessage(error, body));
  }

  private registrationErrorMessage(error: HttpErrorResponse, body: ApiErrorResponse | undefined): string {
    if (this.isEmailAlreadyUsedError(error, body)) {
      return this.transloco.translate('page.home.registration.email-already-used');
    }

    if (error.status >= 500) {
      return this.transloco.translate('common.serverError', {status: error.status});
    }

    if (error.status === 0) {
      return this.transloco.translate('common.connectionError');
    }

    if (error.status >= 400) {
      return body?.message ?? this.transloco.translate('page.home.registration.submit-error-with-status', {
        status: error.status,
      });
    }

    return body?.message ?? this.transloco.translate('page.home.registration.submit-error');
  }

  private isEmailAlreadyUsedError(error: HttpErrorResponse, body: ApiErrorResponse | undefined): boolean {
    return body?.code === 'RESOURCE_ALREADY_EXISTS'
      || error.status === 409;
  }

  private availabilityMessage(reason: RegistrationAvailabilityDto.ReasonEnum | undefined): string {
    switch (reason) {
      case 'INSTITUTION_NOT_FOUND':
        return this.transloco.translate('page.home.registration.availability.institution-not-found');
      case 'INSTITUTION_INACTIVE':
        return this.transloco.translate('page.home.registration.availability.institution-inactive');
      case 'REGISTRATION_DISABLED':
        return this.transloco.translate('page.home.registration.availability.registration-disabled');
      case 'IP_NOT_ALLOWED':
        return this.transloco.translate('page.home.registration.availability.ip-not-allowed');
      case 'AVAILABLE':
        return this.transloco.translate('page.home.registration.availability.available');
      default:
        return this.transloco.translate('page.home.registration.availability.unavailable');
    }
  }

  private extractErrorMessage(error: HttpErrorResponse, fallback: string): string {
    return this.apiError(error)?.message ?? fallback;
  }

  private applyBackendFieldErrors(fieldErrors: Record<string, string>): void {
    this.fieldErrors.set(fieldErrors);

    for (const fieldName of Object.keys(fieldErrors)) {
      const control = this.registrationForm.get(fieldName);
      if (control) {
        control.setErrors({...(control.errors ?? {}), backend: true});
        control.markAsTouched();
      }
    }
  }

  private clearBackendFieldErrors(): void {
    const fieldErrors = this.fieldErrors();
    this.fieldErrors.set({});

    for (const fieldName of Object.keys(fieldErrors)) {
      const control = this.registrationForm.get(fieldName);
      if (!control?.errors?.['backend']) {
        continue;
      }

      const errors = {...control.errors};
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

  private parseInstitutionId(rawValue: string): number | null {
    const value = Number(rawValue);
    return Number.isInteger(value) && value > 0 ? value : null;
  }
}
