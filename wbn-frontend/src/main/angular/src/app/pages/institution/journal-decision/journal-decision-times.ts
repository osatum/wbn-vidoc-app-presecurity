import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { JournalDecisionView } from '../../../core/services/journal-decision-api';
import { JournalDecisionTimeComparison } from './journal-decision-time-comparison';

@Component({
  selector: 'app-journal-decision-times',
  imports: [TranslocoPipe, JournalDecisionTimeComparison],
  template: `
    @for (key of rows; track key) {
      <app-journal-decision-time-comparison
        [label]="'decisionCard.time.' + key | transloco"
        [own]="card().annual?.times?.[key]"
        [comparison]="
          card().benchmarksVisible === true && card().benchmarks?.surveyYear === card().surveyYear
            ? card().benchmarks?.times?.[key]
            : null
        "
      />
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JournalDecisionTimes {
  readonly card = input.required<JournalDecisionView>();
  readonly rows = ['initialAssessment', 'review', 'publication'] as const;
}
