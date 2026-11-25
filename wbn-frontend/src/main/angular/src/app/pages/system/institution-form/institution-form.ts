import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import {
  InstitutionCreateForm,
  InstitutionEmailDomainCreateForm,
  InstitutionIpRuleCreateForm,
  InstitutionService,
  InstitutionViewDto,
} from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ToastService } from '../../../core/services/toast-service';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  type ApiErrorResponse,
  type SystemValidationErrorName,
  type SystemValidationMessages,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';
import { OperatingProfileSetting } from './operating-profile-setting';

type IpRuleType = InstitutionIpRuleCreateForm.RuleTypeEnum;
type IpAddressFamily = InstitutionIpRuleCreateForm.AddressFamilyEnum;
type EditorIndex = number | 'new' | null;
type IpRuleFormGroup = FormGroup<{
  addressFamily: FormControl<IpAddressFamily>;
  ruleType: FormControl<IpRuleType>;
  ruleRaw: FormControl<string>;
  priority: FormControl<number>;
}>;
type EmailDomainFormGroup = FormGroup<{
  domain: FormControl<string>;
}>;
type InstitutionFormLoadState = {
  status: 'success' | 'loading' | 'error';
  error: unknown | null;
};
type InstitutionControlName =
  | 'name'
  | 'institutionType'
  | 'authMode'
  | 'contactData'
  | 'description';
type IpRuleDraftControlName = 'ruleRaw' | 'priority';
type EmailDomainDraftControlName = 'domain';

