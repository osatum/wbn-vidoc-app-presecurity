import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { JournalDecisionShareComparison } from './journal-decision-share-comparison';

@Component({
  selector: 'app-journal-decision-international',
  imports: [TranslocoPipe, JournalDecisionShareComparison],
  template: `@for (key of rows; track key) {
    <app-journal-decision-share-comparison
      [label]="'decisionCard.international.' + key | transloco"
      [own]="
        card().annual?.surveyYear === card().surveyYear
          ? card().annual?.international?.[key]
          : undefined
      "
      [comparison]="
        card().benchmarksVisible === true && card().benchmarks?.surveyYear === card().surveyYear
          ? card().benchmarks?.international?.[key]
          : null
      "
    />
  }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionInternational {
  readonly card = input.required<JournalDecisionView>();
  readonly rows = ['authors', 'reviewers', 'scientificCouncil'] as const;
}
