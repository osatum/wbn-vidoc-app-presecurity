import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, forkJoin } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import {
  InstitutionService,
  InstitutionViewDto,
  UserCreateForm,
  UserMembershipDto,
  UserService,
  UserViewDto,
} from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import { ToastService } from '../../../core/services/toast-service';
import {
  SYSTEM_INSTITUTION_ROLE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
  type ApiErrorResponse,
  type SystemValidationErrorName,
  type SystemValidationMessages,
} from '../../shared/system-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type LoadState = {
  status: 'loading' | 'success' | 'error';
};
type UserCreateControlName = 'email' | 'firstName' | 'lastName' | 'orcid' | 'password' | 'role';
type InstitutionRole = UserCreateForm.RoleEnum;
type UserCreateMode =
  | 'edit'
  | 'alreadyAssignedRole'
  | 'confirmInstitutionAssignment'
  | 'confirmExistingUserAssignment';
type UserCreateStatus =
  | 'CREATED'
  | 'USER_EXISTS'
  | 'USER_ALREADY_ASSIGNED_SELECTED_ROLE'
  | 'USER_ASSIGNED_SELECTED_INSTITUTION_OTHER_ROLE'
  | 'USER_ASSIGNED_OTHER_INSTITUTION'
  | 'EXISTING_USER_ATTACHED';
type UserCreateResult = {
  status?: UserCreateStatus;
  user?: UserViewDto;
  membership?: UserMembershipDto;
};
type RoleOption = {
  value: InstitutionRole;
  labelKey: string;
};

const CREATE_MODE_CONTENT = {
  alreadyAssignedRole: {
    titleKey: 'page.system.user-create.already-assigned-role-title',
    messageKey: 'page.system.user-create.already-assigned-role-message',
  },
  confirmInstitutionAssignment: {
    titleKey: 'page.system.user-create.confirm-institution-assignment-title',
    messageKey: 'page.system.user-create.confirm-institution-assignment-message',
  },
  confirmExistingUserAssignment: {
    titleKey: 'page.system.user-create.confirm-existing-user-title',
    messageKey: 'page.system.user-create.confirm-existing-user-message',
  },
} satisfies Record<Exclude<UserCreateMode, 'edit'>, { titleKey: string; messageKey: string }>;

