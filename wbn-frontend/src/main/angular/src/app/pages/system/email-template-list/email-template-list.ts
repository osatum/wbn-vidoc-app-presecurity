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
import { EMAIL_TEMPLATE_TYPE_LABEL_KEYS } from '../email-template-ui-definitions';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type LoadState = 'loading' | 'success' | 'error';

@Component({
  selector: 'app-email-template-list',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertData, TranslocoPipe, LocalSpinner],
  templateUrl: './email-template-list.html',
  styleUrl: './email-template-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailTemplateList {
  private readonly emailTemplatesService = inject(EmailTemplatesService);

  readonly templates = signal<EmailTemplateDto[]>([]);
  readonly loadState = signal<LoadState>('loading');
  readonly isLoading = computed(() => this.loadState() === 'loading');
  readonly hasError = computed(() => this.loadState() === 'error');

  constructor() {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loadState.set('loading');

    this.emailTemplatesService
      .findEmailTemplates('body', false, { transferCache: false })
      .subscribe({
        next: (templates) => {
          this.templates.set(this.sortTemplates(templates));
          this.loadState.set('success');
        },
        error: () => this.loadState.set('error'),
      });
  }

  emailTypeLabelKey(value: EmailTemplateDto.EmailTypeEnum | undefined): string {
    return value
      ? (EMAIL_TEMPLATE_TYPE_LABEL_KEYS[value] ?? 'page.system.email-template-list.unknown-type')
      : 'page.system.email-template-list.unknown-type';
  }

  stateLabelKey(state: EmailTemplateDto.StateEnum | undefined): string {
    return state ? (SYSTEM_ENTITY_STATE_LABELS[state] ?? '-') : '-';
  }

  stateBadgeClass(state: EmailTemplateDto.StateEnum | undefined): string {
    return state
      ? (SYSTEM_ENTITY_STATE_BADGE_CLASSES[state] ?? 'badge bg-light text-muted')
      : 'badge bg-light text-muted';
  }

  trackByTemplate(_: number, template: EmailTemplateDto): number | string {
    return template.id ?? `${template.emailType ?? ''}-${template.language ?? ''}`;
  }

  private sortTemplates(templates: EmailTemplateDto[]): EmailTemplateDto[] {
    return [...templates].sort((left, right) =>
      `${left.emailType ?? ''}-${left.language ?? ''}`.localeCompare(
        `${right.emailType ?? ''}-${right.language ?? ''}`,
      ),
    );
  }
}
