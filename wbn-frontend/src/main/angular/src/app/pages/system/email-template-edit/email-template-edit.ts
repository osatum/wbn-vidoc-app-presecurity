import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  EmailTemplateDto,
  EmailTemplatesService,
  EmailTemplateUpdateDto,
} from '../../../core/backend';
import { ToastService } from '../../../core/services/toast-service';
import { AlertData } from '../../../shared/alert-data/alert-data';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_ENTITY_STATE_LABELS,
  type ApiErrorResponse,
  type SystemValidationErrorName,
  type SystemValidationMessages,
} from '../../shared/system-ui-definitions';
import {
  EMAIL_TEMPLATE_PLACEHOLDERS,
  EMAIL_TEMPLATE_STATE_OPTIONS,
  EMAIL_TEMPLATE_TYPE_LABEL_KEYS,
  type EmailTemplateControlName,
} from '../email-template-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type LoadState = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-email-template-edit',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AlertData, TranslocoPipe, LocalSpinner],
  templateUrl: './email-template-edit.html',
  styleUrl: './email-template-edit.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailTemplateEdit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly emailTemplatesService = inject(EmailTemplatesService);
  private readonly toastService = inject(ToastService);
  private readonly transloco = inject(TranslocoService);

  readonly templateId = signal<number | null>(
    this.parseTemplateId(this.route.snapshot.paramMap.get('id')),
  );
  readonly template = signal<EmailTemplateDto | null>(null);
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
    const id = this.templateId();
    return id === null ? '/system/admin/email-templates' : `/system/admin/email-templates/${id}`;
  });
  readonly stateOptions = EMAIL_TEMPLATE_STATE_OPTIONS;
  readonly placeholders = EMAIL_TEMPLATE_PLACEHOLDERS;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(128)]],
    language: ['', [Validators.required, Validators.maxLength(16)]],
    subject: ['', [Validators.required, Validators.maxLength(256)]],
    htmlBody: ['', [Validators.required]],
    textBody: [''],
    state: [EmailTemplateUpdateDto.StateEnum.Active, [Validators.required]],
  });

  private readonly validationMessages: SystemValidationMessages<EmailTemplateControlName> = {
    name: {
      required: 'page.system.email-template-list.validation.name.required',
      maxlength: 'page.system.email-template-list.validation.name.maxlength',
    },
    language: {
      required: 'page.system.email-template-list.validation.language.required',
      maxlength: 'page.system.email-template-list.validation.language.maxlength',
    },
    subject: {
      required: 'page.system.email-template-list.validation.subject.required',
      maxlength: 'page.system.email-template-list.validation.subject.maxlength',
    },
    htmlBody: {
      required: 'page.system.email-template-list.validation.html-body.required',
    },
    textBody: {},
    state: {
      required: 'page.system.email-template-list.validation.state.required',
    },
  };

  constructor() {
    this.form.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.clearBackendFieldErrors());
    this.loadTemplate();
  }

  loadTemplate(): void {
    const id = this.templateId();
    if (id === null) {
      this.loadState.set('error');
      return;
    }

    this.loadState.set('loading');
    this.errorMessage.set(null);

    this.emailTemplatesService
      .findEmailTemplate(id, 'body', false, { transferCache: false })
      .subscribe({
        next: (template) => {
          this.template.set(template);
          this.patchForm(template);
          this.loadState.set('success');
        },
        error: () => this.loadState.set('error'),
      });
  }

  save(): void {
    this.errorMessage.set(null);
    this.clearBackendFieldErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const id = this.templateId();
    if (id === null) {
      this.errorMessage.set(
        this.transloco.translate('page.system.email-template-list.no-selection'),
      );
      return;
    }

    const value = this.form.getRawValue();
    const payload: EmailTemplateUpdateDto = {
      name: value.name.trim(),
      language: value.language.trim().toLowerCase(),
      subject: value.subject.trim(),
      htmlBody: value.htmlBody,
      textBody: value.textBody.trim() || undefined,
      state: value.state,
    };

    this.isSaving.set(true);
    this.emailTemplatesService
      .updateEmailTemplate(id, payload, 'body', false, { transferCache: false })
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (template) => this.handleSaveSuccess(template),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  emailTypeLabelKey(value: EmailTemplateDto.EmailTypeEnum | undefined): string {
    return value
      ? (EMAIL_TEMPLATE_TYPE_LABEL_KEYS[value] ?? 'page.system.email-template-list.unknown-type')
      : 'page.system.email-template-list.unknown-type';
  }

  stateLabelKey(
    state: EmailTemplateDto.StateEnum | EmailTemplateUpdateDto.StateEnum | undefined,
  ): string {
    return state ? (SYSTEM_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: EmailTemplateDto.StateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  validationError(controlName: EmailTemplateControlName): string | null {
    const control = this.form.controls[controlName];
    return this.validationErrorForControl(
      control,
      this.validationMessages[controlName],
      controlName,
    );
  }

  resetForm(): void {
    const template = this.template();
    if (template) {
      this.patchForm(template);
    }
  }

  private patchForm(template: EmailTemplateDto): void {
    this.clearBackendFieldErrors();
    this.form.reset({
      name: template.name ?? '',
      language: template.language ?? '',
      subject: template.subject ?? '',
      htmlBody: template.htmlBody ?? '',
      textBody: template.textBody ?? '',
      state:
        template.state === EmailTemplateDto.StateEnum.Inactive
          ? EmailTemplateUpdateDto.StateEnum.Inactive
          : EmailTemplateUpdateDto.StateEnum.Active,
    });
  }

  private handleSaveSuccess(template: EmailTemplateDto): void {
    this.toastService.show(
      this.transloco.translate('page.system.email-template-list.save-success'),
      {
        classname: 'bg-success text-white',
        delay: 5000,
      },
    );

    const id = template.id ?? this.templateId();
    if (id !== null && id !== undefined) {
      void this.router.navigate(['/system/admin/email-templates', id]);
      return;
    }

    void this.router.navigate(['/system/admin/email-templates']);
  }

  private handleError(error: HttpErrorResponse): void {
    const body = error.error as ApiErrorResponse | undefined;
    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.errorMessage.set(
      body?.message ?? this.transloco.translate('page.system.email-template-list.save-error'),
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
    backendPath: string,
  ): string | null {
    const errors = control.errors;

    if (!control.touched || errors === null) {
      return null;
    }

    if (errors['backend']) {
      return this.fieldErrors()[backendPath] ?? null;
    }

    for (const errorName of Object.keys(errors) as SystemValidationErrorName[]) {
      const message = messages[errorName];
      if (message) {
        return this.transloco.translate(message);
      }
    }

    return null;
  }

  private isKnownFieldError(path: string): boolean {
    return ['name', 'language', 'subject', 'htmlBody', 'textBody', 'state'].includes(path);
  }

  private parseTemplateId(value: string | null): number | null {
    const id = Number(value);
    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
