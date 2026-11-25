import { Component, input } from '@angular/core';

@Component({
  selector: 'app-questionnaire-description-item',
  host: {
    class: 'row pb-0',
  },
  template: `
    <dt class="col-5 col-lg-3">{{ label() }}</dt>
    <dd class="col-7 col-lg-9 text-muted">
      <ng-content></ng-content>
    </dd>
  `,
})
export class QuestionnaireDescriptionItemComponent {
  readonly label = input.required<string>();
}
