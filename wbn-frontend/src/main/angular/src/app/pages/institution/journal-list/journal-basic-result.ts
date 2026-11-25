import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { JournalListView } from '../../../core/backend';
import { JournalNameService } from '../../../core/services/journal-name-service';
import { JournalIssuesLinksService } from '../../../core/services/journal-issues-links.service';
import { SafeImageComponent } from '../../../shared/safe-image/safe-image.component';

@Component({
  selector: 'app-journal-basic-result',
  imports: [RouterLink, TranslocoPipe, NgbTooltip, SafeImageComponent],
  templateUrl: './journal-basic-result.html',
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .journal-basic-cover {
      min-width: 131px;
      min-height: 185px;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalBasicResult {
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
  protected readonly names = inject(JournalNameService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = toSignal(this.transloco.langChanges$, {
    initialValue: this.transloco.getActiveLang(),
  });
  readonly country = computed(() => {
    this.language();
    const country = this.journal().country;
    return country ? this.names.getCountryName(country) || country.code || '-' : undefined;
  });
  readonly languages = computed(() => {
    this.language();
    return (this.journal().languages ?? [])
      .map((language) => this.names.getLanguageName(language) || language.code || '-')
      .join(', ');
  });
  readonly ratings = computed(() => {
    const journal = this.journal();
    return [
      {
        year: journal.currentIcvYear ?? this.icvYear(),
        points: journal.currentIcvPoints,
        status: journal.currentStatus ?? 'NOT_EVALUATED',
      },
      {
        year: journal.previousIcvYear ?? this.previousIcvYear(),
        points: journal.previousIcvPoints,
        status: journal.previousStatus ?? 'NOT_EVALUATED',
      },
    ];
  });
  readonly depositMetrics = computed(() => {
    const journal = this.journal();
    return [
      { label: 'fullText', value: journal.percentFullText },
      { label: 'abstract', value: journal.percentAbstracts },
      { label: 'keywords', value: journal.percentKeywords },
      { label: 'references', value: journal.percentReferences },
    ];
  });

  formatPoints(points: number | undefined): string {
    return points == null
      ? '-'
      : new Intl.NumberFormat(this.language(), {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        }).format(points);
  }
}