@Component({
  selector: 'app-institution-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AlertData, LocalSpinner, TranslocoPipe, OperatingProfileSetting],
  templateUrl: './institution-form.html',
  styleUrl: './institution-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionForm {
  readonly institutionId = input<number | null>(null);

  private readonly institutionService = inject(InstitutionService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly toastService = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private loadedInstitutionId: number | null = null;

  readonly isEdit = computed(() => this.institutionId() !== null);
  readonly state = signal<InstitutionFormLoadState>({
    status: 'success',
    error: null,
  });
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');
  readonly loadErrorMessage = computed(() =>
    this.hasError() ? this.transloco.translate('page.system.institution-form.load-error') : null,
  );
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly fieldErrorEntries = computed(() =>
    Object.entries(this.fieldErrors()).filter(([path]) => !this.isKnownFieldError(path)),
  );
  readonly ipRuleEditorIndex = signal<EditorIndex>(null);
  readonly emailDomainEditorIndex = signal<EditorIndex>(null);
  readonly hasPendingElementDraft = computed(
    () => this.ipRuleEditorIndex() !== null || this.emailDomainEditorIndex() !== null,
  );

  readonly institutionTypes = [
    {
      value: InstitutionViewDto.InstitutionTypeEnum.SystemOperator,
      labelKey: 'page.system.institution-form.options.system-operator',
    },
    {
      value: InstitutionViewDto.InstitutionTypeEnum.ResearchUnit,
      labelKey: 'page.system.institution-form.options.research-unit',
    },
  ] satisfies ReadonlyArray<{ value: InstitutionViewDto.InstitutionTypeEnum; labelKey: string }>;

  readonly authModes = [
    {
      value: InstitutionViewDto.AuthModeEnum.AuthRequired,
      labelKey: 'page.system.institution-form.options.auth-required',
    },
    {
      value: InstitutionViewDto.AuthModeEnum.AuthNotRequired,
      labelKey: 'page.system.institution-form.options.auth-not-required',
    },
  ] satisfies ReadonlyArray<{ value: InstitutionViewDto.AuthModeEnum; labelKey: string }>;

  readonly ipRuleTypes = [
    { value: 'SINGLE_IP', labelKey: 'page.system.institution-form.options.single-ip' },
    { value: 'RANGE', labelKey: 'page.system.institution-form.options.range' },
    { value: 'CIDR', labelKey: 'page.system.institution-form.options.cidr' },
    { value: 'WILDCARD', labelKey: 'page.system.institution-form.options.wildcard' },
  ] satisfies ReadonlyArray<{ value: IpRuleType; labelKey: string }>;

  readonly ipAddressFamilies = [
    { value: 'IPV4', label: 'IPv4' },
    { value: 'IPV6', label: 'IPv6' },
  ] satisfies ReadonlyArray<{ value: IpAddressFamily; label: string }>;

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', [Validators.maxLength(4000)]],
    contactData: ['', [Validators.maxLength(4000)]],
    institutionType: [InstitutionViewDto.InstitutionTypeEnum.ResearchUnit, [Validators.required]],
    operatingProfile: this.fb.control<import('../../../core/backend').OperatingProfile | null>(null),
    authMode: [InstitutionViewDto.AuthModeEnum.AuthRequired, [Validators.required]],
    allowRegistration: [false],
    allowOtherIp: [false],
    allowOtherEmailDomain: [false],
    autoActivateNewAccounts: [false],
    institutionIpRules: this.fb.array<IpRuleFormGroup>([]),
    emailDomains: this.fb.array<EmailDomainFormGroup>([]),
  });
  readonly ipRuleDraft = this.createIpRuleGroup();
  readonly emailDomainDraft = this.createEmailDomainGroup();
  private readonly validationMessages: SystemValidationMessages<InstitutionControlName> = {
    name: {
      required: 'page.system.institution-form.validation.name.required',
      maxlength: 'page.system.institution-form.validation.name.maxlength',
    },
    institutionType: {
      required: 'page.system.institution-form.validation.institutionType.required',
    },
    authMode: {
      required: 'page.system.institution-form.validation.authMode.required',
    },
    contactData: {
      maxlength: 'page.system.institution-form.validation.contactData.maxlength',
    },
    description: {
      maxlength: 'page.system.institution-form.validation.description.maxlength',
    },
  };
  private readonly ipRuleDraftValidationMessages: SystemValidationMessages<IpRuleDraftControlName> =
    {
      ruleRaw: {
        required: 'page.system.institution-form.validation.ipRule.required',
        maxlength: 'page.system.institution-form.validation.ipRule.maxlength',
      },
      priority: {
        required: 'page.system.institution-form.validation.priority.required',
        min: 'page.system.institution-form.validation.priority.min',
      },
    };
  private readonly emailDomainDraftValidationMessages: SystemValidationMessages<EmailDomainDraftControlName> =
    {
      domain: {
        required: 'page.system.institution-form.validation.domain.required',
        pattern: 'page.system.institution-form.validation.domain.pattern',
        maxlength: 'page.system.institution-form.validation.domain.maxlength',
      },
    };

  constructor() {
    this.form.controls.institutionType.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) => {
      if (type !== InstitutionViewDto.InstitutionTypeEnum.ResearchUnit) this.form.controls.operatingProfile.setValue(null);
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.clearBackendFieldErrors();
    });

    effect(() => {
      const id = this.institutionId();
      if (id !== null && id !== this.loadedInstitutionId) {
        this.loadedInstitutionId = id;
        this.loadInstitution(id);
      }
    });
  }

  get institutionIpRules(): FormArray<IpRuleFormGroup> {
    return this.form.controls.institutionIpRules;
  }

  get emailDomains(): FormArray<EmailDomainFormGroup> {
    return this.form.controls.emailDomains;
  }

  addIpRule(): void {
    if (this.hasPendingElementDraft()) {
      this.markPendingDraftAsTouched();
      return;
    }

    this.ipRuleDraft.reset({ addressFamily: 'IPV4', ruleType: 'SINGLE_IP', ruleRaw: '', priority: 0 });
    this.ipRuleEditorIndex.set('new');
  }

  editIpRule(index: number): void {
    if (this.hasPendingElementDraft()) {
      this.markPendingDraftAsTouched();
      return;
    }

    this.ipRuleDraft.setValue(this.institutionIpRules.at(index).getRawValue());
    this.ipRuleEditorIndex.set(index);
  }

  confirmIpRule(): void {
    if (this.ipRuleDraft.invalid) {
      this.ipRuleDraft.markAllAsTouched();
      return;
    }

    const value = this.ipRuleDraft.getRawValue();
    const editorIndex = this.ipRuleEditorIndex();
    if (editorIndex === 'new') {
      this.institutionIpRules.push(this.createIpRuleGroup(value));
    } else if (typeof editorIndex === 'number') {
      this.institutionIpRules.at(editorIndex).setValue(value);
    }

    this.ipRuleEditorIndex.set(null);
  }

  removeIpRule(index: number): void {
    if (this.hasPendingElementDraft()) {
      this.markPendingDraftAsTouched();
      return;
    }

    this.institutionIpRules.removeAt(index);
  }

  cancelIpRuleEdit(): void {
    this.ipRuleEditorIndex.set(null);
  }

  availableIpRuleTypes(): ReadonlyArray<{ value: IpRuleType; labelKey: string }> {
    return this.ipRuleDraft.controls.addressFamily.value === 'IPV6'
      ? this.ipRuleTypes.filter(({ value }) => value === 'SINGLE_IP' || value === 'CIDR')
      : this.ipRuleTypes;
  }

  onIpRuleAddressFamilyChange(): void {
    if (
      this.ipRuleDraft.controls.addressFamily.value === 'IPV6' &&
      (this.ipRuleDraft.controls.ruleType.value === 'RANGE' ||
        this.ipRuleDraft.controls.ruleType.value === 'WILDCARD')
    ) {
      this.ipRuleDraft.controls.ruleType.setValue('SINGLE_IP');
    }
    this.ipRuleDraft.updateValueAndValidity();
  }

  addEmailDomain(): void {
    if (this.hasPendingElementDraft()) {
      this.markPendingDraftAsTouched();
      return;
    }

    this.emailDomainDraft.reset({ domain: '' });
    this.emailDomainEditorIndex.set('new');
  }

  editEmailDomain(index: number): void {
    if (this.hasPendingElementDraft()) {
      this.markPendingDraftAsTouched();
      return;
    }

    this.emailDomainDraft.setValue(this.emailDomains.at(index).getRawValue());
    this.emailDomainEditorIndex.set(index);
  }

  confirmEmailDomain(): void {
    if (this.emailDomainDraft.invalid) {
      this.emailDomainDraft.markAllAsTouched();
      return;
    }

    const value = this.emailDomainDraft.getRawValue();
    const editorIndex = this.emailDomainEditorIndex();
    if (editorIndex === 'new') {
      this.emailDomains.push(this.createEmailDomainGroup(value));
    } else if (typeof editorIndex === 'number') {
      this.emailDomains.at(editorIndex).setValue(value);
    }

    this.emailDomainEditorIndex.set(null);
  }

  removeEmailDomain(index: number): void {
    if (this.hasPendingElementDraft()) {
      this.markPendingDraftAsTouched();
      return;
    }

    this.emailDomains.removeAt(index);
  }

  cancelEmailDomainEdit(): void {
    this.emailDomainEditorIndex.set(null);
  }

  save(): void {
    this.errorMessage.set(null);
    this.clearBackendFieldErrors();

    if (this.hasPendingElementDraft()) {
      this.markPendingDraftAsTouched();
      this.errorMessage.set(this.transloco.translate('page.system.institution-form.pending-draft-error'));
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const form = this.toCreateForm();
    const id = this.institutionId();
    const request =
      id === null
        ? this.institutionService.createInstitution(form)
        : this.institutionService.updateInstitution(id, form);

    this.isSaving.set(true);
    request.pipe(finalize(() => this.isSaving.set(false))).subscribe({
      next: (institution) => this.handleSaveSuccess(institution, id),
      error: (error: HttpErrorResponse) => this.handleError(error),
    });
  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  validationError(controlName: InstitutionControlName): string | null {
    const control = this.form.controls[controlName];
    return this.validationErrorForControl(
      control,
      this.validationMessages[controlName],
      controlName,
    );
  }

  ipRuleDraftValidationError(controlName: IpRuleDraftControlName): string | null {
    const control = this.ipRuleDraft.controls[controlName];
    return this.validationErrorForControl(control, this.ipRuleDraftValidationMessages[controlName]);
  }

  ipRuleDraftFormatValidationError(): string | null {
    if (!this.ipRuleDraft.touched || !this.ipRuleDraft.hasError('ipRuleFormat')) {
      return null;
    }

    return this.transloco.translate('page.system.institution-form.validation.ipRule.format');
  }

  emailDomainDraftValidationError(controlName: EmailDomainDraftControlName): string | null {
    const control = this.emailDomainDraft.controls[controlName];
    return this.validationErrorForControl(
      control,
      this.emailDomainDraftValidationMessages[controlName],
    );
  }

  private loadInstitution(id: number): void {
    this.state.set({
      status: 'loading',
      error: null,
    });
    this.errorMessage.set(null);
    this.fieldErrors.set({});

    this.institutionService.findInstitution(id).subscribe({
      next: (institution) => {
        this.patchForm(institution);
        this.state.set({
          status: 'success',
          error: null,
        });
      },
      error: (error: unknown) =>
        this.state.set({
          status: 'error',
          error,
        }),
    });
  }

  private patchForm(institution: InstitutionViewDto): void {
    this.form.patchValue({
      name: institution.name ?? '',
      description: institution.description ?? '',
      contactData: institution.contactData ?? '',
      institutionType:
        institution.institutionType ?? InstitutionViewDto.InstitutionTypeEnum.ResearchUnit,
      authMode: institution.authMode ?? InstitutionViewDto.AuthModeEnum.AuthRequired,
      operatingProfile: institution.operatingProfile ?? null,
      allowRegistration: institution.allowRegistration ?? false,
      allowOtherIp: institution.allowOtherIp ?? false,
      allowOtherEmailDomain: institution.allowOtherEmailDomain ?? false,
      autoActivateNewAccounts: institution.autoActivateNewAccounts ?? false,
    });

    this.institutionIpRules.clear();
    for (const rule of institution.institutionIpRules ?? []) {
      this.institutionIpRules.push(
        this.createIpRuleGroup({
          addressFamily: rule.addressFamily ?? this.inferIpAddressFamily(rule.ruleRaw ?? rule.displayValue ?? ''),
          ruleType: rule.ruleType ?? 'SINGLE_IP',
          ruleRaw: rule.ruleRaw ?? rule.displayValue ?? '',
          priority: rule.priority ?? 0,
        }),
      );
    }

    this.emailDomains.clear();
    for (const domain of institution.emailDomains ?? []) {
      this.emailDomains.push(this.createEmailDomainGroup({ domain: domain.domain ?? '' }));
    }

    this.ipRuleEditorIndex.set(null);
    this.emailDomainEditorIndex.set(null);
  }

  private toCreateForm(): InstitutionCreateForm {
    const value = this.form.getRawValue();

    return {
      name: value.name.trim(),
      description: value.description.trim() || undefined,
      contactData: value.contactData.trim() || undefined,
      institutionType: value.institutionType,
      operatingProfile: value.institutionType === InstitutionViewDto.InstitutionTypeEnum.ResearchUnit ? value.operatingProfile : null,
      authMode: value.authMode,
      allowRegistration: value.allowRegistration,
      allowOtherIp: value.allowOtherIp,
      allowOtherEmailDomain: value.allowOtherEmailDomain,
      autoActivateNewAccounts: value.autoActivateNewAccounts,
      institutionIpRules: value.institutionIpRules.map((rule) => ({
        addressFamily: rule.addressFamily,
        ruleType: rule.ruleType,
        ruleRaw: rule.ruleRaw.trim(),
        priority: rule.priority,
      })),
      emailDomains: value.emailDomains.map((domain) => ({
        domain: domain.domain.trim().toLowerCase(),
      })),
    };
  }

  private handleError(error: HttpErrorResponse): void {
    const body = error.error as ApiErrorResponse | undefined;
    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.errorMessage.set(body?.message ?? this.transloco.translate('page.system.institution-form.save-error'));
  }

  private handleSaveSuccess(
    institution: InstitutionViewDto,
    editedInstitutionId: number | null,
  ): void {
    const institutionId = institution.id ?? editedInstitutionId;
    const message =
      editedInstitutionId === null
        ? this.transloco.translate('page.system.institution-form.save-success-create')
        : this.transloco.translate('page.system.institution-form.save-success-update');

    this.toastService.show(message, {
      classname: 'bg-success text-white',
      delay: 5000,
    });

    if (institutionId !== null && institutionId !== undefined) {
      void this.router.navigate(['/system/admin/institutions', institutionId]);
      return;
    }

    void this.router.navigate(['/system/admin/institutions']);
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
    backendPath?: string,
  ): string | null {
    const errors = control.errors;

    if (!control.touched || errors === null) {
      return null;
    }

    if (errors['backend'] && backendPath) {
      return this.fieldError(backendPath);
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
    return ['name', 'institutionType', 'authMode', 'contactData', 'description'].includes(path);
  }

  private markPendingDraftAsTouched(): void {
    if (this.ipRuleEditorIndex() !== null) {
      this.ipRuleDraft.markAllAsTouched();
    }

    if (this.emailDomainEditorIndex() !== null) {
      this.emailDomainDraft.markAllAsTouched();
    }
  }

  private createIpRuleGroup(value?: Partial<InstitutionIpRuleCreateForm>): IpRuleFormGroup {
    const ruleType: IpRuleType = value?.ruleType ?? 'SINGLE_IP';
    const addressFamily: IpAddressFamily = value?.addressFamily ?? 'IPV4';

    return this.fb.nonNullable.group(
      {
        addressFamily: [addressFamily, [Validators.required]],
        ruleType: [ruleType, [Validators.required]],
        ruleRaw: [value?.ruleRaw ?? '', [Validators.required, Validators.maxLength(255)]],
        priority: [value?.priority ?? 0, [Validators.required, Validators.min(0)]],
      },
      { validators: [(control) => this.ipRuleFormatValidator(control)] },
    );
  }

  private inferIpAddressFamily(value: string): IpAddressFamily {
    return value.includes(':') ? 'IPV6' : 'IPV4';
  }

  private createEmailDomainGroup(
    value?: Partial<InstitutionEmailDomainCreateForm>,
  ): EmailDomainFormGroup {
    return this.fb.nonNullable.group({
      domain: [
        value?.domain ?? '',
        [
          Validators.required,
          Validators.maxLength(255),
          Validators.pattern(
            /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/,
          ),
        ],
      ],
    });
  }

  private ipRuleFormatValidator(control: AbstractControl): ValidationErrors | null {
    const group = control as IpRuleFormGroup;
    const addressFamily = group.controls.addressFamily.value;
    const ruleType = group.controls.ruleType.value;
    const ruleRaw = group.controls.ruleRaw.value.trim();

    if (!ruleRaw) {
      return null;
    }

    if (addressFamily === 'IPV6') {
      if (ruleType === 'RANGE' || ruleType === 'WILDCARD') {
        return { ipRuleFormat: true };
      }
      if (ruleType === 'SINGLE_IP') {
        return this.isValidIpv6(ruleRaw) ? null : { ipRuleFormat: true };
      }
      const [address, prefix, ...rest] = ruleRaw.split('/');
      return rest.length === 0 && this.isValidIpv6(address) && /^(0|[1-9]\d?|1[01]\d|12[0-8])$/.test(prefix)
        ? null
        : { ipRuleFormat: true };
    }

    const ipv4Octet = '(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)';
    const ipv4 = `${ipv4Octet}\\.${ipv4Octet}\\.${ipv4Octet}\\.${ipv4Octet}`;
    const patterns: Record<IpRuleType, RegExp> = {
      SINGLE_IP: new RegExp(`^${ipv4}$`),
      RANGE: new RegExp(`^${ipv4}\\s*-\\s*${ipv4}$`),
      CIDR: new RegExp(`^${ipv4}/(3[0-2]|[12]?\\d)$`),
      WILDCARD: new RegExp(
        `^(${ipv4Octet}|\\*)\\.(${ipv4Octet}|\\*)\\.(${ipv4Octet}|\\*)\\.(${ipv4Octet}|\\*)$`,
      ),
    };

    return patterns[ruleType].test(ruleRaw) ? null : { ipRuleFormat: true };
  }

  private isValidIpv6(value: string): boolean {
    if (!/^[0-9a-fA-F:]+$/.test(value) || !value.includes(':') || value.includes(':::')) {
      return false;
    }

    const doubleColonParts = value.split('::');
    if (doubleColonParts.length > 2) {
      return false;
    }

    const groups = doubleColonParts.flatMap((part) => (part ? part.split(':') : []));
    if (groups.some((group) => !/^[0-9a-fA-F]{1,4}$/.test(group))) {
      return false;
    }

    return doubleColonParts.length === 2 ? groups.length < 8 : groups.length === 8;
  }
}