@Component({
  selector: 'app-user-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AlertData, TranslocoPipe, LocalSpinner],
  templateUrl: './user-create.html',
  styleUrl: './user-create.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserCreate {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly institutionService = inject(InstitutionService);
  private readonly userService = inject(UserService);
  private readonly transloco = inject(TranslocoService);
  private readonly toastService = inject(ToastService);

  readonly institutionId = signal<number | null>(
    this.parseInstitutionId(this.route.snapshot.paramMap.get('id')),
  );
  readonly institution = signal<InstitutionViewDto | null>(null);
  readonly state = signal<LoadState>({ status: 'loading' });
  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly hasError = computed(() => this.state().status === 'error');
  readonly isSaving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly fieldErrorEntries = computed(() =>
    Object.entries(this.fieldErrors()).filter(([path]) => !this.isKnownFieldError(path)),
  );
  readonly mode = signal<UserCreateMode>('edit');
  readonly existingUser = signal<UserViewDto | null>(null);
  readonly existingMemberships = computed(() => this.existingUser()?.memberships ?? []);
  readonly isEditMode = computed(() => this.mode() === 'edit');
  readonly isConfirmationMode = computed(() => this.mode() !== 'edit');
  readonly isRoleChangeMode = computed(() => this.mode() === 'alreadyAssignedRole');
  readonly modeTitleKey = computed(() => {
    const mode = this.mode();
    return mode === 'edit' ? null : CREATE_MODE_CONTENT[mode].titleKey;
  });
  readonly modeMessageKey = computed(() => {
    const mode = this.mode();
    return mode === 'edit' ? null : CREATE_MODE_CONTENT[mode].messageKey;
  });
  readonly visibleMemberships = computed(() => {
    const memberships = this.existingMemberships();
    const institutionId = this.institutionId();

    if (this.mode() === 'confirmInstitutionAssignment') {
      return memberships.filter((membership) => membership.institutionId === institutionId);
    }

    if (this.mode() === 'confirmExistingUserAssignment') {
      return memberships.filter((membership) => membership.institutionId !== institutionId);
    }

    return [];
  });
  readonly roleOptions = signal<ReadonlyArray<RoleOption>>([]);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(128)]],
    firstName: ['', [Validators.required, Validators.maxLength(128)]],
    lastName: ['', [Validators.required, Validators.maxLength(128)]],
    orcid: ['', [Validators.maxLength(19), Validators.pattern(/^\d{4}-\d{4}-\d{4}-\d{3}[0-9Xx]$/)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]],
    role: [UserCreateForm.RoleEnum.InstitutionUser, [Validators.required]],
  });
  private readonly validationMessages: SystemValidationMessages<UserCreateControlName> = {
    email: {
      required: 'page.system.user-create.validation.email.required',
      email: 'page.system.user-create.validation.email.email',
      maxlength: 'page.system.user-create.validation.email.maxlength',
    },
    firstName: {
      required: 'page.system.user-create.validation.first-name.required',
      maxlength: 'page.system.user-create.validation.first-name.maxlength',
    },
    lastName: {
      required: 'page.system.user-create.validation.last-name.required',
      maxlength: 'page.system.user-create.validation.last-name.maxlength',
    },
    orcid: {
      maxlength: 'page.system.user-create.validation.orcid.maxlength',
      pattern: 'page.system.user-create.validation.orcid.pattern',
    },
    password: {
      required: 'page.system.user-create.validation.password.required',
      minlength: 'page.system.user-create.validation.password.minlength',
      maxlength: 'page.system.user-create.validation.password.maxlength',
    },
    role: {
      required: 'page.system.user-create.validation.role.required',
    },
  };

  readonly institutionTypeLabelKey = computed(() => {
    const institutionType = this.institution()?.institutionType;

    return institutionType ? (SYSTEM_INSTITUTION_TYPE_LABELS[institutionType] ?? '-') : '-';
  });

  readonly assignedRoleLabelKey = computed(() => {
    return this.roleLabelKey(this.form.controls.role.value);
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.mode() === 'edit') {
        this.existingUser.set(null);
      }
      this.clearBackendFieldErrors();
    });

    effect(() => {
      this.syncFormControls(this.mode());
    });

    effect(() => {
      const id = this.institutionId();
      if (id === null) {
        this.state.set({ status: 'error' });
        return;
      }

      this.loadInitialData(id);
    });
  }

  save(): void {
    this.submit(false);
  }

  confirmAssignment(): void {
    this.submit(true);
  }

  saveRoleChange(): void {
    this.submit(true);
  }

  changeData(): void {
    this.mode.set('edit');
  }

  fieldError(path: string): string | null {
    return this.fieldErrors()[path] ?? null;
  }

  validationError(controlName: UserCreateControlName): string | null {
    const control = this.form.controls[controlName];
    return this.validationErrorForControl(
      control,
      this.validationMessages[controlName],
      controlName,
    );
  }

  roleLabelKey(role: InstitutionRole | undefined): string {
    return role ? (SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? '-') : '-';
  }

  institutionTypeLabelFor(type: UserMembershipDto.InstitutionTypeEnum | undefined): string {
    return type ? (SYSTEM_INSTITUTION_TYPE_LABELS[type] ?? '-') : '-';
  }

  private loadInitialData(id: number): void {
    this.state.set({ status: 'loading' });
    forkJoin({
      institution: this.institutionService.findInstitution(id),
      preparedForm: this.userService.prepareCreateUserForm(id),
    }).subscribe({
      next: ({ institution, preparedForm }) => {
        const roleOptions = this.toRoleOptions(preparedForm.roleOptions);
        const role =
          preparedForm.role ?? roleOptions[0]?.value ?? UserCreateForm.RoleEnum.InstitutionUser;

        this.mode.set('edit');
        this.existingUser.set(null);
        this.institution.set(institution);
        this.roleOptions.set(roleOptions);
        this.form.patchValue({
          email: preparedForm.email ?? '',
          firstName: preparedForm.firstName ?? '',
          lastName: preparedForm.lastName ?? '',
          orcid: preparedForm.orcid ?? '',
          password: preparedForm.password ?? '',
          role,
        });
        this.state.set({ status: 'success' });
      },
      error: () => this.state.set({ status: 'error' }),
    });
  }

  private submit(attachExistingUser: boolean): void {
    this.errorMessage.set(null);
    this.clearBackendFieldErrors();

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const institutionId = this.institutionId();
    if (institutionId === null) {
      this.errorMessage.set(
        this.transloco.translate('page.system.user-create.invalid-institution-id'),
      );
      return;
    }

    const value = this.form.getRawValue();
    this.isSaving.set(true);

    const form: UserCreateForm = {
      email: value.email.trim().toLowerCase(),
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      orcid: value.orcid.trim().toUpperCase() || undefined,
      password: value.password,
      role: value.role,
      attachExistingUser,
    };

    this.userService
      .createUser(institutionId, form)
      .pipe(finalize(() => this.isSaving.set(false)))
      .subscribe({
        next: (result) =>
          this.handleCreateResult(result as UserCreateResult, institutionId, attachExistingUser),
        error: (error: HttpErrorResponse) => this.handleError(error),
      });
  }

  private handleCreateResult(
    result: UserCreateResult,
    institutionId: number,
    attachExistingUser: boolean,
  ): void {
    switch (result.status) {
      case 'USER_ALREADY_ASSIGNED_SELECTED_ROLE':
        this.showExistingUserMode('alreadyAssignedRole', result.user);
        return;
      case 'USER_ASSIGNED_SELECTED_INSTITUTION_OTHER_ROLE':
        this.showExistingUserMode('confirmInstitutionAssignment', result.user);
        return;
      case 'USER_ASSIGNED_OTHER_INSTITUTION':
      case 'USER_EXISTS':
        this.showExistingUserMode('confirmExistingUserAssignment', result.user);
        return;
      case 'CREATED':
      case 'EXISTING_USER_ATTACHED':
        this.toastService.show(
          attachExistingUser
            ? this.transloco.translate('page.system.user-create.save-success-attach')
            : this.transloco.translate('page.system.user-create.save-success-create'),
          {
            classname: 'bg-success text-white',
            delay: 5000,
          },
        );
        void this.router.navigate(['/system/admin/institutions', institutionId]);
        return;
      default:
        this.errorMessage.set(this.transloco.translate('page.system.user-create.save-error'));
    }
  }

  private showExistingUserMode(
    mode: Exclude<UserCreateMode, 'edit'>,
    user: UserViewDto | undefined,
  ): void {
    this.existingUser.set(user ?? null);
    this.errorMessage.set(null);
    this.mode.set(mode);
  }

  private handleError(error: HttpErrorResponse): void {
    const body = error.error as ApiErrorResponse | undefined;
    this.applyBackendFieldErrors(body?.fieldErrors ?? {});
    this.errorMessage.set(
      body?.message ?? this.transloco.translate('page.system.user-create.save-error'),
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

  private syncFormControls(mode: UserCreateMode): void {
    const editableControls: UserCreateControlName[] =
      mode === 'edit'
        ? ['email', 'firstName', 'lastName', 'orcid', 'password', 'role']
        : mode === 'alreadyAssignedRole'
          ? ['role']
          : [];

    for (const controlName of [
      'email',
      'firstName',
      'lastName',
      'orcid',
      'password',
      'role',
    ] as UserCreateControlName[]) {
      const control = this.form.controls[controlName];
      if (editableControls.includes(controlName)) {
        control.enable({ emitEvent: false });
      } else {
        control.disable({ emitEvent: false });
      }
    }
  }

  private toRoleOptions(
    roles: ReadonlyArray<InstitutionRole> | undefined,
  ): ReadonlyArray<RoleOption> {
    return (roles ?? []).map((role) => ({
      value: role,
      labelKey: SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? role,
    }));
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
    return ['email', 'firstName', 'lastName', 'orcid', 'password', 'role'].includes(path);
  }

  private parseInstitutionId(value: string | null): number | null {
    const id = Number(value);
    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
