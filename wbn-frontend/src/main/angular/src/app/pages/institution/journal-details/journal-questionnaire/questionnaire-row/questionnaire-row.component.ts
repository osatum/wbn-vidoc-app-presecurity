import { Component, inject, input } from '@angular/core';
import { JournalNameService } from '../../../../../core/services/journal-name-service';

@Component({
  selector: 'app-questionnaire-row',
  host: {
    class: 'd-block',
  },
  template: `
    <div class="row g-0" [class.bg-light-subtle]="highlight()">
      <div class="col-4 col-md-5 p-2 header-custom" [class.border-bottom]="borderBottom()">
        {{ description() }}
      </div>
      <div class="col-8 col-md-7 p-2 value-custom" [class.border-bottom]="borderBottom()">
        <ng-content></ng-content>
      </div>
    </div>
  `,
})
export class QuestionnaireRowComponent {
  readonly description = input.required<string>();
  readonly highlight = input(false);
  readonly borderBottom = input(true);

  protected readonly journalNameService = inject(JournalNameService);
}
