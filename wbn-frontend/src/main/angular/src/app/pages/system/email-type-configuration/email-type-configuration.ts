import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { EmailTemplateDto, EmailTemplatesService } from '../../../core/backend';
import { AlertData } from '../../../shared/alert-data/alert-data';
import {
  SYSTEM_ENTITY_STATE_BADGE_CLASSES,
  SYSTEM_ENTITY_STATE_LABELS,
} from '../../shared/system-ui-definitions';
import {
  EMAIL_TEMPLATE_TYPE_LABEL_KEYS,
  EMAIL_TEMPLATE_TYPES,
} from '../email-template-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type LoadState = 'loading' | 'success' | 'error';
type ConfigurationStatus = 'configured' | 'inactive' | 'missing';

type EmailTypeConfigurationRow = {
  emailType: EmailTemplateDto.EmailTypeEnum;
  templates: EmailTemplateDto[];
  activeTemplate?: EmailTemplateDto;
};

@Component({
  selector: 'app-email-type-configuration',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertData, TranslocoPipe, LocalSpinner],
  templateUrl: './email-type-configuration.html',
  styleUrl: './email-type-configuration.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailTypeConfiguration {
  private readonly emailTemplatesService = inject(EmailTemplatesService);

  readonly templates = signal<EmailTemplateDto[]>([]);
  readonly loadState = signal<LoadState>('loading');
  readonly isLoading = computed(() => this.loadState() === 'loading');
  readonly hasError = computed(() => this.loadState() === 'error');
  readonly rows = computed<EmailTypeConfigurationRow[]>(() =>
    EMAIL_TEMPLATE_TYPES.map((emailType) => {
      const templates = this.sortTemplates(
        this.templates().filter((template) => template.emailType === emailType),
      );
      const activeTemplate = templates.find(
        (template) => template.state === EmailTemplateDto.StateEnum.Active,
      );
      return {
        emailType,
        templates,
        activeTemplate,
      };
    }),
  );

  constructor() {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loadState.set('loading');

    this.emailTemplatesService
      .findEmailTemplates('body', false, { transferCache: false })
      .subscribe({
        next: (templates) => {
          this.templates.set(templates);
          this.loadState.set('success');
        },
        error: () => this.loadState.set('error'),
      });
  }

  emailTypeLabelKey(value: EmailTemplateDto.EmailTypeEnum): string {
    return EMAIL_TEMPLATE_TYPE_LABEL_KEYS[value] ?? 'page.system.email-template-list.unknown-type';
  }

  status(row: EmailTypeConfigurationRow): ConfigurationStatus {
    if (row.activeTemplate) {
      return 'configured';
    }

    return row.templates.length > 0 ? 'inactive' : 'missing';
  }

  statusLabelKey(row: EmailTypeConfigurationRow): string {
    switch (this.status(row)) {
      case 'configured':
        return 'page.system.email-type-configuration.status.configured';
      case 'inactive':
        return 'page.system.email-type-configuration.status.inactive';
      default:
        return 'page.system.email-type-configuration.status.missing';
    }
  }

  statusBadgeClass(row: EmailTypeConfigurationRow): string {
    switch (this.status(row)) {
      case 'configured':
        return 'badge bg-success-subtle text-success';
      case 'inactive':
        return 'badge bg-warning-subtle text-warning';
      default:
        return 'badge bg-danger-subtle text-danger';
    }
  }

  stateLabelKey(state: EmailTemplateDto.StateEnum | undefined): string {
    return state ? (SYSTEM_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: EmailTemplateDto.StateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  languages(row: EmailTypeConfigurationRow): string {
    const languages = row.templates
      .map((template) => template.language)
      .filter((language): language is string => Boolean(language))
      .sort((left, right) => left.localeCompare(right));

    return languages.length > 0 ? languages.join(', ') : '-';
  }

  trackByEmailType(_: number, row: EmailTypeConfigurationRow): EmailTemplateDto.EmailTypeEnum {
    return row.emailType;
  }

  private sortTemplates(templates: EmailTemplateDto[]): EmailTemplateDto[] {
    return [...templates].sort((left, right) =>
      `${left.language ?? ''}-${left.name ?? ''}`.localeCompare(
        `${right.language ?? ''}-${right.name ?? ''}`,
      ),
    );
  }
}
