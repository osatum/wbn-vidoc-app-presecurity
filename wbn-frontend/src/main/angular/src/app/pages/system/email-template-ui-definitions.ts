import { EmailTemplateDto, EmailTemplateUpdateDto } from '../../core/backend';

export type EmailTemplateControlName = 'name' | 'language' | 'subject' | 'htmlBody' | 'textBody' | 'state';

export const EMAIL_TEMPLATE_TYPES = Object.values(EmailTemplateDto.EmailTypeEnum);

export const EMAIL_TEMPLATE_TYPE_LABEL_KEYS = {
  ACCOUNT_ACTIVATION_LINK: 'page.system.email-template.type.account-activation-link',
  ACCOUNT_ACTIVATED: 'page.system.email-template.type.account-activated',
  ACCOUNT_WAITING_FOR_CONFIRMATION: 'page.system.email-template.type.account-waiting-for-confirmation',
  ACTIVATION_LINK_REGENERATED: 'page.system.email-template.type.activation-link-regenerated',
  PASSWORD_RESET_LINK: 'page.system.email-template.type.password-reset-link',
  USER_DATA_DELETION_CONFIRMATION: 'page.system.email-template.type.user-data-deletion-confirmation',
} satisfies Record<EmailTemplateDto.EmailTypeEnum, string>;

export const EMAIL_TEMPLATE_PLACEHOLDERS = [
  '{{firstName}}',
  '{{lastName}}',
  '{{fullName}}',
  '{{email}}',
  '{{institutionName}}',
  '{{activationUrl}}',
  '{{passwordResetUrl}}',
  '{{userDataDeletionUrl}}',
] as const;

export const EMAIL_TEMPLATE_STATE_OPTIONS = [
  EmailTemplateUpdateDto.StateEnum.Active,
  EmailTemplateUpdateDto.StateEnum.Inactive,
] satisfies readonly EmailTemplateUpdateDto.StateEnum[];
