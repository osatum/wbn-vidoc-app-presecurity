import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { asapScheduler, filter, observeOn } from 'rxjs';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { DecisionValue } from '../../../core/services/journal-decision-annual';
import { availableDecisionValue, formatDecisionNumber } from './journal-decision-presentation';

const SOCIAL = {
  FACEBOOK: { name: 'Facebook', icon: 'ri-facebook-circle-fill' },
  INSTAGRAM: { name: 'Instagram', icon: 'ri-instagram-line' },
  LINKEDIN: { name: 'LinkedIn', icon: 'ri-linkedin-box-fill' },
  X: { name: 'X', icon: 'ri-twitter-x-line' },
  YOUTUBE: { name: 'YouTube', icon: 'ri-youtube-fill' },
} as const;
const LICENSES = {
  CC_BY: 'CC BY',
  CC_BY_SA: 'CC BY-SA',
  CC_BY_ND: 'CC BY-ND',
  CC_BY_NC: 'CC BY-NC',
  CC_BY_NC_SA: 'CC BY-NC-SA',
  CC_BY_NC_ND: 'CC BY-NC-ND',
} as const;
const CONDITIONS: Record<string, string> = {
  publication_date: 'date',
  article: 'article',
  language_version: 'language',
};

@Component({
  selector: 'app-journal-decision-standards',
  imports: [TranslocoPipe],
  templateUrl: './journal-decision-standards.html',
  styleUrl: './journal-decision-fields.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionStandards {
  readonly card = input.required<JournalDecisionView>();
  private readonly transloco = inject(TranslocoService);
  private readonly translationsLoaded = toSignal(
    this.transloco.events$.pipe(
      filter((event) => event.type === 'translationLoadSuccess'),
      observeOn(asapScheduler),
    ),
    { initialValue: null },
  );
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly data = computed(() =>
    this.card().annual?.surveyYear === this.card().surveyYear
      ? this.card().annual?.standards
      : undefined,
  );
  readonly declarationKeys = ['antiPlagiarism', 'publicCriteria', 'editorialSystem'] as const;
  readonly declarations = computed(() => {
    this.translationsLoaded();
    this.language();
    return this.declarationKeys.map((key) => ({
      key,
      text: this.declaration(this.data()?.[key], key === 'publicCriteria'),
    }));
  });
  readonly reviewing = computed(() => {
    this.translationsLoaded();
    this.language();
    const source = this.data()?.reviewing;
    return {
      models: (source?.models ?? [])
        .filter((model) => model.confirmed === true)
        .map((model) => {
          const value = availableDecisionValue(model.share);
          const share =
            value != null && Number.isFinite(value) && value > 0 && value <= 100
              ? `${formatDecisionNumber(value, this.language())}%`
              : this.t(model.share.status === 'MISSING' ? 'shareMissing' : 'shareInvalid');
          return { code: model.code, name: this.t('models.' + model.code), share };
        }),
      none: source?.none === true,
      invalid: source?.status === 'INVALID',
    };
  });
  readonly access = computed(() => {
    this.translationsLoaded();
    this.language();
    const source = this.data()?.access,
      conditions = this.data()?.accessConditions;
    const type = availableDecisionValue(source);
    const label = type
      ? this.t('access.' + type)
      : this.t(source?.status === 'INVALID' ? 'invalid' : 'noDeclaration');
    const selected = type === 'PAID_ARTICLES' ? availableDecisionValue(conditions) : undefined;
    return {
      label,
      conditions: [...new Set(selected ?? [])].map((value) =>
        CONDITIONS[value]
          ? this.t('conditions.' + CONDITIONS[value])
          : this.t('conditions.other', { value }),
      ),
      conditionNote:
        type === 'PAID_ARTICLES' && !selected?.length
          ? conditions?.status === 'INVALID'
            ? 'conditionsInvalid'
            : 'conditionsMissing'
          : undefined,
    };
  });
  readonly licence = computed(() => {
    this.translationsLoaded();
    this.language();
    const source = this.data()?.licence,
      value = availableDecisionValue(source);
    if (value == null) return this.t(source?.status === 'INVALID' ? 'invalid' : 'noDeclaration');
    return value === 'NONE'
      ? this.t('noCC')
      : value === 'AUTHOR_PREFERENCE'
        ? this.t('authorPreference')
        : LICENSES[value];
  });
  readonly social = computed(() => {
    this.translationsLoaded();
    this.language();
    const source = this.data()?.socialMedia,
      value = availableDecisionValue(source);
    const channels = [...new Set(value?.channels ?? [])].map((channel) => ({
      key: channel,
      ...SOCIAL[channel],
    }));
    const other = value?.other?.trim();
    return {
      channels,
      other,
      text: !value
        ? this.t(source?.status === 'INVALID' ? 'invalid' : 'noDeclaration')
        : !channels.length && !other
          ? this.t(
              value.used === false
                ? 'noSocial'
                : value.used === true
                  ? 'channelsMissing'
                  : 'noDeclaration',
            )
          : undefined,
    };
  });
  private t(key: string, params?: Record<string, unknown>): string {
    return this.transloco.translate('decisionCard.standards.' + key, params);
  }
  private declaration(
    source: DecisionValue<'INDICATED' | 'NONE' | 'NOT_INDICATED'> | undefined,
    publicCriteria: boolean,
  ): string {
    const value = availableDecisionValue(source);
    if (publicCriteria && value === 'INDICATED') return this.t('publicIndicated');
    if (publicCriteria && value === 'NOT_INDICATED') return this.t('publicNotIndicated');
    return this.t(
      value ? 'declaration.' + value : source?.status === 'INVALID' ? 'invalid' : 'noDeclaration',
    );
  }
}
