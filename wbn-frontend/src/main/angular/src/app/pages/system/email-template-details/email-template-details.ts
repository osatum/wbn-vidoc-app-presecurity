import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
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
  selector: 'app-email-template-details',
  standalone: true,
  imports: [CommonModule, RouterLink, AlertData, TranslocoPipe, LocalSpinner],
  templateUrl: './email-template-details.html',
  styleUrl: './email-template-details.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailTemplateDetails {
  private readonly route = inject(ActivatedRoute);
  private readonly emailTemplatesService = inject(EmailTemplatesService);

  readonly templateId = signal<number | null>(
    this.parseTemplateId(this.route.snapshot.paramMap.get('id')),
  );
  readonly template = signal<EmailTemplateDto | null>(null);
  readonly loadState = signal<LoadState>('loading');
  readonly isLoading = computed(() => this.loadState() === 'loading');
  readonly hasError = computed(() => this.loadState() === 'error');
  readonly editUrl = computed(() => {
    const id = this.templateId();
    return id === null
      ? '/system/admin/email-templates'
      : `/system/admin/email-templates/${id}/edit`;
  });

  constructor() {
    this.loadTemplate();
  }

  loadTemplate(): void {
    const id = this.templateId();
    if (id === null) {
      this.loadState.set('error');
      return;
    }

    this.loadState.set('loading');
    this.emailTemplatesService
      .findEmailTemplate(id, 'body', false, { transferCache: false })
      .subscribe({
        next: (template) => {
          this.template.set(template);
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

  private parseTemplateId(value: string | null): number | null {
    const id = Number(value);
    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
