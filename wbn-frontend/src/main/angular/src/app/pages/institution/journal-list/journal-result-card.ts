import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { JournalListView } from '../../../core/backend';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { JournalIssuesLinksService } from '../../../core/services/journal-issues-links.service';
import { SafeImageComponent } from '../../../shared/safe-image/safe-image.component';
import { JournalFees } from './journal-fees';

@Component({
  selector: 'app-journal-result-card',
  standalone: true,
  imports: [RouterLink, TranslocoPipe, NgbTooltip, SafeImageComponent, JournalFees],
  templateUrl: './journal-result-card.html',
  styleUrl: './journal-result-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalResultCard {
  readonly journal = input.required<JournalListView>();
  private readonly issuesLinks = inject(JournalIssuesLinksService);
  readonly issuesUrl = computed(() => this.issuesLinks.url(this.journal().id));
  readonly decisionCardAvailable = input(false);
  readonly detailsRoute = computed(() =>
    this.decisionCardAvailable()
      ? ['/institution/user/journals', this.journal().id, 'decision']
      : ['/institution/user/journals', this.journal().id],
  );
  readonly surveyYear = input<number>();
  readonly icvYear = input<number>();
  readonly previousIcvYear = input<number>();
  readonly priority = input(false);
  readonly navigationState = input<{ journalListOrigin: string }>();
  readonly detailsState = computed(() => ({
    ...this.navigationState(),
    surveyYear: this.surveyYear(),
  }));
  readonly detailsClick = output<MouseEvent>();
  readonly issuesClick = output<MouseEvent>();
  private readonly transloco = inject(TranslocoService);
  private readonly names = inject(JournalNameService);
  private readonly activeLanguage = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly country = computed(() => {
    this.activeLanguage();
    const country = this.journal().country;
    return country ? this.names.getCountryName(country) || country.code : undefined;
  });
  readonly languages = computed(() => {
    this.activeLanguage();
    return (this.journal().languages ?? []).map(
      (language) => this.names.getLanguageName(language) || language.code || '',
    );
  });
  readonly publishers = computed(() => {
    const journal = this.journal();
    return journal.publishers?.length
      ? journal.publishers
      : journal.publisher
        ? [journal.publisher]
        : [];
  });
  readonly icvRatings = computed(() => {
    const journal = this.journal();
    const formatter = new Intl.NumberFormat(this.activeLanguage(), {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    return [
      {
        period: 'current',
        year: journal.currentIcvYear ?? this.icvYear(),
        status: journal.currentStatus ?? JournalListView.CurrentStatusEnum.NotEvaluated,
        points: journal.currentIcvPoints,
      },
      {
        period: 'previous',
        year: journal.previousIcvYear ?? this.previousIcvYear(),
        status: journal.previousStatus ?? JournalListView.PreviousStatusEnum.NotEvaluated,
        points: journal.previousIcvPoints,
      },
    ].map((rating) => ({
      ...rating,
      points: rating.points == null ? '–' : formatter.format(rating.points),
    }));
  });
}
