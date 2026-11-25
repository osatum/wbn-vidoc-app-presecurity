import type { ApiErrorResponse } from '../../core/backend';

export type { ApiErrorResponse };

export type SystemEntityStateValue =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'UNVERIFIED'
  | 'DELETED'
  | 'VERIFIED';

export const SYSTEM_ENTITY_STATE_LABELS = {
  ACTIVE: 'page.system.common.entity-state.active',
  INACTIVE: 'page.system.common.entity-state.inactive',
  UNVERIFIED: 'page.system.common.entity-state.inactive',
  DELETED: 'page.system.common.entity-state.inactive',
  VERIFIED: 'page.system.common.entity-state.inactive',
} satisfies Record<SystemEntityStateValue, string>;

export const SYSTEM_INSTITUTION_ENTITY_STATE_LABELS = {
  ACTIVE: 'page.system.institution-list.entity-state.active',
  INACTIVE: 'page.system.institution-list.entity-state.inactive',
  UNVERIFIED: 'page.system.institution-list.entity-state.inactive',
  DELETED: 'page.system.institution-list.entity-state.inactive',
  VERIFIED: 'page.system.institution-list.entity-state.inactive',
} satisfies Record<SystemEntityStateValue, string>;

export const SYSTEM_ENTITY_STATE_BADGE_CLASSES = {
  ACTIVE: 'badge bg-success-subtle text-success',
  INACTIVE: 'badge bg-secondary-subtle text-secondary',
  UNVERIFIED: 'badge bg-secondary-subtle text-secondary',
  DELETED: 'badge bg-secondary-subtle text-secondary',
  VERIFIED: 'badge bg-secondary-subtle text-secondary',
} satisfies Record<SystemEntityStateValue, string>;

export type SystemInstitutionTypeValue = 'SYSTEM_OPERATOR' | 'RESEARCH_UNIT';

export const SYSTEM_INSTITUTION_TYPE_LABELS = {
  SYSTEM_OPERATOR: 'page.system.institution-list.institution-type.system-operator',
  RESEARCH_UNIT: 'page.system.institution-list.institution-type.research-unit',
} satisfies Record<SystemInstitutionTypeValue, string>;

export type SystemInstitutionRoleValue =
  | 'INSTITUTION_ADMIN'
  | 'INSTITUTION_USER'
  | 'INSTITUTION_VIEWER'
  | 'INSTITUTION_PENDING'
  | 'INSTITUTION_TO_CONFIRM';

export const SYSTEM_INSTITUTION_ROLE_LABELS = {
  INSTITUTION_ADMIN: 'page.system.login-session-list.institution-role.admin',
  INSTITUTION_USER: 'page.system.login-session-list.institution-role.user',
  INSTITUTION_VIEWER: 'page.system.login-session-list.institution-role.viewer',
  INSTITUTION_PENDING: 'page.system.login-session-list.institution-role.pending',
  INSTITUTION_TO_CONFIRM: 'page.system.login-session-list.institution-role.to-confirm',
} satisfies Record<SystemInstitutionRoleValue, string>;

export type SystemValidationErrorName =
  | 'required'
  | 'email'
  | 'maxlength'
  | 'minlength'
  | 'min'
  | 'pattern'
  | 'ipRuleFormat'
  | 'passwordMismatch'
  | 'backend';

export type SystemValidationMessages<TControlName extends string> = Record<
  TControlName,
  Partial<Record<SystemValidationErrorName, string>>
>;
